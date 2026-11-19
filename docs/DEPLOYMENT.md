# SecureTrack — Deployment & Release Guide

This guide details the procedures for deploying the SecureTrack Web Dashboard to Firebase Hosting, deploying Cloud Firestore Security Rules, and producing release builds for Android.

---

## 1. Production Hosting & Cloud Deployment

SecureTrack utilizes Firebase Spark plan compatible services for production deployment.

### 1.1 Prerequisites
1. Install Firebase CLI: `npm install -g firebase-tools`
2. Authenticate: `firebase login`
3. Verify active project: `firebase use track-99c90`

### 1.2 Deploying Web Dashboard
```bash
# 1. Build the production React bundle
cd web-dashboard
npm run build
cd ..

# 2. Deploy to Firebase Hosting
firebase deploy --only hosting
```
Live URL: **`https://track-99c90.web.app`**

### 1.3 Deploying Firestore Security Rules
```bash
firebase deploy --only firestore:rules
```

---

## 2. Android APK Release Build

### 2.1 Debug Assembly (Testing & Demos)
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### 2.2 Production Release Assembly
1. Generate or configure a release keystore in `app/keystore.jks`.
2. Configure signing properties in `gradle.properties` or environment variables:
   ```properties
   RELEASE_STORE_FILE=keystore.jks
   RELEASE_STORE_PASSWORD=***
   RELEASE_KEY_ALIAS=securetrack
   RELEASE_KEY_PASSWORD=***
   ```
3. Run:
   ```bash
   ./gradlew assembleRelease
   ```
Output: `app/build/outputs/apk/release/app-release.apk`

---

## 3. Pre-Release Verification Checklist

- [ ] All unit test suites pass (`./gradlew testDebugUnitTest`, `node --test web-dashboard/test/auth.test.js`, `node --test functions/test/pairing.test.js`).
- [ ] No hardcoded passwords, tokens, or private keys in Git.
- [ ] `firestore.rules` deployed and verified with least-privilege scoping.
- [ ] Web dashboard builds cleanly without TypeScript or lint warnings.
- [ ] Target Android device connects and successfully transmits location telemetry.
