# PlaceMate AI

PlaceMate is a fully native Android app written in Kotlin for Android Studio. It uses Android Views with ViewBinding, MVVM, Hilt, Room, WorkManager, ML Kit, and optional Gemini integration for AI-assisted recognition flows.

## Install The APK

Primary handoff APK:

- `releases/PlaceMate-debug.apk`

Build output copy:

- `app/build/outputs/apk/debug/app-debug.apk`

On an Android phone:

1. Copy `releases/PlaceMate-debug.apk` to the phone.
2. Open it from Files/Downloads.
3. Allow installs from unknown apps for that source if Android prompts.
4. Launch PlaceMate from the app drawer.

## Product Scope

- Splash screen with onboarding gating.
- Dashboard with item counts, taken-item counts, AI engine status, and recent items.
- Nested inventory explorer with folder navigation and in-place search.
- Manual item creation with optional photo and voice assistance.
- Camera/photo recognition for single items.
- Scene scan that creates room and storage hierarchies from a captured image.
- OmniSearch for text, voice, and photo-driven lookup.
- Item detail editing, photo replacement, deletion, taken/returned state, and due-date handling.
- Borrowed-items screen.
- Sentinel audit flow that compares a new scene image against stored contents for a selected location.
- Settings for Gemini enablement, API key, model selection, prompt customization, and reminder cadence.

## Stack

- Kotlin
- Android Views + ViewBinding
- MVVM
- Hilt
- Room
- DataStore + encrypted shared preferences
- WorkManager
- ML Kit
- Gemini API

## Architecture

![PlaceMate-AI Architecture](docs/architecture.png)

## Runtime Notes

- The app stores images through app-scoped `FileProvider` content URIs, not raw filesystem paths.
- Gemini is optional. The app falls back to the local ML Kit pipeline when Gemini is disabled or no API key is present.
- Seed data only runs on an empty database.
- Inventory location edits now preserve existing metadata such as stored photos and creation time.
- Sentinel audits resolve the target location directly instead of depending on pre-collected UI state.
- Add Item now preserves typed field values while location changes are made.
- Recognition services are initialized lazily so non-photo flows do not crash on devices that react badly to eager ML Kit startup.

## Host-Side Verification

Verified in this Linux environment:

- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- `./gradlew assembleDebugAndroidTest`
- `./gradlew connectedDebugAndroidTest`

Current JVM test coverage includes:

- synonym normalization
- category mapping and container detection
- speech intent parsing
- seed-data initialization
- inventory single-placement enforcement
- nested location path resolution
- location metadata preservation during edits
- sentinel audit classification logic
- borrow/return repository side effects

## Hardware Verification Completed

Verified on a connected Vivo 1933 device:

- app install and launch
- onboarding dismissal / entry to the main app
- add-item manual save
- add-item save with hierarchical location creation and final placement persistence
- item detail mark taken / mark returned
- item deletion
- settings persistence
- inventory clear-all-data confirmation and resulting empty state
- camera permission request flow from Add Item
- microphone permission request flow from OmniSearch

Generated artifacts:

- `releases/PlaceMate-debug.apk`
- `releases/PlaceMate-debug-androidTest.apk`
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

## Limits Of This Verification

Not fully verified end to end from this environment:

- real camera capture completion and recognition quality
- real microphone recognition quality/content
- Gemini/network-backed recognition behavior
- Sentinel audit with real scene photos
- scene-scan with real room photos
- delayed notification delivery over time
- every possible OEM-specific edge case

Use `RUNNING.md` for install, run, and focused manual checks for the still-unverified paths.
