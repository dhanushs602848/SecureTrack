import test from 'node:test';
import assert from 'node:assert/strict';

test('SecureTrack Web Dashboard Auth & Command Integrity Suite', async (t) => {

  await t.test('Validates email and session token structure', () => {
    function isValidEmail(email) {
      return typeof email === 'string' && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
    }

    assert.equal(isValidEmail('owner@securetrack.io'), true);
    assert.equal(isValidEmail('invalid-email'), false);
    assert.equal(isValidEmail(''), false);
  });

  await t.test('Command payload sanitizer prevents malformed commands', () => {
    function buildCommandPayload(type, requestId) {
      if (type === 'WIPE_DEVICE') {
        throw new Error('Safety Lock: WIPE_DEVICE is permanently disabled.');
      }
      const allowedTypes = ['LOCATE_NOW', 'LOCK_DEVICE', 'ALARM_ALERT'];
      if (!allowedTypes.includes(type)) {
        throw new Error(`Unsupported command type: ${type}`);
      }
      return {
        commandType: type,
        command: type,
        status: 'QUEUED',
        requestId: requestId || 'req-' + Date.now(),
        createdAt: new Date().toISOString()
      };
    }

    const locateCmd = buildCommandPayload('LOCATE_NOW', 'req-12345');
    assert.equal(locateCmd.commandType, 'LOCATE_NOW');
    assert.equal(locateCmd.status, 'QUEUED');
    assert.equal(locateCmd.requestId, 'req-12345');

    assert.throws(
      () => buildCommandPayload('WIPE_DEVICE', 'req-999'),
      /Safety Lock: WIPE_DEVICE is permanently disabled/
    );

    assert.throws(
      () => buildCommandPayload('MALICIOUS_COMMAND', 'req-000'),
      /Unsupported command type/
    );
  });

  await t.test('Location staleness threshold evaluation (15 minutes)', () => {
    function isLocationStale(timestampIso, nowMs) {
      if (!timestampIso) return true;
      const ts = new Date(timestampIso).getTime();
      if (isNaN(ts)) return true;
      return (nowMs - ts) > 15 * 60 * 1000;
    }

    const now = Date.now();
    const recentIso = new Date(now - 2 * 60 * 1000).toISOString(); // 2m ago
    const staleIso = new Date(now - 20 * 60 * 1000).toISOString(); // 20m ago

    assert.equal(isLocationStale(recentIso, now), false, 'Recent location fix should not be stale');
    assert.equal(isLocationStale(staleIso, now), true, 'Location older than 15m must be flagged stale');
    assert.equal(isLocationStale(null, now), true, 'Missing location timestamp must be flagged stale');
  });

  await t.test('Exact requestId correlation for remote location response', () => {
    function correlateLocationResponse(requestedRequestId, locationRecord) {
      if (!locationRecord || !locationRecord.requestId) {
        return { match: false, reason: 'Missing location record or request ID' };
      }
      if (locationRecord.requestId !== requestedRequestId) {
        return { match: false, reason: 'Uncorrelated location record received' };
      }
      return { match: true, record: locationRecord };
    }

    const reqId = 'req-uuid-abc-123';
    const matchingRecord = { requestId: 'req-uuid-abc-123', latitude: 37.7749, longitude: -122.4194 };
    const mismatchRecord = { requestId: 'old-req-999', latitude: 12.9716, longitude: 77.5946 };

    assert.equal(correlateLocationResponse(reqId, matchingRecord).match, true);
    assert.equal(correlateLocationResponse(reqId, mismatchRecord).match, false);
  });

  await t.test('Device selector state updater retains selected device across Firestore snapshot updates', () => {
    function resolveNextDeviceId(currentId, data) {
      if (currentId && data.some((device) => device.id === currentId)) {
        return currentId;
      }
      return data[0]?.id ?? '';
    }

    const initialDevices = [
      { id: 'vivo-v23', deviceName: 'Vivo V23', model: 'V2130', status: 'ONLINE' },
      { id: 'samsung-s22', deviceName: 'Samsung Galaxy S22', model: 'SM-S901B', status: 'ONLINE' }
    ];

    // 1. Initial empty selection falls back to the first available device (Vivo)
    let selectedId = '';
    selectedId = resolveNextDeviceId(selectedId, initialDevices);
    assert.equal(selectedId, 'vivo-v23', 'Initial snapshot selects first device (Vivo)');

    // 2. Select Vivo - Vivo remains selected
    selectedId = 'vivo-v23';
    selectedId = resolveNextDeviceId(selectedId, initialDevices);
    assert.equal(selectedId, 'vivo-v23', 'Vivo selection persists');

    // 3. User switches selection to Samsung
    selectedId = 'samsung-s22';
    assert.equal(selectedId, 'samsung-s22', 'Selected Samsung device');

    // 4. Trigger subsequent Firestore device snapshot update (e.g., telemetry update)
    const updatedDevicesSnapshot = [
      { id: 'vivo-v23', deviceName: 'Vivo V23', model: 'V2130', status: 'ONLINE', batteryLevel: 82 },
      { id: 'samsung-s22', deviceName: 'Samsung Galaxy S22', model: 'SM-S901B', status: 'ONLINE', batteryLevel: 95 }
    ];
    selectedId = resolveNextDeviceId(selectedId, updatedDevicesSnapshot);
    assert.equal(selectedId, 'samsung-s22', 'Samsung remains selected after Firestore snapshot update');

    // 5. If selected device is removed, safely falls back to the first available device
    const devicesWithoutSamsung = [
      { id: 'vivo-v23', deviceName: 'Vivo V23', model: 'V2130', status: 'ONLINE' }
    ];
    selectedId = resolveNextDeviceId(selectedId, devicesWithoutSamsung);
    assert.equal(selectedId, 'vivo-v23', 'Falls back to Vivo when Samsung is removed');

    // Safe fallback when device list is empty
    selectedId = resolveNextDeviceId(selectedId, []);
    assert.equal(selectedId, '', 'Falls back to empty string when no devices available');
  });

  await t.test('Command queue 15-minute expiry evaluation', () => {
    function resolveEffectiveCommandStatus(cmd, nowMs) {
      const QUEUE_LIMIT_MS = 15 * 60 * 1000;
      const rawStatus = (cmd.status || 'QUEUED').toUpperCase();
      if (['EXECUTED', 'COMPLETED', 'DELIVERED'].includes(rawStatus)) {
        return 'EXECUTED';
      }
      if (['FAILED', 'EXPIRED'].includes(rawStatus)) {
        return rawStatus;
      }
      // Actively executing or claimed commands must NEVER expire
      if (rawStatus === 'EXECUTING' || rawStatus === 'CLAIMED') {
        return rawStatus;
      }
      if (cmd.expiresAt) {
        const expiresMs = typeof cmd.expiresAt === 'number' ? cmd.expiresAt : new Date(cmd.expiresAt).getTime();
        if (!isNaN(expiresMs) && nowMs > expiresMs) {
          return 'EXPIRED';
        }
      }
      if (cmd.createdAt) {
        const createdMs = typeof cmd.createdAt === 'number' ? cmd.createdAt : new Date(cmd.createdAt).getTime();
        if (!isNaN(createdMs) && (nowMs - createdMs > QUEUE_LIMIT_MS)) {
          return 'EXPIRED';
        }
      }
      return rawStatus;
    }

    const now = Date.now();
    const queuedRecent = { status: 'QUEUED', createdAt: new Date(now - 5 * 60 * 1000).toISOString() }; // 5m ago
    const queuedStale = { status: 'QUEUED', createdAt: new Date(now - 16 * 60 * 1000).toISOString() }; // 16m ago
    const executedStale = { status: 'EXECUTED', createdAt: new Date(now - 30 * 60 * 1000).toISOString() }; // 30m ago
    const completedStale = { status: 'COMPLETED', createdAt: new Date(now - 25 * 60 * 1000).toISOString() };
    const executingStale = { status: 'EXECUTING', createdAt: new Date(now - 20 * 60 * 1000).toISOString() }; // Actively executing
    const claimedStale = { status: 'CLAIMED', createdAt: new Date(now - 20 * 60 * 1000).toISOString() };

    // Boundary condition tests
    const boundaryFresh = { status: 'QUEUED', createdAt: now - (14 * 60 * 1000 + 59 * 1000) }; // 14m 59s
    const boundaryExpired = { status: 'QUEUED', createdAt: now - (15 * 60 * 1000 + 1000) }; // 15m 1s

    // Explicit expiresAt
    const explicitExpired = { status: 'QUEUED', createdAt: now - 5000, expiresAt: now - 1000 };
    const explicitFresh = { status: 'QUEUED', createdAt: now - 5000, expiresAt: now + 60000 };

    // Missing / malformed timestamps
    const malformedTimestamp = { status: 'QUEUED', createdAt: 'invalid-date-string' };
    const nullTimestamp = { status: 'QUEUED', createdAt: null };

    assert.equal(resolveEffectiveCommandStatus(queuedRecent, now), 'QUEUED', 'Recent command remains in QUEUED status');
    assert.equal(resolveEffectiveCommandStatus(queuedStale, now), 'EXPIRED', 'Command queued >15m must display EXPIRED');
    assert.equal(resolveEffectiveCommandStatus(executedStale, now), 'EXECUTED', 'Executed command displays EXECUTED regardless of age');
    assert.equal(resolveEffectiveCommandStatus(completedStale, now), 'EXECUTED', 'Completed command displays EXECUTED');
    assert.equal(resolveEffectiveCommandStatus(executingStale, now), 'EXECUTING', 'Actively executing command must not be expired');
    assert.equal(resolveEffectiveCommandStatus(claimedStale, now), 'CLAIMED', 'Claimed command must not be expired');

    assert.equal(resolveEffectiveCommandStatus(boundaryFresh, now), 'QUEUED', '14m 59s command remains QUEUED');
    assert.equal(resolveEffectiveCommandStatus(boundaryExpired, now), 'EXPIRED', '15m 1s command transitions to EXPIRED');

    assert.equal(resolveEffectiveCommandStatus(explicitExpired, now), 'EXPIRED', 'Past expiresAt transitions to EXPIRED');
    assert.equal(resolveEffectiveCommandStatus(explicitFresh, now), 'QUEUED', 'Future expiresAt remains QUEUED');

    assert.equal(resolveEffectiveCommandStatus(malformedTimestamp, now), 'QUEUED', 'Malformed timestamp safely falls back to current status');
    assert.equal(resolveEffectiveCommandStatus(nullTimestamp, now), 'QUEUED', 'Null timestamp safely falls back to current status');
  });

  await t.test('Anti-misuse: Command rate-limiting enforcement (Alarm 2m, Lock 1m, Locate 5/10m)', () => {
    function evaluateRateLimit(commandType, rateData, nowMs) {
      if (commandType === 'ALARM_ALERT') {
        const lastAlarm = rateData.lastAlarmAt || 0;
        if (nowMs - lastAlarm < 120 * 1000) {
          const waitSec = Math.ceil((120 * 1000 - (nowMs - lastAlarm)) / 1000);
          throw new Error(`Rate limit: Alarm cooldown active. Please wait ${waitSec}s.`);
        }
      }

      if (commandType === 'LOCK_DEVICE') {
        const lastLock = rateData.lastLockAt || 0;
        if (nowMs - lastLock < 60 * 1000) {
          const waitSec = Math.ceil((60 * 1000 - (nowMs - lastLock)) / 1000);
          throw new Error(`Rate limit: Lock cooldown active. Please wait ${waitSec}s.`);
        }
      }

      if (commandType === 'LOCATE_NOW') {
        const windowStart = rateData.locateWindowStart || 0;
        let locateCount = rateData.locateCount || 0;

        if (nowMs - windowStart > 10 * 60 * 1000) {
          return { ...rateData, locateWindowStart: nowMs, locateCount: 1, lastLocateAt: nowMs };
        } else {
          if (locateCount >= 5) {
            const waitSec = Math.ceil((10 * 60 * 1000 - (nowMs - windowStart)) / 1000);
            throw new Error(`Rate limit: Maximum 5 location requests per 10 minutes exceeded. Please wait ${waitSec}s.`);
          }
          return { ...rateData, locateCount: locateCount + 1, lastLocateAt: nowMs };
        }
      }

      return { ...rateData };
    }

    const t0 = 1000000;

    // 1. Alarm cooldown: 1 alarm every 2 minutes (120,000 ms)
    assert.doesNotThrow(() => evaluateRateLimit('ALARM_ALERT', { lastAlarmAt: 0 }, t0));
    assert.throws(
      () => evaluateRateLimit('ALARM_ALERT', { lastAlarmAt: t0 - 30 * 1000 }, t0),
      /Alarm cooldown active/
    );
    assert.doesNotThrow(
      () => evaluateRateLimit('ALARM_ALERT', { lastAlarmAt: t0 - 121 * 1000 }, t0)
    );

    // 2. Lock cooldown: 1 lock every 1 minute (60,000 ms)
    assert.doesNotThrow(() => evaluateRateLimit('LOCK_DEVICE', { lastLockAt: 0 }, t0));
    assert.throws(
      () => evaluateRateLimit('LOCK_DEVICE', { lastLockAt: t0 - 45 * 1000 }, t0),
      /Lock cooldown active/
    );
    assert.doesNotThrow(
      () => evaluateRateLimit('LOCK_DEVICE', { lastLockAt: t0 - 65 * 1000 }, t0)
    );

    // 3. Location rate limit: max 5 requests per 10m
    let state = { locateWindowStart: t0, locateCount: 0 };
    for (let i = 0; i < 5; i++) {
      state = evaluateRateLimit('LOCATE_NOW', state, t0 + i * 1000);
      assert.equal(state.locateCount, i + 1);
    }
    // 6th request within window must fail
    assert.throws(
      () => evaluateRateLimit('LOCATE_NOW', state, t0 + 6000),
      /Maximum 5 location requests per 10 minutes exceeded/
    );
    // After 10m window expires, reset and allow
    const afterWindow = evaluateRateLimit('LOCATE_NOW', state, t0 + 11 * 60 * 1000);
    assert.equal(afterWindow.locateCount, 1);
  });

  await t.test('Anti-misuse: Device quota enforcement (max 5 active devices)', () => {
    const MAX_DEVICES_PER_ACCOUNT = 5;

    function checkDeviceQuota(existingDevices) {
      const activeDevices = existingDevices.filter(d => d.status !== 'REVOKED');
      if (activeDevices.length >= MAX_DEVICES_PER_ACCOUNT) {
        throw new Error(`Account device limit reached (Maximum ${MAX_DEVICES_PER_ACCOUNT} devices).`);
      }
      return true;
    }

    const underLimit = [
      { id: 'dev-1', status: 'ONLINE' },
      { id: 'dev-2', status: 'ONLINE' },
      { id: 'dev-3', status: 'REVOKED' } // revoked doesn't count towards active
    ];
    assert.equal(checkDeviceQuota(underLimit), true);

    const atLimit = [
      { id: 'dev-1', status: 'ONLINE' },
      { id: 'dev-2', status: 'ONLINE' },
      { id: 'dev-3', status: 'ONLINE' },
      { id: 'dev-4', status: 'ONLINE' },
      { id: 'dev-5', status: 'ONLINE' }
    ];
    assert.throws(
      () => checkDeviceQuota(atLimit),
      /Account device limit reached/
    );
  });

  await t.test('Anti-misuse: Target device ID validation and safety lock', () => {
    function validateCommandDispatch(targetDeviceId, pathDeviceId, commandType) {
      if (!targetDeviceId || !pathDeviceId || targetDeviceId !== pathDeviceId) {
        throw new Error('Target device ID mismatch or missing');
      }
      if (commandType === 'WIPE_DEVICE') {
        throw new Error('Safety Lock: Remote factory wipe is permanently disabled');
      }
      const allowed = ['LOCATE_NOW', 'LOCK_DEVICE', 'ALARM_ALERT'];
      if (!allowed.includes(commandType)) {
        throw new Error(`Unsupported command type: ${commandType}`);
      }
      return true;
    }

    assert.equal(validateCommandDispatch('device-123', 'device-123', 'LOCATE_NOW'), true);
    assert.throws(
      () => validateCommandDispatch('device-123', 'device-456', 'LOCATE_NOW'),
      /Target device ID mismatch/
    );
    assert.throws(
      () => validateCommandDispatch('', 'device-123', 'LOCATE_NOW'),
      /Target device ID mismatch/
    );
    assert.throws(
      () => validateCommandDispatch('device-123', 'device-123', 'WIPE_DEVICE'),
      /Safety Lock: Remote factory wipe is permanently disabled/
    );
  });

  await t.test('Anti-misuse: Append-only audit log entry structure and non-repudiation', () => {
    function createAuditRecord(ownerUid, action, targetDeviceId, details) {
      const allowedActions = [
        'COMMAND_LOCATE_NOW',
        'COMMAND_LOCK_DEVICE',
        'COMMAND_ALARM_ALERT',
        'DEVICE_APPROVED',
        'DEVICE_REVOKED'
      ];
      if (!allowedActions.includes(action)) {
        throw new Error(`Invalid audit action: ${action}`);
      }
      if (!ownerUid || !targetDeviceId) {
        throw new Error('Audit record requires authenticated owner and target device');
      }
      return {
        ownerUid,
        action,
        targetDeviceId,
        initiatedFrom: 'WEB_CONSOLE',
        timestamp: Date.now(),
        ...details
      };
    }

    const commandAudit = createAuditRecord('owner-1', 'COMMAND_LOCK_DEVICE', 'phone-s22', { requestId: 'req-01' });
    assert.equal(commandAudit.action, 'COMMAND_LOCK_DEVICE');
    assert.equal(commandAudit.targetDeviceId, 'phone-s22');
    assert.equal(commandAudit.initiatedFrom, 'WEB_CONSOLE');

    const enrollAudit = createAuditRecord('owner-1', 'DEVICE_APPROVED', 'phone-vivo', { deviceName: 'Vivo V23' });
    assert.equal(enrollAudit.action, 'DEVICE_APPROVED');

    const revokeAudit = createAuditRecord('owner-1', 'DEVICE_REVOKED', 'phone-vivo');
    assert.equal(revokeAudit.action, 'DEVICE_REVOKED');

    assert.throws(
      () => createAuditRecord('owner-1', 'UNAUTHORIZED_ACTION', 'phone-1'),
      /Invalid audit action/
    );
  });
});
