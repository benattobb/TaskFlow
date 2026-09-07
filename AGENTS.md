# AGENTS.md — TaskFlow Project Instructions & Guidelines

## Overview
**TaskFlow** is an Android (Kotlin / Jetpack Compose) task-capture application featuring local natural-language task parsing, Google Tasks & Google Calendar sync, home-screen widgets (Quick capture, Type a task, Voice capture, Focus), and Android Live Updates / Focus notifications for Pixel and Android devices. It also includes a static Firebase web hosting landing page in `hosting/public`.

## Local Environment & Tooling
- **Java Home**: OpenJDK 21 (`/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home`) configured automatically via `gradlew` and `gradle.properties`.
- **Android SDK Location**: Project local SDK at `.android-sdk/` configured in `local.properties`.
- **Package Name**: `com.example.taskflow`

## Common Commands
### Build & Test
- **Run Unit Tests**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Build Debug APK**:
  ```bash
  ./gradlew assembleDebug
  ```
- **Run Lint & Verification**:
  ```bash
  ./gradlew lint testDebugUnitTest assembleDebug
  ```
  *Debug APK location*: `app/build/outputs/apk/debug/app-debug.apk`

### Firebase Hosting (Web Landing Page)
- **Firebase CLI**:
  ```bash
  npx -y firebase-tools@latest hosting:channel:deploy
  ```

## Key Architecture Components
- **NLP Engine**: `com.example.taskflow.nlp` — Performs zero-cost, privacy-preserving, on-device parsing of task text (dates, times, recurrence, tags).
- **Google Sync**: `com.example.taskflow.google` — Handles Google Tasks API and Google Calendar API synchronization.
- **UI**: `com.example.taskflow.ui` — Jetpack Compose UI components and Material 3 design system.
- **Widgets**: `com.example.taskflow.widget` — Glance/AppWidget providers for quick capture, voice, focus, and typing surfaces.
- **Focus & Live Surface**: `com.example.taskflow.FocusLiveUpdate` — Manages active focus notifications and Android 16+ Live Update promotion.

## Code Conventions
- Target Java/Kotlin compatibility: JDK 21.
- All network calls to Google APIs must be HTTPS-only with deterministic IDs to prevent duplication.
- Local NLP logic must remain cost-free and privacy-preserving without external cloud LLM dependencies during task extraction.
