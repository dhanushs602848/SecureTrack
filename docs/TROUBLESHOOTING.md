# SecureTrack — Troubleshooting & FAQ Guide

This guide helps resolve common development, deployment, and operational issues encountered with SecureTrack.

---

## 1. Android Application Issues

### Issue 1: App stops sending location when screen is locked
- **Cause**: Android OS battery optimization has restricted the application or location permission was set to "While using the app" instead of "Allow all the time".
- **Solution**:
  1. Open Android **Settings > Apps > SecureTrack > Permissions > Location** and select **Allow all the time**.
  2. Open Android **Settings > Apps > SecureTrack > Battery** and select **Unrestricted**.

### Issue 2: QR Scanner shows black screen or fails to open
- **Cause**: Camera permission has been denied.
- **Solution**: Open **Settings > Apps > SecureTrack > Permissions > Camera** and grant access.

### Issue 3: Remote Lock or Wipe does not execute
- **Cause**: Device Admin privileges were not activated or were disabled in system settings.
- **Solution**: Open SecureTrack, navigate to Security settings, and tap **Activate Device Administrator**. Confirm with the Android system prompt.

---

## 2. Web Dashboard Issues

### Issue 1: "internal [0]" error when revoking a device
- **Cause**: Legacy Cloud Function URL mismatch or missing direct Firestore delete rule.
- **Solution**: Ensure commit `c0d8fb4` or later is deployed. SecureTrack uses direct, owner-authorized Firestore document deletion with rule enforcement (`request.auth.uid == resource.data.ownerUid`).

### Issue 2: Map does not display or tiles fail to load
- **Cause**: Offline network connectivity or Content Security Policy blocking OpenStreetMap tile servers (`tile.openstreetmap.org`).
- **Solution**: Verify active internet connection and check browser console network inspector.

### Issue 3: Device shows OFFLINE even when phone is powered on
- **Cause**: Phone has no cellular or Wi-Fi data connection, or the background tracking service was killed by aggressive third-party battery cleaners.
- **Solution**: Verify internet on the phone, restart the SecureTrack app, and confirm that the ongoing notification "SecureTrack Protection Active" appears in the status bar.

---

## 3. Build & Environment Issues

### Issue: `./gradlew assembleDebug` fails with Java version error
- **Cause**: Gradle 8.x requires JDK 17.
- **Solution**: Ensure your `JAVA_HOME` environment variable points to a JDK 17 installation (e.g., Eclipse Temurin 17 or Android Studio bundled JDK).
