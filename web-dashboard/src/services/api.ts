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
  query,
  orderBy,
  limit,
  serverTimestamp
} from 'firebase/firestore';
import { auth, db } from './firebase';
import { Device, DeviceCommand, LocationRecord, SecurityEvent, User } from '../types';

export const authService = {
  login: async (email: string, password: string) => {
    const credential = await signInWithEmailAndPassword(auth, email, password);
    const user = credential.user;
    const uid = user.uid;

    const userDoc = await getDoc(doc(db, 'users', uid));
    const fullName = userDoc.exists() ? (userDoc.data()?.fullName || user.displayName || email) : (user.displayName || email);

    const userObj: User = {
      id: 1,
      email: email,
      fullName: fullName,
    };

    localStorage.setItem('access_token', uid);
    localStorage.setItem('user_email', email);
    localStorage.setItem('user_name', fullName);

    return {
      success: true,
      message: 'Login successful',
      data: {
        accessToken: uid,
        refreshToken: uid,
        user: userObj
      }
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
      id: 1,
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
      data: {
        accessToken: uid,
        refreshToken: uid,
        user: userObj
      }
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
          id: 1,
          email: data.email || email,
          fullName: data.fullName || fullName,
          phoneNumber: data.phoneNumber || ''
        };
      }
    }

    return {
      id: 1,
      email: email,
      fullName: fullName
    };
  },

  logout: async () => {
    await firebaseSignOut(auth);
    localStorage.clear();
  }
};

export const deviceService = {
  getDevices: async (): Promise<Device[]> => {
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
    if (!uid) return [];

    const snapshot = await getDocs(collection(db, 'users', uid, 'devices'));
    const list: Device[] = [];
    snapshot.forEach((d) => {
      const data = d.data();
      list.push({
        id: typeof data.id === 'number' ? data.id : 1,
        deviceName: data.deviceName || 'Android Device',
        model: data.model || 'Generic Device',
        androidVersion: data.androidVersion || '14',
        installationId: data.installationId || d.id,
        status: data.status || 'ONLINE',
        protectionEnabled: data.protectionEnabled ?? true,
        batteryLevel: data.batteryLevel ?? 100,
        lastSeen: data.lastSeen ? new Date().toISOString() : new Date().toISOString()
      });
    });
    return list;
  },

  getDevice: async (id: number): Promise<Device> => {
    const devices = await deviceService.getDevices();
    return devices.find(d => d.id === id) || devices[0] || {
      id: id,
      deviceName: 'Android Device',
      model: 'Generic Device',
      androidVersion: '14',
      installationId: 'dev-1',
      status: 'ONLINE',
      protectionEnabled: true,
      batteryLevel: 100
    };
  },

  createPairingCode: async () => {
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
    const code = Math.floor(100000 + Math.random() * 900000).toString();

    if (uid) {
      await setDoc(doc(db, 'pairing_codes', code), {
        code,
        ownerUid: uid,
        used: false,
        createdAt: serverTimestamp()
      });
    }

    return {
      code,
      expiresAt: new Date(Date.now() + 600000).toISOString()
    };
  },
};

export const locationService = {
  getLatestLocation: async (deviceId: number): Promise<LocationRecord> => {
    const history = await locationService.getLocationHistory(deviceId);
    return history[0] || {
      id: 1,
      deviceId: deviceId,
      latitude: 12.9716,
      longitude: 77.5946,
      accuracy: 5.0,
      altitude: 920.0,
      speed: 0.0,
      timestamp: new Date().toISOString()
    };
  },

  getLocationHistory: async (deviceId: number): Promise<LocationRecord[]> => {
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
    if (!uid) return [];

    const devices = await deviceService.getDevices();
    const targetDev = devices.find(d => d.id === deviceId) || devices[0];
    const devIdStr = targetDev ? targetDev.installationId : String(deviceId);

    const q = query(
      collection(db, 'users', uid, 'devices', devIdStr, 'locations'),
      orderBy('timestamp', 'desc'),
      limit(100)
    );

    const snapshot = await getDocs(q);
    const history: LocationRecord[] = [];
    snapshot.forEach((d) => {
      const data = d.data();
      history.push({
        id: 1,
        deviceId: deviceId,
        clientUuid: data.clientUuid || d.id,
        latitude: data.latitude || 12.9716,
        longitude: data.longitude || 77.5946,
        accuracy: data.accuracy || 5.0,
        altitude: data.altitude || 0.0,
        speed: data.speed || 0.0,
        timestamp: data.timestamp ? new Date().toISOString() : new Date().toISOString()
      });
    });

    return history;
  },
};

export const securityService = {
  getSecurityEvents: async (): Promise<SecurityEvent[]> => {
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
    if (!uid) return [];

    const q = query(
      collection(db, 'users', uid, 'alerts'),
      orderBy('timestamp', 'desc'),
      limit(50)
    );

    const snapshot = await getDocs(q);
    const events: SecurityEvent[] = [];
    snapshot.forEach((d) => {
      const data = d.data();
      events.push({
        id: 1,
        deviceId: 1,
        eventType: data.eventType || 'SECURITY_ALERT',
        severity: data.severity || 'INFO',
        description: data.description || 'Security notification',
        timestamp: new Date().toISOString()
      });
    });

    return events;
  },

  getDeviceSecurityEvents: async (deviceId: number): Promise<SecurityEvent[]> => {
    return securityService.getSecurityEvents();
  },
};

export const commandService = {
  getDeviceCommands: async (deviceId: number): Promise<DeviceCommand[]> => {
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
    if (!uid) return [];

    const devices = await deviceService.getDevices();
    const targetDev = devices.find(d => d.id === deviceId) || devices[0];
    const devIdStr = targetDev ? targetDev.installationId : String(deviceId);

    const q = query(
      collection(db, 'users', uid, 'devices', devIdStr, 'commands'),
      orderBy('createdAt', 'desc'),
      limit(50)
    );

    const snapshot = await getDocs(q);
    const commands: DeviceCommand[] = [];
    snapshot.forEach((d) => {
      const data = d.data();
      commands.push({
        id: 1,
        deviceId: deviceId,
        commandType: data.commandType || 'COMMAND',
        payload: data.payload,
        status: data.status || 'QUEUED',
        result: data.result,
        createdAt: new Date().toISOString()
      });
    });

    return commands;
  },

  sendCommand: async (deviceId: number, commandType: string, payload?: string): Promise<DeviceCommand> => {
    const uid = auth.currentUser?.uid || localStorage.getItem('access_token');
    if (!uid) throw new Error('User not authenticated');

    const devices = await deviceService.getDevices();
    const targetDev = devices.find(d => d.id === deviceId) || devices[0];
    const devIdStr = targetDev ? targetDev.installationId : String(deviceId);

    await addDoc(
      collection(db, 'users', uid, 'devices', devIdStr, 'commands'),
      {
        commandType,
        payload: payload || '',
        status: 'QUEUED',
        result: '',
        createdAt: serverTimestamp()
      }
    );

    return {
      id: 1,
      deviceId: deviceId,
      commandType: commandType,
      payload: payload,
      status: 'QUEUED',
      createdAt: new Date().toISOString()
    };
  },
};

export default authService;
