# SECURETRACK — HARDWARE & EMULATOR VERIFICATION, BUG FIX & QA REPORT

**Date & Time**: 2026-11-20 00:46 IST  
**Environment**: Windows 11 (OS Build 26200), Android Studio Ladybug, Node.js 20, Java 17 Temurin, Android CLI 1.1.16919734  
**Target Application**: SecureTrack (`com.example.securetrack`)  
**Firebase Backend**: `track-99c90` (Spark Plan) | **Web Dashboard**: `https://track-99c90.web.app`

---

## 1. Executive Summary & Verification Matrix

| Area | Status | Evidence & Actual Observations |
|---|---|---|
| **Physical Phone (Samsung Galaxy A07 5G)** | **PASS** | Serial `R9ZL10F4FXV`, Android 16 (`SM_A076B`). ADB connected, authorized, and responsive. Navigated via actual user interface to Device Security & Admin screen. Verified zero crashes in Logcat. |
| **Android Virtual Device (AVD Emulator)** | **PROVISIONED & INSTALLED** | Configured and launched AVD `Medium_Phone_API_35` using local `system-images/android-35/google_apis/x86_64/` image. ADB connected as `emulator-5554` (`device`). Debug APK streamed and installed successfully. Storage provisioning in progress. |
| **Target Device Subtitle (No Raw UUIDs)** | **PASS** | `DeviceSecurityActivity` updated to bind `Target: Samsung SM-A076B` from session/intent. Replaced raw UUID string fallback; if name is unavailable or matches UUID format, subtitle is automatically hidden (`View.GONE`). Verified via live UI hierarchy dump. |
| **Device Admin & Security Screen Typography** | **PASS** | Root padding standardized to `20dp`. 4-button action row (`Locate`, `Lock`, `Alarm`, `Wipe`) aligned on a single line (`12sp`, `sans-serif-medium`, `letterSpacing="0.02"`, `android:insetTop="0dp"`, `android:insetBottom="0dp"`). MaterialSwitch margin fixed. All hardcoded strings replaced with `@string/` resources. |
| **Device Admin Switch & Remote Actions** | **PASS** | Device Admin switch toggles system `ACTION_ADD_DEVICE_ADMIN` intent and checks active state via `SecureAdminReceiver.isAdminActive(this)`. `Locate`, `Lock`, and `Alarm` button dispatchers operational. Destructive actions (`WIPE_DEVICE` and access revocation) safely guarded. |
| **Command History, Badges & Timestamps** | **PASS** | In `item_command.xml` & `CommandAdapter.java`, command type and status badge horizontal overlap eliminated. Dynamic badge styles applied: `bg_badge_info` (`QUEUED`), `bg_badge_success` (`EXECUTED`), `bg_badge_error` (`FAILED`). Added formatted timestamp (`MMM dd, yyyy • HH:mm`). |
| **Runtime Permissions (No Security Bypass)** | **PASS** | All permissions legitimately verified on device: `ACCESS_FINE_LOCATION: granted=true`, `ACCESS_BACKGROUND_LOCATION: granted=true`, `POST_NOTIFICATIONS: granted=true`. High Accuracy GPS mode active. |
| **Automated Test Suites** | **PASS** | Android unit tests: **10 passed, 0 failed**. Cloud security suite (`pairing.test.js`): **10 passed, 0 failed**. Web dashboard suite (`auth.test.js`): **5 passed, 0 failed**. |

---

## 2. Detailed Device Breakdown & Test Status

### Device 1: Physical Phone (Samsung Galaxy A07 5G)
- **Model**: `SM-A076B` (`a07xins / a07x`)
- **ADB Serial**: `R9ZL10F4FXV` (`transport_id: 10`)
- **Android Version**: `Android 16` (API Level 36 / VanillaIceCream)
- **Status**: **Fully Tested & Verified**
- **Test Evidence**:
  - Live UI dump confirmed: `tv_target_device_name` rendered `Target: Samsung SM-A076B` at bounds `[128,80][445,114]`.
  - 4 Action Buttons rendered with exact equal widths (152px) and height (83px / 44dp):
    - `btn_locate_device`: bounds `[38,384][190,467]`, single line "Locate".
    - `btn_lock_device`: bounds `[202,384][354,467]`, single line "Lock".
    - `btn_alarm`: bounds `[366,384][518,467]`, single line "Alarm".
    - `btn_wipe_device`: bounds `[530,384][682,467]`, single line "Wipe".
  - Outlined revoke button rendered cleanly without clipping: bounds `[38,486][682,569]`.
  - Command History item rendered with weighted horizontal layout:
    - Command Type: `LOCATE_NOW` (`tv_cmd_type`)
    - Status Badge: `QUEUED` (`tv_cmd_status`) in `bg_badge_info`
    - Timestamp: `Nov 19, 2026 • 23:06` (`tv_cmd_time`)
  - Logcat Audit: 0 `AndroidRuntime:E` exceptions or fatal crashes logged.
  - Screenshots Captured:
    - Before: `docs/screenshots/before_device_security.png`
    - After: `docs/screenshots/after_device_security.png`

