const functions = require('firebase-functions');
const admin = require('firebase-admin');
const crypto = require('crypto');

if (!admin.apps.length) {
  admin.initializeApp();
}
const db = admin.firestore();

/**
 * Hash pairing code using SHA-256 for secure storage.
 * Plaintext codes are never stored or logged.
 */
function hashPairingCode(code) {
  return crypto.createHash('sha256').update(String(code).trim()).digest('hex');
}

/**
 * Hash an identifier (IP address, installation ID, etc.) for rate limit tracking.
 */
function hashIdentifier(identifier) {
  return crypto.createHash('sha256').update(String(identifier).trim()).digest('hex');
}

/**
 * Extract caller's IP address from raw request headers or connection.
 */
function getClientIp(context) {
  if (context && context.rawRequest) {
    const forwarded = context.rawRequest.headers && context.rawRequest.headers['x-forwarded-for'];
    if (forwarded) {
      const firstIp = String(forwarded).split(',')[0].trim();
      if (firstIp) return firstIp;
    }
    if (context.rawRequest.ip) {
      return String(context.rawRequest.ip).trim();
    }
  }
  return 'unknown-ip';
}

/**
 * Atomic check-and-increment rate limiter using Firestore transaction.
 * Guarantees that concurrent requests cannot race past the attempt threshold.
 */
async function checkAndIncrementRateLimit(bucketKey, maxAttempts, windowMs) {
  const docRef = db.collection('pairing_rate_limits').doc(bucketKey);
  const now = Date.now();

  return await db.runTransaction(async (transaction) => {
    const doc = await transaction.get(docRef);
    if (!doc.exists) {
      transaction.set(docRef, {
        attempts: 1,
        firstAttemptAt: admin.firestore.Timestamp.fromMillis(now),
        lastAttemptAt: admin.firestore.Timestamp.fromMillis(now)
      });
      return { allowed: true, attempts: 1 };
    }

    const data = doc.data();
    const firstAttempt = data.firstAttemptAt ? data.firstAttemptAt.toMillis() : 0;
    const elapsed = now - firstAttempt;

    if (elapsed > windowMs) {
      // Window expired, reset window atomically
      transaction.set(docRef, {
        attempts: 1,
        firstAttemptAt: admin.firestore.Timestamp.fromMillis(now),
        lastAttemptAt: admin.firestore.Timestamp.fromMillis(now)
      });
      return { allowed: true, attempts: 1 };
    }

    // Inside current window: if already at or above limit, reject
    if ((data.attempts || 0) >= maxAttempts) {
      return { allowed: false, attempts: data.attempts };
    }

    // Increment attempts atomically
    const newAttempts = (data.attempts || 0) + 1;
    transaction.update(docRef, {
      attempts: newAttempts,
      lastAttemptAt: admin.firestore.Timestamp.fromMillis(now)
    });

    return { allowed: true, attempts: newAttempts };
  });
}

/**
 * Reset rate limit bucket upon successful pairing.
 */
async function resetRateLimit(bucketKey) {
  try {
    await db.collection('pairing_rate_limits').doc(bucketKey).delete();
  } catch (e) {
    console.warn(`Could not reset rate limit bucket ${bucketKey}:`, e.message);
  }
}

/**
 * Callable: Create a secure single-use 8-digit device pairing code.
 * App Check enforced. Authenticated owner only.
 */
exports.createDevicePairingCode = functions
  .runWith({ enforceAppCheck: true })
  .https.onCall(async (data, context) => {
    // Defense-in-depth App Check validation
    if (process.env.NODE_ENV === 'production' && !context.app) {
      throw new functions.https.HttpsError(
        'failed-precondition',
        'The function must be called from an App Check verified application.'
      );
    }

    if (!context.auth || !context.auth.uid) {
      throw new functions.https.HttpsError(
        'unauthenticated',
        'User must be authenticated to generate a pairing code.'
      );
    }

    const ownerUid = context.auth.uid;
    const now = Date.now();

    // Check device limit (max 3 active devices per account)
    const MAX_ACTIVE_DEVICES = 3;
    const devicesSnap = await db.collection('users').doc(ownerUid).collection('devices').get();
    const activeDevices = devicesSnap.docs.filter((d) => d.data().status !== 'REVOKED');
    if (activeDevices.length >= MAX_ACTIVE_DEVICES) {
      throw new functions.https.HttpsError(
        'resource-exhausted',
        'You have reached the maximum of 3 active devices. Revoke an existing device to connect another.'
      );
    }

    // Check active codes limit (max 5 active codes per user)
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

    // Never log plaintext pairing code.
    return {
      code: codeStr,
      expiresAt: new Date(expiresAtMillis).toISOString()
    };
  });

