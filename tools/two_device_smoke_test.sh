#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ADB:-/home/meng/Android/Sdk/platform-tools/adb}"
APK="${APK:-$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk}"
PACKAGE="${PACKAGE:-com.firmmy.dashcam}"
RECORDER_SERIAL="${RECORDER_SERIAL:-}"
VIEWER_SERIAL="${VIEWER_SERIAL:-}"
DASHCAM_IP="${DASHCAM_IP:-}"
HOTSPOT_SSID="${HOTSPOT_SSID:-}"
HOTSPOT_PASSWORD="${HOTSPOT_PASSWORD:-}"
REPORT_DIR="$ROOT_DIR/build/reports/two-device"
RECORDER_SERVICE="$PACKAGE/.RecorderForegroundService"
AUTO_UI="${AUTO_UI:-1}"
VERIFY_VIEWER_UI="${VERIFY_VIEWER_UI:-1}"
SMOKE_INTERACTIVE="${SMOKE_INTERACTIVE:-0}"

if [[ -z "$RECORDER_SERIAL" || -z "$VIEWER_SERIAL" ]]; then
  echo "RECORDER_SERIAL and VIEWER_SERIAL are required." >&2
  echo "Example: RECORDER_SERIAL=serial-a VIEWER_SERIAL=serial-b tools/two_device_smoke_test.sh" >&2
  exit 2
fi

mkdir -p "$REPORT_DIR"

adb_device() {
  "$ADB" -s "$1" "$@"
}

device_shell() {
  local serial="$1"
  shift
  "$ADB" -s "$serial" shell "$@"
}

dump_screen() {
  local serial="$1"
  local output="$REPORT_DIR/window-$serial.xml"
  timeout 8 "$ADB" -s "$serial" shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  timeout 5 "$ADB" -s "$serial" shell cat /sdcard/window.xml > "$output" 2>/dev/null || true
  printf '%s\n' "$output"
}

tap_text() {
  local serial="$1"
  local text="$2"
  local xml
  xml="$(dump_screen "$serial")"
  local point
  point="$(python3 - "$xml" "$text" <<'PY'
import html
import re
import sys
path, target = sys.argv[1], sys.argv[2]
data = open(path, encoding="utf-8", errors="ignore").read()
for node in re.finditer(r'<node\b[^>]*>', data):
    tag = node.group(0)
    text = re.search(r'text="([^"]*)"', tag)
    content = re.search(r'content-desc="([^"]*)"', tag)
    values = [
        html.unescape(match.group(1))
        for match in (text, content)
        if match is not None
    ]
    if not any(target in value for value in values):
        continue
    match = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
    if match:
        left, top, right, bottom = map(int, match.groups())
        print((left + right) // 2, (top + bottom) // 2)
        sys.exit(0)
sys.exit(1)
PY
)"
  if [[ -z "$point" ]]; then
    return 1
  fi
  # shellcheck disable=SC2086
  "$ADB" -s "$serial" shell input tap $point
}

tap_if_present() {
  local serial="$1"
  local text="$2"
  [[ "$AUTO_UI" == "1" ]] || return 0
  tap_text "$serial" "$text" >/dev/null 2>&1 || true
}

allow_runtime_dialogs() {
  local serial="$1"
  for _ in {1..6}; do
    tap_if_present "$serial" "继续安装"
    tap_if_present "$serial" "允许"
    tap_if_present "$serial" "仅在使用该应用时允许"
    tap_if_present "$serial" "While using the app"
    tap_if_present "$serial" "Allow"
    sleep 0.5
  done
}

install_apk() {
  local serial="$1"
  echo "Installing APK on $serial"
  if ! "$ADB" -s "$serial" install -r "$APK"; then
    echo "Install failed on $serial. Checking the device screen for installer confirmation."
    tap_if_present "$serial" "继续安装"
    tap_if_present "$serial" "重新安装"
    tap_if_present "$serial" "安装"
    sleep 2
    "$ADB" -s "$serial" install -r "$APK"
  fi
}

grant_permissions() {
  local serial="$1"
  for permission in \
    android.permission.CAMERA \
    android.permission.RECORD_AUDIO \
    android.permission.POST_NOTIFICATIONS \
    android.permission.ACCESS_FINE_LOCATION \
    android.permission.NEARBY_WIFI_DEVICES; do
    device_shell "$serial" pm grant "$PACKAGE" "$permission" >/dev/null 2>&1 || true
  done
}

start_app() {
  local serial="$1"
  device_shell "$serial" am start -n "$PACKAGE/.MainActivity" >/dev/null
}