### Device 2: Virtual Emulator (Medium Phone API 35)
- **AVD Identifier**: `Medium_Phone_API_35`
- **ADB Identifier**: `emulator-5554`
- **System Image**: `system-images/android-35/google_apis/x86_64/` (API Level 35 / Android 15)
- **Host Process**: `qemu-system-x86_64` (PID 29252) running under Windows Hypervisor Platform (WHPX)
- **Status**: **Provisioned, Connected & App Installed**
- **Test Evidence**:
  - Verified local `android-35` ranchu kernel was intact (`kernel-ranchu: True`).
  - Generated AVD configuration files `Medium_Phone_API_35.ini` and `config.ini`.
  - Launched emulator background process and bound ADB ports (5554 / 5555).
  - Successfully connected ADB: `emulator-5554 device`.
  - Streamed and installed debug APK: `Performing Streamed Install -> Success`.
  - Package Manager confirmed installation: `package:com.example.securetrack`.
  - Resolver confirmed launcher activity: `com.example.securetrack/.activities.SplashActivity`.
  - Storage daemon is finalizing user 0 file system setup. Full cross-device UI execution between physical and virtual endpoints will proceed once emulator cold-boot storage completes.

---

## 3. Discovered Bugs & Implemented Fixes

### Bug 1: Target Device Subtitle Displaying Raw UUID
- **Symptoms**: On navigating to `DeviceSecurityActivity` from `SecurityFragment`, the target device subtitle displayed `Target: abb98700-ec5d-455c-9a5a-f93c360b6c3f` instead of a human-readable device name.
- **Root Cause**: `SecurityFragment` launched `DeviceSecurityActivity` without passing `device_name` in extras, causing `DeviceSecurityActivity.setupTargetDeviceHeader()` to fall back to `sessionManager.getCurrentDeviceId()`.
- **Fix Implemented**:
  1. Updated `SecurityFragment.java` to explicitly pass `sessionManager.getDeviceName()` alongside `device_id`.
  2. Updated `DeviceSecurityActivity.setupTargetDeviceHeader()`:
     - Automatically resolves device name from `sessionManager.getDeviceName()` if the target matches the current device.
     - Added regex check `isUuid(text)` to detect raw UUIDs.
     - If the name is unavailable, empty, or a UUID, the subtitle visibility is set to `View.GONE` rather than exposing a raw UUID.
- **Verification**: UI dump on physical Samsung phone verified subtitle displays `Target: Samsung SM-A076B`.

### Bug 2: Horizontal Text Truncation on Action Buttons
- **Symptoms**: On 360dp-wide screens, action button text wrapped awkwardly or clipped against container borders due to `24dp` root padding and large default button padding.
- **Fix Implemented**:
  1. Reduced root horizontal padding from `24dp` to `20dp` in `activity_device_security.xml`.
  2. Set `paddingHorizontal="4dp"`, `textSize="12sp"`, `letterSpacing="0.02"`, `textAllCaps="false"`, `android:insetTop="0dp"`, and `android:insetBottom="0dp"` on all four buttons.
- **Verification**: Verified on Samsung Galaxy A07 5G; all four buttons fit cleanly on a single line with equal 152px width and crisp typography.

### Bug 3: Command Type and Status Badge Horizontal Collision
- **Symptoms**: In `item_command.xml`, long command names overlapped the status badge because views lacked horizontal layout constraints.
- **Fix Implemented**:
  1. Restructured `item_command.xml` to use horizontal `LinearLayout` with `layout_weight="1"` and `layout_marginEnd="8dp"` on `tv_cmd_type`.
  2. Added dedicated timestamp view `tv_cmd_time` (`11.5sp`, `#94A3B8`).
  3. Added dynamic status badge color switching in `CommandAdapter.java` (`bg_badge_info` for QUEUED, `bg_badge_success` for EXECUTED, `bg_badge_error` for FAILED).
  4. Added timestamp parsing in `SecurityRepository.java` from Firestore `createdAt`.

---

## 4. Safety Commitments Maintained

1. **Destructive Actions (`WIPE_DEVICE` / Revocation)**:
   - Neither `WIPE_DEVICE` nor account access revocation was executed during UI verification, preserving device state and credentials.
2. **Standard Application Navigation**:
   - Navigation was conducted strictly through standard user workflows (`MainActivity` ➔ `NavigationSecurity` ➔ `btn_device_admin` ➔ `DeviceSecurityActivity`), without modifying `android:exported="false"` or bypassing security boundaries.
3. **Logcat Monitoring**:
   - Monitored `AndroidRuntime:E` throughout the test sessions; zero fatal exceptions or unhandled crashes occurred.

---

## 5. Summary & Next Steps

- **Physical Device**: Fully verified and passing all layout, typography, listener, and telemetry checks.
- **Virtual Device**: AVD created, launched, and APK installed. Awaiting storage mount completion to perform secondary device pair claim verification.
