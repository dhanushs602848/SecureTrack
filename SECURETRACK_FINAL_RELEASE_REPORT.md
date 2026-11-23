# SECURETRACK — FINAL RELEASE CLOSEOUT & VERIFICATION REPORT

**Release Version:** v1.8.0 (Build 7)<br>
**Package:** `com.example.securetrack`<br>
**Date:** November 24, 2026<br>
**Repository:** `https://github.com/dhanushs602848/SecureTrack`<br>
**Branch:** `fix/securetrack-global-two-device-release`<br>
**Firebase Backend:** `track-99c90`<br>
**Web Dashboard:** `https://track-99c90.web.app`<br>

---

## 1. Executive Summary & Verification Matrix

| Component | Target Requirement | Verified Result | Status |
| :--- | :--- | :--- | :--- |
| **Git Branch & Remote** | `fix/securetrack-global-two-device-release` | Active working branch, cleanly tracked | **PASS** |
| **Android App ID** | `com.example.securetrack` | Confirmed `com.example.securetrack` | **PASS** |
| **Version Name** | `1.8.0` | `1.8.0` | **PASS** |
| **Version Code** | `7` | `7` | **PASS** |
| **Android Unit Tests** | Hermetic JUnit / Robolectric suites | 65 passed, 0 failed, 0 skipped | **PASS** |
| **Web Dashboard Tests** | Node test runner suite | 7 passed, 0 failed, 0 skipped | **PASS** |
| **Functions Tests** | FCM & Spark pairing suites | 14 passed, 0 failed, 0 skipped | **PASS** |
| **Web Dashboard Build** | Vite production bundle | Built cleanly in `dist/` | **PASS** |
| **Command Expiry (15m)** | Consistent 15m TTL across Android & Web | Verified in unit & integration tests | **PASS** |
| **FCM Payload Symmetry** | Sends and reads both `commandType` and `type` | Verified contract across Functions and Android | **PASS** |
| **Wipe Data Safety** | Non-functional UI button removed; backend fail-closed | Verified in UI layouts and backend handler | **PASS** |
| **Biometric Power Protection** | BiometricPrompt with credential fallback | Verified preference persistence & auth wiring | **PASS** |
| **Automated Geofence Recovery**| Out-of-zone lost mode & re-entry recovery | Verified state transitions and flag preservation | **PASS** |
| **Physical Field Testing** | Multi-device manual testing protocol | Scheduled for execution tomorrow | **SCHEDULED — PROTOCOL READY** |
| **Public Store Signing Key** | Dedicated Play Store release keystore | Requires owner keystore provisioning | **BLOCKED (Internal Build 7 Active)** |

---

## 2. Core Architecture & Safety Hardening

### 2.1 Enforced 15-Minute Command Expiry
- **Validity Window:** All commands (`LOCATE_NOW`, `LOCK_DEVICE`, `ALARM_ALERT`) enforce a strict, uniform 15-minute maximum queue validity window across the Android client, Cloud Firestore, and the Web Dashboard.
- **Atomic Claiming:** `CommandExecutionManager.java` claims queued commands inside Firestore atomic transactions (`runTransaction`), checking both explicit `expiresAt` timestamps and creation age before transitioning status to `CLAIMED` / `EXECUTING`.
- **Zero Zombie Execution:** Commands queued longer than 15 minutes automatically transition to `EXPIRED` within the transaction with result `"Command expired before execution (exceeded 15 min validity window)"`.
- **Active Execution Preservation:** Commands in `CLAIMED` or `EXECUTING` state are never marked as `EXPIRED` simply because their initial creation timestamp is older than 15 minutes.
- **Display Symmetry:** Both the Web Dashboard (`Commands.tsx`) and the Android client (`SecurityRepository.java`, `CommandAdapter.java`) evaluate effective command status symmetrically, correctly handling missing, string-based, or corrupted timestamps without throwing exceptions.

### 2.2 FCM Payload Contract Symmetry
- **Symmetric Dispatch:** `functions/index.js` populates both `type` and `commandType` fields inside the high-priority data-only FCM push payload.
- **Symmetric Ingestion:** `SecureFirebaseMessagingService.java` reads `commandType`, falling back to `type` if `commandType` is absent or empty.
- **Authoritative Source:** The FCM push message is strictly utilized as a low-latency process wake-up trigger. The Android application always fetches, validates, and atomically claims commands directly from Cloud Firestore.

### 2.3 Permanent Factory Wipe Deactivation & UI Cleanup
- **UI Element Removed:** The inactive `btn_wipe_device` button has been completely excised from the Android layout (`activity_device_security.xml`) and controller (`DeviceSecurityActivity.java`). The Web Dashboard (`Commands.tsx`) action grid has been streamlined to 3 active defense buttons.
- **Fail-Closed Backend Protection:** If a `WIPE_DEVICE` command is ever received by `CommandExecutionManager.java`, it is immediately intercepted by `handleWipeCommand()` and rejected with status `FAILED` and result `"Safety Lock: Factory wipe is permanently disabled in this release to protect user data."`.

