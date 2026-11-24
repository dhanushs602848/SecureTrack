import {
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signInWithPopup,
  signInWithRedirect,
  GoogleAuthProvider,
  sendPasswordResetEmail,
  signOut as firebaseSignOut
} from 'firebase/auth';
import {
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  addDoc,
  updateDoc,
  deleteDoc,
  writeBatch,
  runTransaction,
  Transaction,
  query,
  orderBy,
  limit,
  serverTimestamp,
  onSnapshot,
  Timestamp
} from 'firebase/firestore';
import { auth, db } from './firebase';
import { Device, DeviceCommand, LocationRecord, SecurityEvent, User, SafeZone } from '../types';

const convertTimestamp = (ts: any): string => {
  if (!ts) return '';
  if (ts.toDate) return ts.toDate().toISOString();
  if (typeof ts === 'string') return ts;
  if (ts instanceof Date) return ts.toISOString();
  return '';
};

export const authService = {
  login: async (email: string, password: string) => {
    const credential = await signInWithEmailAndPassword(auth, email, password);
    const user = credential.user;
    const token = await user.getIdToken();

    const userDoc = await getDoc(doc(db, 'users', user.uid));
    const fullName = userDoc.exists()
      ? (userDoc.data()?.fullName || user.displayName || email)
      : (user.displayName || email);
    const phoneNumber = userDoc.exists() ? (userDoc.data()?.phoneNumber || '') : '';

    const userObj: User = {
      id: user.uid,
      email: email,
      fullName: fullName,
      phoneNumber: phoneNumber
    };

    return {
      success: true,
      message: 'Login successful',
      data: { token, user: userObj }
    };
  },

  loginWithGoogle: async () => {
    const provider = new GoogleAuthProvider();
    provider.setCustomParameters({ prompt: 'select_account' });
    let credential;
    try {
      credential = await signInWithPopup(auth, provider);
    } catch (err: any) {
      if (err.code === 'auth/popup-blocked') {
        await signInWithRedirect(auth, provider);
        return;
      }
      throw err;
    }

    const user = credential.user;
    const token = await user.getIdToken();

    const userRef = doc(db, 'users', user.uid);
    const userDoc = await getDoc(userRef);
    let fullName = user.displayName || user.email || 'Google User';

    if (!userDoc.exists()) {
      await setDoc(userRef, {
        id: user.uid,
        email: user.email || '',
        fullName: fullName,
        phoneNumber: user.phoneNumber || '',
        createdAt: serverTimestamp(),
        authProvider: 'google.com'
      }, { merge: true });
    } else {
      fullName = userDoc.data()?.fullName || fullName;
    }

    const userObj: User = {
      id: user.uid,
      email: user.email || '',
      fullName: fullName
    };

    return {
      success: true,
      message: 'Google Sign-In successful',
      data: { token, user: userObj }
    };
  },

  register: async (email: string, password: string, fullName: string, phoneNumber?: string) => {
    const credential = await createUserWithEmailAndPassword(auth, email, password);
    const user = credential.user;
    const token = await user.getIdToken();

    // Store non-sensitive user profile in Firestore
    await setDoc(doc(db, 'users', user.uid), {
      id: user.uid,
      email: email,
      fullName: fullName,
      phoneNumber: phoneNumber || '',
      createdAt: serverTimestamp(),
      authProvider: 'password'
    }, { merge: true });

    const userObj: User = {
      id: user.uid,
      email: email,
      fullName: fullName,
      phoneNumber: phoneNumber
    };

    return {
      success: true,
      message: 'Registration successful',
      data: { token, user: userObj }
    };
  },

  sendPasswordReset: async (email: string) => {
    await sendPasswordResetEmail(auth, email);
    return {
      success: true,
      message: 'Password reset email sent'
    };
  },

  getCurrentUser: async (): Promise<User | null> => {
    const currentUser = auth.currentUser;
    if (!currentUser) return null;

    const uid = currentUser.uid;
    const email = currentUser.email || '';
    let fullName = currentUser.displayName || email;

    const userDoc = await getDoc(doc(db, 'users', uid));
    if (userDoc.exists()) {
      const data = userDoc.data();
      return {
        id: uid,
        email: data.email || email,
        fullName: data.fullName || fullName,
        phoneNumber: data.phoneNumber || ''
      };
    }

    return { id: uid, email, fullName };
  },

  logout: async () => {
    await firebaseSignOut(auth);
  }
};

