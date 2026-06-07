# DashCam

DashCam is an Android dash camera app for an older phone. It uses Jetpack Compose, CameraX, Room, and a foreground service to support recording, segmented video capture, photo capture, local media browsing, and storage management.

## Requirements

- Android Studio or a local Android SDK installation
- JDK 17 or newer
- Android SDK path available through `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or `local.properties`

If you do not already have a local SDK path configured, set one of these:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
```

## Build

Build the debug APK:

```bash
ANDROID_HOME=/home/meng/Android/Sdk ANDROID_SDK_ROOT=/home/meng/Android/Sdk ./gradlew assembleDebug
```

Run unit tests:

```bash
ANDROID_HOME=/home/meng/Android/Sdk ANDROID_SDK_ROOT=/home/meng/Android/Sdk ./gradlew testDebugUnitTest
```

Optional checks:

```bash
ANDROID_HOME=/home/meng/Android/Sdk ANDROID_SDK_ROOT=/home/meng/Android/Sdk ./gradlew ktlintCheck
```

## Install

Install the debug build on a connected Android device:

```bash
ANDROID_HOME=/home/meng/Android/Sdk ANDROID_SDK_ROOT=/home/meng/Android/Sdk ./gradlew installDebug
```

Or install the APK directly with `adb` after building:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On the phone, launch the app from the launcher or with:

```bash
adb shell am start -n com.firmmy.dashcam/.MainActivity
```

## Notes

- Package name: `com.firmmy.dashcam`
- Minimum Android version: 26
- Target SDK: 36
