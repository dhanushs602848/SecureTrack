# SecureTrack — Web Dashboard Setup & Development Guide

This document describes how to set up, build, test, and deploy the SecureTrack Web Dashboard application.

---

## 1. Technology Stack

- **Framework**: React 18
- **Language**: TypeScript 5
- **Build Tool / Bundler**: Vite 5
- **Styling**: Tailwind CSS 3
- **Icons**: Lucide React
- **Cloud Integration**: Firebase JS SDK (`firebase/app`, `firebase/auth`, `firebase/firestore`)
- **Mapping**: Leaflet / React-Leaflet & OpenStreetMap tiles

---

## 2. Prerequisites

- **Node.js**: v18.0.0 or v20.x LTS
- **Package Manager**: npm (v9 or v10)
- **Firebase CLI**: `npm install -g firebase-tools` (or run via `npx firebase-tools`)

---

## 3. Local Installation & Development

Navigate to the dashboard directory:

```bash
cd web-dashboard

# Install dependencies
npm install

# Start local development server
npm run dev
```

The application will be served locally at `http://localhost:5173`.

---

## 4. Running Web Unit Tests

The test suite validates authentication state transitions, form validation, and session boundaries:

```bash
# From repository root or web-dashboard/
node --test web-dashboard/test/auth.test.js
```

---

## 5. Building for Production

Compile TypeScript and bundle assets:

```bash
cd web-dashboard
npm run build
```

The compiled SPA bundle will be output to `web-dashboard/dist/`.

---

## 6. Deploying to Firebase Hosting

Ensure you are logged in to the Firebase CLI and the project `track-99c90` is active:

```bash
# Build the latest distribution
cd web-dashboard
npm run build
cd ..

# Deploy to Firebase Hosting
firebase deploy --only hosting
```

The live web dashboard is hosted at:
**`https://track-99c90.web.app`**
