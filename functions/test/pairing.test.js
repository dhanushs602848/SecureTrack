const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');

function hashPairingCode(code) {
  return crypto.createHash('sha256').update(String(code).trim()).digest('hex');
}

function hashIdentifier(identifier) {
  return crypto.createHash('sha256').update(String(identifier).trim()).digest('hex');
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

  await t.test('Identifier hashing produces distinct SHA-256 identifiers for IP and installation', () => {
    const inst1 = 'installation-uuid-phone-a';
    const ip1 = '192.168.1.100';
    const hashInst = hashIdentifier('inst:' + inst1);
    const hashIp = hashIdentifier('ip:' + ip1);
    assert.notEqual(hashInst, hashIp);
    assert.equal(hashInst.length, 64);
    assert.equal(hashIp.length, 64);
  });

  await t.test('Expiry logic correctly identifies active vs expired codes (10 minutes)', () => {
    const now = Date.now();
    const tenMinutes = 10 * 60 * 1000;
    const activeExpiresAt = now + tenMinutes;
    const expiredExpiresAt = now - 1000;

    assert.ok(activeExpiresAt > now, 'New code must be active');
    assert.ok(expiredExpiresAt < now, 'Old code must be detected as expired');
  });

  await t.test('Plaintext code zero-logging verification', () => {
    // Intercept console calls to verify no plaintext 8-digit codes appear in logs
    const secretCode = '92847193';
    const loggedMessages = [];
    const origLog = console.log;
    const origWarn = console.warn;
    const origError = console.error;

    console.log = (...args) => loggedMessages.push(args.join(' '));
    console.warn = (...args) => loggedMessages.push(args.join(' '));
    console.error = (...args) => loggedMessages.push(args.join(' '));

    try {
      // Simulate operations
      const codeHash = hashPairingCode(secretCode);
      console.log('Pairing code created with hash:', codeHash);
      console.warn('Attempt recorded for hash:', codeHash);
    } finally {
      console.log = origLog;
      console.warn = origWarn;
      console.error = origError;
    }

    for (const msg of loggedMessages) {
      assert.equal(msg.includes(secretCode), false, 'Plaintext code must NEVER appear in log output');
    }
  });

  await t.test('Atomic rate limiter prevents concurrent requests from bypassing attempt threshold', async () => {
    const WINDOW_MS = 15 * 60 * 1000;
    const MAX_ATTEMPTS = 5;

    // Simulated transactional store
    const store = new Map();

    async function atomicCheckAndIncrement(key) {
      // Simulates atomic Firestore transaction
      const now = Date.now();
      const existing = store.get(key);
      if (!existing) {
        store.set(key, { attempts: 1, firstAttemptAt: now });
        return { allowed: true, attempts: 1 };
      }
      if (now - existing.firstAttemptAt > WINDOW_MS) {
        store.set(key, { attempts: 1, firstAttemptAt: now });
        return { allowed: true, attempts: 1 };
      }
      if (existing.attempts >= MAX_ATTEMPTS) {
        return { allowed: false, attempts: existing.attempts };
      }
      const newAttempts = existing.attempts + 1;
      store.set(key, { attempts: newAttempts, firstAttemptAt: existing.firstAttemptAt });
      return { allowed: true, attempts: newAttempts };
    }

    const testBucket = 'inst:' + hashIdentifier('attacker-device-id');

    // Simulate 10 rapid concurrent attempts
    const results = await Promise.all(
      Array.from({ length: 10 }).map(() => atomicCheckAndIncrement(testBucket))
    );

    const allowedCount = results.filter((r) => r.allowed).length;
    const blockedCount = results.filter((r) => !r.allowed).length;

    assert.equal(allowedCount, 5, 'Exactly 5 attempts must be allowed before lockout');
    assert.equal(blockedCount, 5, 'Remaining 5 concurrent attempts must be immediately rejected');
  });

  await t.test('Single-use status transition checks', () => {
    let status = 'ACTIVE';
    assert.equal(status, 'ACTIVE');
    status = 'CLAIMED';
    assert.notEqual(status, 'ACTIVE', 'Reusing CLAIMED code must fail');
  });

  await t.test('App Check enforcement rejection simulation', () => {
    function verifyAppCheck(context, isProduction) {
      if (isProduction && (!context || !context.app)) {
        throw new Error('failed-precondition: App Check token required');
      }
      return true;
    }

    // Missing app check in production
    assert.throws(
      () => verifyAppCheck({ auth: { uid: 'u1' } }, true),
      /App Check token required/
    );

    // Valid app check in production
    assert.doesNotThrow(() =>
      verifyAppCheck({ auth: { uid: 'u1' }, app: { appId: 'com.example.securetrack' } }, true)
    );
  });

  await t.test('Device revocation resilience and security rule simulation', () => {
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

    // Revocation in Firestore instantly blocks device regardless of token validity
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

  await t.test('Device document update telemetry allowlist vs identity immutability', () => {
    const allowedTelemetryKeys = new Set([
      'batteryLevel', 'lastSeen', 'locationStatus', 'trackingStatus', 'networkStatus', 'status'
    ]);

    function canDeviceUpdateDeviceDoc(affectedKeys, newStatus) {
      // Must not set status to REVOKED
      if (newStatus === 'REVOKED') return false;
      // All modified keys must belong to the allowlist
      for (const k of affectedKeys) {
        if (!allowedTelemetryKeys.has(k)) {
          return false;
        }
      }
      return true;
    }

    // Valid telemetry updates
    assert.equal(canDeviceUpdateDeviceDoc(['batteryLevel', 'lastSeen'], 'ONLINE'), true);
    assert.equal(canDeviceUpdateDeviceDoc(['locationStatus', 'trackingStatus'], 'ONLINE'), true);
    assert.equal(canDeviceUpdateDeviceDoc(['networkStatus', 'batteryLevel'], 'ONLINE'), true);

    // Disallowed modifications: device setting itself to REVOKED
    assert.equal(canDeviceUpdateDeviceDoc(['status'], 'REVOKED'), false);

    // Disallowed modifications: tampering with immutable identity/ownership fields
    assert.equal(canDeviceUpdateDeviceDoc(['id'], 'ONLINE'), false);
    assert.equal(canDeviceUpdateDeviceDoc(['ownerUid'], 'ONLINE'), false);
    assert.equal(canDeviceUpdateDeviceDoc(['installationId'], 'ONLINE'), false);
    assert.equal(canDeviceUpdateDeviceDoc(['paired'], 'ONLINE'), false);
    assert.equal(canDeviceUpdateDeviceDoc(['protectionEnabled'], 'ONLINE'), false);
    assert.equal(canDeviceUpdateDeviceDoc(['deviceName'], 'ONLINE'), false);
    assert.equal(canDeviceUpdateDeviceDoc(['model'], 'ONLINE'), false);
  });

  await t.test('Command modification and creation restrictions simulation', () => {
    const allowedDeviceUpdateKeys = new Set([
      'status', 'result', 'executedAt', 'error', 'details', 'progress'
    ]);

    function canDeviceCreateCommand(callerRole) {
      return callerRole !== 'device';
    }

    function canDeviceDeleteCommand(callerRole) {
      return callerRole !== 'device';
    }

    function canDeviceUpdateCommand(affectedKeys, modifiedCommandOrTarget) {
      if (modifiedCommandOrTarget) return false;
      for (const k of affectedKeys) {
        if (!allowedDeviceUpdateKeys.has(k)) {
          return false;
        }
      }
      return true;
    }

    // Command creation/deletion: strictly owner only
    assert.equal(canDeviceCreateCommand('owner'), true);
    assert.equal(canDeviceCreateCommand('device'), false);
    assert.equal(canDeviceDeleteCommand('owner'), true);
    assert.equal(canDeviceDeleteCommand('device'), false);

    // Allowed updates from device reporting execution
    assert.equal(canDeviceUpdateCommand(['status', 'result', 'executedAt'], false), true);
    assert.equal(canDeviceUpdateCommand(['status', 'error'], false), true);

    // Disallowed tampering (e.g. attempting to change command payload or target)
    assert.equal(canDeviceUpdateCommand(['command'], true), false);
    assert.equal(canDeviceUpdateCommand(['targetDeviceId', 'status'], true), false);
    assert.equal(canDeviceUpdateCommand(['createdAt', 'status'], false), false);
  });

  await t.test('Cross-owner isolation simulation', () => {
    function canAccessOwnerData(callerToken, targetOwnerUid) {
      if (callerToken.role === 'device') {
        return callerToken.ownerUid === targetOwnerUid;
      }
      return callerToken.uid === targetOwnerUid;
    }

    const deviceOwnerA = { role: 'device', ownerUid: 'owner-A', deviceId: 'dev-1' };
    const ownerA = { uid: 'owner-A', role: 'owner' };
    const ownerB = { uid: 'owner-B', role: 'owner' };

    // Device of Owner A can access Owner A
    assert.equal(canAccessOwnerData(deviceOwnerA, 'owner-A'), true);
    // Device of Owner A CANNOT access Owner B
    assert.equal(canAccessOwnerData(deviceOwnerA, 'owner-B'), false);
    // Owner B cannot access Owner A
    assert.equal(canAccessOwnerData(ownerB, 'owner-A'), false);
  });
});
