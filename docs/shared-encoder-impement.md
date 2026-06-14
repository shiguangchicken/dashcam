# Shared Encoder Implementation Status

## Goal

Implement `docs/shared-encoder.md` step by step:

- Use a real camera preview surface for RecorderScreen.
- Replace JPEG snapshot live background with a shared encoded pipeline.
- Stream live H.264 over WebSocket to RemoteViewerScreen.
- Keep recording owned by the foreground service.

## Steps

| Step | Status | Notes |
|---|---|---|
| 1. Add implementation tracker | Done | This document records each implementation step and verification result. |
| 2. Add H.264 stream protocol and stream hub | Done | Added stream config, access-unit framing, fan-out hub, and keyframe-aware server send behavior. |
| 3. Add `/ws/live/h264` endpoint and client URL | Done | Added WebSocket endpoint and `RemoteDashCamClient.liveH264WebSocketUrl()`. Existing MJPEG endpoint remains as fallback. |
| 4. Add app runtime bridge for encoded frames | Done | `RecorderRuntimeState` owns `H264LiveStream`; `AppRemoteDataSource` exposes it to `/ws/live/h264`. |
| 5. Add RecorderScreen real preview surface attachment | Done | App hosts a TextureView-backed preview surface. The surface is registered while RecorderScreen is visible so it exists before REC binds the Camera2 session. |
| 6. Replace CameraX recording with shared Camera2/MediaCodec | Done | Foreground service now uses `Camera2SharedEncoderFacade`: Camera2 feeds one MediaCodec input surface for MP4 muxing and H.264 live stream fan-out, plus the local preview surface when attached. 60-fps selection is best-effort with tested fallback selection. |
| 7. Add RemoteViewer H.264 decoder surface | Done | Remote live dashboard and home preview use a TextureView-backed MediaCodec AVC decoder connected to `/ws/live/h264`. MJPEG WebView is no longer the primary live renderer. |
| 8. Remove JPEG live background path | Done | RecorderScreen no longer accepts or decodes JPEG live frames for backgrounds. Runtime live availability now depends on H.264 stream availability. The old MJPEG endpoint remains only as a compatibility API. |
| 9. Device validation | In Progress | Mi 10 local Camera2 recording, live camera background, and media output verified. Samsung install verified. Full two-device remote stream validation intentionally skipped per request. |
| 10. Fix remote H.264 rotation | Ready For Device Check | H.264 stream config now includes recorder camera `rotationDegrees`; RemoteViewer applies a TextureView transform to rotate and crop only the decoded video surface. Updated APK installed on Mi 10 and Samsung for two-device validation. |

## Current Verification

- `./gradlew :core-network:testDebugUnitTest`: passed.
- `./gradlew :app:compileDebugKotlin :core-network:testDebugUnitTest`: passed.
- `./gradlew :app:compileDebugKotlin :core-media:compileDebugKotlin :feature-recorder:compileDebugKotlin`: passed.
- `./gradlew :core-media:testDebugUnitTest`: passed.
- `./gradlew :app:compileDebugKotlin :feature-remote:compileDebugKotlin`: passed.
- `./gradlew ktlintCheck testDebugUnitTest`: passed.
- `./gradlew :app:compileDebugKotlin :feature-recorder:compileDebugKotlin :core-media:testDebugUnitTest :core-network:testDebugUnitTest`: passed after preview surface timing fix.
- `./gradlew :app:compileDebugKotlin :feature-recorder:compileDebugKotlin assembleDebug`: passed after switching recorder preview host to `PreviewView.COMPATIBLE`.
- `./gradlew ktlintCheck testDebugUnitTest`: passed after device-visible preview fix.
- `./gradlew :feature-remote:compileDebugKotlin :app:compileDebugKotlin`: passed after adding the remote H.264 decoder surface.
- `./gradlew ktlintCheck testDebugUnitTest`: passed after Camera2 encoder, remote decoder, and JPEG background removal.
- `./gradlew assembleDebug`: passed after final implementation updates.
- `./gradlew :core-network:testDebugUnitTest :app:compileDebugKotlin :feature-remote:compileDebugKotlin`: passed after adding stream rotation metadata.
- `./gradlew ktlintCheck testDebugUnitTest`: passed after remote video rotation fix.
- `./gradlew :core-network:testDebugUnitTest assembleDebug`: passed before installing rotation-fix APK.

## Device Notes

- `/home/meng/Android/Sdk/platform-tools/adb devices -l`: Mi 10 `4e348abc` and Samsung SM-G7810 `RFCRA0JHHYW` are connected.
- Rebuilt APK installed successfully on both connected devices.
- Mi 10 recorder UI validation after Camera2 encoder switch: REC starts recording, the recording background shows the live camera image, STOP finalizes recording, and new MP4 files were created under `/sdcard/Android/data/com.firmmy.dashcam/files/DashCam/videos/driving/2026-06-13/`.
- Final debug APK installed successfully on Mi 10 `4e348abc`.
- Rotation-fix debug APK installed successfully on Mi 10 `4e348abc` and Samsung SM-G7810 `RFCRA0JHHYW`.
- Full two-device remote stream validation was not run because the current request excluded it.
- Samsung direct foreground-service smoke hit Android package restrictions after force-stop: `START_DRIVING` returned `Error: Not found; no service started`, and direct receiver broadcast was skipped as a restricted package. Continue validation through the app UI or the two-device smoke script after install/start clears that state.