const parseDeviceDoc = (d: any): Device => {
  const data = d.data();
  const now = Date.now();
  let status: 'ONLINE' | 'OFFLINE' | 'REVOKED' = data.status || 'OFFLINE';
  const lastSeenTs = data.lastSeen ? (data.lastSeen.toMillis ? data.lastSeen.toMillis() : 0) : 0;

  if (status !== 'REVOKED') {
    if (lastSeenTs > 0 && now - lastSeenTs > 5 * 60 * 1000) {
      status = 'OFFLINE';
    }
  }

  return {
    id: d.id,
    deviceName: data.deviceName || 'Android Device',
    model: data.model || 'Generic Device',
    androidVersion: data.androidVersion || '14',
    installationId: data.installationId || d.id,
    status: status,
    protectionEnabled: data.protectionEnabled !== undefined ? data.protectionEnabled : (data.locationStatus !== 'DISABLED'),
    protectionStatus: data.protectionStatus || (data.locationStatus === 'DISABLED' ? 'DEGRADED' : status === 'OFFLINE' ? 'OFFLINE' : 'ACTIVE'),
    protectionReason: data.protectionReason || (data.locationStatus === 'DISABLED' ? 'Location services switched off in system settings' : 'All protection systems operational'),
    batteryLevel: data.batteryLevel ?? 100,
    lastSeen: convertTimestamp(data.lastSeen),
    locationStatus: data.locationStatus || 'ENABLED',
    trackingStatus: data.trackingStatus || 'ACTIVE',
    networkStatus: data.networkStatus || 'CONNECTED',
    lastLatitude: data.lastLatitude,
    lastLongitude: data.lastLongitude,
    locationAccuracy: data.locationAccuracy,
    locationSource: data.locationSource || 'GPS',
    locationTimestamp: convertTimestamp(data.locationTimestamp || data.lastSeen),
    paired: data.paired || false,
    ownerUid: data.ownerUid
  };
};