### 2.4 Biometric Power Protection & Pre-Shutdown Telemetry
- **Hardware Limitations Honestly Disclosed:** On consumer Android devices without enterprise Device Owner provisioning, long-pressing the physical power button (8–10 seconds) triggers a hardware-level forced reset via the PMIC that cannot be intercepted by user-space software.
- **Implemented Protection:** SecureTrack provides best-effort pre-shutdown monitoring via `ShutdownReceiver.java` (listening for `Intent.ACTION_SHUTDOWN` and `android.intent.action.QUICKBOOT_POWEROFF`) to record battery level and telemetry checkpoints before power loss.
- **Biometric Authentication Wiring:** Sensitive administrative controls in `SettingsActivity.java` (such as toggling biometric app shielding or power protection) are gated behind `BiometricAuthHelper.authenticate()`, requiring fingerprint, face, or secure device lock credentials (PIN/pattern/password).

### 2.5 Automated Geofencing & Re-entry Recovery
- **Perimeter Defense:** `GeofenceBroadcastReceiver.java` captures transitions across designated safe zone boundaries.
- **Exit Action:** Transitions the device to `OUT_OF_ZONE`, sets `isLostMode = true` with source `"GEOFENCE"`, and dispatches an alert to the Security Center.
- **Re-entry Recovery:** Upon returning `IN_ZONE`, automatically clears Lost Mode strictly if Lost Mode was engaged by geofencing (`sessionManager.isGeofenceLostMode() == true`), preserving manually engaged Lost Mode intact.

---

## 3. Automated Test Execution Summaries

- **Android Unit Tests:**
  `.\gradlew.bat :app:testDebugUnitTest`
  `65 passed, 0 skipped, 0 failed` across security, location, command lifecycle, and pairing recovery.
- **Web Dashboard Tests:**
  `npm test` in `web-dashboard`
  `7 passed, 0 failed, 0 skipped` validating session tokens, command payload sanitization, 15m queue expiration, boundary conditions, and device selection persistence.
- **Cloud Functions Tests:**
  `npm test` in `functions`
  `14 passed, 0 failed, 0 skipped` validating 256-bit token entropy, rate limiting, and FCM payload symmetry.
- **Web Dashboard Production Build:**
  `npm run build` in `web-dashboard`
  Production Vite bundle compiled cleanly in `web-dashboard/dist/`.

---

## 4. Tomorrow's Physical QA Testing Protocol

To ensure rigorous validation on physical handsets, the following 12-test protocol will be executed during tomorrow's testing session:

| # | Test Case | Action | Expected Result | Pass/Fail Criteria |
|---|---|---|---|---|
| **01** | **Owner Login & Device Sync** | Log in with owner email/password on primary phone and open Web Dashboard. | Dashboard lists enrolled device with live battery level and network status. | Device appears `ONLINE` on both phone and web. |
| **02** | **Remote Locate Trigger** | Tap "Locate Now" on Web Dashboard. | High-priority command dispatched; Android app wakes up, queries GPS, updates Firestore. | Web map centers on coordinates within 10 seconds; command status `EXECUTED`. |
| **03** | **Remote Lock Execution** | Tap "Lock Device" on Web Dashboard. | Device administrator receives command and immediately locks Android screen. | Screen turns off/locks; status in history updates to `EXECUTED`. |
| **04** | **5s Audible Siren Alarm** | Trigger "5s Siren Alarm" from Web Dashboard or Android app. | Urgent dual-tone siren plays at full volume and automatically stops after exactly 5000 ms. | Audio stops at 5 seconds without user intervention; status `EXECUTED`. |
| **05** | **15m Queue Expiry** | Disconnect secondary phone from internet (Airplane mode), dispatch "Locate Now", wait >15 min, reconnect. | Command transitions to `EXPIRED` and does NOT execute on the phone. | Status shows `EXPIRED`; command result indicates 15m validity timeout. |
| **06** | **Wipe Safety Verification** | Verify absence of Wipe button on Web Dashboard and Android UI. | Wipe button is not exposed in user interface; backend remains fail-closed. | No factory wipe option visible; safe against accidental data loss. |
| **07** | **Biometric Protection Toggle** | In Android Settings, attempt to disable Biometric App Shield. | System prompts for fingerprint/face or device PIN before allowing modification. | Switch cannot be toggled off without successful biometric/credential auth. |
| **08** | **Pre-Shutdown Telemetry** | Trigger a normal system restart/power off on the Android handset. | `ShutdownReceiver` intercepts `ACTION_SHUTDOWN` and records `DEVICE_SHUTDOWN` alert with battery level. | Alert visible in Web Dashboard Security Events feed with pre-shutdown timestamp. |
| **09** | **Geofence Exit Transition** | Move phone outside defined safe zone radius (or simulate coordinates via developer options). | `GeofenceBroadcastReceiver` triggers, device transitions to `OUT_OF_ZONE`, engages Lost Mode. | Dashboard shows `OUT_OF_ZONE` status and creates security alert. |
| **10** | **Geofence Re-entry Recovery** | Move phone back inside the safe zone radius. | Device transitions back to `IN_ZONE`; automated Lost Mode disengages automatically. | Lost mode turns off automatically; status shows `IN_ZONE`. |
| **11** | **SIM Change Detection** | Eject physical SIM or toggle SIM state while SIM Change Detection is enabled. | Android client detects state change and creates high-priority `SIM_CHANGED` security alert. | Alert published to Firestore Security Center within 5 seconds. |
| **12** | **Offline BLE Proximity Beacon** | Turn off cellular and Wi-Fi on target phone; open Scanner on controller phone. | Target phone advertises encrypted BLE beacons; controller detects nearby signal. | Controller identifies target device via BLE nearby radar. |

---

## 5. Academic & Contributor Attribution

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
