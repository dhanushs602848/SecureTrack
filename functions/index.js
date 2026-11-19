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
 */
exports.claimDevicePairingCode = functions.https.onCall(async (data, context) => {
  const { code, deviceName, model, androidVersion, installationId, batteryLevel } = data || {};

  if (!code || String(code).trim().length !== 8) {
    throw new functions.https.HttpsError(
      'invalid-argument',
      'A valid 8-digit pairing code is required.'
    );
  }

  if (!installationId) {
    throw new functions.https.HttpsError(
      'invalid-argument',
      'Device installation ID is required.'
    );
  }

  const codeHash = hashPairingCode(code);
  const codeRef = db.collection('pairing_codes').doc(codeHash);

  let ownerUid = null;
  const deviceId = installationId;

  // Run atomic transaction to validate and consume pairing code
  await db.runTransaction(async (transaction) => {
    const codeDoc = await transaction.get(codeRef);
    if (!codeDoc.exists) {
      throw new functions.https.HttpsError(
        'not-found',
        'Invalid or unrecognized pairing code.'
      );
    }

    const codeData = codeDoc.data();

    // Check attempts / anti-brute-force
    if (codeData.attempts && codeData.attempts >= 5) {
      transaction.update(codeRef, { status: 'LOCKED' });
      throw new functions.https.HttpsError(
        'permission-denied',
        'Pairing code locked due to excessive failed attempts.'
      );
    }

    if (codeData.status !== 'ACTIVE') {
      throw new functions.https.HttpsError(
        'failed-precondition',
        `Pairing code is ${codeData.status.toLowerCase()} and cannot be reused.`
      );
    }

    const expiresAt = codeData.expiresAt.toMillis();
    if (Date.now() > expiresAt) {
      transaction.update(codeRef, { status: 'EXPIRED' });
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
      claimedInstallationId: installationId
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
      installationId: installationId,
      paired: true,
      ownerUid: ownerUid,
      lastSeen: admin.firestore.FieldValue.serverTimestamp(),
      locationStatus: 'ENABLED',
      trackingStatus: 'ACTIVE'
    }, { merge: true });
  });

  // Issue restricted custom token with claims: role = 'device', ownerUid, deviceId
  const customToken = await admin.auth().createCustomToken(deviceId, {
    role: 'device',
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
 */
exports.revokePairedDevice = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated.');
  }

  const { deviceId } = data || {};
  if (!deviceId) {
    throw new functions.https.HttpsError('invalid-argument', 'Device ID required.');
  }

  const ownerUid = context.auth.uid;
  const deviceRef = db.collection('users').doc(ownerUid).collection('devices').doc(deviceId);
  const deviceDoc = await deviceRef.get();

  if (!deviceDoc.exists) {
    throw new functions.https.HttpsError('not-found', 'Device not found.');
  }

  await deviceRef.update({
    status: 'REVOKED',
    protectionEnabled: false,
    revokedAt: admin.firestore.FieldValue.serverTimestamp()
  });

  // Revoke device auth tokens if custom auth user exists
  try {
    await admin.auth().revokeRefreshTokens(deviceId);
  } catch (e) {
    // If device was not an independent auth user, ignore
  }

  return { success: true };
});
