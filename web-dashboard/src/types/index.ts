export interface User {
  id: number;
  email: string;
  fullName: string;
  phoneNumber?: string;
}

export interface Device {
  id: number;
  userId: number;
  deviceName: string;
  model: string;
  androidVersion: string;
  installationId: string;
  status: 'ONLINE' | 'OFFLINE';
  protectionEnabled: boolean;
  batteryLevel: number;
  lastSeen: string;
  createdAt: string;
}

export interface LocationRecord {
  id: number;
  deviceId: number;
  clientUuid: string;
  latitude: number;
  longitude: number;
  accuracy: number;
  altitude: number;
  speed: number;
  timestamp: string;
}

export interface SecurityEvent {
  id: number;
  deviceId: number;
  eventType: string;
  severity: 'INFO' | 'WARNING' | 'HIGH' | 'CRITICAL';
  description: string;
  timestamp: string;
}

export interface DeviceCommand {
  id: number;
  deviceId: number;
  commandType: string;
  payload?: string;
  status: 'QUEUED' | 'DELIVERED' | 'RECEIVED' | 'EXECUTED' | 'FAILED' | 'EXPIRED';
  result?: string;
  createdAt: string;
  executedAt?: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}
