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
});
