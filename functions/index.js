const functions = require('firebase-functions');
const admin = require('firebase-admin');
const crypto = require('crypto');

if (!admin.apps.length) {
  admin.initializeApp();
}
const db = admin.firestore();

/**
 * Hash pairing code using SHA-256 for secure storage.
 */
function hashPairingCode(code) {
  return crypto.createHash('sha256').update(String(code).trim()).digest('hex');
}

/**
 * Hash installation ID using SHA-256 for rate limit tracking.
 */
function hashInstallationId(id) {
  return crypto.createHash('sha256').update(String(id).trim()).digest('hex');
}

/**
 * Callable: Create a secure single-use 8-digit device pairing code.
 * Only authenticated owners can create codes.
 */
exports.createDevicePairingCode = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'User must be authenticated to generate a pairing code.'
    );
  }

  const ownerUid = context.auth.uid;
  const now = Date.now();

  // Rate limiting: check recent active codes created by this user
  const recentCodes = await db.collection('pairing_codes')
    .where('ownerUid', '==', ownerUid)
    .where('status', '==', 'ACTIVE')
    .get();

  if (recentCodes.size >= 5) {
    throw new functions.https.HttpsError(
      'resource-exhausted',
      'Too many active pairing codes. Please wait or use an existing code.'
    );
  }

  // Generate cryptographically random 8-digit code (10000000 to 99999999)
  const codeNum = crypto.randomInt(10000000, 100000000);
  const codeStr = codeNum.toString();
  const codeHash = hashPairingCode(codeStr);
  const expiresAtMillis = now + 10 * 60 * 1000; // 10 minutes

  await db.collection('pairing_codes').doc(codeHash).set({
    ownerUid: ownerUid,
    status: 'ACTIVE',
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    expiresAt: admin.firestore.Timestamp.fromMillis(expiresAtMillis),
    attempts: 0
  });

  return {
    code: codeStr,
    expiresAt: new Date(expiresAtMillis).toISOString()
  };
});

/**
 * Callable: Claim a pairing code from an enrolling secondary phone.
 * Atomically consumes code and enrolls the phone under the owner's account.
 * Enforces per-installation rate limiting across codes to prevent brute-force attacks.
 */