start_role_if_needed() {
  local serial="$1"
  local role_text="$2"
  start_app "$serial"
  sleep 1
  tap_if_present "$serial" "$role_text"
  sleep 1
  tap_if_present "$serial" "Continue"
  allow_runtime_dialogs "$serial"
}

scroll_and_tap_text() {
  local serial="$1"
  local text="$2"
  [[ "$AUTO_UI" == "1" ]] || return 1
  for _ in {1..3}; do
    if tap_text "$serial" "$text" >/dev/null 2>&1; then
      return 0
    fi
    device_shell "$serial" input swipe 540 1900 540 700 300 >/dev/null
    sleep 0.3
  done
  return 1
}

wait_for_manual_step() {
  local message="$1"
  echo "$message"
  if [[ "$SMOKE_INTERACTIVE" == "1" ]]; then
    read -r
    return 0
  else
    echo "Set SMOKE_INTERACTIVE=1 to pause here for manual device interaction." >&2
    return 1
  fi
}

pull_recorder_db() {
  local output="$REPORT_DIR/recorder-dashcam.db"
  "$ADB" -s "$RECORDER_SERIAL" exec-out run-as "$PACKAGE" cat "databases/dashcam.db" > "$output" 2>/dev/null || true
  "$ADB" -s "$RECORDER_SERIAL" exec-out run-as "$PACKAGE" cat "databases/dashcam.db-wal" > "$output-wal" 2>/dev/null || true
  "$ADB" -s "$RECORDER_SERIAL" exec-out run-as "$PACKAGE" cat "databases/dashcam.db-shm" > "$output-shm" 2>/dev/null || true
  printf '%s\n' "$output"
}

read_recorder_setting() {
  local key="$1"
  local db
  db="$(pull_recorder_db)"
  if [[ ! -s "$db" ]]; then
    return 1
  fi
  sqlite3 "$db" "SELECT value FROM app_settings WHERE key = '$key' LIMIT 1;" 2>/dev/null || true
}

refresh_recorder_settings_from_db() {
  HOTSPOT_SSID="${HOTSPOT_SSID:-$(read_recorder_setting hotspot_ssid)}"
  HOTSPOT_PASSWORD="${HOTSPOT_PASSWORD:-$(read_recorder_setting hotspot_password)}"
}

ensure_recorder_video_sample() {
  echo "Ensuring recorder has a real video sample"
  device_shell "$RECORDER_SERIAL" am start-foreground-service \
    -n "$RECORDER_SERVICE" \
    -a com.firmmy.dashcam.action.START_DRIVING \
    --ez com.firmmy.dashcam.extra.AUDIO_ENABLED false \
    --ei com.firmmy.dashcam.extra.SEGMENT_DURATION_MINUTES 1 >/dev/null || true
  sleep 25
  device_shell "$RECORDER_SERIAL" am start-foreground-service \
    -n "$RECORDER_SERVICE" \
    -a com.firmmy.dashcam.action.STOP >/dev/null || true
  sleep 5
}

connect_viewer_wifi() {
  if [[ -z "$HOTSPOT_SSID" || -z "$HOTSPOT_PASSWORD" ]]; then
    echo "HOTSPOT_SSID/HOTSPOT_PASSWORD not provided."
    wait_for_manual_step "Connect viewer device $VIEWER_SERIAL to the recorder hotspot manually, then press Enter."
    return
  fi

  echo "Connecting viewer to hotspot $HOTSPOT_SSID"
  if ! device_shell "$VIEWER_SERIAL" cmd wifi connect-network "$HOTSPOT_SSID" wpa2 "$HOTSPOT_PASSWORD"; then
    wait_for_manual_step "Automatic Wi-Fi connect failed. Connect viewer to $HOTSPOT_SSID manually, then press Enter."
  fi
}

viewer_http_get() {
  local url="$1"
  if device_shell "$VIEWER_SERIAL" 'command -v curl >/dev/null'; then
    device_shell "$VIEWER_SERIAL" curl -fsS "$url"
  elif device_shell "$VIEWER_SERIAL" 'command -v wget >/dev/null'; then
    device_shell "$VIEWER_SERIAL" wget -qO- "$url"
  fi
  return 127
}

host_http_get() {
  local url="$1"
  curl -fsS "$url"
}

api_get() {
  local path="$1"
  local url="http://$DASHCAM_IP:8080$path"
  if host_http_get "$url"; then
    return 0
  fi
  viewer_http_get "$url"
}

viewer_nc_request() {
  local request="$1"
  printf '%b' "$request" | "$ADB" -s "$VIEWER_SERIAL" shell "nc -w 5 $DASHCAM_IP 8080" 2>/dev/null
}

