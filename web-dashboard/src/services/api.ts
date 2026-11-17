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
  deleteDoc,
  query,
  orderBy,
  limit,
  serverTimestamp,
  onSnapshot
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

    return { id: uid || '1', email: email, fullName: fullName };
  },

  logout: async () => {
    await firebaseSignOut(auth);
    localStorage.clear();
  }
};

export const deviceService = {
  subscribeToDevices: (callback: (devices: Device[]) => void) => {
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
    if (!uid) { callback([]); return () => {}; }

    return onSnapshot(collection(db, 'users', uid, 'devices'), (snapshot) => {
      const list: Device[] = [];
      const now = Date.now();
      snapshot.forEach((d) => {
        const data = d.data();

        let status = data.status || 'OFFLINE';
        const lastSeenTs = data.lastSeen ? (data.lastSeen.toMillis ? data.lastSeen.toMillis() : 0) : 0;

        if (lastSeenTs > 0) {
          if (now - lastSeenTs > 5 * 60 * 1000) {
            status = 'OFFLINE';
          }
        }

        list.push({
          id: d.id,
          deviceName: data.deviceName || 'Android Device',
          model: data.model || 'Generic Device',
          androidVersion: data.androidVersion || '14',
          installationId: data.installationId || d.id,
          status: status as any,
          protectionEnabled: data.protectionEnabled ?? true,
          batteryLevel: data.batteryLevel ?? 100,
          lastSeen: convertTimestamp(data.lastSeen)
        });
      });
      callback(list);
    });
  },

  createPairingCode: async (): Promise<{ code: string; expiresAt: string }> => {
    throw new Error('Server-side pairing functions not deployed. Firebase Cloud Functions with billing enabled is required.');
  },
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
            timestamp: convertTimestamp(data.timestamp)
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
            eventType: data.eventType || 'SECURITY_ALERT',
            severity: data.severity || 'INFO',
            description: data.description || 'Security notification',
            timestamp: convertTimestamp(data.timestamp),
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
