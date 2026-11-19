# SecureTrack — User & Operation Guide

This guide provides end-to-end instructions for end users and device owners operating SecureTrack.

---

## 1. Getting Started

SecureTrack consists of two interfaces:
1. **The Web Dashboard**: Accessed via any modern web browser at `https://track-99c90.web.app`.
2. **The Android Mobile App**: Installed on your protected mobile handset.

---

## 2. Setting Up an Account

1. Open `https://track-99c90.web.app` in your browser.
2. If you don't have an account, click **Sign Up** and enter your email address and a secure password.
3. Sign in to reach the main **Dashboard** overview.

---

## 3. Installing & Authorizing the Android App

1. Transfer and install `app-debug.apk` (or downloaded APK) onto your Android device.
2. Open **SecureTrack** on your phone.
3. Grant the required permissions when prompted:
   - **Location Permission**: Choose **Allow all the time** so location can be tracked even when your screen is locked.
   - **Camera Permission**: Needed to scan the pairing QR code.
   - **Device Admin Permission**: Tap **Activate this device admin app** to enable remote lock capabilities.
   - **Notifications**: Allow notifications so tracking status is visible.

---

## 4. Pairing Your Device

1. On the Web Dashboard, navigate to the **Devices** tab and click **Add New Device**.
2. A single-use QR pairing code will appear on screen.
3. On your Android phone, tap **Scan QR Code to Pair**.
4. Point your camera at the QR code on your computer screen.
5. Within 1-2 seconds, the phone will confirm **Pairing Successful!** and start the background tracking service.
6. The phone will immediately appear in your Web Dashboard device list with status **ONLINE**.

---

## 5. Tracking & Anti-Theft Actions

### 5.1 Real-Time Location Map
- Navigate to the **Map** tab on the Web Dashboard.
- Click on your device in the list to center the map on its current coordinates.
- Battery level, network state, and last reported timestamp are updated automatically.

### 5.2 Ring Alarm
- If you misplace your device nearby, click **Ring Alarm**.
- The phone will play a high-volume alarm sound even if set to silent or vibrate mode.

### 5.3 Lock Screen
- If your device is lost or stolen, click **Lock Device**.
- The device screen will immediately lock, requiring the device PIN/password to regain access.

### 5.4 Revoke Device
- If you replace or decommission a device, click **Revoke** on the Devices tab.
- This disconnects the device from your account and prevents further telemetry uploads.
