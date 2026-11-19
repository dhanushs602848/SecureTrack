# Contributing to SecureTrack

Thank you for your interest in contributing to SecureTrack.

## Code of Conduct
Please maintain a respectful, constructive, and collaborative tone when opening issues or submitting pull requests.

## Development Workflow
1. **Fork & Branch**: Create a feature branch from `main` (e.g., `feature/device-telemetry-optimization`).
2. **Coding Standards**:
   - Android code uses standard Java conventions with Android Jetpack guidelines.
   - Web Dashboard uses TypeScript, React functional components, and Tailwind CSS.
   - Maintain clear comments and avoid hardcoding credentials.
3. **Local Testing**:
   - Android: Run `./gradlew :app:testDebugUnitTest` and ensure `:app:assembleDebug` succeeds.
   - Web Dashboard: Run `npm run build` and `node --test web-dashboard/test/auth.test.js`.
   - Security Suite: Run `node --test functions/test/pairing.test.js`.
4. **Submitting Pull Requests**:
   - Provide a clear description of the problem solved.
   - Reference any relevant issues.
   - Ensure continuous integration checks pass.
