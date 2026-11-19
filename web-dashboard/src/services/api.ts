import {
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
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
  query,
  orderBy,
  limit,
  serverTimestamp,
  onSnapshot,
  Timestamp,
  runTransaction
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
    const uid = user.uid;

    const userDoc = await getDoc(doc(db, 'users', uid));
    const fullName = userDoc.exists() ? (userDoc.data()?.fullName || user.displayName || email) : (user.displayName || email);

    const userObj: User = {
      id: uid,
      email: email,
      fullName: fullName,
    };

    localStorage.setItem('access_token', uid);
    localStorage.setItem('user_email', email);
    localStorage.setItem('user_name', fullName);

    return {
      success: true,
      message: 'Login successful',
      data: { accessToken: uid, refreshToken: uid, user: userObj }
    };
  },

  register: async (email: string, password: string, fullName: string, phoneNumber?: string) => {
    const credential = await createUserWithEmailAndPassword(auth, email, password);
    const user = credential.user;
    const uid = user.uid;

    await setDoc(doc(db, 'users', uid), {
      id: uid,
      email: email,
      fullName: fullName,
      phoneNumber: phoneNumber || '',
      createdAt: serverTimestamp()
    }, { merge: true });

    const userObj: User = {
      id: uid,
      email: email,
      fullName: fullName,
      phoneNumber: phoneNumber
    };

    localStorage.setItem('access_token', uid);
    localStorage.setItem('user_email', email);
    localStorage.setItem('user_name', fullName);

    return {
      success: true,
      message: 'Registration successful',
      data: { accessToken: uid, refreshToken: uid, user: userObj }
    };
  },

  getCurrentUser: async (): Promise<User> => {
    const currentUser = auth.currentUser;
    const uid = currentUser?.uid || localStorage.getItem('access_token');
    const email = currentUser?.email || localStorage.getItem('user_email') || '';
    const fullName = currentUser?.displayName || localStorage.getItem('user_name') || email;

    if (uid) {
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
    }

    return { id: uid || '', email: email, fullName: fullName };
  },

  logout: async () => {
    await firebaseSignOut(auth);
    localStorage.clear();
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
    protectionEnabled: data.protectionEnabled ?? true,
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
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
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
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
    if (!uid) return [];

    const snapshot = await getDocs(collection(db, 'users', uid, 'devices'));
    const list: Device[] = [];
    snapshot.forEach((d) => {
      list.push(parseDeviceDoc(d));
    });
    return list;
  },

  getRecoveryInfo: async (deviceId: string): Promise<{ imei1?: string; imei2?: string; consentGiven?: boolean } | null> => {
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
    if (!uid) return null;

    const docRef = doc(db, 'users', uid, 'private_recovery', deviceId);
    const snapshot = await getDoc(docRef);
    if (snapshot.exists()) {
      return snapshot.data() as { imei1?: string; imei2?: string; consentGiven?: boolean };
    }
    return null;
  },

  createPairingCode: async (): Promise<{ code: string; expiresAt: string }> => {
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
    if (!uid) throw new Error('User not authenticated');

    // Generate random 8-digit code
    const code = Math.floor(10000000 + Math.random() * 90000000).toString();
    const expiresAtMillis = Date.now() + 10 * 60 * 1000;

    // Compute simple hash for client-side storage
    let hash = 0;
    for (let i = 0; i < code.length; i++) {
      hash = ((hash << 5) - hash) + code.charCodeAt(i);
      hash |= 0;
    }
    const codeId = code;

    await setDoc(doc(db, 'pairing_codes', codeId), {
      code: code,
      ownerUid: uid,
      status: 'ACTIVE',
      createdAt: serverTimestamp(),
      expiresAt: Timestamp.fromMillis(expiresAtMillis),
      attempts: 0
    });

    return {
      code,
      expiresAt: new Date(expiresAtMillis).toISOString()
    };
  },

  revokePairedDevice: async (deviceId: string): Promise<void> => {
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
    if (!uid) throw new Error('User not authenticated');

    const devRef = doc(db, 'users', uid, 'devices', deviceId);
    await updateDoc(devRef, {
      status: 'REVOKED',
      protectionEnabled: false,
      revokedAt: serverTimestamp()
    });
  }
};

export const locationService = {
  subscribeToLocationHistory: (deviceId: string, callback: (history: LocationRecord[]) => void) => {
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
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
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
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
            zoneName: data.zoneName
          });
        }
      });
      callback(events);
    });
  },
};

export const commandService = {
  subscribeToDeviceCommands: (deviceId: string, callback: (commands: DeviceCommand[]) => void) => {
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
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
          createdAt: convertTimestamp(data.createdAt),
          executedAt: convertTimestamp(data.executedAt)
        });
      });
      callback(commands);
    });
  },

  sendCommand: async (deviceId: string, commandType: string, payload?: string): Promise<DeviceCommand> => {
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
    if (!uid) throw new Error('User not authenticated');

    const commandDoc = await addDoc(
      collection(db, 'users', uid, 'devices', deviceId, 'commands'),
      {
        commandType,
        payload: payload || '',
        status: 'QUEUED',
        result: '',
        createdAt: serverTimestamp()
      }
    );

    return {
      id: commandDoc.id,
      deviceId: deviceId,
      commandType: commandType,
      payload: payload,
      status: 'QUEUED',
      createdAt: new Date().toISOString()
    };
  },
};

export const safeZoneService = {
  subscribeToSafeZones: (callback: (zones: SafeZone[]) => void) => {
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
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
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
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
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
    if (!uid) throw new Error('User not authenticated');

    await setDoc(doc(db, 'users', uid, 'safe_zones', zoneId), {
      ...updates,
      updatedAt: serverTimestamp()
    }, { merge: true });
  },

  deleteSafeZone: async (zoneId: string) => {
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
    if (!uid) throw new Error('User not authenticated');

    await deleteDoc(doc(db, 'users', uid, 'safe_zones', zoneId));
  }
};

export default authService;
