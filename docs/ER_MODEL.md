# Entity-Relationship (ER) Model

## 1. ER Diagram

```mermaid
erDiagram
    USER ||--o{ DEVICE : "owns and manages (1:N)"
    USER ||--o{ PAIRING_INVITATION : "creates (1:N)"
    USER ||--o{ ALERT : "receives (1:N)"
    USER ||--o{ SAFE_ZONE : "defines (1:N)"
    DEVICE ||--o{ LOCATION_RECORD : "records telemetry (1:N)"
    DEVICE ||--o{ DEVICE_COMMAND : "receives commands (1:N)"
    DEVICE ||--o| PRIVATE_RECOVERY : "associated with (1:1)"

    USER {
        string uid PK
        string email
        string fullName
        string phoneNumber
        string authProvider
        timestamp createdAt
    }

    DEVICE {
        string deviceId PK
        string ownerUid FK
        string deviceAuthUid
        string deviceName
        string model
        string androidVersion
        string status
        boolean protectionEnabled
        int batteryLevel
        timestamp lastSeen
    }

    LOCATION_RECORD {
        string locationId PK
        string deviceId FK
        double latitude
        double longitude
        float accuracy
        float speed
        timestamp timestamp
    }

    DEVICE_COMMAND {
        string commandId PK
        string deviceId FK
        string commandType
        string payload
        string status
        timestamp createdAt
        timestamp executedAt
    }

    PAIRING_INVITATION {
        string tokenHash PK
        string ownerUid FK
        string status
        timestamp createdAt
        timestamp expiresAt
        string claimedByUid
        string claimedDeviceId
    }

    PRIVATE_RECOVERY {
        string deviceId PK, FK
        string imei1
        string imei2
        boolean consentGiven
    }
```

---

## 2. Cardinality & Relationship Breakdown
- **User to Device (1 : N)**: One user account can register and monitor multiple Android smartphones. A smartphone is enrolled to exactly one human owner at a time.
- **Device to Location Record (1 : N)**: Each mobile device continuously pushes location telemetry, forming a detailed temporal track.
- **Device to Device Command (1 : N)**: Owners send queued commands (`LOCATE`, `RING_ALARM`, `LOCK`) to target devices.
- **User to Pairing Invitation (1 : N)**: An owner generates one-time, 10-minute pairing invitations. Each invitation is uniquely keyed by a SHA-256 token hash.
- **Device to Private Recovery (1 : 1)**: Sensitive hardware data (such as optional device IMEIs) is isolated in a private subcollection accessible only by the verified human owner.
