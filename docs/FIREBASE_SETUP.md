# SecureTrack — Firebase Configuration & Setup Guide

This guide describes the Firebase cloud architecture supporting SecureTrack, including Authentication, Cloud Firestore data store, Security Rules, and Firebase Hosting.

---

## 1. Project Overview

- **Project ID**: `track-99c90`
- **Hosting URL**: `https://track-99c90.web.app`
- **Pricing Tier**: Spark (Free Tier Compatible — no external cloud billing required)

---

## 2. Enabled Services

| Service | Purpose | Configuration |
|---|---|---|
| **Firebase Authentication** | User authentication for the Web Dashboard and Android device owners | Email/Password provider enabled |
| **Cloud Firestore** | Real-time NoSQL document store for devices, telemetry, commands, and pairing | Production mode with granular rules |
| **Firebase Hosting** | Single-Page Application (SPA) hosting for the React/Vite dashboard | Rewrites configured to `/index.html` |

---

## 3. Cloud Firestore Security Rules

The active security rules are defined in `firestore.rules` at the root of the repository.

### Key Security Guarantees:
1. **User Scoping**: Users can only read and write data in collections where `resource.data.ownerUid == request.auth.uid` or where the parent document belongs to their authenticated UID.
2. **Device Telemetry Writes**: Enrolled devices authenticate using their registered device identity or owner UID, allowing real-time battery, network, and GPS coordinates ingestion without exposing cross-tenant device telemetry.
3. **Idempotent Revocation**: Device owners can safely delete or revoke device documents, preventing unauthorized cross-user modifications. *(Note: Cloud Firestore native document deletion deletes the parent document; nested subcollections like `/devices/{id}/locations` remain accessible only by direct ID path unless cleaned up via batch deletion).*
4. **Pairing Code Verification**: Unpaired invitations can only be claimed once with valid matching tokens.

### Deploying Firestore Rules:
```bash
firebase deploy --only firestore:rules
```

---

## 4. Web Dashboard Configuration

The web client (`web-dashboard/`) connects to Firebase using configuration variables provided via `.env` or build configuration:

```typescript
// web-dashboard/src/services/firebase.ts
import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getFirestore } from "firebase/firestore";

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: "track-99c90.firebaseapp.com",
  projectId: "track-99c90",
  storageBucket: "track-99c90.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID
};

export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);
```

---

## 5. Android Client Integration

The Android client integrates via `google-services.json` placed in the `app/` directory:
- Applies the Google Services Gradle plugin (`com.google.gms.google-services`).
- Uses Firebase BoM for dependency resolution:
  ```kotlin
  implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
  implementation("com.google.firebase:firebase-auth")
  implementation("com.google.firebase:firebase-firestore")
  ```
