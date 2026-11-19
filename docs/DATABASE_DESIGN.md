# Cloud Firestore Database Design

## Database Paradigm
SecureTrack uses Google Cloud Firestore, a scalable NoSQL document-oriented cloud database. Collections store JSON-like documents with subcollections for device telemetry.

---

## 1. Top-Level Collections Overview
```
├── users/ (Collection)
│   └── {userId}/ (Document: User Profile)
│       ├── devices/ (Subcollection: Registered Devices)
│       │   └── {deviceId}/ (Document: Device State)
│       │       ├── locations/ (Subcollection: Coordinate History)
│       │       └── commands/ (Subcollection: Remote Commands)
│       ├── private_recovery/ (Subcollection: Sensitive Hardware Keys)
│       │   └── {deviceId}/ (Document: Recovery Data)
│       ├── alerts/ (Subcollection: Security Alerts)
│       └── safe_zones/ (Subcollection: Geofences)
└── pairing_invitations/ (Collection: Ephemeral Cryptographic Invitations)
    └── {tokenHash}/ (Document: Invitation State)
```

> **Note on Deletion Semantics:** In Cloud Firestore, deleting a document (such as `/users/{userId}/devices/{deviceId}`) deletes that specific document only. Subcollections (like `/locations` or `/commands`) are not deleted recursively by the database engine. If complete cascading removal is required, subcollection documents must be deleted in batches.

---

## 2. Document Schema Specifications

### `users/{userId}`
Stores core profile information for the account owner.
```json
{
  "id": "FIREBASE_UID_STRING",
  "email": "user@example.com",
  "fullName": "Test User",
  "phoneNumber": "+1 555-0100",
  "authProvider": "password",
  "createdAt": "Timestamp"
}
```

### `users/{userId}/devices/{deviceId}`
Represents an enrolled smartphone managed by the user.
```json
{
  "id": "dev-uuid-sample-01",
  "ownerUid": "FIREBASE_UID_STRING",
  "deviceAuthUid": "ANONYMOUS_OR_CLIENT_AUTH_UID",
  "deviceName": "Android Test Device",
  "model": "Generic-A01",
  "androidVersion": "14",
  "installationId": "dev-uuid-sample-01",
  "batteryLevel": 84,
  "status": "ONLINE",
  "paired": true,
  "protectionEnabled": true,
  "locationStatus": "ENABLED",
  "trackingStatus": "ACTIVE",
  "networkStatus": "CONNECTED",
  "lastSeen": "Timestamp",
  "lastLatitude": 37.774929,
  "lastLongitude": -122.419416
}
```

### `users/{userId}/devices/{deviceId}/locations/{locationId}`
Time-series GPS coordinate logs streamed from the mobile client.
```json
{
  "latitude": 37.774929,
  "longitude": -122.419416,
  "accuracy": 12.5,
  "altitude": 15.0,
  "speed": 0.0,
  "bearing": 0.0,
  "batteryLevel": 84,
  "isCharging": false,
  "networkType": "WIFI",
  "provider": "fused",
  "recordedAt": "Timestamp",
  "uploadedAt": "Timestamp"
}
```

### `users/{userId}/devices/{deviceId}/commands/{commandId}`
Remote administrative instructions dispatched from the Web Dashboard.
```json
{
  "commandType": "RING_ALARM",
  "payload": "VOLUME_MAX",
  "status": "QUEUED",
  "result": "",
  "createdAt": "Timestamp",
  "executedAt": null
}
```

### `pairing_invitations/{tokenHash}`
Single-use cryptographic invitations with 10-minute expiry.
```json
{
  "ownerUid": "FIREBASE_UID_STRING",
  "ownerEmail": "user@example.com",
  "status": "PENDING_CLAIM",
  "createdAt": "Timestamp",
  "expiresAt": "Timestamp (now + 10 mins)",
  "claimedByUid": null,
  "claimedDeviceId": null,
  "deviceInfo": null,
  "approvedAt": null
}
```