api_get_to_file() {
  local path="$1"
  local output="$2"
  local raw="$output.raw"
  local request="GET $path HTTP/1.1\r\nHost: $DASHCAM_IP\r\nConnection: close\r\n\r\n"
  if curl -fsS "http://$DASHCAM_IP:8080$path" > "$output"; then
    return 0
  fi
  if viewer_http_get "http://$DASHCAM_IP:8080$path" > "$output"; then
    return 0
  fi
  viewer_nc_request "$request" > "$raw"
  head -n 1 "$raw" | grep -q " 200 "
  sed -n '/^\r\{0,1\}$/,$p' "$raw" | sed '1d' | tr -d '\r' > "$output"
}

api_post_command_to_file() {
  local command="$1"
  local output="$2"
  local raw="$output.raw"
  local body="{\"command\":\"$command\"}"
  local length=${#body}
  local request
  request="POST /api/command HTTP/1.1\r\nHost: $DASHCAM_IP\r\n"
  request+="Content-Type: application/json\r\n"
  request+="Content-Length: $length\r\nConnection: close\r\n\r\n$body"
  if curl -fsS \
    -H "Content-Type: application/json" \
    -d "$body" \
    "http://$DASHCAM_IP:8080/api/command" > "$output"; then
    return 0
  fi
  viewer_nc_request "$request" > "$raw"
  head -n 1 "$raw" | grep -q " 200 "
  sed -n '/^\r\{0,1\}$/,$p' "$raw" | sed '1d' | tr -d '\r' > "$output"
}

api_range_to_file() {
  local media_id="$1"
  local output="$2"
  local raw="$output.raw"
  local path="/api/media/$media_id/stream"
  local request
  request="GET $path HTTP/1.1\r\nHost: $DASHCAM_IP\r\n"
  request+="Range: bytes=0-1023\r\nConnection: close\r\n\r\n"
  if curl -fsS \
    -D "$output.headers" \
    -H "Range: bytes=0-1023" \
    "http://$DASHCAM_IP:8080$path" > "$output"; then
    grep -q "206" "$output.headers"
    return 0
  fi
  viewer_nc_request "$request" > "$raw"
  head -n 1 "$raw" | tee "$output.status" | grep -q " 206 "
  sed -n '/^\r\{0,1\}$/,$p' "$raw" | sed '1d' > "$output"
}

first_video_id() {
  grep -o '"id":[0-9]\+' "$REPORT_DIR/media_video.json" | head -n 1 | cut -d: -f2
}

viewer_gateway_ip() {
  local neighbor
  neighbor="$(
    device_shell "$VIEWER_SERIAL" ip neigh |
      awk '/wlan0/ && $1 ~ /^[0-9.]+$/ && $1 !~ /\.0$/ && $0 !~ /FAILED|INCOMPLETE/ {print $1; exit}'
  )"
  if [[ -n "$neighbor" ]]; then
    printf '%s\n' "$neighbor"
    return 0
  fi
  device_shell "$VIEWER_SERIAL" ip route |
    awk '/default/ && /wlan0/ {print $3; exit}' |
    cut -d/ -f1
}

connect_viewer_app_and_open_video() {
  start_app "$VIEWER_SERIAL"
  sleep 1
  start_role_if_needed "$VIEWER_SERIAL" "Remote viewer mode"
  tap_if_present "$VIEWER_SERIAL" "Manual IP"
  device_shell "$VIEWER_SERIAL" input text "$DASHCAM_IP" >/dev/null || true
  tap_if_present "$VIEWER_SERIAL" "Connect"
  sleep 6
  dump_screen "$VIEWER_SERIAL" > /dev/null
  if ! grep -q "Recorder status" "$REPORT_DIR/window-$VIEWER_SERIAL.xml"; then
    echo "Viewer UI did not show remote status; check $REPORT_DIR/window-$VIEWER_SERIAL.xml" >&2
    return 1
  fi
  scroll_and_tap_text "$VIEWER_SERIAL" "$(date +%Y-%m-%d)" || {
    echo "Could not tap a video row by date; leaving UI dump for manual inspection." >&2
    return 1
  }
  sleep 3
  dump_screen "$VIEWER_SERIAL" > /dev/null
  grep -q "remote_video_player\\|Delete\\|Back" "$REPORT_DIR/window-$VIEWER_SERIAL.xml"
}

echo "Building debug APK"
ANDROID_HOME="${ANDROID_HOME:-/home/meng/Android/Sdk}" \
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/home/meng/Android/Sdk}" \
  "$ROOT_DIR/gradlew" -p "$ROOT_DIR" assembleDebug

install_apk "$RECORDER_SERIAL"
install_apk "$VIEWER_SERIAL"
grant_permissions "$RECORDER_SERIAL"
grant_permissions "$VIEWER_SERIAL"
start_role_if_needed "$RECORDER_SERIAL" "Recorder mode"
start_role_if_needed "$VIEWER_SERIAL" "Remote viewer mode"

