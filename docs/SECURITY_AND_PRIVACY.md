# SecureTrack — Security Architecture & Privacy Policy

This document outlines the security controls, threat mitigations, cryptographic handshakes, anti-misuse safeguards, and privacy protections implemented across the SecureTrack ecosystem.

---

## 1. Threat Model & Mitigations

| Threat Vector | Potential Impact | Security Mitigation Implemented |
|---|---|---|
| **Covert Stalking / Covert Tracking** | Abuser silently installs app to track victim without consent | Mandatory explicit consent disclosure checkbox during enrolment; registered owner identity banner on Android dashboard; persistent foreground notification with direct "Stop Protection" affordance. |
| **Unauthorized Device Pairing** | Attacker links their phone to a victim's dashboard | Cryptographically secure random 256-bit invitation tokens; SHA-256 one-way hashing; 10-minute validity TTL; two-phase claim & human-owner approval handshake with BLE shared secret generation. |
| **Command Spam & Harassment** | Abuser rapidly triggers siren alarm or remote lock | Server-enforced Firestore transactions with rate limits: Siren alarm cooldown (1 per 2m; max 5.0s burst), Lock cooldown (1 per 1m), Location refresh cap (max 5 per 10m). |
| **Destructive Command Abuse** | Remote wipe permanently destroying user data | `WIPE_DEVICE` is permanently prohibited and blocked across web console, Cloud Functions, and Android execution engine. Supported commands strictly limited to `LOCATE_NOW`, `LOCK_DEVICE`, and `ALARM_ALERT`. |
| **Cross-Tenant Telemetry Snooping** | User A reads location or status of User B's devices | Firestore Security Rules strictly enforce `request.auth.uid == resource.data.ownerUid` on all reads, writes, and collection queries. |
| **Device Credential Hijacking / Revocation Abuse** | Stolen or rogue device continues to stream telemetry | Owner can revoke device access instantly from Web Dashboard. Revoked devices are denied Firestore telemetry writes via security rules (`resource.data.status != 'REVOKED'`). |
| **Account Quota Exhaustion** | Abuser pairs dozens of unauthorized devices | Enforced account device quota: maximum 5 active devices per registered owner account. |
| **Audit Log Tampering / Non-Repudiation** | Attacker deletes or modifies history of remote commands | Append-only audit logs under `/users/{uid}/audit_logs/{logId}`; Firestore security rules allow read and create for owner, but strictly deny update and delete (`allow update, delete: if false`). |

---

## 2. Consent & Tracking Transparency (Section B)

SecureTrack is designed strictly as an anti-theft asset tracking and device recovery platform. It enforces full transparency to prevent covert surveillance or stalkerware deployment:

1. **Mandatory Informed Consent**:
   - During primary registration and secondary pairing enrolment on Android, the user is presented with a clear disclosure explaining:
     - The exact Google/Firebase account that will receive device telemetry.
     - The collection of precise GPS coordinates, network status, battery level, and hardware identifiers.
     - The ability of the owner to issue remote lock and siren commands.
   - The user must explicitly check the confirmation checkbox (`enrolment_consent_checkbox`) before the registration or pairing submission button is enabled.
2. **Transparent Identity Banner**:
   - The Android application home screen (`fragment_home.xml`) features an active **TRANSPARENT DEFENSE ACTIVE** banner.
   - It prominently displays the enrolled owner's email address or account UID, ensuring anyone holding the handset is informed of the managing account.
3. **Persistent Foreground Notification & Stop Protection**:
   - Location monitoring is handled via `LocationTrackingService` running as an official Android Foreground Service.
   - It displays a non-dismissible notification indicating that tracking is active.
   - It provides an immediate **"Stop Protection"** action button that halts GPS updates, unregisters network listeners, closes Firestore real-time snapshot listeners, and updates cloud status to `STOPPED`.

---

## 3. Pairing Handshake Security

Device enrollment follows a zero-trust, two-phase approval protocol:
1. **Web Dashboard Initiation**: Authenticated owner requests a pairing invitation.
2. **Cryptographic Token Generation**: Web client generates a 256-bit cryptographically secure random token (`crypto.getRandomValues`) and stores its SHA-256 hash in `/pairing_invitations/{tokenHash}` with `ownerUid`, 10-minute TTL, and `status: 'PENDING_CLAIM'`.
3. **QR Out-of-Band Delivery**: Plaintext token is encoded into a QR code payload.
4. **Secondary Device Claim**: Android device scans the QR code, authenticates anonymously via Firebase Auth (`deviceAuthUid`), and submits its device hardware info, transitioning invitation status to `PENDING_APPROVAL`.
5. **Human-in-the-Loop Owner Approval**: The owner reviews the device model and name in the Web Console and explicitly clicks **Approve**. A 256-bit BLE shared secret is generated, the device is registered under `/users/{ownerUid}/devices/{deviceId}`, and the invitation status is marked `APPROVED`.