export const deviceService = {
  subscribeToDevices: (callback: (devices: Device[]) => void) => {
    const uid = auth.currentUser?.uid;
    if (!uid) { callback([]); return () => {}; }

    return onSnapshot(collection(db, 'users', uid, 'devices'), (snapshot) => {
      const list: Device[] = [];
      snapshot.forEach((d) => {
        list.push(parseDeviceDoc(d));
      });
      callback(list);
    });
  },

  getDevices: async (): Promise<Device[]> => {
    const uid = auth.currentUser?.uid;
    if (!uid) return [];

    const snapshot = await getDocs(collection(db, 'users', uid, 'devices'));
    const list: Device[] = [];
    snapshot.forEach((d) => {
      list.push(parseDeviceDoc(d));
    });
    return list;
  },

  getRecoveryInfo: async (deviceId: string): Promise<{ imei1?: string; imei2?: string; consentGiven?: boolean } | null> => {
    const uid = auth.currentUser?.uid;
    if (!uid) return null;

    const docRef = doc(db, 'users', uid, 'private_recovery', deviceId);
    const snapshot = await getDoc(docRef);
    if (snapshot.exists()) {
      return snapshot.data() as { imei1?: string; imei2?: string; consentGiven?: boolean };
    }
    return null;
  },

  /**
   * Spark-Compatible: Generate a cryptographically secure 256-bit one-time pairing invitation.
   * Stored as SHA-256 hash in Firestore with a 10-minute validity window.
   */
  createPairingInvitation: async (): Promise<{ rawToken: string; tokenHash: string; expiresAt: string; qrPayload: string }> => {
    const user = auth.currentUser;
    if (!user) throw new Error('User not authenticated');

    // Configurable maximum device count per account (anti-abuse quota enforcement)
    const MAX_DEVICES_PER_ACCOUNT = 5;
    const devicesSnap = await getDocs(collection(db, 'users', user.uid, 'devices'));
    const activeDevices = devicesSnap.docs.filter(d => d.data().status !== 'REVOKED');
    if (activeDevices.length >= MAX_DEVICES_PER_ACCOUNT) {
      throw new Error(`Account device limit reached (Maximum ${MAX_DEVICES_PER_ACCOUNT} devices). Please revoke an existing device before pairing a new one.`);
    }

    // Generate 32 cryptographically random bytes (256 bits)
    const bytes = new Uint8Array(32);
    window.crypto.getRandomValues(bytes);
    const rawToken = Array.from(bytes).map((b) => b.toString(16).padStart(2, '0')).join('');

    // Compute SHA-256 hash
    const msgBuffer = new TextEncoder().encode(rawToken);
    const hashBuffer = await window.crypto.subtle.digest('SHA-256', msgBuffer);
    const tokenHash = Array.from(new Uint8Array(hashBuffer))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');

    const expiresAtDate = new Date(Date.now() + 10 * 60 * 1000);

    // Save invitation to Firestore
    await setDoc(doc(db, 'pairing_invitations', tokenHash), {
      ownerUid: user.uid,
      ownerEmail: user.email || '',
      status: 'PENDING_CLAIM',
      createdAt: serverTimestamp(),
      expiresAt: Timestamp.fromDate(expiresAtDate),
      claimedByUid: null,
      claimedDeviceId: null,
      deviceInfo: null,
      approvedAt: null
    });

    const qrPayload = JSON.stringify({
      scheme: 'securetrack',
      action: 'pair',
      token: rawToken,
      ownerUid: user.uid
    });

    return {
      rawToken,
      tokenHash,
      expiresAt: expiresAtDate.toISOString(),
      qrPayload
    };
  },

  /**
   * Real-time subscription to an active pairing invitation.
   */
  subscribeToInvitation: (tokenHash: string, callback: (invitation: any) => void) => {
    return onSnapshot(doc(db, 'pairing_invitations', tokenHash), (snapshot) => {
      if (snapshot.exists()) {
        callback({ id: snapshot.id, ...snapshot.data() });
      } else {
        callback(null);
      }
    });
  },

  /**
   * Owner approves an enrolling device.
   * Enrolls device under users/{ownerUid}/devices/{deviceId} and marks invitation APPROVED.
   */
  approveDeviceClaim: async (tokenHash: string, invitation: any): Promise<void> => {
    const user = auth.currentUser;
    if (!user || user.uid !== invitation.ownerUid) {
      throw new Error('Unauthorized to approve this pairing request');
    }

    const deviceId = invitation.claimedDeviceId;
    if (!deviceId) throw new Error('Missing claimed device ID');

    // Generate 256-bit cryptographically secure shared pairing secret
    const secretBytes = new Uint8Array(32);
    window.crypto.getRandomValues(secretBytes);
    const bleSharedSecret = Array.from(secretBytes).map((b) => b.toString(16).padStart(2, '0')).join('');

    const batch = writeBatch(db);

    // 1. Create registered device document under owner account
    const devRef = doc(db, 'users', user.uid, 'devices', deviceId);
    batch.set(devRef, {
      id: deviceId,
      ownerUid: user.uid,
      deviceAuthUid: invitation.claimedByUid,
      deviceName: invitation.deviceInfo?.deviceName || 'Paired Android Device',
      model: invitation.deviceInfo?.model || 'Android Phone',
      androidVersion: invitation.deviceInfo?.androidVersion || '14',
      batteryLevel: invitation.deviceInfo?.batteryLevel ?? 100,
      status: 'ONLINE',
      paired: true,
      protectionEnabled: true,
      installationId: deviceId,
      bleSharedSecret: bleSharedSecret,
      lastSeen: serverTimestamp(),
      locationStatus: 'ENABLED',
      trackingStatus: 'ACTIVE',
      networkStatus: 'CONNECTED'
    }, { merge: true });

    // 2. Mark invitation APPROVED atomically with shared secret
    const invRef = doc(db, 'pairing_invitations', tokenHash);
    batch.update(invRef, {
      status: 'APPROVED',
      approvedAt: serverTimestamp(),
      bleSharedSecret: bleSharedSecret
    });

    // 3. Record audit log entry for device enrollment
    const auditRef = doc(collection(db, 'users', user.uid, 'audit_logs'));
    batch.set(auditRef, {
      ownerUid: user.uid,
      action: 'DEVICE_APPROVED',
      targetDeviceId: deviceId,
      deviceName: invitation.deviceInfo?.deviceName || 'Paired Android Device',
      initiatedFrom: 'WEB_CONSOLE',
      timestamp: serverTimestamp()
    });

    await batch.commit();
  },

  /**
   * Owner rejects an enrolling device.
   */
  rejectDeviceClaim: async (tokenHash: string): Promise<void> => {
    await updateDoc(doc(db, 'pairing_invitations', tokenHash), {
      status: 'REJECTED'
    });
  },

  /**
   * Revoke device credentials immediately in Firestore.
   * Owner-authorized, safe, and idempotent.
   */
  revokePairedDevice: async (deviceId: string): Promise<void> => {
    const uid = auth.currentUser?.uid;
    if (!uid) {
      throw new Error('User not authenticated. Please log in to revoke devices.');
    }

    try {
      const deviceRef = doc(db, 'users', uid, 'devices', deviceId);
      const batch = writeBatch(db);

      batch.update(deviceRef, {
        status: 'REVOKED',
        protectionEnabled: false,
        trackingStatus: 'STOPPED',
        revokedAt: serverTimestamp()
      });

      // Record audit log entry for device revocation
      const auditRef = doc(collection(db, 'users', uid, 'audit_logs'));
      batch.set(auditRef, {
        ownerUid: uid,
        action: 'DEVICE_REVOKED',
        targetDeviceId: deviceId,
        initiatedFrom: 'WEB_CONSOLE',
        timestamp: serverTimestamp()
      });

      await batch.commit();
    } catch (err: any) {
      console.error('Firestore revokePairedDevice failed:', {
        code: err?.code,
        message: err?.message,
        stack: err?.stack,
        path: `users/${uid}/devices/${deviceId}`,
        deviceId
      });
      const enhancedError: any = new Error(
        err?.code ? `[${err.code}] ${err.message}` : (err?.message || 'Failed to revoke device in Firestore')
      );
      enhancedError.code = err?.code || 'unknown';
      enhancedError.originalError = err;
      throw enhancedError;
    }
  },

  /**
   * Remove/delete a device registration from the owner's account.
   * Owner-authorized, safe, and targets only the specified device document.
   */
  deleteDevice: async (deviceId: string): Promise<void> => {
    const uid = auth.currentUser?.uid;
    if (!uid) {
      throw new Error('User not authenticated. Please log in to delete devices.');
    }

    try {
      const deviceRef = doc(db, 'users', uid, 'devices', deviceId);
      await deleteDoc(deviceRef);
    } catch (err: any) {
      console.error('Firestore deleteDevice failed:', {
        code: err?.code,
        message: err?.message,
        stack: err?.stack,
        path: `users/${uid}/devices/${deviceId}`,
        deviceId
      });
      const enhancedError: any = new Error(
        err?.code ? `[${err.code}] ${err.message}` : (err?.message || 'Failed to delete device document')
      );
      enhancedError.code = err?.code || 'unknown';
      enhancedError.originalError = err;
      throw enhancedError;
    }
  },

  setLostMode: async (deviceId: string, isLostMode: boolean): Promise<void> => {
    const uid = auth.currentUser?.uid;
    if (!uid) throw new Error('Not authenticated');
    await updateDoc(doc(db, 'users', uid, 'devices', deviceId), {
      isLostMode: isLostMode,
      updatedAt: serverTimestamp()
    });
  }
};

