# SecureTrack — Test Strategy & Verification Guide

This document covers the automated and manual testing strategies employed across the SecureTrack project, including unit tests, integration tests, and physical device verification procedures.

---

## 1. Test Architecture Overview

| Layer | Framework / Tools | Scope |
|---|---|---|
| **Android Unit Tests** | JUnit 4, Robolectric, Mockito | Model serialization, repository logic, session state handling |
| **Web Dashboard Tests** | Node.js built-in test runner (`node:test`) | Authentication state, form validation, error message parsing |
| **Handshake & Rules Tests** | Node.js test runner | Invitation token generation, single-use claim, owner revocation |
| **Continuous Integration** | GitHub Actions (`.github/workflows/ci.yml`) | Multi-job build, test, and assembly verification |

---

## 2. Executing Automated Test Suites

### 2.1 Android Unit Tests
Run the Gradle test task from the project root:
```bash
./gradlew :app:testDebugUnitTest --info
```

### 2.2 Web Dashboard Unit Tests
Run the web authentication and validation tests:
```bash
node --test web-dashboard/test/auth.test.js
```

### 2.3 Cloud Pairing & Revocation Test Suite
Run the 10-step cryptographic pairing and revocation test suite:
```bash
node --test functions/test/pairing.test.js
```

**Test Coverage Summary for Pairing & Revocation:**
- `generatePairingCode`: Verifies cryptographically secure entropy and structure.
- `validatePairingCode`: Ensures 10-minute expiration window is strictly checked.
- `claimPairingCode`: Verifies single-use guarantee and owner association.
- `revokeDevice`: Validates owner authorization, prevents cross-user revocation, and verifies idempotent cleanup of duplicate offline registrations.

---

## 3. Physical Device Verification Protocol

When testing on physical Android test hardware:

1. **Splash & Welcome**: App boots cleanly without crashing, showing animated pulse radar.
2. **Permissions Flow**: Location (fine + background), Camera, and Device Admin prompts appear in sequence.
3. **QR Code Pairing**: Web dashboard displays fresh QR code; camera scans and links device in under 2 seconds.
4. **Live Telemetry Stream**: Device battery percentage and GPS coordinates update on the web dashboard map.
5. **Command Execution**:
   - `Ring Alarm`: Triggers audible ringtone on device (deferred to daytime testing).
   - `Lock Device`: Invokes DevicePolicyManager to lock screen.
6. **Device Revocation**: Web dashboard "Revoke" button cleanly removes target device registration without `internal [0]` errors.

---

## 4. Physical Two-Device Acceptance Testing (23 November 2026)

Initial physical two-device validation performed with **Samsung Galaxy A07 5G** (Owner/Controller) and **Vivo V2428** (Paired Secondary):

| Test ID | Scenario | Status | Verified Details & Pending Scope |
|---|---|---|---|
| **PHY-01** | Device List Visibility | **PASS** | Vivo device appears in the Samsung SecureTrack device list with accurate name, model, and online status. |
| **PHY-02** | Initial Location Display | **PASS** | Samsung device successfully displays the Vivo device's reported location coordinates. |
| **PHY-03** | Move Vivo & Refresh Coordinates ("Locate Now") | **PENDING** | Move Vivo device and press Locate Now on Samsung to verify fresh GPS coordinates are received. |
| **PHY-04** | Location Timestamp Update | **PENDING** | Verify that the location timestamp updates accurately upon telemetry ingestion. |
| **PHY-05** | Remote Lock Functionality | **PENDING** | Verify screen lock invocation via DevicePolicyManager on Vivo. |
| **PHY-06** | Remote Siren Functionality | **PENDING** | Verify 5-second synthesized alarm siren on Vivo (deferred until daytime). |
| **PHY-07** | Locked-Screen FCM Background Wake-up | **PENDING** | Verify high-priority FCM wake-up on Vivo with screen locked under OEM power management. |
| **PHY-08** | End-to-End Real-World Conditions | **PENDING** | Verify complete two-device workflow under realistic loss/theft field conditions. |
