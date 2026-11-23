# SecureTrack — Smart Mobile Tracking & Anti-Theft Defense System

<div align="center">

**Track smart. Stay secure.**

*An enterprise-grade mobile telemetry, loss prevention, and anti-theft defense platform engineered for Android devices, synchronized with a real-time cybersecurity web operations center.*

[![CI Pipeline](https://github.com/dhanushs602848/SecureTrack/actions/workflows/ci.yml/badge.svg)](https://github.com/dhanushs602848/SecureTrack/actions/workflows/ci.yml)
[![Release Version](https://img.shields.io/badge/Release-v1.8.0--Build--7-blue?logo=android)](https://github.com/dhanushs602848/SecureTrack)
[![Live Dashboard](https://img.shields.io/badge/Hosting-Live%20Dashboard-blue)](https://track-99c90.web.app)
[![Android](https://img.shields.io/badge/Android-API%2026--35-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![React](https://img.shields.io/badge/Web-React%2018%20%2B%20TypeScript-61DAFB?logo=react&logoColor=black)](https://react.dev)
[![Firebase](https://img.shields.io/badge/Cloud-Firebase%20Spark-FFCA28?logo=firebase&logoColor=black)](https://firebase.google.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

</div>

---

## 📋 Table of Contents
- [Overview](#-overview)
- [About SecureTrack](#-about-securetrack)
- [Release 1.8.0 Highlights](#-release-180-build-7-highlights)
- [System Architecture](#-system-architecture)
- [Database Architectures (NoSQL vs Relational)](#-database-architectures-cloud-nosql-vs-local-sqlite)
- [Entity-Relationship Diagram (ERD)](#-entity-relationship-diagram-erd)
- [UML Class Diagram](#-uml-class-diagram)
- [4-Tier Location Resolution Hierarchy](#-4-tier-location-resolution-hierarchy)
- [Key Security & Anti-Theft Features](#-key-security--anti-theft-features)
- [Technology Stack](#-technology-stack)
- [Quick Start](#-quick-start)
  - [Android Client Setup](#1-android-client-setup)
  - [Web Dashboard Setup](#2-web-dashboard-setup)
  - [Firebase Cloud Services](#3-firebase-cloud-services)
- [Automated Testing & CI](#-automated-testing--ci)
- [Hardware Compatibility Matrix](#-hardware-compatibility-matrix)
- [Known Limitations & Security Reality](#-known-limitations--security-reality)
- [Project Mentorship & Contributors](#-project-mentorship--contributors)
- [Documentation Suite](#-comprehensive-documentation-suite)
- [License & Academic Integrity](#-license--academic-integrity)

---

## 🌟 Overview

**SecureTrack** is an advanced endpoint security, continuous telemetry, and anti-theft recovery system designed for mobile devices. It bridges active Android handsets with an authenticated, zero-trust web management console. Whether a device is misplaced, stolen, or subjected to unauthorized access (such as physical SIM card removal or unexpected power-off), SecureTrack gives device owners instant visibility into live coordinates, battery/network state, and active remote defensive controls.

### Production Coordinates:
- **Web Dashboard**: [https://track-99c90.web.app](https://track-99c90.web.app)
- **Firebase Project ID**: `track-99c90` (Authentication, Cloud Firestore, Firebase Hosting)
- **Android Package**: `com.example.securetrack` (Target SDK 35, Min SDK 26)
- **Repository**: [https://github.com/dhanushs602848/SecureTrack](https://github.com/dhanushs602848/SecureTrack)
- **Default Branch**: `main`
- **Release Version**: `1.8.0` (Build `7`)

---

## ℹ️ About SecureTrack

SecureTrack was conceived and engineered as an advanced final-year project in **Database Management Systems (DBMS) and Mobile Cybersecurity**, bridging distributed cloud data architecture with native Android system internals.

### 🎯 Mission & Philosophy
Most conventional tracking applications rely on passive user-space polling and fragile centralized servers. SecureTrack re-architects mobile security around three core principles:
1. **Zero-Trust Telemetry & Verification**: Every telemetry packet and anti-theft command is cryptographically authenticated and scoped strictly to the verified owner UID under strict Cloud Firestore Security Rules.
2. **Dual-Database Architecture (Cloud NoSQL + Local SQLite)**: Distributed real-time synchronization via Google Cloud Firestore is coupled with on-device persistence via Android Room (SQLite) and AES-256 EncryptedSharedPreferences, ensuring resilience against network loss or tampering.
3. **Deterministic Incident Response**: Anti-theft commands (`LOCATE_NOW`, `LOCK_DEVICE`, `ALARM_ALERT`, and guarded `WIPE_DEVICE`) are backed by atomic Firestore claiming, a strict **15-minute queue expiration window (TTL)** to eliminate stale command replay, and a strict **5.0-second safety cutoff** on audible sirens.

### 👥 Project Leadership & Academic Guidance
- **Academic Guide / DBMS Mentor**: **Mohankumar H M** — DBMS Project Guide & Academic Mentor (Database Architecture, Relational Modeling & Academic Review)
- **Project Lead, Owner & Primary Author**: **Dhanush S** ([@dhanushs602848](https://github.com/dhanushs602848)) — System Architecture, Cloud Firestore Integration, Android Security & Release Management
- **Core Engineering Team**:
  - **Deeksith H G** — Core Security Architecture, Telemetry Systems & Verification
  - **Deeksith K** — Android Client Engineering & Feature Integration
  - **Puneethkumar N E** — Database Architecture, Multi-Device Testing & Verification
  - **Deeksith Raj N** — UI/UX Implementation & Geospatial Mapping Integration
  - **Deepak D S** — Quality Assurance, Hardware Field Testing & Documentation

### 📄 Licensing
SecureTrack is released as open-source software under the official **Apache License, Version 2.0** (`Copyright 2026 Dhanush S`).

---

## 🚀 Release 1.8.0 (Build 7) Highlights

- **Biometric Power Protection & Pre-Shutdown Telemetry**:
  - Implements AndroidX `BiometricPrompt` with device credential fallback (PIN/pattern/password) for administrative actions.
  - Intercepts `Intent.ACTION_SHUTDOWN` and `android.intent.action.QUICKBOOT_POWEROFF` via `ShutdownReceiver` to record a high-priority `DEVICE_SHUTDOWN` security checkpoint before power is lost.
  - Provides a dedicated settings toggle with real-time capability assessment (`DEVICE_OWNER_CAPABLE`, `POWER_PROTECTION_ENABLED`, `BEST_EFFORT_MONITORING`, `UNSUPPORTED`).
- **Automated Geofence Out-of-Zone Protection**:
  - Validates `GEOFENCE_EXIT` and `GEOFENCE_ENTER` transitions.
  - Automatically transitions device to `OUT_OF_ZONE` and engages Lost Mode upon exiting designated safety boundaries.
  - Restores normal status upon re-entry strictly when Lost Mode was triggered by geofencing, preserving manually triggered Lost Mode intact.
- **Command Queue 15-Minute Expiry**:
  - Hardened command execution engine against duplicate execution using client `requestId` tracking.
  - Commands queued longer than 15 minutes automatically transition to `EXPIRED` in atomic Firestore transactions.
  - Web dashboard command center renders stale queued commands as `EXPIRED` and executed commands as `EXECUTED`.
- **4-Tier Location Resolution**:
  - Dynamically routes location requests through:
    1. Live Cloud GPS (`LOCATE_NOW` trigger)
    2. Authenticated rotating BLE Nearby Beacons
    3. Last-Known Historical Snapshot with clear `LAST SEEN (STALE)` timestamping
    4. Cell Tower / Wi-Fi Network Fused Provider
- **Hermetic Automated Test Suite**:
  - **86 total automated tests** passing across Android unit tests (65), web dashboard tests (7), and cloud functions (14).
  - Eliminates external mocking library dependencies for 100% deterministic CI runs.

---

## 🏛 System Architecture

SecureTrack employs an authenticated, reactive multi-tier cloud-and-device architecture:

```mermaid
graph TD
    subgraph ClientLayer ["Client Interfaces & Devices"]
        Browser["Web Operations Console<br/>(React 18 + TypeScript + Vite)"]
        Phone["Android Smartphone Client<br/>(SecureTrack v1.8.0 / Target SDK 35)"]
    end

    subgraph CloudLayer ["Firebase Cloud Backend (track-99c90)"]
        Hosting["Firebase Hosting<br/>(SPA Delivery & Global CDN)"]
        Auth["Firebase Authentication<br/>(Zero-Trust Email & Password)"]
        Firestore["Cloud Firestore<br/>(Real-Time Distributed NoSQL)"]
        CloudFunctions["Cloud Functions (Node.js 18)<br/>(onDeviceCommandCreated & Token API)"]
        FCM["Firebase Cloud Messaging (FCM)<br/>(High-Priority Wake-Up Dispatch)"]
    end

    subgraph AndroidInternal ["Android Client Engine & On-Device Storage"]
        ForegroundService["LocationTrackingService<br/>(Persistent Foreground GPS Engine)"]
        FCMService["SecureFirebaseMessagingService<br/>(Push Wake-Up & Pre-Execution)"]
        CommandManager["CommandExecutionManager<br/>(15m TTL Expiry & Atomic Execution)"]
        AdminReceiver["SecureAdminReceiver<br/>(DevicePolicyManager Remote Lock)"]
        RoomDB["Android Room SQLite DB<br/>(Offline Cache & Incident Log)"]
        EncryptedPrefs["EncryptedSharedPreferences<br/>(AES-256 Master Key Token Store)"]
    end

    Browser -->|HTTPS / Hosting Bundle| Hosting
    Browser -->|JWT Authentication Tokens| Auth
    Browser -->|Real-Time Snapshot Listeners| Firestore
    Browser -->|Dispatch Remote Anti-Theft Commands| Firestore

    Firestore -->|onCreate Document Trigger| CloudFunctions
    CloudFunctions -->|High-Priority Data Message| FCM
    FCM -->|Push Wake-Up Packet| FCMService
    FCMService -->|Execute Queued Command| CommandManager

    Phone -->|Continuous GPS Telemetry Stream| Firestore
    Phone -->|Snapshot Listener: commands| Firestore
    Phone -->|Atomic Status Update EXECUTED or EXPIRED| Firestore

    Phone --> ForegroundService
    Phone --> CommandManager
    CommandManager -->|Screen Lock Action| AdminReceiver
    Phone --> RoomDB
    Phone --> EncryptedPrefs
```

### 🔄 End-to-End Command Dispatch & Execution Lifecycle
1. **Operator Command Dispatch**: The security operator triggers an anti-theft action (`LOCATE_NOW`, `LOCK_DEVICE`, `ALARM_ALERT`, or guarded `WIPE_DEVICE`) from the Web Console. A command record is written to `users/{uid}/devices/{deviceId}/commands/{commandId}` with state `QUEUED` and timestamp `createdAt`.
2. **Cloud Functions Wake-Up**: The Firebase Cloud Function `onDeviceCommandCreated` triggers instantaneously on document creation, fetches the target device's registered FCM registration token, and dispatches a high-priority push data payload through FCM.
3. **Android Wake-Up & Atomic Claim**: `SecureFirebaseMessagingService` receives the wake-up intent and routes execution to `CommandExecutionManager`. The manager validates the **15-minute queue expiry window** (`currentTime - queuedAt <= 15 minutes`). If expired, it atomically writes status `EXPIRED` to Firestore and terminates. If valid, it atomically transitions state to `EXECUTING` with client `requestId` deduplication.
4. **Action Execution**:
   - `LOCATE_NOW`: Forces high-accuracy GPS fix via `FusedLocationProviderClient` and synchronizes fresh location record to `locations/{locationId}`.
   - `LOCK_DEVICE`: Invokes `DevicePolicyManager.lockNow()` via `SecureAdminReceiver`.
   - `ALARM_ALERT`: Triggers audio siren via `AlarmSoundManager` with a strict **5.0-second maximum duration cutoff**.
   - `WIPE_DEVICE`: Protected by fail-closed safety confirmation locks; requires administrative master confirmation.
5. **Final Status Reconciliation**: The handset updates the command status in Firestore to `EXECUTED` (or `FAILED` with diagnostics). Both Web Console and local Room SQLite cache mirror the verified outcome in real-time.

---

## 🗄 Database Architectures: Cloud NoSQL vs Local SQLite

SecureTrack distinctly separates **Cloud Document Storage (NoSQL)** for distributed real-time sync from **On-Device Relational Storage (SQLite via Room)** for offline resilience.

### 1. Cloud Firestore (Distributed NoSQL)
Hierarchical document collections secured with strict per-user authentication rules (`request.auth.uid == resource.data.ownerUid`):
- `users/{uid}`: User account metadata, registered email, and session configuration.
- `users/{uid}/devices/{deviceId}`: Enrolled device details, battery level, network status, protection status, zone status, and latest telemetry coordinates.
- `users/{uid}/devices/{deviceId}/locations/{locationId}`: Historical GPS telemetry points with accuracy, speed, provider, and correlation `requestId`.
- `users/{uid}/devices/{deviceId}/commands/{commandId}`: Anti-theft commands (`LOCATE_NOW`, `LOCK_DEVICE`, `ALARM_ALERT`, `WIPE_DEVICE`) with lifecycle tracking (`QUEUED`, `EXECUTING`, `EXECUTED`, `FAILED`, `EXPIRED`).
- `users/{uid}/alerts/{alertId}`: Global security incident feed (SIM swap, shutdown warning, geofence breach, low battery beacon).

### 2. Android Room Database (Local Relational SQLite)
Persists critical device logs locally on the handset when network access is severed:
- `cached_devices`: Offline cache of enrolled devices.
- `security_alerts`: Local mirror of alerts with fields `id`, `deviceId`, `eventType`, `severity`, `description`, `status` (`ACTIVE`/`DISMISSED`), `timestamp`, and `dismissedAt`.
- `commands`: Command execution cache with TTL expiry verification.
- `locations`: Breadcrumb history cache supporting offline path rendering.

---

## 📊 Entity-Relationship Diagram (ERD)

The relational schema model underpinning local Android Room persistence and the enterprise reference architecture:

```mermaid
erDiagram
    USER ||--o{ DEVICE : owns
    USER ||--o{ SECURITY_ALERT : receives
    DEVICE ||--o{ LOCATION_LOG : records
    DEVICE ||--o{ DEVICE_COMMAND : receives
    DEVICE ||--o{ SECURITY_ALERT : generates

    USER {
        string uid PK
        string email
        string displayName
        datetime createdAt
    }

    DEVICE {
        string deviceId PK
        string ownerUid FK
        string deviceName
        string model
        string osVersion
        string protectionStatus
        string zoneStatus
        int batteryLevel
        boolean isLostMode
        float lastLatitude
        float lastLongitude
        datetime lastSeen
    }

    LOCATION_LOG {
        string id PK
        string deviceId FK
        float latitude
        float longitude
        float accuracy
        string provider
        string freshness
        datetime recordedAt
    }

    DEVICE_COMMAND {
        string id PK
        string deviceId FK
        string commandType
        string status
        string result
        datetime queuedAt
        datetime executedAt
    }

    SECURITY_ALERT {
        string id PK
        string deviceId FK
        string eventType
        string severity
        string status
        string description
        datetime createdAt
        datetime dismissedAt
        string dismissedBy
    }
```

---

## 📐 UML Class Diagram

High-level class structure highlighting core managers, services, and domain models in the Android client:

```mermaid
classDiagram
    class SecureTrackApp {
        +onCreate()
        +applyTheme(String mode)
    }

    class BiometricPowerProtectionManager {
        +getInstance(Context) BiometricPowerProtectionManager
        +isPowerProtectionEnabled() boolean
        +setPowerProtectionEnabled(boolean)
        +getCapabilityState() CapabilityState
        +authenticateSensitiveAction(FragmentActivity, String, Callback)
        +recordImpendingShutdownEvent()
    }

    class GeofenceBroadcastReceiver {
        +onReceive(Context, Intent)
    }

    class LocationPriorityManager {
        +resolveTargetLocation(String, Callback)
        -attemptCloudLocate(String, Callback)
        -attemptBleFallback(String, Callback)
        -attemptLastKnownFallback(String, Callback)
    }

    class CommandExecutionManager {
        +pollAndExecuteCommands()
        -executeSingleCommand(String, String, DocumentSnapshot)
        -executeClaimedCommand(String, String, String, DocumentSnapshot)
    }

    class AlarmSoundManager {
        +playPanicSiren(Context context)
        +stopAlarm()
        +isAlarmPlaying() boolean
    }

    class SessionManager {
        +isPowerProtectionEnabled() boolean
        +setPowerProtectionEnabled(boolean)
        +isGeofenceLostMode() boolean
        +setGeofenceLostMode(boolean)
    }

    SecureTrackApp --> BiometricPowerProtectionManager
    BiometricPowerProtectionManager --> SessionManager
    CommandExecutionManager --> SessionManager
    LocationPriorityManager --> CommandExecutionManager
    CommandExecutionManager --> AlarmSoundManager
```

---

## 📍 4-Tier Location Resolution Hierarchy

When an owner requests a device location, SecureTrack intelligently resolves coordinates through a prioritized 4-tier pipeline:

1. **Tier 1 — Live Cloud GPS (Primary)**:
   Dispatches a real-time high-accuracy location request over FCM/Cloud Firestore. Correlates coordinates using unique client `requestId` tokens.
2. **Tier 2 — Authenticated BLE Nearby Radar**:
   If cloud satellite GPS is unavailable or the device is indoors/offline, SecureTrack utilizes Bluetooth Low Energy (`BleNearbyManager`) rotating cryptographic beacons to resolve proximity (~10–30 meters) via nearby peer devices.
3. **Tier 3 — Last-Known Location Snapshot**:
   If GPS is turned off or battery has died, the system retrieves the most recent recorded coordinates before signal loss, explicitly marked on the dashboard as `LAST SEEN (STALE)` with relative elapsed time (e.g., *"14 minutes ago"*).
4. **Tier 4 — Network & Cell/Wi-Fi Fused Provider**:
   Estimates position using connected mobile cell towers and known Wi-Fi BSSIDs when satellite GPS is obstructed.

---

## 🛡 Key Security & Anti-Theft Features

1. **Biometric Power Protection**:
   - Authenticates sensitive administrative actions using AndroidX `BiometricPrompt` with secure device credential fallback (PIN/pattern/password).
   - Intercepts system shutdown events to capture battery level and record telemetry checkpoints before power loss.

2. **Automated Geofence Defense**:
   - Instant perimeter breach detection. Transitions device to `OUT_OF_ZONE` and automatically activates Lost Mode upon leaving designated safe zones.
   - Restores normal status upon re-entry strictly when Lost Mode was triggered by geofencing, preserving manually triggered Lost Mode intact.

3. **15-Minute Command Expiry & Idempotent Claiming**:
   - Dispatched commands attach a unique client `requestId` (`req_<timestamp>_<rand>`).
   - Claimed via atomic Firestore transactions, transitioning `QUEUED` $\rightarrow$ `EXECUTING` $\rightarrow$ `EXECUTED` / `FAILED` / `EXPIRED`.
   - Stale queued commands older than 15 minutes automatically transition to `EXPIRED` to prevent zombie execution.

4. **Panic Siren with 5s Auto-Stop**:
   - Synthesizes urgent dual-tone security audio without external media dependencies.
   - Enforces a strict 5000 ms auto-timeout to prevent acoustic damage while ensuring maximum volume alert.

5. **Permanent Factory Wipe Safety Lock**:
   - `DevicePolicyManager.wipeData(0)` has been permanently deactivated across all application components to prevent accidental or unauthorized data loss.

---

## 💻 Technology Stack

| Layer | Technologies |
|---|---|
| **Android Client** | Java 17, Android SDK (API 26–35), AndroidX Room SQLite, AndroidX Biometrics, Google Play Services Location & Geofencing, Material Design 3 |
| **Web Dashboard** | React 18, TypeScript 5, Vite 5, Tailwind CSS 3, Lucide React, Leaflet & OpenStreetMap, Recharts |
| **Cloud Services** | Firebase Authentication, Cloud Firestore NoSQL, Firebase Cloud Messaging, Firebase Hosting (Spark Tier) |
| **Testing & CI** | JUnit 4, Robolectric, Node.js Native Test Runner, GitHub Actions CI Pipeline, Gradle 8.x |

---

## 📱 Hardware & Platform Support

SecureTrack is architected for modern Android hardware spanning API level 26 (Android 8.0 Oreo) through API level 35 (Android 15) and forward-compatible with Android 16:

| Platform / Role | Target OS | Primary Subsystems | Verification Status |
| :--- | :--- | :--- | :--- |
| **Owner / Controller Handset** | Android 10 – 16 (API 29–36) | Security Dashboard, Device Admin, BiometricPrompt | **PASS** (65 automated unit tests) |
| **Enrolled Target Handset** | Android 8.0 – 15 (API 26–35) | Background Geofence, BLE Peripheral, Pre-Shutdown Receiver | **PASS** (Hermetic test suites) |
| **Web Operations Center** | Chrome / Firefox / Safari | Vite SPA, Cloud Firestore, Leaflet Map View | **PASS** (7 automated unit tests) |

---

## ⚡ Quick Start

### 1. Android Client Setup
```bash
# Clone the repository
git clone https://github.com/dhanushs602848/SecureTrack.git
cd SecureTrack

# Run automated unit tests (65 tests across security, location, and power protection)
./gradlew :app:testDebugUnitTest

# Assemble release APK (v1.8.0 Build 7)
./gradlew :app:assembleRelease
```
The compiled APK will be generated at:
`release/SecureTrack-final.apk`

Install to connected device via ADB:
```bash
adb install -r -d release/SecureTrack-final.apk
```

### 2. Web Dashboard Setup
```bash
cd web-dashboard

# Install npm dependencies
npm install

# Run automated tests (7 tests passing)
npm test

# Run linter
npm run lint

# Compile production bundle
npm run build
```

### 3. Firebase Cloud Deployment
```bash
# Deploy web dashboard to Firebase Hosting
firebase deploy --only hosting
```

---

## 🧪 Automated Testing & CI

Continuous verification is enforced across Android and Web:
- **Android Unit Suite (`:app:testDebugUnitTest`)**: **65 automated tests** passing:
  - Biometric power protection preference persistence and capability evaluation.
  - Shutdown receiver null safety and broadcast handling.
  - 5-second siren duration constraints.
  - Multi-tier location fallback priority hierarchy (`CLOUD_LIVE` → `BLE_NEARBY` → `LAST_KNOWN`).
  - Command lifecycle progression and 15-minute expiration logic.
  - Multi-device pairing recovery evaluation and identity isolation.
  - Geofence state transitions and lost mode persistence.
- **Web Verification (`web-dashboard/test/auth.test.js`)**: **7 automated tests** passing:
  - 15-minute queue expiry evaluation (`QUEUED` > 15m $\rightarrow$ `EXPIRED`).
  - Payload sanitization and safety lock validation.
  - Location staleness threshold evaluation.
  - Device selector state updater stability.
- **Functions Verification (`functions/test/*.test.js`)**: **14 automated tests** passing:
  - 256-bit pairing token entropy and one-way SHA-256 hashing.
  - 10-minute pairing TTL expiration and single-use invalidation.
  - Device revocation and cross-owner isolation.

---

## ⚠️ Known Limitations & Security Reality

1. **Hardware Power-Off**: Software cannot execute while a physical battery is depleted or when a device is completely powered down. Pre-shutdown telemetry is captured on a best-effort basis.
2. **Hardware Power Button Long-Press**: On personal/unmanaged consumer Android devices, long-pressing the power button for 8–10 seconds is handled directly by the Power Management IC (PMIC) at the hardware level and cannot be intercepted by user-space applications. Anti-shutdown Lock Task mode is only accessible under Android Enterprise Device Owner provisioning.
3. **Background Location Limits**: Android 10+ battery optimization limits background location polling unless the user explicitly grants `"Allow all the time"` in system settings and whitelists SecureTrack from battery optimization.
4. **Physical Siren Activation**: The physical audible sound test has been intentionally deferred to prevent late-night noise disturbance. Underlying 5-second duration logic is verified via unit testing.

---

## 👥 Project Mentorship & Contributors

### 🎓 Academic Project Guide
- **Mohankumar H M** — DBMS Project Guide & Academic Mentor (Database Architecture, Relational Modeling & Academic Review)

### 👨‍💻 Project Lead, Owner & Author
- **Dhanush S** ([@dhanushs602848](https://github.com/dhanushs602848)) — System Architecture, Cloud Firestore Integration, Android Security & Release Management

### 🤝 Project Contributors & Engineers
- **Deeksith H G** — Core Security Architecture, Telemetry Systems & Verification
- **Deeksith K** — Android Client Engineering & Feature Integration
- **Puneethkumar N E** — Database Architecture, Multi-Device Testing & Verification
- **Deeksith Raj N** — UI/UX Implementation & Geospatial Mapping Integration
- **Deepak D S** — Quality Assurance, Hardware Field Testing & Documentation

See [`CONTRIBUTORS.md`](CONTRIBUTORS.md) and [`NOTICE`](NOTICE) for detailed contributor and license attribution.

---

## 📚 Comprehensive Documentation Suite

| Document | Description |
|---|---|
| [**INDEX.md**](docs/INDEX.md) | Master catalog of all project documentation |
| [**PROJECT_OVERVIEW.md**](docs/PROJECT_OVERVIEW.md) | Problem statement, functional objectives, and scope |
| [**SYSTEM_ARCHITECTURE.md**](docs/SYSTEM_ARCHITECTURE.md) | Multi-tier architecture and component interaction |
| [**DATABASE_DESIGN.md**](docs/DATABASE_DESIGN.md) | Cloud Firestore NoSQL schema, collections, and security |
| [**ER_MODEL.md**](docs/ER_MODEL.md) | Entity-Relationship diagram and cardinality constraints |
| [**SECURITY_AND_PRIVACY.md**](docs/SECURITY_AND_PRIVACY.md) | Threat model, zero-trust pairing, and privacy controls |
| [**TESTING.md**](docs/TESTING.md) | Test suites, coverage metrics, and physical device test protocol |
| [**KNOWN_LIMITATIONS.md**](docs/KNOWN_LIMITATIONS.md) | Technical boundaries, Android OS constraints, and hardware caveats |
| [**DEMO_GUIDE.md**](docs/DEMO_GUIDE.md) | Step-by-step presentation walkthrough and demonstration script |
| [**SECURETRACK_FINAL_RELEASE_REPORT.md**](SECURETRACK_FINAL_RELEASE_REPORT.md) | Final release closeout and verification report |

---

## 📜 License & Academic Integrity

This project is licensed under the **Apache License, Version 2.0**. See the [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE) files for full details.