export const locationService = {
  subscribeToLocationHistory: (deviceId: string, callback: (history: LocationRecord[]) => void) => {
    const uid = auth.currentUser?.uid;
    if (!uid || !deviceId) { callback([]); return () => {}; }

    const q = query(
      collection(db, 'users', uid, 'devices', deviceId, 'locations'),
      orderBy('timestamp', 'desc'),
      limit(100)
    );

    return onSnapshot(q, (snapshot) => {
      const history: LocationRecord[] = [];
      snapshot.forEach((d) => {
        const data = d.data();
        if (data.latitude !== undefined && data.longitude !== undefined) {
          history.push({
            id: d.id,
            deviceId: deviceId,
            clientUuid: data.clientUuid || d.id,
            latitude: data.latitude,
            longitude: data.longitude,
            accuracy: data.accuracy || 0,
            altitude: data.altitude || 0,
            speed: data.speed || 0,
            timestamp: convertTimestamp(data.timestamp),
            source: data.source || 'GPS'
          });
        }
      });
      callback(history);
    });
  },
};

export const securityService = {
  subscribeToSecurityEvents: (callback: (events: SecurityEvent[]) => void, deviceId?: string) => {
    const uid = auth.currentUser?.uid;
    if (!uid) { callback([]); return () => {}; }

    const q = query(
      collection(db, 'users', uid, 'alerts'),
      orderBy('timestamp', 'desc'),
      limit(50)
    );

    return onSnapshot(q, (snapshot) => {
      const events: SecurityEvent[] = [];
      snapshot.forEach((d) => {
        const data = d.data();
        if (!deviceId || data.deviceId === deviceId) {
          events.push({
            id: d.id,
            deviceId: data.deviceId || 'unknown',
            deviceName: data.deviceName || 'Android Device',
            eventType: data.eventType || 'SECURITY_ALERT',
            severity: data.severity || 'INFO',
            description: data.description || 'Security notification',
            timestamp: convertTimestamp(data.timestamp),
            locationStatus: data.locationStatus,
            zoneId: data.zoneId,
            zoneName: data.zoneName,
            status: data.status || 'ACTIVE',
            executionStatus: data.executionStatus,
            commandId: data.commandId,
            triggerSource: data.triggerSource,
            dismissedAt: convertTimestamp(data.dismissedAt),
            dismissedBy: data.dismissedBy,
            resolvedAt: convertTimestamp(data.resolvedAt)
          });
        }
      });
      callback(events);
    });
  },

  dismissAlert: async (alertId: string): Promise<void> => {
    const uid = auth.currentUser?.uid;
    if (!uid) throw new Error('User not authenticated');
    await updateDoc(doc(db, 'users', uid, 'alerts', alertId), {
      status: 'DISMISSED',
      dismissedAt: serverTimestamp(),
      dismissedBy: uid
    });
  },

  resolveAlert: async (alertId: string): Promise<void> => {
    const uid = auth.currentUser?.uid;
    if (!uid) throw new Error('User not authenticated');
    await updateDoc(doc(db, 'users', uid, 'alerts', alertId), {
      status: 'RESOLVED',
      resolvedAt: serverTimestamp()
    });
  },

  restoreAlert: async (alertId: string): Promise<void> => {
    const uid = auth.currentUser?.uid;
    if (!uid) throw new Error('User not authenticated');
    await updateDoc(doc(db, 'users', uid, 'alerts', alertId), {
      status: 'ACTIVE',
      dismissedAt: null,
      dismissedBy: null
    });
  },

  bulkDismissResolvedAlerts: async (): Promise<number> => {
    const uid = auth.currentUser?.uid;
    if (!uid) throw new Error('User not authenticated');
    const q = query(collection(db, 'users', uid, 'alerts'));
    const snap = await getDocs(q);
    const batch = writeBatch(db);
    let count = 0;
    snap.forEach((d) => {
      const data = d.data();
      if (data.status === 'RESOLVED' || data.severity === 'INFO') {
        batch.update(doc(db, 'users', uid, 'alerts', d.id), {
          status: 'DISMISSED',
          dismissedAt: serverTimestamp(),
          dismissedBy: uid
        });
        count++;
      }
    });
    if (count > 0) {
      await batch.commit();
    }
    return count;
  }
};