/**
 * Callable: Claim a pairing code from an enrolling secondary phone.
 * Atomically consumes code and enrolls the phone under the owner's account.
 * App Check enforced. Rate limited on both server-derived IP and installation ID.
 */
exports.claimDevicePairingCode = functions
  .runWith({ enforceAppCheck: true })
  .https.onCall(async (data, context) => {
    // Defense-in-depth App Check validation
    if (process.env.NODE_ENV === 'production' && !context.app) {
      throw new functions.https.HttpsError(
        'failed-precondition',
        'The function must be called from an App Check verified application.'
      );
    }

    const { code, deviceName, model, androidVersion, installationId, batteryLevel } = data || {};

    if (!installationId || typeof installationId !== 'string' || installationId.trim().length === 0) {
      throw new functions.https.HttpsError(
        'invalid-argument',
        'Device installation ID is required.'
      );
    }

    const cleanInstallationId = installationId.trim();
    const clientIp = getClientIp(context);

    // Multi-factor rate limit identities:
    // 1. Caller installation identity
    // 2. Server-derived client IP address
    const instBucketKey = 'inst:' + hashIdentifier(cleanInstallationId);
    const ipBucketKey = 'ip:' + hashIdentifier(clientIp);

    const WINDOW_MS = 15 * 60 * 1000; // 15 minutes
    const MAX_ATTEMPTS = 5;

    // Atomically increment and verify both rate limit buckets
    // This prevents concurrent requests from bypassing the rate limit threshold
    const [instRate, ipRate] = await Promise.all([
      checkAndIncrementRateLimit(instBucketKey, MAX_ATTEMPTS, WINDOW_MS),
      checkAndIncrementRateLimit(ipBucketKey, MAX_ATTEMPTS * 2, WINDOW_MS)
    ]);

    if (!instRate.allowed || !ipRate.allowed) {
      throw new functions.https.HttpsError(
        'resource-exhausted',
        'Too many failed pairing attempts. Please wait 15 minutes before trying again.'
      );
    }

    if (!code || String(code).trim().length !== 8) {
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
    const now = Date.now();

    await db.runTransaction(async (transaction) => {
      const codeDoc = await transaction.get(codeRef);
      if (!codeDoc.exists) {
        throw new functions.https.HttpsError(
          'not-found',
          'Invalid or unrecognized pairing code.'
        );
      }

      const codeData = codeDoc.data();

      // Check attempts on the code document itself
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

      // Concurrency-safe device quota enforcement inside the transaction (Max 3 active devices)
      const MAX_ACTIVE_DEVICES = 3;
      const devicesSnap = await transaction.get(
        db.collection('users').doc(ownerUid).collection('devices')
      );
      const activeDevices = devicesSnap.docs.filter(
        (d) => d.data().status !== 'REVOKED' && d.id !== deviceId
      );
      if (activeDevices.length >= MAX_ACTIVE_DEVICES) {
        throw new functions.https.HttpsError(
          'resource-exhausted',
          'You have reached the maximum of 3 active devices. Revoke an existing device to connect another.'
        );
      }

      // Atomically claim the single-use code
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

    // Reset rate limits on successful claim
    await Promise.all([
      resetRateLimit(instBucketKey),
      resetRateLimit(ipBucketKey)
    ]);

    // Issue restricted custom token with claims: role = 'device', revoked = false, ownerUid, deviceId
    const customToken = await admin.auth().createCustomToken(deviceId, {
      role: 'device',
      revoked: false,
      ownerUid: ownerUid,
      deviceId: deviceId
    });

    // Never log plaintext tokens or codes.
    return {
      success: true,
      ownerUid: ownerUid,
      deviceId: deviceId,
      customToken: customToken
    };
  });

/**
 * Callable: Server-side approval of a pairing invitation with concurrency-safe device quota check.
 * App Check enforced.
 */
exports.approvePairingInvitation = functions
  .runWith({ enforceAppCheck: true })
  .https.onCall(async (data, context) => {
    if (!context.auth || !context.auth.uid) {
      throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated.');
    }

    const { tokenHash } = data || {};
    if (!tokenHash || typeof tokenHash !== 'string') {
      throw new functions.https.HttpsError('invalid-argument', 'Valid token hash is required.');
    }

    const ownerUid = context.auth.uid;
    const invRef = db.collection('pairing_invitations').doc(tokenHash.trim());
    const MAX_ACTIVE_DEVICES = 3;

    let deviceId = null;

    await db.runTransaction(async (transaction) => {
      const invDoc = await transaction.get(invRef);
      if (!invDoc.exists) {
        throw new functions.https.HttpsError('not-found', 'Pairing invitation not found.');
      }

      const invData = invDoc.data();
      if (invData.ownerUid !== ownerUid) {
        throw new functions.https.HttpsError('permission-denied', 'Unauthorized to approve this pairing request.');
      }

      if (invData.status !== 'PENDING_APPROVAL') {
        throw new functions.https.HttpsError(
          'failed-precondition',
          `Invitation is ${invData.status ? invData.status.toLowerCase() : 'invalid'} and cannot be approved.`
        );
      }

      deviceId = invData.claimedDeviceId;
      if (!deviceId) {
        throw new functions.https.HttpsError('failed-precondition', 'Missing claimed device ID.');
      }

      // Check device quota inside the transaction: count non-REVOKED devices
      const devicesSnap = await transaction.get(
        db.collection('users').doc(ownerUid).collection('devices')
      );
      const activeDevices = devicesSnap.docs.filter(
        (d) => d.data().status !== 'REVOKED' && d.id !== deviceId
      );
      if (activeDevices.length >= MAX_ACTIVE_DEVICES) {
        throw new functions.https.HttpsError(
          'resource-exhausted',
          'You have reached the maximum of 3 active devices. Revoke an existing device to connect another.'
        );
      }

      // Generate 256-bit cryptographically secure shared secret
      const bleSharedSecret = crypto.randomBytes(32).toString('hex');

      // 1. Register device under users/{ownerUid}/devices/{deviceId}
      const devRef = db.collection('users').doc(ownerUid).collection('devices').doc(deviceId);
      transaction.set(devRef, {
        id: deviceId,
        ownerUid: ownerUid,
        deviceAuthUid: invData.claimedByUid,
        deviceName: invData.deviceInfo?.deviceName || 'Paired Android Device',
        model: invData.deviceInfo?.model || 'Android Phone',
        androidVersion: invData.deviceInfo?.androidVersion || '14',
        batteryLevel: typeof invData.deviceInfo?.batteryLevel === 'number' ? invData.deviceInfo.batteryLevel : 100,
        status: 'ONLINE',
        paired: true,
        protectionEnabled: true,
        installationId: deviceId,
        bleSharedSecret: bleSharedSecret,
        lastSeen: admin.firestore.FieldValue.serverTimestamp(),
        locationStatus: 'ENABLED',
        trackingStatus: 'ACTIVE',
        networkStatus: 'CONNECTED'
      }, { merge: true });

      // 2. Mark invitation APPROVED
      transaction.update(invRef, {
        status: 'APPROVED',
        approvedAt: admin.firestore.FieldValue.serverTimestamp(),
        bleSharedSecret: bleSharedSecret
      });

      // 3. Append audit log
      const auditRef = db.collection('users').doc(ownerUid).collection('audit_logs').doc();
      transaction.set(auditRef, {
        ownerUid: ownerUid,
        action: 'DEVICE_APPROVED',
        targetDeviceId: deviceId,
        deviceName: invData.deviceInfo?.deviceName || 'Paired Android Device',
        initiatedFrom: 'SERVER_CONSOLE',
        timestamp: admin.firestore.FieldValue.serverTimestamp()
      });
    });

    return { success: true, deviceId: deviceId };
  });

/**
 * Callable: Revoke a paired device from the owner account.
 * App Check enforced.
 * Unconditionally sets device status to REVOKED in Firestore to immediately cut off access,
 * and records any token or claims revocation errors on the device document.
 */
exports.revokePairedDevice = functions
  .runWith({ enforceAppCheck: true })
  .https.onCall(async (data, context) => {
    // Defense-in-depth App Check validation
    if (process.env.NODE_ENV === 'production' && !context.app) {
      throw new functions.https.HttpsError(
        'failed-precondition',
        'The function must be called from an App Check verified application.'
      );
    }

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

    // Step 1: Unconditionally mark device status as REVOKED in Firestore
    // This immediately revokes all permissions in Firestore security rules
    await deviceRef.update({
      status: 'REVOKED',
      protectionEnabled: false,
      trackingStatus: 'STOPPED',
      revokedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    const revocationErrors = [];

    // Step 2: Revoke device refresh tokens
    try {
      await admin.auth().revokeRefreshTokens(cleanDeviceId);
    } catch (e) {
      const errMsg = `Refresh token revocation failed: ${e.message}`;
      console.error(errMsg);
      revocationErrors.push(errMsg);
    }

    // Step 3: Mark custom claims as revoked
    try {
      await admin.auth().setCustomUserClaims(cleanDeviceId, {
        role: 'revoked',
        revoked: true,
        ownerUid: ownerUid,
        deviceId: cleanDeviceId
      });
    } catch (e) {
      const errMsg = `Custom user claims update failed: ${e.message}`;
      console.error(errMsg);
      revocationErrors.push(errMsg);
    }

    // Step 4: Record security errors on the device document if any secondary steps failed
    if (revocationErrors.length > 0) {
      await deviceRef.update({
        revocationWarnings: revocationErrors,
        revocationWarningRecordedAt: admin.firestore.FieldValue.serverTimestamp()
      });
      return {
        success: true,
        deviceStatus: 'REVOKED',
        warnings: revocationErrors
      };
    }

    return { success: true, deviceStatus: 'REVOKED' };
  });

/**
 * Firestore trigger: When a remote command is issued by an owner or web dashboard,
 * send a high-priority FCM push wake-up data message to the target device.
 */
exports.onDeviceCommandCreated = functions.firestore
  .document('users/{userId}/devices/{deviceId}/commands/{commandId}')
  .onCreate(async (snap, context) => {
    const { userId, deviceId, commandId } = context.params;
    const commandData = snap.data();

    if (!commandData) {
      console.log(`Command ${commandId} contains no data, skipping FCM dispatch.`);
      return null;
    }

    try {
      // 1. Fetch target device document to retrieve its registered FCM token
      const deviceRef = db.collection('users').doc(userId).collection('devices').doc(deviceId);
      const deviceDoc = await deviceRef.get();

      if (!deviceDoc.exists) {
        console.warn(`Target device ${deviceId} under owner ${userId} not found.`);
        return null;
      }

      const deviceData = deviceDoc.data();
      const fcmToken = deviceData.fcmToken;

      if (!fcmToken || typeof fcmToken !== 'string' || fcmToken.trim().length === 0) {
        console.log(`Device ${deviceId} has no FCM token registered; skipping FCM push.`);
        return null;
      }

      // 2. Build high-priority data-only payload
      const payload = {
        token: fcmToken.trim(),
        data: {
          commandId: String(commandId),
          targetDeviceId: String(deviceId),
          ownerUid: String(userId),
          type: String(commandData.commandType || commandData.type || 'COMMAND_WAKE'),
          commandType: String(commandData.commandType || commandData.type || 'COMMAND_WAKE'),
          requestId: String(commandData.requestId || commandId),
          timestamp: String(commandData.createdAt || Date.now())
        },
        android: {
          priority: 'high'
        }
      };

      // 3. Send message via Firebase Admin Messaging
      const response = await admin.messaging().send(payload);
      console.log(`FCM push wake-up sent for command ${commandId} to device ${deviceId}: ${response}`);

      await snap.ref.update({
        fcmDispatchedAt: admin.firestore.FieldValue.serverTimestamp(),
        fcmMessageId: response
      }).catch(err => console.warn(`Could not update fcmDispatchedAt on command: ${err.message}`));

      return response;
    } catch (error) {
      console.error(`Error sending FCM push for command ${commandId}:`, error);

      // Clean up stale or invalid tokens automatically
      if (error.code === 'messaging/registration-token-not-registered' ||
          error.code === 'messaging/invalid-registration-token') {
        console.warn(`Stale FCM token detected for device ${deviceId}. Clearing token.`);
        try {
          await db.collection('users').doc(userId).collection('devices').doc(deviceId).update({
            fcmToken: admin.firestore.FieldValue.delete(),
            fcmTokenInvalidatedAt: admin.firestore.FieldValue.serverTimestamp()
          });
        } catch (cleanupErr) {
          console.error(`Failed to clear invalid FCM token: ${cleanupErr.message}`);
        }
      }
      return null;
    }
  });
