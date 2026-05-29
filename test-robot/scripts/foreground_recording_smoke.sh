#!/usr/bin/env bash
set -euo pipefail

ADB="${ADB:-/home/meng/Android/Sdk/platform-tools/adb}"
APK="${APK:-app/build/outputs/apk/debug/app-debug.apk}"
PACKAGE="${PACKAGE:-com.firmmy.dashcam}"
SERVICE="${SERVICE:-com.firmmy.dashcam/.RecorderForegroundService}"
SEGMENT_MINUTES="${SEGMENT_MINUTES:-1}"
SCREEN_OFF_SECONDS="${SCREEN_OFF_SECONDS:-120}"

"$ADB" install -r "$APK"
"$ADB" shell pm grant "$PACKAGE" android.permission.CAMERA || true
"$ADB" shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
"$ADB" shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS || true

"$ADB" shell am start-foreground-service \
    -n "$SERVICE" \
    -a com.firmmy.dashcam.action.START_DRIVING \
    --ez com.firmmy.dashcam.extra.AUDIO_ENABLED false \
    --ei com.firmmy.dashcam.extra.SEGMENT_DURATION_MINUTES "$SEGMENT_MINUTES"

"$ADB" shell input keyevent 26
sleep "$SCREEN_OFF_SECONDS"
"$ADB" shell input keyevent 26

"$ADB" shell ls -l "/sdcard/Android/data/$PACKAGE/files/DashCam/videos/driving"
"$ADB" shell am start-foreground-service -n "$SERVICE" -a com.firmmy.dashcam.action.STOP
