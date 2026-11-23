# Project Overview

## 1. Problem Statement
Smartphones are central to modern digital identity, communication, and personal security. Loss or unauthorized relocation of a mobile device introduces severe privacy risks, asset loss, and disruption. Traditional single-device find-my-phone tools frequently suffer from:
- Reliance on active display use or active user intervention.
- Complex or insecure multi-device pairing.
- Vulnerability to remote command spoofing or stale command re-execution.
- Lack of centralized web management for enrolled devices.
- Fragile single-database architectures that break down under intermittent network connectivity.

## 2. Project Mission & Objectives
**SecureTrack** is an enterprise-grade mobile telemetry, loss prevention, and anti-theft defense platform engineered to monitor, protect, and remotely manage Android devices from a centralized Web Operations Console.

### Primary Objectives:
1. **Multi-Device Enrollment**: Allow account owners to register and manage multiple Android smartphones under one authenticated identity.
2. **Cryptographic Pairing**: Implement single-use, 256-bit cryptographic enrollment tokens (10-minute TTL, SHA-256 hashed) with verified owner authorization.
3. **Continuous Telemetry & Real-Time Tracking**: Record and stream GPS coordinates, battery level, network status, and operational heartbeats to Cloud Firestore with 4-tier location resolution fallback.
4. **Deterministic Remote Incident Response**: Provide instant anti-theft commands (`LOCATE_NOW`, `LOCK_DEVICE`, `ALARM_ALERT`, `WIPE_DEVICE`) governed by an atomic **15-minute queue expiry window** and a **5.0-second siren safety limit**.
5. **Dual-Database Resilience**: Couple Cloud Firestore NoSQL for distributed real-time sync with local Android Room SQLite persistence for offline telemetry caching and incident resilience.
6. **Owner-Authorized Revocation**: Give owners immediate control to deactivate enrolled devices and revoke permissions directly from the Web Dashboard.
7. **Security Isolation**: Enforce least-privilege security rules where devices can only push telemetry, and only verified owners can trigger administrative actions.

## 3. Scope & Supported Platforms
- **Android Client**: Native Android application developed in Java and XML, targeting Android 8.0 to 15 (API level 26–35, Target SDK 35).
- **Web Operations Console**: Single-page application built with React 18, TypeScript, Tailwind CSS, and Vite.
- **Backend & Cloud Services**: Firebase Authentication (Email/Password), Cloud Firestore NoSQL database, Cloud Functions (Node.js 18), Firebase Cloud Messaging (FCM), and Firebase Hosting.
- **Legacy Reference Backend**: Spring Boot 3.2.5 REST API with MySQL schema for enterprise relational references and academic comparison.

## 4. Project Leadership & Mentorship
- **Academic Project Guide**: **Mohankumar H M** — DBMS Project Guide & Academic Mentor
- **Project Lead, Owner & Author**: **Dhanush S** ([@dhanushs602848](https://github.com/dhanushs602848))
- **Core Engineering Team**:
  - **Deeksith H G** — Core Security Architecture, Telemetry Systems & Verification
  - **Deeksith K** — Android Client Engineering & Feature Integration
  - **Puneethkumar N E** — Database Architecture, Multi-Device Testing & Verification
  - **Deeksith Raj N** — UI/UX Implementation & Geospatial Mapping Integration
  - **Deepak D S** — Quality Assurance, Hardware Field Testing & Documentation

## 5. Licensing
SecureTrack is distributed under the **Apache License, Version 2.0** (`Copyright 2026 Dhanush S`). See `LICENSE` and `NOTICE` for full terms.
