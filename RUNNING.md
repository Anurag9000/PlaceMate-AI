# Running PlaceMate

## Quick Install On A Phone

Use the packaged APK:

- `releases/PlaceMate-debug.apk`

Direct install steps on Android:

1. Move `releases/PlaceMate-debug.apk` to the phone.
2. Open it from the Downloads or Files app.
3. If Android blocks the install, enable `Install unknown apps` for that source.
4. Finish the install and launch PlaceMate.

## Android Studio

1. Open the project in Android Studio.
2. Let Gradle sync complete.
3. Confirm Android SDK 35 is installed.
4. Use the `app` run configuration.
5. Launch on an emulator or physical device running Android 7.0 or newer.

## Command Line

From the repo root:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

Install the debug app on a connected device or emulator:

```bash
./gradlew installDebug
```

## Runtime Permissions

Grant these when prompted to exercise the full feature set:

- Camera
- Microphone
- Notifications

## Manual Validation Checklist

Use this checklist for the paths not yet fully proven in the automated/device suite:

1. Launch the app and verify splash routes to onboarding on first run and to home after onboarding completion.
2. Confirm the seeded sample data appears only once on a fresh install.
3. From Home, verify total items, taken items, AI engine label, and recent items render correctly.
4. Open Settings and test Gemini toggle, API key save, model refresh, prompt reset, prompt save, and reminder cadence save.
5. Add an item from a captured photo and confirm name/category suggestions populate.
6. Start speech input during add-item flow and confirm parsed fields update from real speech.
7. Open Inventory and verify folder navigation, search, add-folder, scene scan entry point, and breadcrumb behavior.
8. Run a scene scan and confirm rooms, storage nodes, and detected items are created in the expected hierarchy from a real room image.
9. Open OmniSearch and verify text, voice, and visual search flows using real microphone/camera input.
10. Open an item detail screen and verify edit and replace-photo flows.
11. Open Taken Items and verify only borrowed items appear.
12. Open Sentinel, select a location, run an audit image, and verify matched, missing, and new classifications.
13. Leave a taken item pending long enough to confirm reminder delivery and notification behavior.

## Verification Already Completed In This Environment

Completed in this Linux environment:

- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- `./gradlew assembleDebugAndroidTest`
- `./gradlew connectedDebugAndroidTest`

Completed on a connected Vivo 1933 device through instrumentation:

- app launch
- add-item manual save
- add-item hierarchical location creation and placement persistence
- item mark taken / mark returned
- item deletion
- settings persistence
- inventory clear-all-data confirmation
- camera permission prompt from Add Item
- microphone permission prompt from OmniSearch

Built artifacts:

- `releases/PlaceMate-debug.apk`
- `releases/PlaceMate-debug-androidTest.apk`
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

## Troubleshooting

- If Gradle cannot locate the SDK, set `ANDROID_HOME` or create `local.properties`.
- If the first build needs dependencies, allow Gradle network access.
- If Gradle caches get stale, run `./gradlew clean`.
- If Android Studio marks tests as missing resources, re-sync Gradle; JVM tests are configured to include Android resources.
- If install is blocked on the phone, enable `Install unknown apps` for the Files/Downloads app you used to open the APK.
