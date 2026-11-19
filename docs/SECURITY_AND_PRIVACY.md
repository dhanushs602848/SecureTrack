# SecureTrack — Security Architecture & Privacy Policy

This document outlines the security controls, threat mitigations, cryptographic handshakes, and privacy protections implemented across SecureTrack.

---

## 1. Threat Model & Mitigations

| Threat Vector | Potential Impact | Security Mitigation Implemented |
|---|---|---|
| **Unauthorized Device Pairing** | Attacker links their phone to a victim's dashboard | Cryptographically secure random invitation tokens generated on web client; single-use validation; strictly enforced 10-minute expiration; owner UID validation in Firestore rules. |
| **Cross-Tenant Telemetry Snooping** | User A reads location or status of User B's devices | Firestore Security Rules enforce `request.auth.uid == resource.data.ownerUid` on all reads and queries. |
| **Device Tampering / App Termination** | Thief kills tracking app or reboots device | `LocationTrackingService` runs as an explicit Android Foreground Service with a sticky notification; `BootReceiver` restarts tracking on device boot. |
| **Unauthorized Remote Wipe / Lock** | Attacker issues unauthorized device administration commands | Commands collection enforces write restrictions to the verified device owner; Android client validates command schema before invoking `DevicePolicyManager`. |
| **Credential & API Key Exposure** | Leakage of sensitive secrets in client repositories | Firebase client configuration relies on restricted Firestore rules rather than hidden client secrets; no privileged service account keys are bundled in client binaries. |

---

## 2. Pairing Handshake Security

The device enrollment workflow operates over a structured security model:
1. **Web Dashboard Initiation**: Authenticated owner requests a pairing token in the web interface.
2. **Cryptographic Token Generation**: The web client generates a cryptographically random code (using browser `crypto.getRandomValues`) and stores it in Firestore under `/pairing_invitations/{invitationId}` with `ownerUid`, `createdAt`, `expiresAt` (10-minute TTL), and `used: false`.
3. **Out-of-Band Delivery**: Displayed as a QR code on the authenticated web dashboard.
4. **Android Client Verification**: The device scans the QR code, calls Firestore to claim the invitation atomically, marks `used: true`, and associates the device document with `ownerUid`.
5. **Enforcement**: Rules and client logic reject expired, already used, or mismatched UID claims.

---

## 3. Privacy Principles

- **Data Minimization**: SecureTrack only collects telemetry necessary for tracking and security: geographic coordinates, battery percentage, charging state, network connectivity, and device model.
- **Data Ownership**: All location records are owned strictly by the registered user account.
- **Right to Erasure / Revocation**: The device owner can revoke or delete device documents directly from the Web Dashboard at any time. Revocation immediately ceases device telemetry ingestion. *(Note: Deleting a parent document in Cloud Firestore does not automatically delete nested subcollections like `/devices/{id}/locations`; orphaned subcollections should be purged via batch scripts if complete erasure is required).*
