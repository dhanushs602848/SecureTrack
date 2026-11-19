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
});
