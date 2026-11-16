# SecureTrack — Smart Mobile Tracking & Anti-Theft System

![SecureTrack Logo](app/src/main/res/drawable/ic_shield_logo.xml)

**Tagline:** Track smart. Stay secure.

SecureTrack is a production-grade, multi-platform mobile tracking and anti-theft solution designed to protect Android mobile endpoints. The monorepo incorporates a native Android application (Java/XML), a robust Spring Boot REST API (Java 21), an authoritative MySQL database schema managed by Flyway, and a responsive React TypeScript Web Dashboard.

---

## 🏛 System Architecture

```
┌────────────────────────────────┐         ┌────────────────────────────────┐
│  Android App (Java / XML)      │         │  React Web Dashboard           │
│  - FusedLocationProviderClient │         │  - TypeScript + Vite           │
│  - Foreground Tracking Service │         │  - Tailwind CSS                │
│  - DeviceAdminReceiver         │         │  - Leaflet OpenStreetMap       │
│  - Room Offline Sync Queue     │         │  - JWT Session Auth            │
└───────────────┬────────────────┘         └───────────────┬────────────────┘
                │                                          │
                │              HTTPS REST API              │
                └────────────────────┬─────────────────────┘
                                     │
                     ┌───────────────▼────────────────┐
                     │  Spring Boot REST Backend      │
                     │  - Java 21 / Spring Security   │
                     │  - JWT Authentication          │
                     │  - Command State Engine        │
                     │  - Flyway Migrations           │
                     └───────────────┬────────────────┘
                                     │
                     ┌───────────────▼────────────────┐
                     │  MySQL 8.4 Database            │
                     │  - users, devices, locations   │
                     │  - security_events, commands   │
                     └────────────────────────────────┘
```

---

## 📱 Tech Stack & Component Overview

### 1. Native Android Application (`app/`)
* **Language:** Java 17 / Android 14+ (API 35 target)
* **UI:** Native XML matching Figma Make palette (`#152C4F` Navy, `#255DA8` Blue, `#F8F9FB` Surface)
* **Architecture:** ViewBinding, Retrofit 2 + Gson, Room Database, WorkManager, FusedLocationProviderClient
* **Security & Anti-Theft:** `DeviceAdminReceiver` (remote lock), `LocationTrackingService` (Foreground Service), `SimChangeReceiver`
* **Maps:** OpenStreetMap Osmdroid with visible copyright attribution

### 2. Spring Boot REST Backend (`backend/`)
* **Framework:** Spring Boot 3.2.5, Java 21, Maven
* **Security:** Spring Security, JWT (JJWT 0.12), BCrypt, Ownership Authorization Filters
* **Database & ORM:** Spring Data JPA, Hibernate, MySQL Connector, Flyway Migrations
* **Documentation:** OpenAPI / Swagger UI (`/swagger-ui.html`)

### 3. Database Schema (`database/` & `backend/src/main/resources/db/migration/`)
* **Authoritative Schema:** `V1__init_schema.sql` (Flyway)
* **Tables:** `users`, `devices`, `device_pairing_codes`, `locations` (with unique `client_uuid` for idempotent upload), `security_events`, `device_commands` (`QUEUED`, `DELIVERED`, `RECEIVED`, `EXECUTED`, `FAILED`), `refresh_tokens`, `password_reset_tokens`.

### 4. React Web Dashboard (`web-dashboard/`)
* **Tech Stack:** React 18, TypeScript, Vite, Tailwind CSS, Lucide Icons, React-Leaflet (OpenStreetMap)
* **Features:** Live tracking, location history timeline, device management, pairing code generation, remote command center, security alert monitor.

---

## 🚀 Setup & Execution Guide

### Prerequisites
* JDK 21 installed
* Android Studio Ladybug or newer
* MySQL 8.4 Server running on port 3306
* Node.js 18+ and npm

---

### 1. Database Setup
Create MySQL database `securetrack_db`:
```sql
CREATE DATABASE securetrack_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

### 2. Backend Setup
1. Navigate to `backend/`:
   ```bash
   cd backend
   ```
2. Configure credentials in `src/main/resources/application.yml` or set environment variables:
   ```bash
   export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/securetrack_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
   export SPRING_DATASOURCE_USERNAME="root"
   export SPRING_DATASOURCE_PASSWORD="root"
   ```
3. Run Spring Boot API:
   ```bash
   mvn spring-boot:run
   ```
   API runs at `http://localhost:8080`. Swagger documentation available at `http://localhost:8080/swagger-ui.html`.

---

### 3. Android Setup & Networking
1. Open the root `SecureTrack` folder in Android Studio.
2. Build the Debug APK:
   ```bash
   ./gradlew app:assembleDebug
   ```
   APK Output Path: `app/build/outputs/apk/debug/app-debug.apk`
3. **Emulator Networking:** By default, Android emulator accesses local backend via `http://10.0.2.2:8080/`.
4. **Physical Phone Networking:** Connect phone and host machine to the same Wi-Fi network and update server URL in app Settings screen to `http://<YOUR_LOCAL_IP>:8080/`.

---

### 4. Web Dashboard Setup
1. Navigate to `web-dashboard/`:
   ```bash
   cd web-dashboard
   ```
2. Install dependencies and start Vite dev server:
   ```bash
   npm install
   npm run dev
   ```
3. Open browser at `http://localhost:5173`.

---

## 🧪 Testing

* **Backend Integration Tests:** Run `mvn test` in `backend/` to execute MockMvc security and command lifecycle tests.
* **Android Unit Tests:** Run `./gradlew test` in root.

---

## 🔒 Security & Privacy Notices
* Background location access is strictly opt-in and toggled manually by the user.
* Remote lock capability requires explicit activation of Device Administrator privileges.
* Pairing codes expire in 10 minutes and are single-use.
