---
description: How to build and install the PlaceMate APK on your phone.
---

# How to Install PlaceMate

## Option 1: Quick Install (Debug APK)
This is the fastest method. The "Debug" APK works perfectly but is signed with a rigorous "debug" key (valid for ~30 years).

### 1. Build the APK
Run this command in the terminal:
```bash
./gradlew assembleDebug
```
// turbo
The APK will be saved at:  
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Transfer to Phone
- **USB**: Connect your phone, choose "File Transfer" mode, and copy the `.apk` file to your "Downloads" folder.
- **Email/Drive**: Email the file to yourself or upload to Google Drive.

### 3. Install
1. Open your phone's File Manager.
2. Tap the `app-debug.apk`.
3. If prompted, allow "Install from Unknown Sources" for your file manager.
4. Tap "Install".

## Option 2: Release Build (Advanced)
Only needed if you want to publish to the Play Store.

1. Generate a Keystore:
   ```bash
   keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias
   ```
2. Configure `build.gradle.kts` signing configs.
3. Run `./gradlew assembleRelease`.