echo "On recorder $RECORDER_SERIAL, choose Recorder mode and tap Hotspot on if automation does not do it."
scroll_and_tap_text "$RECORDER_SERIAL" "Hotspot on" ||
  scroll_and_tap_text "$RECORDER_SERIAL" "WIFI" ||
  true
sleep 8
refresh_recorder_settings_from_db

ensure_recorder_video_sample
refresh_recorder_settings_from_db

echo "On viewer $VIEWER_SERIAL, scan the recorder QR if validating the full app flow."
echo "If DASHCAM_IP is not set, connect Wi-Fi now or let the script try stored hotspot credentials."

if [[ -z "$DASHCAM_IP" ]]; then
  connect_viewer_wifi
  DASHCAM_IP="$(viewer_gateway_ip)"
fi
if [[ -z "$DASHCAM_IP" ]]; then
  read -rp "DashCam IP [default 192.168.43.1]: " DASHCAM_IP
  DASHCAM_IP="${DASHCAM_IP:-192.168.43.1}"
fi
connect_viewer_wifi

echo "Collecting Wi-Fi status"
device_shell "$VIEWER_SERIAL" cmd wifi status > "$REPORT_DIR/viewer_wifi_status.txt" || true
device_shell "$RECORDER_SERIAL" dumpsys activity services "$PACKAGE" > "$REPORT_DIR/recorder_services.txt" || true

echo "Checking /api/status"
api_get_to_file "/api/status" "$REPORT_DIR/status.json"
cat "$REPORT_DIR/status.json"

echo "Checking /api/media"
api_get_to_file "/api/media?type=video" "$REPORT_DIR/media_video.json"
cat "$REPORT_DIR/media_video.json"

VIDEO_ID="$(first_video_id)"
if [[ -z "$VIDEO_ID" ]]; then
  echo "No video item returned by remote API after recording sample." >&2
  exit 1
fi

echo "Checking /api/media/$VIDEO_ID/stream Range playback"
api_range_to_file "$VIDEO_ID" "$REPORT_DIR/media_${VIDEO_ID}_range.bin"

echo "Checking /api/live.mjpeg while recorder is actively recording"
api_post_command_to_file "driving" "$REPORT_DIR/live_start_command.json"
sleep 8
api_get_to_file "/api/status" "$REPORT_DIR/live_status.json"
if ! grep -q '"liveStreamAvailable":true' "$REPORT_DIR/live_status.json"; then
  echo "Live stream was not advertised while recording." >&2
  cat "$REPORT_DIR/live_status.json" >&2
  exit 1
fi
timeout 8 curl -fsS "http://$DASHCAM_IP:8080/api/live.mjpeg" > "$REPORT_DIR/live_preview.mjpeg" ||
  timeout 8 "$ADB" -s "$VIEWER_SERIAL" shell curl -fsS "http://$DASHCAM_IP:8080/api/live.mjpeg" > "$REPORT_DIR/live_preview.mjpeg" || {
    echo "Live MJPEG request failed while recording." >&2
    exit 1
  }
grep -q -- "--dashcam-frame" "$REPORT_DIR/live_preview.mjpeg" || {
  echo "Live MJPEG response did not include the expected multipart boundary." >&2
  exit 1
}
api_post_command_to_file "stop" "$REPORT_DIR/live_stop_command.json" || true

echo "Triggering remote photo command"
api_post_command_to_file "photo" "$REPORT_DIR/photo_command.json"
cat "$REPORT_DIR/photo_command.json"

echo "Opening remote video in viewer UI"
if [[ "$VERIFY_VIEWER_UI" == "1" ]]; then
  "$ADB" -s "$VIEWER_SERIAL" logcat -c || true
  connect_viewer_app_and_open_video || {
    echo "Viewer video UI validation failed. Check the screen and $REPORT_DIR/window-$VIEWER_SERIAL.xml" >&2
    exit 1
  }
  "$ADB" -s "$VIEWER_SERIAL" logcat -d -t 1000 > "$REPORT_DIR/viewer_logcat_after_playback.txt" || true
  if grep -E "FATAL EXCEPTION|AndroidRuntime|NoSuchMethodError|VideoView.*FATAL" \
    "$REPORT_DIR/viewer_logcat_after_playback.txt"; then
    echo "Viewer logcat contains a crash signature after playback." >&2
    exit 1
  fi
else
  echo "Skipping viewer UI playback validation because VERIFY_VIEWER_UI=$VERIFY_VIEWER_UI"
fi

echo "Two-device smoke completed. Reports: $REPORT_DIR"
