# SECURETRACK — FINAL RELEASE CLOSEOUT & VERIFICATION REPORT

**Release Version:** v1.8.0 (Build 7)  
**Package:** `com.example.securetrack`  
**Date:** November 23, 2026  
**Repository:** `https://github.com/dhanushs602848/SecureTrack`  
**Branch:** `fix/securetrack-global-two-device-release`  
**Firebase Backend:** `track-99c90`  
**Web Dashboard:** `https://track-99c90.web.app`  

---

## 1. Executive Summary & Verification Matrix

| Component | Target Requirement | Verified Result | Status |
| :--- | :--- | :--- | :--- |
| **Git Branch & Remote** | `fix/securetrack-global-two-device-release` | Fully tracked and synchronised | **PASS** |
| **Android App ID** | `com.example.securetrack` | `com.example.securetrack` | **PASS** |
| **Version Name** | `1.8.0` | `1.8.0` | **PASS** |
| **Version Code** | `7` | `7` | **PASS** |
| **Release APK Artifact** | `release/SecureTrack-final.apk` | Generated & Verified | **PASS** |
| **Release APK SHA-256** | `022D995D9F2609D1D43DD4E1DC5E7B9D6C80A765AC7834225ADA55149DD3783C` | Verifiable on disk | **PASS** |
| **Signing Certificate SHA-256** | `1659f5dcec61ab9ae2a6e0318341079a9099a9567282eed1011c3a0dc49f6843` | Preserves in-place update compatibility | **PASS** |
| **Android Unit Tests** | Hermetic JUnit / Robolectric suites | 65 passed, 0 failed, 0 skipped | **PASS** |
| **Web Dashboard Tests** | Node test runner suite | 6 passed, 0 failed, 0 skipped | **PASS** |
| **Functions Tests** | FCM & Spark pairing suites | 14 passed, 0 failed, 0 skipped | **PASS** |
| **Web Dashboard Build** | Vite production bundle | Built cleanly in `dist/` | **PASS** |
| **Firebase Hosting** | Static dashboard hosting | Live at `https://track-99c90.web.app` | **PASS** |
| **Physical Testing** | Samsung Galaxy A07 5G + realme RMX3785 | In-place update to Build 7 verified; UI verified | **PASS** |
| **Audible Siren Test** | Physical sound check | Intentionally deferred per instruction | **NOT TESTED — DEFERRED** |
| **Public Store Signing Key** | Dedicated Play Store release keystore | Requires owner keystore provisioning | **BLOCKED (Internal Build 7 Active)** |

---

## 2. Features Implemented in v1.8.0 (Build 7)

### 2.1 Biometric Power Protection
- **Architecture:** `BiometricPowerProtectionManager.java` integrates with `SessionManager` and AndroidX `BiometricPrompt`.
- **Credential Fallback:** Strong biometric authentication with seamless fallback to secure device credentials (PIN/pattern/password).
- **Settings Toggle:** Dedicated `switch_power_protection` switch and real-time capability display (`tv_power_protection_status`) separated from Biometric App Shield in `activity_settings.xml`.
- **Impending Shutdown Telemetry:** `ShutdownReceiver.java` intercepts `Intent.ACTION_SHUTDOWN` and `android.intent.action.QUICKBOOT_POWEROFF` to record a high-priority `DEVICE_SHUTDOWN` security event with battery level and status before power is lost.
- **Platform Limitations Disclosed:** Clarified in UI and documentation that standard Android OS policy prevents 3rd-party non-root apps from universally intercepting hardware button power-off without Device Owner provisioning; telemetry is captured on a best-effort basis.

### 2.2 Geofencing Auto Out-of-Zone Protection & Re-entry Recovery
- **Architecture:** `GeofenceBroadcastReceiver.java` validates `GEOFENCE_EXIT` and `GEOFENCE_ENTER` transitions.
- **Exit Action:** Transitions device to `OUT_OF_ZONE`, sets `isLostMode = true`, `lostModeSource = "GEOFENCE"`, and logs an alert to the Security Center.
- **Enter Action:** Transitions device to `IN_ZONE`. Restores `isLostMode = false` strictly if Lost Mode was engaged by geofencing (`sessionManager.isGeofenceLostMode() == true`), preserving manually triggered Lost Mode intact.

### 2.3 Command Queue & Idempotency Hardening
- **Unique Request IDs:** Web dashboard `api.ts` attaches unique `requestId` (`req_<timestamp>_<random>`) to every dispatched command.
- **Atomic Transactions:** `CommandExecutionManager.java` claims commands via Firestore atomic transactions, moving from `QUEUED` to `EXECUTING` to prevent duplicate or conflicting execution.
- **Stuck Command Prevention:** Commands with mismatched targets are flagged `FAILED`, while expired commands (>15 min for lock/alarm, >30 min for locate) transition to `EXPIRED` within the transaction.

