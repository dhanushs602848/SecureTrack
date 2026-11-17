export interface User {
  id: string;
  email: string;
  fullName: string;
  phoneNumber?: string;
}

export interface Device {
  id: string;
  userId?: string;
  deviceName: string;
  model: string;
  androidVersion: string;
  installationId: string;
  status: 'ONLINE' | 'OFFLINE';
  protectionEnabled: boolean;
  batteryLevel: number;
  lastSeen?: string;
  createdAt?: string;
}

export interface LocationRecord {
  id: string;
  deviceId: string;
  clientUuid?: string;
  latitude: number;
  longitude: number;
  accuracy: number;
  altitude: number;
  speed: number;
  timestamp: string;
}

export interface SecurityEvent {
  id: string;
  deviceId: string;
  eventType: string;
  severity: 'INFO' | 'WARNING' | 'HIGH' | 'CRITICAL';
  description: string;
  timestamp: string;
  zoneId?: string;
  zoneName?: string;
}

export interface DeviceCommand {
  id: string;
  deviceId: string;
  commandType: string;
  payload?: string;
  status: 'QUEUED' | 'DELIVERED' | 'RECEIVED' | 'EXECUTED' | 'FAILED' | 'EXPIRED';
  result?: string;
  createdAt: string;
  executedAt?: string;
}

export interface SafeZone {
  id: string;
  zoneId: string;
  name: string;
  latitude: number;
  longitude: number;
  radiusMeters: number;
  enabled: boolean;
  deviceId?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}
