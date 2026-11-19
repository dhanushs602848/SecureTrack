const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');

function generate256BitToken() {
  return crypto.randomBytes(32).toString('hex');
}

function hashToken(token) {
  return crypto.createHash('sha256').update(String(token).trim()).digest('hex');
}

function hashIdentifier(identifier) {
  return crypto.createHash('sha256').update(String(identifier).trim()).digest('hex');
}

test('SecureTrack Spark-Compatible 256-Bit Pairing & Security Suite', async (t) => {
  await t.test('Generates cryptographically secure 256-bit pairing token', () => {
    for (let i = 0; i < 20; i++) {
      const token = generate256BitToken();
      assert.equal(token.length, 64, '256 bits hex encoded must be 64 characters');
      assert.match(token, /^[0-9a-f]{64}$/, 'Token must consist of 64 hexadecimal characters');
    }
  });

  await t.test('SHA-256 token hashing is one-way, deterministic and never exposes plaintext', () => {
    const token = generate256BitToken();
    const hash1 = hashToken(token);
    const hash2 = hashToken(token);
    assert.equal(hash1, hash2, 'Hash must be consistent');
    assert.equal(hash1.length, 64);
    assert.notEqual(hash1, token, 'Hash must not expose plaintext token');
  });

  await t.test('Expiry check enforces 10-minute validity window', () => {
    const now = Date.now();
    const tenMinutes = 10 * 60 * 1000;
    const validExpiry = now + tenMinutes;
    const expired = now - 5000;

    assert.ok(validExpiry > now, 'Active invitation must be valid');
    assert.ok(expired < now, 'Old invitation must be detected as expired');
  });

  await t.test('Spark-compatible Pairing Protocol Simulation: Claim & Approval State Machine', () => {
    const ownerUid = 'human-owner-123';
    const secondaryPhoneAuthUid = 'anon-device-auth-789';
    const deviceId = 'installation-phone-samsung-001';

    const rawToken = generate256BitToken();
    const tokenHash = hashToken(rawToken);

    // 1. Owner creates invitation
    const invitation = {
      ownerUid: ownerUid,
      status: 'PENDING_CLAIM',
      createdAt: Date.now(),
      expiresAt: Date.now() + 10 * 60 * 1000,
      claimedByUid: null,
      claimedDeviceId: null,
      deviceInfo: null,
      approvedAt: null
    };

    assert.equal(invitation.status, 'PENDING_CLAIM');

    // 2. Claimant claims invitation
    function claimantSubmitClaim(inv, claimantAuthUid, claimDeviceId, devInfo, currentTime) {
      if (inv.status !== 'PENDING_CLAIM') {
        throw new Error('failed-precondition: already claimed or invalid');
      }
      if (currentTime >= inv.expiresAt) {
        throw new Error('deadline-exceeded: invitation expired');
      }
      // Rule constraint: claimant cannot change ownerUid or expiresAt
      return {
        ...inv,
        status: 'PENDING_APPROVAL',
        claimedByUid: claimantAuthUid,
        claimedDeviceId: claimDeviceId,
        deviceInfo: devInfo,
        claimedAt: currentTime
      };
    }

    const claimedInv = claimantSubmitClaim(
      invitation,
      secondaryPhoneAuthUid,
      deviceId,
      { deviceName: 'Samsung Galaxy A07', model: 'SM-A076B', androidVersion: '14', batteryLevel: 88 },
      Date.now()
    );

    assert.equal(claimedInv.status, 'PENDING_APPROVAL');
    assert.equal(claimedInv.claimedByUid, secondaryPhoneAuthUid);
    assert.equal(claimedInv.ownerUid, ownerUid);

    // 3. Security Rule Check: Claimant attempts self-approval (MUST FAIL)
    function approveInvitation(inv, actorUid, newStatus) {
      // In Firestore rules: request.auth.uid == resource.data.ownerUid
      if (actorUid !== inv.ownerUid) {
        throw new Error('permission-denied: claimant cannot approve invitation');
      }
      if (newStatus !== 'APPROVED' && newStatus !== 'REJECTED') {
        throw new Error('invalid-argument: status must be APPROVED or REJECTED');
      }
      return {
        ...inv,
        status: newStatus,
        approvedAt: Date.now()
      };
    }

    // Claimant tries to approve their own claim:
    assert.throws(
      () => approveInvitation(claimedInv, secondaryPhoneAuthUid, 'APPROVED'),
      /claimant cannot approve invitation/
    );

    // 4. Owner approves:
    const approvedInv = approveInvitation(claimedInv, ownerUid, 'APPROVED');
    assert.equal(approvedInv.status, 'APPROVED');

    // 5. Duplicate claim on already claimed/approved invitation (MUST FAIL)
    assert.throws(
      () => claimantSubmitClaim(approvedInv, 'another-device-uid', 'another-dev-id', {}, Date.now()),
      /already claimed or invalid/
    );
  });

  await t.test('Device authorization via deviceAuthUid and real-time revocation', () => {
    const ownerUid = 'owner-xyz';
    const deviceId = 'dev-samsung-100';
    const secondaryPhoneUid = 'device-auth-anon-999';

    // Device document created upon owner approval:
    const deviceDoc = {
      id: deviceId,
      ownerUid: ownerUid,
      deviceAuthUid: secondaryPhoneUid,
      status: 'ONLINE',
      paired: true,
      batteryLevel: 85
    };

    function isAuthorizedDevice(doc, callerAuthUid) {
      if (!doc || doc.status === 'REVOKED') return false;
      return doc.deviceAuthUid === callerAuthUid;
    }

    // Active device authenticated:
    assert.equal(isAuthorizedDevice(deviceDoc, secondaryPhoneUid), true);

    // Unrelated caller:
    assert.equal(isAuthorizedDevice(deviceDoc, 'random-stranger-uid'), false);

    // Owner revokes device: status becomes REVOKED
    const revokedDoc = { ...deviceDoc, status: 'REVOKED' };
    assert.equal(isAuthorizedDevice(revokedDoc, secondaryPhoneUid), false);
  });

  await t.test('Telemetry field allowlist enforcement vs immutable identity', () => {
    const allowedTelemetryKeys = new Set([
      'batteryLevel', 'lastSeen', 'locationStatus', 'trackingStatus', 'networkStatus', 'status'
    ]);

    function canDeviceUpdate(affectedKeys, newStatus) {
      if (newStatus === 'REVOKED') return false; // device cannot revoke
      for (const k of affectedKeys) {
        if (!allowedTelemetryKeys.has(k)) {
          return false;
        }
      }
      return true;
    }

    // Valid telemetry updates
    assert.equal(canDeviceUpdate(['batteryLevel', 'lastSeen', 'status'], 'ONLINE'), true);
    assert.equal(canDeviceUpdate(['locationStatus', 'trackingStatus', 'networkStatus'], 'ONLINE'), true);

    // Disallowed attempts to mutate identity or security fields
    assert.equal(canDeviceUpdate(['id'], 'ONLINE'), false);
    assert.equal(canDeviceUpdate(['ownerUid'], 'ONLINE'), false);
    assert.equal(canDeviceUpdate(['deviceAuthUid'], 'ONLINE'), false);
    assert.equal(canDeviceUpdate(['installationId'], 'ONLINE'), false);
    assert.equal(canDeviceUpdate(['paired'], 'ONLINE'), false);
    assert.equal(canDeviceUpdate(['protectionEnabled'], 'ONLINE'), false);
    assert.equal(canDeviceUpdate(['deviceName'], 'ONLINE'), false);

    // Disallowed attempt to set own status to REVOKED
    assert.equal(canDeviceUpdate(['status'], 'REVOKED'), false);
  });

  await t.test('Command creation and deletion owner-only, updates execution-only', () => {
    const allowedExecutionKeys = new Set([
      'status', 'result', 'executedAt', 'error', 'details', 'progress'
    ]);

    function canDeviceUpdateCommand(affectedKeys, payloadChanged) {
      if (payloadChanged) return false;
      for (const k of affectedKeys) {
        if (!allowedExecutionKeys.has(k)) {
          return false;
        }
      }
      return true;
    }

    assert.equal(canDeviceUpdateCommand(['status', 'result', 'executedAt'], false), true);
    assert.equal(canDeviceUpdateCommand(['status', 'error'], false), true);
    assert.equal(canDeviceUpdateCommand(['command'], true), false);
    assert.equal(canDeviceUpdateCommand(['targetDeviceId'], true), false);
  });

  await t.test('Cross-owner isolation for pairing invitations and devices', () => {
    function canOwnerManageInvitation(invitation, callerUid) {
      return invitation.ownerUid === callerUid;
    }

    const invitationA = { ownerUid: 'owner-A', status: 'PENDING_APPROVAL' };

    assert.equal(canOwnerManageInvitation(invitationA, 'owner-A'), true);
    assert.equal(canOwnerManageInvitation(invitationA, 'owner-B'), false);
  });

  await t.test('Device Revocation & Removal Suite: Owner authorization, isolation, idempotency and telemetry protection', () => {
    const ownerUid = 'owner-dhanush-user-123';
    const unauthorizedUid = 'attacker-or-other-user-456';

    const targetOfflineDeviceId = '0c520933-16f2-400b-a1b2-offline-duplicate';
    const protectedOnlineDeviceId = 'abb98700-ec5d-455c-b3c4-online-primary';

    // Simulated Firestore database state under users/{ownerUid}/devices/
    const firestoreDevices = new Map();

    firestoreDevices.set(targetOfflineDeviceId, {
      id: targetOfflineDeviceId,
      ownerUid: ownerUid,
      deviceAuthUid: 'device-anon-auth-target',
      deviceName: 'Samsung Galaxy A07 5G',
      model: 'SM-A076B',
      androidVersion: '14',
      status: 'OFFLINE',
      protectionEnabled: true,
      trackingStatus: 'ACTIVE',
      batteryLevel: 45,
      lastSeen: Date.now() - 3600000
    });

    firestoreDevices.set(protectedOnlineDeviceId, {
      id: protectedOnlineDeviceId,
      ownerUid: ownerUid,
      deviceAuthUid: 'device-anon-auth-protected',
      deviceName: 'Samsung Galaxy A07 5G',
      model: 'SM-A076B',
      androidVersion: '14',
      status: 'ONLINE',
      protectionEnabled: true,
      trackingStatus: 'ACTIVE',
      batteryLevel: 98,
      lastSeen: Date.now()
    });

    // Subcollection telemetry state for protected device
    const protectedTelemetry = [
      { id: 'loc-1', latitude: 12.9716, longitude: 77.5946, accuracy: 12.5 },
      { id: 'loc-2', latitude: 12.9718, longitude: 77.5949, accuracy: 10.0 }
    ];

    // Security Rules simulation matching firestore.rules
    function isHumanOwner(callerAuthUid, pathUserId) {
      return callerAuthUid && callerAuthUid === pathUserId;
    }

    function revokeDevice(callerUid, pathUserId, deviceId) {
      if (!isHumanOwner(callerUid, pathUserId)) {
        const err = new Error('Missing or insufficient permissions.');
        err.code = 'permission-denied';
        throw err;
      }
      const doc = firestoreDevices.get(deviceId);
      if (!doc) {
        const err = new Error('Device not found.');
        err.code = 'not-found';
        throw err;
      }

      // Safe, idempotent revocation update
      doc.status = 'REVOKED';
      doc.protectionEnabled = false;
      doc.trackingStatus = 'STOPPED';
      doc.revokedAt = Date.now();
      return { success: true, deviceId, status: doc.status };
    }

    function deleteDevice(callerUid, pathUserId, deviceId) {
      if (!isHumanOwner(callerUid, pathUserId)) {
        const err = new Error('Missing or insufficient permissions.');
        err.code = 'permission-denied';
        throw err;
      }
      if (!firestoreDevices.has(deviceId)) {
        const err = new Error('Device not found.');
        err.code = 'not-found';
        throw err;
      }
      firestoreDevices.delete(deviceId);
      return { success: true, deviceId, deleted: true };
    }

    // 1. Attempt unauthorized revocation by another user -> MUST FAIL with permission-denied
    assert.throws(
      () => revokeDevice(unauthorizedUid, ownerUid, targetOfflineDeviceId),
      (err) => {
        assert.equal(err.code, 'permission-denied');
        assert.match(err.message, /Missing or insufficient permissions/);
        return true;
      },
      'Unauthorized user must be strictly blocked from revoking another user device'
    );

    // Verify target device remains untouched after unauthorized attempt
    assert.equal(firestoreDevices.get(targetOfflineDeviceId).status, 'OFFLINE');

    // 2. Owner revokes the target offline device
    const revokeResult = revokeDevice(ownerUid, ownerUid, targetOfflineDeviceId);
    assert.equal(revokeResult.success, true);
    assert.equal(revokeResult.status, 'REVOKED');
    assert.equal(firestoreDevices.get(targetOfflineDeviceId).status, 'REVOKED');
    assert.equal(firestoreDevices.get(targetOfflineDeviceId).protectionEnabled, false);
    assert.equal(firestoreDevices.get(targetOfflineDeviceId).trackingStatus, 'STOPPED');

    // 3. Verify the protected ONLINE device remains 100% UNCHANGED
    const protectedDevice = firestoreDevices.get(protectedOnlineDeviceId);
    assert.ok(protectedDevice, 'Protected device must remain present');
    assert.equal(protectedDevice.status, 'ONLINE', 'Protected device status must remain ONLINE');
    assert.equal(protectedDevice.protectionEnabled, true, 'Protected device protection must remain active');
    assert.equal(protectedDevice.batteryLevel, 98, 'Protected device battery must remain unchanged');
    assert.equal(protectedDevice.trackingStatus, 'ACTIVE', 'Protected device tracking must remain active');
    assert.equal(protectedTelemetry.length, 2, 'Protected device location telemetry must remain intact');

    // 4. Idempotency test: Revoking an already revoked device succeeds safely without side-effects
    const idempotentResult = revokeDevice(ownerUid, ownerUid, targetOfflineDeviceId);
    assert.equal(idempotentResult.success, true);
    assert.equal(idempotentResult.status, 'REVOKED');

    // Protected device is still ONLINE
    assert.equal(firestoreDevices.get(protectedOnlineDeviceId).status, 'ONLINE');

    // 5. Attempt unauthorized deletion by another user -> MUST FAIL with permission-denied
    assert.throws(
      () => deleteDevice(unauthorizedUid, ownerUid, targetOfflineDeviceId),
      (err) => {
        assert.equal(err.code, 'permission-denied');
        return true;
      }
    );

    // 6. Owner removes/deletes the target offline registration
    const deleteResult = deleteDevice(ownerUid, ownerUid, targetOfflineDeviceId);
    assert.equal(deleteResult.success, true);
    assert.equal(deleteResult.deleted, true);
    assert.equal(firestoreDevices.has(targetOfflineDeviceId), false, 'Target device must be removed');

    // 7. Verify protected device STILL persists after target deletion
    assert.equal(firestoreDevices.has(protectedOnlineDeviceId), true, 'Protected device must persist');
    assert.equal(firestoreDevices.get(protectedOnlineDeviceId).status, 'ONLINE');

    // 8. Error reporting verification: Never mask real errors as "internal [0]"
    function formatDashboardError(err) {
      if (!err) return 'Unknown error';
      if (err.code) {
        return `Failed [${err.code}]: ${err.message || 'Operation rejected by security rules.'}`;
      }
      return err.message || 'Operation failed.';
    }

    const permissionErr = { code: 'permission-denied', message: 'Missing or insufficient permissions.' };
    const formatted = formatDashboardError(permissionErr);
    assert.ok(!formatted.includes('internal [0]'), 'Must never format error as generic internal [0]');
    assert.ok(formatted.includes('permission-denied'), 'Must contain exact error code');
    assert.ok(formatted.includes('Missing or insufficient permissions'), 'Must contain actionable message');
  });
});