export const commandService = {
  subscribeToDeviceCommands: (deviceId: string, callback: (commands: DeviceCommand[]) => void) => {
    const uid = auth.currentUser?.uid;
    if (!uid || !deviceId) { callback([]); return () => {}; }

    const q = query(
      collection(db, 'users', uid, 'devices', deviceId, 'commands'),
      orderBy('createdAt', 'desc'),
      limit(50)
    );

    return onSnapshot(q, (snapshot) => {
      const commands: DeviceCommand[] = [];
      snapshot.forEach((d) => {
        const data = d.data();
        commands.push({
          id: d.id,
          deviceId: deviceId,
          commandType: data.commandType || 'COMMAND',
          payload: data.payload,
          status: data.status || 'QUEUED',
          result: data.result,
          transport: data.transport || 'CLOUD',
          createdAt: convertTimestamp(data.createdAt),
          expiresAt: convertTimestamp(data.expiresAt),
          executedAt: convertTimestamp(data.executedAt)
        });
      });
      callback(commands);
    });
  },

  sendCommand: async (deviceId: string, commandType: string, payload?: string): Promise<DeviceCommand> => {
    const uid = auth.currentUser?.uid;
    if (!uid) throw new Error('User not authenticated');

    // Anti-misuse safety guard: WIPE_DEVICE permanently blocked
    if (commandType === 'WIPE_DEVICE') {
      throw new Error('Safety Lock: Remote factory wipe is permanently disabled to protect user data.');
    }

    const allowedTypes = ['LOCATE_NOW', 'LOCK_DEVICE', 'ALARM_ALERT'];
    if (!allowedTypes.includes(commandType)) {
      throw new Error(`Unsupported command type: ${commandType}`);
    }

    // Server-enforced rate limiting & audit logging via atomic Firestore transaction
    const rateLimitDocRef = doc(db, 'users', uid, 'command_rate_limits', deviceId);
    const commandCollectionRef = collection(db, 'users', uid, 'devices', deviceId, 'commands');
    const newCommandDocRef = doc(commandCollectionRef);
    const auditDocRef = doc(collection(db, 'users', uid, 'audit_logs'));

    const now = Date.now();
    const requestId = 'req_' + now + '_' + Math.random().toString(36).substring(2, 9);
    const expiresAtDate = new Date(now + 15 * 60 * 1000);

    await runTransaction(db, async (transaction: Transaction) => {
      const rateDoc = await transaction.get(rateLimitDocRef);
      const rateData = rateDoc.exists() ? rateDoc.data() : {};

      // 1. Alarm cooldown: 1 alarm every 2 minutes (120,000 ms)
      if (commandType === 'ALARM_ALERT') {
        const lastAlarm = rateData.lastAlarmAt ? convertTimestampToMillis(rateData.lastAlarmAt) : 0;
        if (now - lastAlarm < 120 * 1000) {
          const waitSec = Math.ceil((120 * 1000 - (now - lastAlarm)) / 1000);
          throw new Error(`Rate limit: Alarm cooldown active. Please wait ${waitSec}s before sounding the alarm again.`);
        }
      }

      // 2. Lock cooldown: 1 lock every 1 minute (60,000 ms)
      if (commandType === 'LOCK_DEVICE') {
        const lastLock = rateData.lastLockAt ? convertTimestampToMillis(rateData.lastLockAt) : 0;
        if (now - lastLock < 60 * 1000) {
          const waitSec = Math.ceil((60 * 1000 - (now - lastLock)) / 1000);
          throw new Error(`Rate limit: Lock cooldown active. Please wait ${waitSec}s before sending another lock command.`);
        }
      }

      // 3. Location rate limit: max 5 requests per 10 minutes (600,000 ms)
      if (commandType === 'LOCATE_NOW') {
        const windowStart = rateData.locateWindowStart ? convertTimestampToMillis(rateData.locateWindowStart) : 0;
        let locateCount = rateData.locateCount || 0;

        if (now - windowStart > 10 * 60 * 1000) {
          // Reset 10-minute window
          transaction.set(rateLimitDocRef, {
            ...rateData,
            locateWindowStart: serverTimestamp(),
            locateCount: 1,
            lastLocateAt: serverTimestamp()
          }, { merge: true });
        } else {
          if (locateCount >= 5) {
            const waitSec = Math.ceil((10 * 60 * 1000 - (now - windowStart)) / 1000);
            throw new Error(`Rate limit: Maximum 5 location requests per 10 minutes exceeded. Please wait ${waitSec}s.`);
          }
          transaction.set(rateLimitDocRef, {
            ...rateData,
            locateCount: locateCount + 1,
            lastLocateAt: serverTimestamp()
          }, { merge: true });
        }
      }

      // Update specific timestamps for alarm and lock
      if (commandType === 'ALARM_ALERT') {
        transaction.set(rateLimitDocRef, { ...rateData, lastAlarmAt: serverTimestamp() }, { merge: true });
      } else if (commandType === 'LOCK_DEVICE') {
        transaction.set(rateLimitDocRef, { ...rateData, lastLockAt: serverTimestamp() }, { merge: true });
      }

      // Create command document with exact targetDeviceId
      transaction.set(newCommandDocRef, {
        commandType,
        command: commandType,
        targetDeviceId: deviceId,
        requestId: requestId,
        payload: payload || '',
        status: 'QUEUED',
        transport: 'CLOUD',
        result: '',
        createdAt: serverTimestamp(),
        expiresAt: Timestamp.fromDate(expiresAtDate)
      });

      // Record immutable audit log entry
      transaction.set(auditDocRef, {
        ownerUid: uid,
        action: `COMMAND_${commandType}`,
        targetDeviceId: deviceId,
        commandId: newCommandDocRef.id,
        requestId: requestId,
        initiatedFrom: 'WEB_CONSOLE',
        timestamp: serverTimestamp()
      });
    });

    return {
      id: newCommandDocRef.id,
      deviceId: deviceId,
      commandType: commandType,
      payload: payload,
      status: 'QUEUED',
      transport: 'CLOUD',
      requestId: requestId,
      createdAt: new Date().toISOString(),
      expiresAt: expiresAtDate.toISOString()
    };
  },
};

