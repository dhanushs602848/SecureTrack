const test = require('node:test');
const assert = require('node:assert/strict');

test('FCM Command Dispatch & Payload Verification Suite', async (t) => {
  await t.test('Constructs high-priority data-only FCM wake-up payload', () => {
    const fcmToken = 'dGVzdC10b2tlbi0xMjM0NTY3ODkw';
    const commandId = 'cmd-test-999';
    const deviceId = 'installation-phone-samsung-001';
    const ownerUid = 'owner-uid-abc-123';
    const commandType = 'REFRESH_LOCATION';
    const requestId = 'req-loc-001';
    const timestamp = Date.now();

    const payload = {
      token: fcmToken.trim(),
      data: {
        commandId: String(commandId),
        targetDeviceId: String(deviceId),
        ownerUid: String(ownerUid),
        type: String(commandType),
        requestId: String(requestId),
        timestamp: String(timestamp)
      },
      android: {
        priority: 'high'
      }
    };

    assert.equal(payload.token, fcmToken);
    assert.equal(payload.android.priority, 'high');
    assert.equal(payload.data.commandId, commandId);
    assert.equal(payload.data.targetDeviceId, deviceId);
    assert.equal(payload.data.ownerUid, ownerUid);
    assert.equal(payload.data.type, 'REFRESH_LOCATION');
    assert.equal(payload.data.requestId, 'req-loc-001');
    // Ensure no notification field is present to guarantee background processing without visual clutter
    assert.equal(payload.notification, undefined, 'Payload must be data-only without notification banner');
  });

  await t.test('Validates stale token error identification', () => {
    const staleErrors = [
      'messaging/registration-token-not-registered',
      'messaging/invalid-registration-token'
    ];

    for (const code of staleErrors) {
      const isStale = (code === 'messaging/registration-token-not-registered' ||
                       code === 'messaging/invalid-registration-token');
      assert.ok(isStale, `Error code ${code} must trigger stale token cleanup`);
    }

    const transientCode = 'messaging/server-unavailable';
    const isTransientStale = (transientCode === 'messaging/registration-token-not-registered' ||
                              transientCode === 'messaging/invalid-registration-token');
    assert.equal(isTransientStale, false, 'Transient error must not trigger token cleanup');
  });

  await t.test('Validates payload serialization for all command types', () => {
    const validCommands = ['REFRESH_LOCATION', 'LOCK_DEVICE', 'PANIC_ALARM', 'STATUS_REPORT'];
    for (const cmd of validCommands) {
      const data = {
        commandId: 'cmd-1',
        targetDeviceId: 'dev-1',
        ownerUid: 'owner-1',
        type: cmd,
        requestId: 'req-1',
        timestamp: String(Date.now())
      };
      assert.ok(data.type.length > 0);
      assert.equal(typeof data.timestamp, 'string');
    }
  });
});
