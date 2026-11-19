# Security Policy

## Supported Versions
| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability
The SecureTrack project takes security issues seriously:
1. If you discover a potential vulnerability, please report it privately to the maintainer rather than opening a public issue.
2. Provide technical reproduction details, impacted components (Android app, Web Dashboard, or Firestore Rules), and potential impact.
3. Responsible disclosures will be reviewed promptly and addressed in an upcoming release.

## Architecture Guardrails
- **Credential Storage**: No passwords, private tokens, or sensitive account keys are ever committed to the repository.
- **Access Control**: Device telemetry writes are strictly isolated to authenticated device identities, and administrative operations require human owner authentication.
