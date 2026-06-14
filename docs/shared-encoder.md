# Shared Encoder Recording And Live Streaming Design

## Summary

DashCam needs a real live camera pipeline for both the local recorder dashboard and the remote viewer. The current live background path uses JPEG snapshots:

```text
Camera frame -> ImageAnalysis -> JPEG bytes -> Compose polling -> Bitmap decode -> Image draw
```

That design is useful as a fallback, but it cannot deliver smooth 60 fps preview. It allocates and decodes many JPEGs, increases CPU and GC pressure, and can compete with video recording.

The target design replaces the snapshot path with a shared Camera2 and MediaCodec pipeline:

- RecorderScreen uses a real camera preview surface behind the HUD.
- Recording writes encoded H.264 video to MP4.
- Remote live view receives the same encoded H.264 stream over LAN.
- The foreground recorder service remains the recording owner.

## Current Problems

### JPEG Preview Is Not A Real Preview

The current RecorderScreen live background is not a camera preview surface. It is a sequence of JPEG frames produced by `ImageAnalysis`, stored in runtime state, polled by Compose, decoded into bitmaps, and drawn as an `Image`.

This has several limits:

- Preview frame rate is capped by throttling and Compose polling.
- JPEG encode/decode work is CPU-heavy.
- Frequent `ByteArray` and bitmap churn causes GC pressure.
- Increasing the rate toward 60 fps risks UI jank, heat, battery drain, and recording instability.

### CameraX VideoCapture Does Not Expose Encoded Frames

The existing CameraX `VideoCapture` API records to a file, but it does not expose the encoded H.264 access units that are being written. Because of that, it cannot directly feed a low-latency remote stream.

Serving the active MP4 file while it is still being written is also not a reliable live-stream design. Many players cannot play the file until MP4 metadata is finalized, and range reads from a growing muxed file are fragile.

## Target Architecture

### Ownership

The foreground service remains the camera and recording owner. This preserves background recording, service notification behavior, media indexing, and segmented recording semantics.

RecorderScreen is only a preview surface provider. It does not own recording and does not open a competing camera session.

### Camera Session

Replace the CameraX recording internals with a Camera2 capture session that can target multiple surfaces:

- MediaCodec H.264 input surface for recording and remote streaming.
- RecorderScreen preview surface when the UI is visible.
- Optional still capture surface for photo capture.

The active capture session should be rebuilt when the UI preview surface attaches or detaches, without stopping the active recording encoder.

### Video Encoder

Use `MediaCodec` for H.264 encoding. The encoder output is duplicated to:

- `MediaMuxer`, for the local MP4 recording file.
- A live stream hub, for remote subscribers.

The encoder output is the source of truth for both saved video and remote video. This avoids double video encoding and keeps the remote stream aligned with what is being recorded.

### FPS Selection

60 fps is best-effort, not mandatory.

The camera pipeline should query `CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES` and choose:

1. A 60 fps range if available for the selected camera and mode.
2. Otherwise, the highest supported stable range.

If 60 fps is not available, recording must still start successfully at the fallback frame rate.

## Local Recorder Preview

RecorderScreen should use a real surface preview:

- Host a full-screen `SurfaceView` or `PreviewView` behind the existing HUD.
- Show the idle design image when not recording.
- Show the camera preview surface when recording and the preview surface is attached.
- Avoid JPEG polling, bitmap decoding, and frame-delay loops for local live background.

The app layer should provide the preview surface to the foreground service through a small surface registry or binder-style controller. The service attaches the surface to the active Camera2 session when available and detaches it when the composable leaves the screen.

Expected behavior:

- Tap REC: service starts recording, attaches encoder surface, and uses preview surface if available.
- Navigate away: recording continues; preview surface detaches.
- Return to RecorderScreen: preview surface reattaches to the active recording session.
- Tap STOP: recording stops, muxer finalizes MP4, and idle background returns.

## Remote H.264 Streaming

### Transport

Use a WebSocket endpoint:

```text
/ws/live/h264
```

This replaces the MJPEG live endpoint for real remote live view. The MJPEG endpoint may remain temporarily as a compatibility fallback, but it should not be the primary remote live path.

### Stream Messages

The first WebSocket message is text JSON describing the stream:

```json
{
  "codec": "h264",
  "width": 1920,
  "height": 1080,
  "fps": 60,
  "format": "annex-b",
  "sps": "<base64>",
  "pps": "<base64>"
}
```

