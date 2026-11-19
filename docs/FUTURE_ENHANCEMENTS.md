# SecureTrack — Future Enhancements Roadmap

This document outlines realistic, architecturally sound future enhancements planned for the SecureTrack platform.

---

## 1. Planned Technical Enhancements

### 1.1 Bluetooth Low Energy (BLE) Crowd-Sourced Finding
- **Concept**: Implement background BLE advertising using rotating ephemeral cryptographic keys (similar to open tracker protocols).
- **Benefit**: Allows nearby SecureTrack devices to securely detect and relay location pings for an offline lost device without exposing user identity.

### 1.2 Geofencing & Boundary Alerts
- **Concept**: Integrate Android's Geofencing API and Turf.js on the dashboard to allow owners to define "Safe Zones" (e.g., home, office, college campus).
- **Benefit**: Automatically notifies the user if their device exits designated geographic coordinates during specific hours.

### 1.3 Failed Unlock Photo Capture (Intruder Alert)
- **Concept**: Utilize CameraX and `DeviceAdminReceiver.onPasswordFailed()` to capture a silent photo from the front camera after consecutive incorrect PIN attempts.
- **Benefit**: Uploads an encrypted image to Firebase Storage and alerts the dashboard to identify unauthorized handlers.

### 1.4 SIM Card Swap Detection & SMS Fallback
- **Concept**: Monitor telephony state changes using Android's `TelephonyManager`. If an unauthorized SIM card is inserted, the app triggers an SMS containing GPS coordinates to a designated emergency phone number even if mobile data is toggled off.

### 1.5 Android Enterprise / MDM Profile Integration
- **Concept**: Provide Android Enterprise Device Owner provisioning (via QR code during initial device factory setup).
- **Benefit**: Permanently prevents factory reset and uninstallation of the security application by unauthorized parties.
