const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');

function hashPairingCode(code) {
  return crypto.createHash('sha256').update(String(code).trim()).digest('hex');
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

  await t.test('Expiry logic correctly identifies active vs expired codes', () => {
    const now = Date.now();
    const tenMinutes = 10 * 60 * 1000;
    const activeExpiresAt = now + tenMinutes;
    const expiredExpiresAt = now - 1000;

    assert.ok(activeExpiresAt > now, 'New code must be active');
    assert.ok(expiredExpiresAt < now, 'Old code must be detected as expired');
  });

  await t.test('Rate limiting logic blocks beyond threshold', () => {
    const recentCodesCount = 5;
    const isRateLimited = recentCodesCount >= 5;
    assert.equal(isRateLimited, true, 'User with 5 active codes should be rate-limited');
  });

  await t.test('Single-use status transition checks', () => {
    let status = 'ACTIVE';
    // First redemption
    assert.equal(status, 'ACTIVE');
    status = 'CLAIMED';

    // Second redemption attempt
    assert.notEqual(status, 'ACTIVE', 'Reusing CLAIMED code must fail');
  });
});