Binary messages contain encoded access units. Each binary message should include a small fixed header followed by the H.264 bytes:

```text
u64 presentationTimeUs
u8 flags
u32 payloadSize
payload bytes
```

Flags:

- `0x01`: keyframe.
- `0x02`: codec config repeated in payload.

The server must send SPS/PPS before the first IDR frame for each subscriber and after reconnect. If a subscriber joins between keyframes, it waits until the next IDR frame.

### Stream Hub

The live stream hub receives encoded output from the video encoder and fans it out to connected remote viewers.

Responsibilities:

- Track active subscribers.
- Keep the latest SPS/PPS codec config.
- Drop frames for slow subscribers instead of blocking the encoder.
- Prefer latest-frame behavior over queue growth.
- Request or wait for a keyframe when a new subscriber joins.
- Mark `RemoteStatus.liveStreamAvailable` true only when recording and stream data is available.

## Remote Viewer Decode

RemoteViewerScreen should decode H.264 with Android `MediaCodec` into a surface:

- Use a `SurfaceView` or equivalent Android view behind the remote HUD.
- Connect to `/ws/live/h264` when remote status says live stream is available.
- Configure decoder from the stream config JSON.
- Feed binary access units into decoder input buffers.
- Render decoder output to the surface.
- On disconnect or recording stop, release decoder resources and return to the idle design image.

Remote live audio is out of scope for v1. Saved MP4 should still contain audio when enabled.

## MP4 Recording

Recording uses `MediaMuxer`:

- Add H.264 video track after encoder output format is available.
- Add AAC audio track when audio recording is enabled.
- Write encoded samples with presentation timestamps.
- Finalize muxer on stop before registering the media item.

Audio path:

- Use `AudioRecord` for microphone capture.
- Use AAC `MediaCodec` for encoding.
- Mux audio into the saved MP4.
- Do not stream remote live audio in v1.

Segment rotation must stop and finalize the current muxer, register the completed segment, then start a new muxer and continue encoding with minimal interruption.

## Photo Capture

Photo capture should continue to work:

- When idle, use a still capture path from Camera2.
- While recording, either capture from a still surface if the device supports the active surface combination, or save a high-quality frame from the preview/encoder path.

Photo capture must not stop or corrupt the active recording.

## Error Handling

Recording is higher priority than preview and remote streaming.

If preview surface attachment fails:

- Continue recording.
- Keep RecorderScreen HUD functional.
- Show idle/fallback background only if no preview surface can be attached.

If remote streaming fails:

- Continue recording.
- Disconnect only the failed subscriber.
- Keep local MP4 output intact.

If 60 fps is unsupported:

- Fall back to the best supported FPS.
- Continue recording.
- Report actual FPS in stream config and media metadata.

## Public API Changes

Expected app/network contract changes:

- Add `/ws/live/h264`.
- Add a remote client URL helper for the H.264 WebSocket endpoint.
- Update remote status to represent H.264 live availability.
- Add app-level preview surface attach/detach API between RecorderScreen and recorder service.

The existing media list, media stream/download APIs, settings APIs, hotspot flow, and role selection flow stay unchanged.

## Test Plan

### Unit Tests

- FPS range selection prefers 60 fps when available.
- FPS range selection falls back to the highest supported range.
- Stream hub sends codec config before keyframes.
- Stream hub drops frames for slow subscribers without blocking encoder input.
- Remote status reports live stream availability only while recording and encoder output is available.

### Instrumentation Tests

- RecorderScreen idle displays `recorder_idle_background`.
- Tap REC displays the real preview surface, not a decoded JPEG `Image`.
- Stop recording creates a playable MP4 and thumbnail.
- RecorderScreen navigation away/back detaches and reattaches preview without stopping recording.
- Remote viewer connects to `/ws/live/h264`, configures decoder, and shows live video background.
- Remote viewer returns to idle image when recording stops.

### Device Smoke Tests

Run on both connected devices:

- Start recording from RecorderScreen.
- Confirm the local background preview is smooth.
- Stop recording and confirm MP4 plus thumbnail appear in media.
- Connect remote device over hotspot.
- Start recording on recorder phone.
- Confirm remote live video starts and stops cleanly.
- Confirm unsupported 60 fps devices fall back without failing recording.

## Assumptions

- The service remains the only recording owner.
- 60 fps is best-effort.
- Remote live transport is custom H.264 over WebSocket, not WebRTC.
- Remote live audio is out of scope for v1.
- Saved video quality and media indexing remain higher priority than preview or remote streaming.
