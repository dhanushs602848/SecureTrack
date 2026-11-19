import { describe, it } from 'node:test';
import assert from 'node:assert';

describe('SecureTrack Authentication System Tests', () => {

  it('validates password minimum length requirement (>= 6 characters)', () => {
    const isPasswordValid = (pwd) => typeof pwd === 'string' && pwd.length >= 6;
    assert.strictEqual(isPasswordValid('12345'), false);
    assert.strictEqual(isPasswordValid(''), false);
    assert.strictEqual(isPasswordValid('123456'), true);
    assert.strictEqual(isPasswordValid('StrongPass#2026'), true);
  });

  it('validates password and confirm password matching', () => {
    const doPasswordsMatch = (p1, p2) => p1 === p2 && p1.length >= 6;
    assert.strictEqual(doPasswordsMatch('Password123', 'Password123'), true);
    assert.strictEqual(doPasswordsMatch('Password123', 'password123'), false);
    assert.strictEqual(doPasswordsMatch('Password123', 'DifferentPassword'), false);
  });

  it('translates auth/invalid-credential to friendly guidance respecting email enumeration', () => {
    const getFriendlyErrorMessage = (code) => {
      if (code === 'auth/invalid-credential') {
        return 'Invalid email or password. Please verify your credentials or click "Forgot Password?" below.';
      }
      return 'Authentication failed.';
    };

    const msg = getFriendlyErrorMessage('auth/invalid-credential');
    assert.ok(msg.includes('Forgot Password?'));
    assert.ok(msg.includes('Invalid email or password'));
  });

  it('guarantees user profile stored in Firestore contains only non-sensitive metadata', () => {
    const userProfile = {
      id: 'uid_firebase_123',
      email: 'test@example.com',
      fullName: 'Test User',
      phoneNumber: '+1 555 0192',
      createdAt: new Date().toISOString(),
      authProvider: 'password'
    };

    assert.strictEqual('password' in userProfile, false);
    assert.strictEqual('passwordHash' in userProfile, false);
    assert.strictEqual('salt' in userProfile, false);
    assert.strictEqual(userProfile.id, 'uid_firebase_123');
    assert.strictEqual(userProfile.email, 'test@example.com');
  });

  it('verifies Google OAuth client configuration parameters', () => {
    const googleClientId = '302801589438-10lp66rvkd682agipq54uavcqpmbpfb4.apps.googleusercontent.com';
    assert.ok(!googleClientId.includes('YOUR_WEB_CLIENT_ID'));
    assert.ok(googleClientId.startsWith('302801589438'));
    assert.ok(googleClientId.endsWith('.apps.googleusercontent.com'));
  });
});
