# Project Overview

## 1. Problem Statement
Smartphones are central to modern digital identity, communication, and personal security. Loss or unauthorized relocation of a mobile device introduces severe privacy risks, asset loss, and disruption. Traditional single-device find-my-phone tools frequently suffer from:
- Reliance on active display use or active user intervention.
- Complex or insecure multi-device pairing.
- Vulnerability to remote command spoofing.
- Lack of centralized web management for enrolled devices.

## 2. Project Mission & Objectives
**SecureTrack** is a smart mobile tracking and anti-theft platform engineered to monitor, protect, and remotely manage Android devices from a centralized Web Dashboard.

### Primary Objectives:
1. **Multi-Device Enrolment**: Allow account owners to register and manage multiple Android smartphones under one identity.
2. **Cryptographic Pairing**: Implement single-use, 256-bit QR-based pairing invitations with owner approval.
3. **Telemetry & Real-Time Tracking**: Record and stream GPS coordinates, battery level, network status, and operational heartbeats to Cloud Firestore.
4. **Owner-Authorized Revocation**: Give owners immediate control to deactivate enrolled devices and revoke permissions directly from the Web Dashboard.
5. **Security Isolation**: Enforce least-privilege security rules where devices can only push telemetry, and only verified owners can trigger administrative actions.

## 3. Scope & Supported Platforms
- **Android Client**: Native Android application developed in Java and XML, targeting Android 10+ (API level 29–34).
- **Web Dashboard**: Single-page application built with React, TypeScript, Tailwind CSS, and Vite.
- **Backend & Cloud Services**: Firebase Authentication (Email/Password + Google Sign-In), Cloud Firestore NoSQL database, and Firebase Hosting.
- **Legacy Reference Backend**: Spring Boot 3.2.5 REST API with MySQL schema for enterprise relational references.
