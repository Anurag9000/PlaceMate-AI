# Running PlaceMate

To build and run the PlaceMate Android application, follow these steps:

## Prerequisites
- **Android Studio**: Ladybug or later recommended.
- **Android Emulator** or **Physical Device**: API 26 (Android 8.0) or higher.
- **Permissions**: Ensure you grant Camera and Microphone permissions when prompted for multimodal features.

## Option 1: Using Android Studio (Recommended)
1.  Open the project in Android Studio.
2.  Wait for the **Gradle Sync** to finish (the screenshot shows it's already successful!).
3.  Select **'app'** in the run configuration dropdown.
4.  Select your target device (Emulator or Physical).
5.  Click the **Run** button (Green Arrow) or press `Shift + F10`.

## Option 2: Using the Command Line
From the root of the project (`d:\Research_projects\PLACEMATE`), run:

```powershell
# Build the debug APK
./gradlew assembleDebug

# Install and run on the connected device
./gradlew installDebug
```

## Troubleshooting
- **Gradle Version**: If you see a version error, ensure `gradle/wrapper/gradle-wrapper.properties` has `distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-all.zip`.
- **Hilt Errors**: If you encounter dependency injection errors, perform a **Build > Clean Project** followed by **Build > Rebuild Project**.