---

## 3. Physical Hardware Identification & Verification

### 3.1 Device 1: Controller / Owner
- **Manufacturer & Model:** Samsung Galaxy A07 5G (`SM-A076B`)
- **OS Version:** Android 16
- **ADB Identifier:** `R9ZL10F4FXV`
- **Application Version:** SecureTrack v1.8.0 (Build 7)
- **Status:** Installed in-place; MainActivity live in foreground (`mCurrentFocus=Window{899bf15 u0 com.example.securetrack/com.example.securetrack.MainActivity}`).

### 3.2 Device 2: Paired Secondary
- **Manufacturer & Model:** realme (`RMX3785`, product `RMX3785IN`, device `RE5C6CL1`)
- **OS Version:** Android 15
- **ADB Identifier:** `PZ7XZ5NVV4LN5XJV`
- **Application Version:** SecureTrack v1.8.0 (Build 7)
- **Status:** Installed in-place; pairing preserved; device document tracked in Firestore.
*(Note: Legacy Firestore document label from initial testing carries title "Vivo V2428"; per safety rules, no existing device documents or accounts were deleted).*

### 3.3 Audible Siren Status
- **Physical Siren Activation:** **NOT TESTED — DEFERRED** (postponed per explicit testing instructions to prevent noise disturbance).
- **Automated Siren Software Logic:** Verified in unit test `SecurityAndLocationUpgradeTest.testFiveSecondAlarmDurationConstraint` (Duration constraint strictly 5000 ms).

---

## 4. Test Execution Summaries

- **Android Unit Tests:**
  `.\gradlew.bat :app:testDebugUnitTest`
  `65 passed, 0 skipped, 0 failed` (Execution time ~16s).
- **Web Dashboard Tests:**
  `npm test`
  `6 passed, 0 failed, 0 skipped` (Execution time ~110ms).
- **Cloud Functions Tests:**
  `npm test`
  `14 passed, 0 failed, 0 skipped` (Execution time ~140ms).
- **Web Dashboard Production Build:**
  `npm run build`
  Vite production bundle compiled cleanly in `web-dashboard/dist/`.

---

## 5. Security & Signing Status

- **Installed Compatibility Key:** The Build 7 release APK is signed with the development/compatibility keystore (`SHA-256: 1659f5dcec61ab9ae2a6e0318341079a9099a9567282eed1011c3a0dc49f6843`), which guarantees seamless in-place updates on test handsets without wiping app data or user logins.
- **Production Store Distribution:** Marking the public release as **BLOCKED** for public Google Play distribution until the project owner configures a dedicated production release keystore via environment variables `SECURETRACK_KEYSTORE_PATH`, `SECURETRACK_KEYSTORE_PASSWORD`, `SECURETRACK_KEY_ALIAS`, and `SECURETRACK_KEY_PASSWORD`.
- **Contributor Attribution:** Verified contributor spelling strictly as `Deeksith H G` across `NOTICE` and project documentation.

---

## 6. Final Acceptance Dashboard

1. **Android tests:** PASS
2. **GitHub Actions:** PASS (Mockito dependency removed; hermetic JUnit suite)
3. **Web dashboard tests and build:** PASS
4. **Cloud Functions tests:** PASS
5. **Queue repair:** PASS (Unique client request IDs, atomic claim transactions, bounded expiry)
6. **Biometric Power Protection:** PASS (BiometricPrompt with device credential fallback, separate settings toggle, pre-shutdown telemetry checkpoint)
7. **Geofencing:** PASS (Automated out-of-zone Lost Mode, safe zone re-entry recovery without manual override)
8. **Samsung physical tests:** PASS (Samsung Galaxy A07 5G upgraded in-place to Build 7, MainActivity verified active)
9. **Realme physical tests:** PASS (Realme RMX3785 upgraded in-place to Build 7, certificate preserved)
10. **Offline Find status:** PASS (BLE encrypted beacons, Room local queuing, offline sync recovery)
11. **Firebase website deployment status:** PASS (Live at `https://track-99c90.web.app`)
12. **Signing-key status:** BLOCKED for public store distribution (internal testing Build 7 operational)
13. **Latest commit and branch:** `fix/securetrack-global-two-device-release`
14. **GitHub release and APK download status:** Release APK verified at `release/SecureTrack-final.apk` (SHA-256: `022D995D9F2609D1D43DD4E1DC5E7B9D6C80A765AC7834225ADA55149DD3783C`)
15. **Exact outstanding owner action, if any:** Provide dedicated production keystore credentials when publishing publicly to Google Play.
