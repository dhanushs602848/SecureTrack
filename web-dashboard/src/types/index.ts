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
  status: 'ONLINE' | 'OFFLINE' | 'REVOKED';
  protectionEnabled: boolean;
  protectionStatus?: 'ACTIVE' | 'DEGRADED' | 'NEEDS_ATTENTION' | 'OFFLINE';
  protectionReason?: string;
  batteryLevel: number;
  lastSeen?: string;
  createdAt?: string;
  imei1?: string;
  imei2?: string;
  locationStatus?: 'ENABLED' | 'DISABLED' | 'PERMISSION_DENIED';
  trackingStatus?: 'ACTIVE' | 'STOPPED' | 'INTERRUPTED';
  networkStatus?: string;
  lastLatitude?: number;
  lastLongitude?: number;
  locationAccuracy?: number;
  locationSource?: 'GPS' | 'LAST_KNOWN' | 'NETWORK_ESTIMATE';
  locationTimestamp?: string;
  isLostMode?: boolean;
  paired?: boolean;
  ownerUid?: string;
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
  source?: 'GPS' | 'LAST_KNOWN' | 'NETWORK_ESTIMATE' | 'BLE_NEARBY';
}

export interface SecurityEvent {
  id: string;
  deviceId: string;
  deviceName?: string;
  eventType: string;
  severity: 'INFO' | 'WARNING' | 'HIGH' | 'CRITICAL';
  description: string;
  timestamp: string;
  locationStatus?: 'ENABLED' | 'DISABLED';
  zoneId?: string;
  zoneName?: string;
  status?: 'ACTIVE' | 'ACKNOWLEDGED' | 'RESOLVED' | 'DISMISSED';
  executionStatus?: 'REQUESTED' | 'STARTED' | 'COMPLETED' | 'FAILED' | string;
  commandId?: string;
  triggerSource?: string;
  dismissedAt?: string;
  dismissedBy?: string;
  resolvedAt?: string;
}

export interface DeviceCommand {
  id: string;
  deviceId: string;
  commandType: string;
  payload?: string;
  status: 'QUEUED' | 'DELIVERED' | 'RECEIVED' | 'EXECUTING' | 'EXECUTED' | 'FAILED' | 'EXPIRED';
  result?: string;
  transport?: 'CLOUD' | 'BLE';
  createdAt: string;
  expiresAt?: string;
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
