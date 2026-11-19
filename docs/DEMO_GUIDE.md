# SecureTrack — College Presentation & Live Demo Guide

This script provides a structured 5-to-7 minute demonstration walkthrough designed for evaluators, professors, technical reviewers, and portfolio showcases.

---

## 1. Presentation Outline & Timing

| Segment | Duration | Focus Area |
|---|---|---|
| **1. Introduction & Problem** | 1 min | Mobile device theft statistics, recovery challenges, and SecureTrack's mission. |
| **2. Architecture Overview** | 1.5 min | Real-time Firebase cloud layer, Android foreground service, zero-trust pairing handshake. |
| **3. Live Pairing Demonstration** | 1.5 min | Generate pairing QR on web -> Scan with mobile device -> Instant status transition to ONLINE. |
| **4. Live Telemetry & Map** | 1 min | Real-time GPS coordinate updates, battery percentage, charging state telemetry. |
| **5. Remote Anti-Theft Commands** | 1 min | Triggering Ring Alarm and Screen Lock via DevicePolicyManager. |
| **6. Security & Q&A Wrap-Up** | 1 min | Cloud Firestore security rules, revocation idempotency, and defense-in-depth design. |

---

## 2. Step-by-Step Demonstration Script

### Step 1: Web Dashboard Login
1. Open Chrome/Edge and navigate to `https://track-99c90.web.app`.
2. Log into the test account.
3. *Key talking point*: "The dashboard is a responsive React 18 Single-Page Application with TypeScript and Tailwind CSS, deployed on Firebase Hosting."

### Step 2: Device Pairing Handshake
1. Click **Devices** -> **Add New Device**.
2. Show the generated pairing QR code.
3. *Key talking point*: "Instead of transmitting user credentials, we generate a single-use cryptographically random token in the browser with a 10-minute TTL to bind the device securely."
4. On the physical Android phone, open SecureTrack and tap **Scan QR Code to Pair**.
5. Scan the computer screen. Within 2 seconds, the phone vibrates and confirms enrollment.
6. The web dashboard updates reactively to show the device as **ONLINE** without refreshing the page.

### Step 3: Real-Time Telemetry & Tracking
1. Navigate to **Map View**.
2. Point out the live location marker, accuracy radius, and battery percentage matching the physical phone.
3. *Key talking point*: "The Android client runs a fused location provider inside a persistent Foreground Service, ensuring continuous telemetry even when background battery optimizations are active."

### Step 4: Remote Commands Execution (Illustrative Demonstration)
1. Click **Ring Alarm**.
2. Show the command document dispatched to Cloud Firestore (note: hardware execution depends on active foreground listener and system admin policy).
3. Click **Lock Device**.
4. Show the command dispatch to invoke `DevicePolicyManager`.

### Step 5: Safe Revocation
1. Back on the Devices screen, click **Revoke** on a test device.
2. Observe that the device document is cleanly removed without error alerts, demonstrating owner-authorized direct Firestore revocation.

---

## 3. Frequently Asked Evaluator Questions & Answers

**Q: How does the app prevent the thief from simply closing it?**  
*A: SecureTrack runs an Android Foreground Service with an ongoing system notification. Android's OS treats foreground services with high priority, preventing low-memory kills. Additionally, a `BootReceiver` restarts tracking automatically if the device is rebooted.*

**Q: Does tracking work if the phone loses internet connectivity?**  
*A: When internet is lost, the device buffers telemetry locally. Once cellular or Wi-Fi connectivity is restored, cached location points are flushed to Firestore.*

**Q: What prevents another user from viewing my location?**  
*A: All Firestore document paths are protected by strict Security Rules that enforce `request.auth.uid == resource.data.ownerUid`. Cross-tenant queries are blocked at the database engine level.*
