import { Timestamp } from 'firebase/firestore';

export interface LocationRecord {
  id?: string;
  deviceId: string;
  clientUuid?: string;
  latitude: number;
  longitude: number;
  accuracy: number;
  altitude?: number;
  speed?: number;
  timestamp: string;
  source: string;
}

export interface DeviceCommand {
  id: string;
  deviceId: string;
  commandType: string;
  payload?: string;
  status: 'CREATED' | 'QUEUED' | 'PENDING' | 'CLAIMED' | 'EXECUTING' | 'EXECUTED' | 'DELIVERED' | 'FAILED' | 'EXPIRED';
  result?: string;
  transport?: string;
  createdAt: string;
  expiresAt?: string;
  executedAt?: string;
  claimedAt?: string;
  requestId?: string;
}

export interface SecurityEvent {
  id: string;
  deviceId: string;
  deviceName: string;
  eventType: string;
  severity: 'INFO' | 'WARNING' | 'CRITICAL' | 'HIGH';
  description: string;
  timestamp: string;
  locationStatus?: string;
  zoneId?: string;
  zoneName?: string;
  status?: 'ACTIVE' | 'RESOLVED' | 'DISMISSED';
  executionStatus?: string;
  commandId?: string;
  triggerSource?: string;
  dismissedAt?: string;
  dismissedBy?: string;
  resolvedAt?: string;
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
  createdAt: string;
  updatedAt: string;
}

export interface Device {
  id: string;
  ownerUid?: string;
  deviceAuthUid?: string;
  deviceName: string;
  model: string;
  androidVersion: string;
  batteryLevel: number;
  status: 'ONLINE' | 'OFFLINE' | 'UNREACHABLE' | 'REVOKED';
  paired: boolean;
  protectionEnabled: boolean;
  protectionStatus?: string;
  protectionReason?: string;
  powerProtectionEnabled?: boolean;
  isLostMode?: boolean;
  installationId?: string;
  lastSeen?: string;
  locationStatus?: string;
  trackingStatus?: string;
  networkStatus?: string;
  lastLocation?: LocationRecord;
  lastLatitude?: number;
  lastLongitude?: number;
  lastAccuracy?: number;
  locationAccuracy?: number;
  locationSource?: string;
  locationTimestamp?: string;
  lastRequestId?: string;
  fcmToken?: string;
}

export interface User {
  id: string;
  email: string;
  fullName: string;
  phoneNumber?: string;
}
