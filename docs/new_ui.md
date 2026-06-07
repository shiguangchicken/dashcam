# New UI And Live Recording Stream Plan

## Summary

Implement the Stitch-based DroidDash UI and prepare an MJPEG live preview stream for remote clients while the recorder is active.

The implementation keeps the existing Android/Compose architecture, replaces the plain form-style screens with the dark HUD design from `design/stitch`, and adds a low-latency live preview endpoint for remote clients.

## Key Changes

- Apply the DroidDash dark design system globally using the palette in `design/stitch/droiddash/DESIGN.md`.
- Redesign the recorder/server UI as a full-screen dash HUD with status chips, large telemetry, a central REC control, camera/mic/lock/menu controls, QR/hotspot details, and active remote viewer status.
- Redesign the remote/client UI with a focused QR/Wi-Fi connection workflow, live preview area, command controls, storage summary, media browser tabs, thumbnail cards, and settings controls.
- Redesign local media and settings screens with dark panels, filters, thumbnail cards, metadata panels, and prominent destructive/save actions.
- Add the MJPEG live preview API and remote viewer surface. Live camera frame production remains gated until it can be enabled without destabilizing CameraX recording on the Mi 10.

## Live Streaming API

- Add `RemoteStatus.liveStreamAvailable`.
- Add `DashCamRemoteDataSource.livePreviewFrame(): ByteArray?`.
- Add `RemoteDashCamClient.liveStreamUrl(): String`.
- Add `GET /api/live.mjpeg`.
- The app publishes `503` until a safe preview-frame producer is attached. A CameraX `ImageAnalysis` producer was removed after it caused Mi 10 recording to stall without writing a usable video.
- The server streams `multipart/x-mixed-replace` frames at a capped rate and returns `503` when no live preview frame is available.
- The remote client displays the feed when available and falls back to a dark status placeholder when unavailable.

## Implementation Tasks

1. Update docs, theme, reusable UI primitives, and screen layouts.
2. Add the live preview frame contract and server stream plumbing.
3. Publish recorder runtime status from the foreground service.
4. Expose `/api/live.mjpeg` in `core-network`.
5. Wire app remote data source/client to the live stream.
6. Update JVM and instrumentation tests.

## Test Plan

- Run `./gradlew testDebugUnitTest ktlintCheck`.
- Verify `/api/live.mjpeg` returns `503` without a frame and emits multipart JPEG data with a fake frame.
- Verify `RemoteStatus.liveStreamAvailable` JSON round-trips and `RemoteDashCamClient.liveStreamUrl()` targets `/api/live.mjpeg`.
- Verify recorder, remote connection, remote dashboard, media browser, and settings Compose tests still find their primary controls.
- On connected devices, start the recorder server, connect the remote client, start recording, confirm the remote live preview fallback is shown when no frame is available, then stop recording and confirm the final segment appears in media.

## Assumptions

- Live streaming means live camera preview while recording, not playback of an unfinished MP4 segment.
- MJPEG preview is acceptable for v1; recorded video quality remains unchanged.
- Speed, GPS lock, temperature, and active viewer details may use existing/default data where real sensor/viewer tracking is not implemented.
- Existing navigation and module boundaries stay in place.

## Validation Results

- `./gradlew testDebugUnitTest ktlintCheck`: passed.
- `./gradlew assembleDebug`: passed.
- Mi 10 (`4e348abc`) UI smoke after reinstall and permission restore: passed. Tapped REC on the HUD, timer advanced to `00:08`, status changed to `Driving · Recording driving`, tapped STOP, and the app saved `/sdcard/Android/data/com.firmmy.dashcam/files/DashCam/videos/driving/2026-06-07/20260607_183256_001.mp4` at `15,004,174` bytes.
- Mi 10 setup prompts: location permission had to be allowed from the MIUI dialog before reaching the HUD. Camera and microphone permissions were also restored with adb after reinstall.

## Untested Or Blocked

- Live camera MJPEG frames from the physical recorder are not enabled. The `/api/live.mjpeg` server path is unit-tested with fake frames and returns `503` when no frame is available, but Mi 10 device validation uses the remote fallback state. The previous `ImageAnalysis` producer was removed because it caused the reported stuck `00:01`/no-video behavior.
- Two-device remote live preview was not fully completed after the Mi 10 recording regression was prioritized. The API and remote UI are present; physical frame streaming still needs a CameraX-safe producer.
- `:app:connectedDebugAndroidTest` on Mi 10 was unstable in earlier runs and did not complete reliably. Samsung connected instrumentation completed previously, but the final Mi 10 verification was the manual HUD smoke above.