---

## 4. Anti-Misuse & Rate Limiting Controls

To eliminate harassment and resource abuse, remote command dispatching in the Web Dashboard (`api.ts`) is executed within an atomic Firestore transaction (`runTransaction`) verifying rate limits before creating command documents:

| Control | Limit / Cooldown | Enforcement Mechanism |
|---|---|---|
| **Siren Alarm (`ALARM_ALERT`)** | 1 alert per 2 minutes (120s cooldown) | Verified in atomic transaction; siren hardware playback capped at 5.0 seconds. |
| **Device Lock (`LOCK_DEVICE`)** | 1 lock per 1 minute (60s cooldown) | Verified in atomic transaction; sets device into secured mode. |
| **Locate Request (`LOCATE_NOW`)** | Maximum 5 requests per 10-minute window | Rolling 10-minute sliding counter stored in `command_rate_limits/{deviceId}`. |
| **Device Quota** | Maximum 5 active devices per account | Checked in `createPairingInvitation`; prevents account resource spam. |
| **Factory Wipe (`WIPE_DEVICE`)** | Permanently Disabled | Denied in web UI, Firestore rules, and Android `CommandExecutionManager`. |
| **Command Validity Window** | 15 minutes TTL | Commands older than 15 minutes transition to `EXPIRED` and are rejected by Android execution engine. |
| **Target Device Binding** | `targetDeviceId == deviceId` | Firestore rules reject commands where target ID does not match the subcollection path. |

---

## 5. Append-Only Audit Trail (Non-Repudiation)

All critical remote operations are recorded into an append-only audit trail at `/users/{uid}/audit_logs/{logId}`:
- **Logged Actions**: `COMMAND_LOCATE_NOW`, `COMMAND_LOCK_DEVICE`, `COMMAND_ALARM_ALERT`, `DEVICE_APPROVED`, `DEVICE_REVOKED`.
- **Payload Attributes**: `ownerUid`, `action`, `targetDeviceId`, `commandId`, `requestId`, `initiatedFrom: 'WEB_CONSOLE'`, and server-assigned timestamp.
- **Security Rules**:
  ```javascript
  match /audit_logs/{logId} {
    allow read, create: if request.auth != null && request.auth.uid == userId;
    allow update, delete: if false; // Append-only; updates and deletions strictly prohibited
  }
  ```

---

## 6. Data Minimization & Retention Policy

- **Location Telemetry**: Only essential coordinates (`latitude`, `longitude`, `accuracy`, `speed`, `altitude`, `timestamp`, `source`) are collected. Location history is bounded to a rolling window of the 100 most recent records per device to prevent unbounded location profiling.
- **Private Recovery Credentials**: Device IMEI and recovery metadata are stored in a segregated subcollection (`/users/{uid}/private_recovery/{deviceId}`) isolated from regular telemetry listeners and protected by strict owner authorization.
- **Device Revocation**: When an owner revokes a device, its `status` is set to `REVOKED` and `protectionEnabled` is set to `false`. Security rules immediately block subsequent telemetry writes from that device's auth credentials.

---

## 7. Client & Backend Integrity (Firebase App Check)

For production deployment, Firebase App Check provides backend defense against scraped requests and unauthorized clients:
- **Android Client**: Attestation provider: **Play Integrity API**. Verifies that requests originate from the authentic, unmodified SecureTrack APK signed with the official release key and running on a genuine certified Android device.
- **Web Dashboard**: Attestation provider: **reCAPTCHA Enterprise**. Protects Firebase endpoints against automated bot scripts.

---

## 8. Honest Operational Boundaries & Limitations

In compliance with rigorous engineering and academic standards, SecureTrack acknowledges realistic physical and operating system boundaries:
1. **Operating System Authority**: SecureTrack operates within Android userland. If a thief performs a hardware factory reset via fastboot/bootloader or flashes custom firmware, the software agent cannot persist without system-level vendor ROM integration.
2. **Network & RF Physicality**: Remote locating and command dispatch require cellular or Wi-Fi data connectivity. If a device is placed in a radio-frequency Faraday bag or subterranean vault without network reception, commands remain queued until network connectivity resumes (or until the 15-minute validity window expires).
3. **Power Dependency**: An unpowered device with a depleted or physically removed battery cannot execute commands or transmit GPS coordinates until power is restored.
4. **Consent-Driven Model**: SecureTrack is not a rootkit or hidden spyware. It purposefully makes its presence known via persistent notifications, home screen banners, and mandatory consent prompts to protect user civil liberties and adhere to privacy laws.