exports.claimDevicePairingCode = functions.https.onCall(async (data, context) => {
  const { code, deviceName, model, androidVersion, installationId, batteryLevel } = data || {};

  if (!installationId || typeof installationId !== 'string' || installationId.trim().length === 0) {
    throw new functions.https.HttpsError(
      'invalid-argument',
      'Device installation ID is required.'
    );
  }

  const cleanInstallationId = installationId.trim();
  const instHash = hashInstallationId(cleanInstallationId);
  const rateLimitRef = db.collection('pairing_rate_limits').doc(instHash);

  const now = Date.now();
  const WINDOW_MS = 15 * 60 * 1000; // 15-minute sliding window
  const MAX_ATTEMPTS = 5;

  // Check rate limit document across all attempted codes for this installation
  const rateDoc = await rateLimitRef.get();
  if (rateDoc.exists) {
    const rateData = rateDoc.data();
    const firstAttempt = rateData.firstAttemptAt ? rateData.firstAttemptAt.toMillis() : 0;
    if (now - firstAttempt < WINDOW_MS) {
      if ((rateData.attempts || 0) >= MAX_ATTEMPTS) {
        throw new functions.https.HttpsError(
          'resource-exhausted',
          'Too many failed pairing attempts. Please wait 15 minutes before trying again.'
        );
      }
    }
  }

  // Helper to record failed attempt for this installation
  const recordFailure = async () => {
    try {
      await db.runTransaction(async (t) => {
        const snap = await t.get(rateLimitRef);
        if (!snap.exists) {
          t.set(rateLimitRef, {
            attempts: 1,
            firstAttemptAt: admin.firestore.Timestamp.fromMillis(now),
            lastAttemptAt: admin.firestore.Timestamp.fromMillis(now)
          });
        } else {
          const data = snap.data();
          const first = data.firstAttemptAt ? data.firstAttemptAt.toMillis() : 0;
          if (now - first > WINDOW_MS) {
            // Reset window after expiry
            t.set(rateLimitRef, {
              attempts: 1,
              firstAttemptAt: admin.firestore.Timestamp.fromMillis(now),
              lastAttemptAt: admin.firestore.Timestamp.fromMillis(now)
            });
          } else {
            t.update(rateLimitRef, {
              attempts: (data.attempts || 0) + 1,
              lastAttemptAt: admin.firestore.Timestamp.fromMillis(now)
            });
          }
        }
      });
    } catch (e) {
      console.error('Failed to update rate limit record:', e);
    }
  };

  if (!code || String(code).trim().length !== 8) {
    await recordFailure();
    throw new functions.https.HttpsError(
      'invalid-argument',
      'A valid 8-digit pairing code is required.'
    );
  }

  const cleanCode = String(code).trim();
  const codeHash = hashPairingCode(cleanCode);
  const codeRef = db.collection('pairing_codes').doc(codeHash);

  let ownerUid = null;
  const deviceId = cleanInstallationId;

  try {
    await db.runTransaction(async (transaction) => {
      const codeDoc = await transaction.get(codeRef);
      if (!codeDoc.exists) {
        throw new functions.https.HttpsError(
          'not-found',
          'Invalid or unrecognized pairing code.'
        );
      }

      const codeData = codeDoc.data();

      // Check attempts on the code itself
      if (codeData.attempts && codeData.attempts >= MAX_ATTEMPTS) {
        transaction.update(codeRef, { status: 'LOCKED' });
        throw new functions.https.HttpsError(
          'permission-denied',
          'Pairing code locked due to excessive failed attempts.'
        );
      }

      if (codeData.status !== 'ACTIVE') {
        transaction.update(codeRef, { attempts: (codeData.attempts || 0) + 1 });
        throw new functions.https.HttpsError(
          'failed-precondition',
          `Pairing code is ${codeData.status.toLowerCase()} and cannot be reused.`
        );
      }

      const expiresAt = codeData.expiresAt ? codeData.expiresAt.toMillis() : 0;
      if (now > expiresAt) {
        transaction.update(codeRef, {
          status: 'EXPIRED',
          attempts: (codeData.attempts || 0) + 1
        });
        throw new functions.https.HttpsError(
          'deadline-exceeded',
          'Pairing code has expired. Please request a new code from the owner.'
        );
      }

      ownerUid = codeData.ownerUid;

      // Mark code as claimed atomically
      transaction.update(codeRef, {
        status: 'CLAIMED',
        claimedAt: admin.firestore.FieldValue.serverTimestamp(),
        claimedInstallationId: cleanInstallationId
      });

      // Register or update device under owner's account
      const deviceRef = db.collection('users').doc(ownerUid)
        .collection('devices').doc(deviceId);

      transaction.set(deviceRef, {
        id: deviceId,
        deviceName: deviceName || 'Paired Android Device',
        model: model || 'Android',
        androidVersion: androidVersion || '14',
        batteryLevel: typeof batteryLevel === 'number' ? batteryLevel : 100,
        status: 'ONLINE',
        protectionEnabled: true,
        installationId: cleanInstallationId,
        paired: true,
        ownerUid: ownerUid,
        lastSeen: admin.firestore.FieldValue.serverTimestamp(),
        locationStatus: 'ENABLED',
        trackingStatus: 'ACTIVE'
      }, { merge: true });
    });
  } catch (err) {
    // Record failed attempt on caller/installation
    await recordFailure();
    throw err;
  }

  // Reset rate limits for this installation upon successful claim
  try {
    await rateLimitRef.delete();
  } catch (e) {
    console.warn('Could not reset rate limit ref:', e);
  }

  // Issue restricted custom token with claims: role = 'device', revoked = false, ownerUid, deviceId
  const customToken = await admin.auth().createCustomToken(deviceId, {
    role: 'device',
    revoked: false,
    ownerUid: ownerUid,
    deviceId: deviceId
  });

  return {
    success: true,
    ownerUid: ownerUid,
    deviceId: deviceId,
    customToken: customToken
  };
});

/**
 * Callable: Revoke a paired device from the owner account.
 * Sets device status to REVOKED, revokes refresh tokens, and marks auth custom claims as revoked.
 */
exports.revokePairedDevice = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated.');
  }

  const { deviceId } = data || {};
  if (!deviceId || typeof deviceId !== 'string') {
    throw new functions.https.HttpsError('invalid-argument', 'Device ID required.');
  }

  const ownerUid = context.auth.uid;
  const cleanDeviceId = deviceId.trim();
  const deviceRef = db.collection('users').doc(ownerUid).collection('devices').doc(cleanDeviceId);
  const deviceDoc = await deviceRef.get();

  if (!deviceDoc.exists) {
    throw new functions.https.HttpsError('not-found', 'Device not found.');
  }

  await deviceRef.update({
    status: 'REVOKED',
    protectionEnabled: false,
    trackingStatus: 'STOPPED',
    revokedAt: admin.firestore.FieldValue.serverTimestamp()
  });

  // Revoke device refresh tokens
  try {
    await admin.auth().revokeRefreshTokens(cleanDeviceId);
  } catch (e) {
    console.warn('Could not revoke refresh tokens for device:', cleanDeviceId, e.message);
  }

  // Mark custom claims as revoked
  try {
    await admin.auth().setCustomUserClaims(cleanDeviceId, {
      role: 'revoked',
      revoked: true,
      ownerUid: ownerUid,
      deviceId: cleanDeviceId
    });
  } catch (e) {
    console.warn('Could not update custom user claims for revoked device:', cleanDeviceId, e.message);
  }

  return { success: true };
});
