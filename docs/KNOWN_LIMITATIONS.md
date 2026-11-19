# SecureTrack — Known Limitations & System Boundaries

In accordance with strict technical integrity and engineering standards, this document lists known limitations and architectural boundaries of SecureTrack.

---

## 1. Hardware & OS-Level Boundaries

1. **Powered-Off Tracking**:
   - Like standard commercial Android tracking applications, SecureTrack requires the device to be powered on to execute location updates and transmit network packets. Tracking a device whose battery has fully drained or that has been turned off requires OEM-level firmware integration (such as Google's Find My Device powered-off finding network on select hardware), which is outside the scope of user-space Android applications.

2. **Network Connectivity Requirements**:
   - Remote anti-theft commands (`RING`, `LOCK`, `LOCATE`) require an active network connection (Cellular data or Wi-Fi) to receive Firestore command events. If the device is in Airplane Mode, incoming commands are queued until connectivity resumes.

3. **Android Device Admin Revocation**:
   - On unmanaged consumer Android devices, an owner or knowledgeable thief can navigate to Android **Settings > Security > Device Admin Apps** and manually deactivate admin rights. While deactivation triggers `onDisableRequested()`, consumer Android cannot completely prevent an administrator from being removed without Android Enterprise MDM / Samsung Knox enrollment.

---

## 2. Platform & Quota Boundaries

1. **Firebase Spark Plan**:
   - SecureTrack is intentionally engineered to operate within Firebase's zero-cost **Spark Plan**. Consequently, background automated cleanup tasks rely on client-side / rule-based validation rather than continuous hourly Google Cloud Scheduler jobs.

2. **Background Location Frequency**:
   - Android 10+ restricts background location polling frequency when apps are not foregrounded. SecureTrack maintains real-time tracking by operating an explicit **Foreground Service** with an ongoing notification. If the user revokes the foreground notification or forces battery optimization to "Restricted", the OS may throttle location updates to interval batches.
