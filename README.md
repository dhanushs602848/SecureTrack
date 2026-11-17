# SecureTrack — Smart Mobile Tracking & Anti-Theft System

![SecureTrack Logo](app/src/main/res/drawable/ic_securetrack_logo.xml)

**Tagline:** Track smart. Stay secure.

SecureTrack is a production-grade, multi-platform mobile tracking and anti-theft solution designed to protect Android mobile endpoints. The monorepo incorporates a native Android application (Java/XML), a Spring Boot REST API (Java 17/21), an authoritative MySQL database schema managed by Flyway, and a responsive React TypeScript Web Dashboard.

---

## 🏛 System Architecture

```
┌────────────────────────────────┐         ┌────────────────────────────────┐
│  Android App (Java / XML)      │         │  React Web Dashboard           │
│  - FusedLocationProviderClient │         │  - TypeScript + Vite           │
│  - Foreground Tracking Service │         │  - Tailwind CSS                │
│  - Credential Manager & Google │         │  - Leaflet OpenStreetMap       │
│  - DeviceAdminReceiver         │         │  - JWT Session Auth            │
└───────────────┬────────────────┘         └───────────────┬────────────────┘
                │                                          │
                │              HTTPS REST API              │
                └────────────────────┬─────────────────────┘
                                     │
                     ┌───────────────▼────────────────┐
                     │  Spring Boot REST Backend      │
                     │  - Java 17/21 / Spring Security│
                     │  - JWT Auth + Google OAuth2 ID │
                     │  - Command State Engine        │
                     │  - Flyway Migrations           │
                     └───────────────┬────────────────┘
                                     │
                     ┌───────────────▼────────────────┐
                     │  MySQL 8.4 Database            │
                     │  - users (with google_sub_id)  │
                     │  - devices, locations, etc.    │
                     └────────────────────────────────┘
```

---

## 📱 Features & Component Overview

### 1. Native Android Application (`app/`)
* **UI & Branding:** Crisp navy (`#152C4F`) and blue (`#255DA8`) palette matching Figma specs. Custom shield & location pin vector logo (`ic_securetrack_logo.xml`).
* **Authentication:** Traditional Email/Password registration/login + **Sign in with Google** via Credential Manager (`GetGoogleIdOption`) and Play Services Auth API.
* **Tracking & Location:** `FusedLocationProviderClient`, `LocationTrackingService` (Foreground Service with ongoing notification), `BootReceiver` reboot status handling, and OpenStreetMap Osmdroid with visible copyright attribution.
* **Anti-Theft & Remote Lock:** `DeviceAdminReceiver` for remote locking, `SimChangeReceiver` for SIM swap monitoring, and `CommandExecutionManager` for processing remote device commands.

### 2. Spring Boot REST Backend (`backend/`)
* **Framework:** Spring Boot 3.2.5, Java 17/21, Maven (`mvnw` / `mvnw.cmd`).
* **Google OAuth Verification:** `GoogleIdTokenVerifier` verifies token signature, audience, issuer, and expiry. Creates or links corresponding SecureTrack accounts via `google_sub_id`.
* **Security & JWT:** Spring Security, JWT (JJWT 0.12), BCrypt password hashing, and ownership authorization filters.
* **Documentation:** OpenAPI / Swagger UI (`http://localhost:8080/swagger-ui.html`).

### 3. Database Schema (`database/` & `backend/src/main/resources/db/migration/`)
* **Flyway Migrations:** `V1__init_schema.sql` (core tables) and `V2__add_google_sub_id.sql` (Google account linking).

### 4. React Web Dashboard (`web-dashboard/`)
* **Tech Stack:** React 18, TypeScript, Vite, Tailwind CSS, React-Leaflet (OpenStreetMap).

---

## 🔑 Google Cloud OAuth Setup Guide

To enable live Google Sign-In authentication on Android:

1. **Open Google Cloud Console:** [Google Cloud Credentials](https://console.cloud.google.com/apis/credentials)
2. **Create Web Application Client ID:**
   - Application Type: **Web application**
   - Name: `SecureTrack Backend Web Client`
   - Copy the generated **Client ID** (e.g. `123456789-abcdef.apps.googleusercontent.com`).
3. **Create Android Client ID:**
   - Application Type: **Android**
   - Package Name: `com.example.securetrack`
   - SHA-1 Certificate Fingerprint: `1A:86:27:77:E2:B1:F7:B6:7A:14:AB:60:C8:F5:04:E8:BC:77:D0:F7`
4. **Configure Credentials:**
   - **Android App:** Update `default_web_client_id` in `app/src/main/res/values/strings.xml` with your **Web Client ID**.
   - **Backend Environment:** Set `GOOGLE_CLIENT_ID` environment variable to your **Web Client ID**.

---

## 🚀 Execution Instructions

### 1. Database & Backend
```bash
# Set environment variables
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/securetrack_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export SPRING_DATASOURCE_USERNAME="securetrack_app"
export SPRING_DATASOURCE_PASSWORD="securetrack_pass"
export JWT_SECRET="YOUR_256_BIT_SECRET_KEY"
export GOOGLE_CLIENT_ID="YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"

# Run Spring Boot backend via Maven Wrapper
cd backend
./mvnw clean spring-boot:run
```

### 2. Android Debug APK
```bash
./gradlew app:assembleDebug
```
APK Path: `app/build/outputs/apk/debug/app-debug.apk`

---

## 🔒 Security & Secret Protection
* All production secrets and credentials must be set via environment variables.
* `.gitignore` excludes temporary build artifacts, `.env`, local properties, and keystores.
