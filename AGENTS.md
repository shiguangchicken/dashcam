# Repository Guidelines

## Project Structure & Module Organization

DashCam is a multi-module Android Gradle project. The main app entry point is in `app/src/main`, with Compose screens, the foreground recorder service, app wiring, and Android resources under `app/src/main/res`. Shared domain models and utilities live in `core-common`; persistence is in `core-database`; CameraX/media recording logic is in `core-media`; remote API, hotspot, NSD, and HTTP code are in `core-network`; voice stubs are in `core-voice`. Feature UI modules are `feature-recorder`, `feature-remote`, and `feature-settings`. Instrumentation helpers and smoke scripts live in `test-robot` and `tools`; design references are under `design/stitch`; planning and environment notes are in `docs`.

## Build, Test, and Development Commands

Use the Gradle wrapper from the repository root. If the Android SDK is not configured globally, prefix commands with:

```bash
ANDROID_HOME=/home/meng/Android/Sdk ANDROID_SDK_ROOT=/home/meng/Android/Sdk
```

Key commands:

```bash
./gradlew assembleDebug              # Build the debug APK
./gradlew installDebug               # Install on a connected device
./gradlew testDebugUnitTest          # Run JVM unit tests for debug variants
./gradlew ktlintCheck                # Check tabs and trailing spaces in .kt/.kts files
./gradlew :app:connectedDebugAndroidTest  # Run app instrumentation tests
```

Two-device validation scripts include `tools/two_device_smoke_test.sh` and `test-robot/scripts/foreground_recording_smoke.sh`.

## Coding Style & Naming Conventions

Use Kotlin for new code unless the touched module already requires Java interop, as in `core-network/src/main/java/.../NsdServiceInfoFactory.java`. Target JVM 17. Follow standard Kotlin formatting with four-space indentation, no tabs, and no trailing spaces. Keep packages under `com.firmmy.dashcam`; mirror module ownership in package paths such as `com.firmmy.dashcam.core.media` or `com.firmmy.dashcam.feature.remote`. Name Compose screens with a `Screen` suffix, tests with a `Test` or `InstrumentedTest` suffix, and marker classes with `ModuleMarker`.

## Testing Guidelines

Place local JVM tests in `src/test/java` and device tests in `src/androidTest/java` within the owning module. Prefer focused tests near the behavior being changed, for example `core-media/src/test/java/.../SegmentRecordingControllerTest.kt`. Run `./gradlew testDebugUnitTest ktlintCheck` before submitting changes; add connected tests when modifying Android framework, CameraX, Compose UI, or service behavior.

## Commit & Pull Request Guidelines

Recent commits use short imperative subjects such as `Fix recorder Wi-Fi connect crash` and `Implement remote viewer UI and two-device smoke`. Keep the first line concise and behavior-focused. Pull requests should describe the user-visible change, list tests run, link related issues or docs, and include screenshots or device notes for UI, camera, hotspot, or remote-viewer changes.

## Security & Configuration Tips

Do not commit `local.properties`, SDK paths, signing keys, APKs, or captured media. Keep device-specific details in local notes or `docs/env.md` only when they are intentional project documentation.