/**
 * Helper to parse Firestore timestamp, Date or number into epoch millis
 */
function convertTimestampToMillis(ts: any): number {
  if (!ts) return 0;
  if (typeof ts === 'number') return ts;
  if (ts.toMillis && typeof ts.toMillis === 'function') return ts.toMillis();
  if (ts.seconds !== undefined) return ts.seconds * 1000;
  const d = new Date(ts).getTime();
  return isNaN(d) ? 0 : d;
}

export const auditService = {
  subscribeToAuditLogs: (callback: (logs: any[]) => void) => {
    const uid = auth.currentUser?.uid;
    if (!uid) { callback([]); return () => {}; }

    const q = query(
      collection(db, 'users', uid, 'audit_logs'),
      orderBy('timestamp', 'desc'),
      limit(50)
    );

    return onSnapshot(q, (snapshot) => {
      const logs: any[] = [];
      snapshot.forEach((d) => {
        const data = d.data();
        logs.push({
          id: d.id,
          action: data.action,
          targetDeviceId: data.targetDeviceId,
          commandId: data.commandId,
          initiatedFrom: data.initiatedFrom,
          timestamp: convertTimestamp(data.timestamp)
        });
      });
      callback(logs);
    });
  }
};

export const safeZoneService = {
  subscribeToSafeZones: (callback: (zones: SafeZone[]) => void) => {
    const uid = auth.currentUser?.uid;
    if (!uid) { callback([]); return () => {}; }

    return onSnapshot(collection(db, 'users', uid, 'safe_zones'), (snapshot) => {
      const list: SafeZone[] = [];
      snapshot.forEach((d) => {
        const data = d.data();
        list.push({
          id: d.id,
          zoneId: data.zoneId || d.id,
          name: data.name || 'Safe Zone',
          latitude: data.latitude || 0,
          longitude: data.longitude || 0,
          radiusMeters: data.radiusMeters || 100,
          enabled: data.enabled ?? true,
          deviceId: data.deviceId,
          createdAt: convertTimestamp(data.createdAt),
          updatedAt: convertTimestamp(data.updatedAt)
        });
      });
      callback(list);
    });
  },

  createSafeZone: async (zone: Omit<SafeZone, 'id' | 'createdAt' | 'updatedAt' | 'zoneId'>) => {
    const uid = auth.currentUser?.uid;
    if (!uid) throw new Error('User not authenticated');

    const zoneRef = doc(collection(db, 'users', uid, 'safe_zones'));
    const zoneId = zoneRef.id;

    await setDoc(zoneRef, {
      ...zone,
      zoneId,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
    return zoneId;
  },

  updateSafeZone: async (zoneId: string, updates: Partial<SafeZone>) => {
    const uid = auth.currentUser?.uid;
    if (!uid) throw new Error('User not authenticated');

    await setDoc(doc(db, 'users', uid, 'safe_zones', zoneId), {
      ...updates,
      updatedAt: serverTimestamp()
    }, { merge: true });
  },

  deleteSafeZone: async (zoneId: string) => {
    const uid = auth.currentUser?.uid;
    if (!uid) throw new Error('User not authenticated');

    await deleteDoc(doc(db, 'users', uid, 'safe_zones', zoneId));
  }
};

export default authService;
