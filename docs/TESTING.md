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
5. **Command Execution (Status: Unverified / In-Progress)**:
   - `Ring Alarm`: Triggers audible ringtone on device (needs physical multi-device validation across manufacturer aggressive power management).
   - `Lock Device`: Invokes DevicePolicyManager to lock screen (needs confirmation across Android 14/15 OEM lockscreen policies).
6. **Device Revocation**: Web dashboard "Revoke" button cleanly removes target device registration without `internal [0]` errors.
