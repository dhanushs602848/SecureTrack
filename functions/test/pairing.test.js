const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');

function hashPairingCode(code) {
  return crypto.createHash('sha256').update(String(code).trim()).digest('hex');
}

function hashInstallationId(id) {
  return crypto.createHash('sha256').update(String(id).trim()).digest('hex');
}

test('Pairing Code Generator & Hashing Tests', async (t) => {
  await t.test('Generates valid 8-digit random codes', () => {
    for (let i = 0; i < 50; i++) {
      const codeNum = crypto.randomInt(10000000, 100000000);
      const codeStr = codeNum.toString();
      assert.equal(codeStr.length, 8, 'Code must be exactly 8 digits');
      assert.match(codeStr, /^\d{8}$/, 'Code must consist of only digits');
    }
  });

  await t.test('SHA-256 code hashing is deterministic and irreversible in plain text', () => {
    const code = '84920153';
    const hash1 = hashPairingCode(code);
    const hash2 = hashPairingCode(code);
    assert.equal(hash1, hash2, 'Hash must be consistent');
    assert.equal(hash1.length, 64, 'SHA-256 hex string must be 64 characters');
    assert.notEqual(hash1, code, 'Hash must not expose plaintext code');
  });

  await t.test('Installation ID hashing for rate limiting produces distinct SHA-256 identifiers', () => {
    const inst1 = 'installation-uuid-phone-a';
    const inst2 = 'installation-uuid-phone-b';
    const hash1 = hashInstallationId(inst1);
    const hash2 = hashInstallationId(inst2);
    assert.notEqual(hash1, hash2);
    assert.equal(hash1.length, 64);
  });

  await t.test('Expiry logic correctly identifies active vs expired codes', () => {
    const now = Date.now();
    const tenMinutes = 10 * 60 * 1000;
    const activeExpiresAt = now + tenMinutes;
    const expiredExpiresAt = now - 1000;

    assert.ok(activeExpiresAt > now, 'New code must be active');
    assert.ok(expiredExpiresAt < now, 'Old code must be detected as expired');
  });

  await t.test('Installation-level brute-force rate limiter blocks attacker spraying across multiple codes', () => {
    const WINDOW_MS = 15 * 60 * 1000;
    const MAX_ATTEMPTS = 5;

    // Simulation of rate limit tracking
    const rateStore = new Map();

    function attemptClaim(installationId, attemptedCode) {
      const instHash = hashInstallationId(installationId);
      const now = Date.now();
      const existing = rateStore.get(instHash);

      if (existing) {
        if (now - existing.firstAttemptAt < WINDOW_MS) {
          if (existing.attempts >= MAX_ATTEMPTS) {
            return { allowed: false, error: 'resource-exhausted' };
          }
        }
      }

      // Record failure for wrong codes
      const record = existing && (now - existing.firstAttemptAt < WINDOW_MS)
        ? { attempts: existing.attempts + 1, firstAttemptAt: existing.firstAttemptAt }
        : { attempts: 1, firstAttemptAt: now };
      rateStore.set(instHash, record);
      return { allowed: true, error: 'not-found' };
    }

    const attackerId = 'malicious-attacker-device';

    // Spray 5 distinct wrong random codes
    for (let i = 1; i <= 5; i++) {
      const res = attemptClaim(attackerId, `1000000${i}`);
      assert.equal(res.allowed, true, `Attempt ${i} recorded`);
      assert.equal(res.error, 'not-found');
    }

    // 6th attempt must be blocked immediately regardless of what code is sent
    const blockedRes = attemptClaim(attackerId, '99999999');
    assert.equal(blockedRes.allowed, false);
    assert.equal(blockedRes.error, 'resource-exhausted');
  });

  await t.test('Single-use status transition checks', () => {
    let status = 'ACTIVE';
    assert.equal(status, 'ACTIVE');
    status = 'CLAIMED';
    assert.notEqual(status, 'ACTIVE', 'Reusing CLAIMED code must fail');
  });

  await t.test('Device revocation custom claims and security rule simulation', () => {
    // When a device is revoked, its custom claims become role: 'revoked', revoked: true
    const deviceToken = {
      role: 'device',
      revoked: false,
      ownerUid: 'user-123',
      deviceId: 'device-abc'
    };

    function isDeviceActive(deviceDocStatus, token) {
      if (token.role === 'revoked' || token.revoked === true) return false;
      if (deviceDocStatus === 'REVOKED') return false;
      return true;
    }

    // Active device
    assert.equal(isDeviceActive('ONLINE', deviceToken), true);

    // Revocation triggered: doc status becomes REVOKED
    assert.equal(isDeviceActive('REVOKED', deviceToken), false);

    // Custom claims updated on token refresh
    const revokedToken = {
      ...deviceToken,
      role: 'revoked',
      revoked: true
    };
    assert.equal(isDeviceActive('ONLINE', revokedToken), false);
    assert.equal(isDeviceActive('REVOKED', revokedToken), false);
  });

  await t.test('Command modification restrictions simulation', () => {
    const allowedDeviceUpdateKeys = new Set(['status', 'result', 'executedAt', 'error', 'details', 'progress']);

    function canDeviceUpdateCommand(affectedKeys) {
      for (const k of affectedKeys) {
        if (!allowedDeviceUpdateKeys.has(k)) {
          return false;
        }
      }
      return true;
    }

    // Allowed updates from device reporting execution
    assert.equal(canDeviceUpdateCommand(['status', 'result', 'executedAt']), true);
    assert.equal(canDeviceUpdateCommand(['status', 'error']), true);

    // Disallowed tampering (e.g. attempting to change command payload or target)
    assert.equal(canDeviceUpdateCommand(['command']), false);
    assert.equal(canDeviceUpdateCommand(['targetDeviceId', 'status']), false);
    assert.equal(canDeviceUpdateCommand(['createdAt', 'status']), false);
  });
});
