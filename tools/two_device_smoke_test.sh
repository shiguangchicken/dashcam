#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ADB:-/home/meng/Android/Sdk/platform-tools/adb}"
APK="${APK:-$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk}"
PACKAGE="${PACKAGE:-com.firmmy.dashcam}"
RECORDER_SERIAL="${RECORDER_SERIAL:-}"
VIEWER_SERIAL="${VIEWER_SERIAL:-}"
DASHCAM_IP="${DASHCAM_IP:-}"
PAIRING_TOKEN="${PAIRING_TOKEN:-}"
HOTSPOT_SSID="${HOTSPOT_SSID:-}"
HOTSPOT_PASSWORD="${HOTSPOT_PASSWORD:-}"
REPORT_DIR="$ROOT_DIR/build/reports/two-device"

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

install_apk() {
  local serial="$1"
  echo "Installing APK on $serial"
  if ! "$ADB" -s "$serial" install -r "$APK"; then
    echo "Install failed on $serial. Check the device screen and tap \"继续安装\", then press Enter."
    read -r
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

connect_viewer_wifi() {
  if [[ -z "$HOTSPOT_SSID" || -z "$HOTSPOT_PASSWORD" ]]; then
    echo "HOTSPOT_SSID/HOTSPOT_PASSWORD not provided."
    echo "Connect viewer device $VIEWER_SERIAL to the recorder hotspot manually, then press Enter."
    read -r
    return
  fi

  echo "Connecting viewer to hotspot $HOTSPOT_SSID"
  if ! device_shell "$VIEWER_SERIAL" cmd wifi connect-network "$HOTSPOT_SSID" wpa2 "$HOTSPOT_PASSWORD"; then
    echo "Automatic Wi-Fi connect failed. Connect manually, then press Enter."
    read -r
  fi
}

viewer_http_get() {
  local url="$1"
  if device_shell "$VIEWER_SERIAL" 'command -v curl >/dev/null'; then
    device_shell "$VIEWER_SERIAL" curl -fsS -H "Authorization: Bearer $PAIRING_TOKEN" "$url"
  elif device_shell "$VIEWER_SERIAL" 'command -v wget >/dev/null'; then
    device_shell "$VIEWER_SERIAL" wget -qO- --header "Authorization: Bearer $PAIRING_TOKEN" "$url"
  else
    echo "Neither curl nor wget is available on viewer shell." >&2
    return 127
  fi
}

host_http_get() {
  local url="$1"
  curl -fsS -H "Authorization: Bearer $PAIRING_TOKEN" "$url"
}

api_get() {
  local path="$1"
  local url="http://$DASHCAM_IP:8080$path"
  if host_http_get "$url"; then
    return 0
  fi
  viewer_http_get "$url"
}

echo "Building debug APK"
ANDROID_HOME="${ANDROID_HOME:-/home/meng/Android/Sdk}" \
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/home/meng/Android/Sdk}" \
  "$ROOT_DIR/gradlew" -p "$ROOT_DIR" assembleDebug

install_apk "$RECORDER_SERIAL"
install_apk "$VIEWER_SERIAL"
grant_permissions "$RECORDER_SERIAL"
grant_permissions "$VIEWER_SERIAL"
start_app "$RECORDER_SERIAL"
start_app "$VIEWER_SERIAL"

echo "On recorder $RECORDER_SERIAL, choose Recorder mode and tap Hotspot on."
echo "On viewer $VIEWER_SERIAL, choose Remote viewer mode and save the recorder pairing token."
echo "If DASHCAM_IP or PAIRING_TOKEN are not set, enter them now."

if [[ -z "$DASHCAM_IP" ]]; then
  read -rp "DashCam IP [default 192.168.43.1]: " DASHCAM_IP
  DASHCAM_IP="${DASHCAM_IP:-192.168.43.1}"
fi
if [[ -z "$PAIRING_TOKEN" ]]; then
  read -rp "Pairing token: " PAIRING_TOKEN
fi

connect_viewer_wifi

echo "Collecting Wi-Fi status"
device_shell "$VIEWER_SERIAL" cmd wifi status > "$REPORT_DIR/viewer_wifi_status.txt" || true
device_shell "$RECORDER_SERIAL" dumpsys activity services "$PACKAGE" > "$REPORT_DIR/recorder_services.txt" || true

echo "Checking /api/status"
api_get "/api/status" | tee "$REPORT_DIR/status.json"

echo "Checking /api/media"
api_get "/api/media?type=video" | tee "$REPORT_DIR/media_video.json"

echo "Triggering remote photo command"
curl -fsS \
  -H "Authorization: Bearer $PAIRING_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"command":"photo"}' \
  "http://$DASHCAM_IP:8080/api/command" | tee "$REPORT_DIR/photo_command.json" || {
    echo "Host curl command failed. Trigger photo from the viewer UI and press Enter."
    read -r
  }

echo "Two-device smoke completed. Reports: $REPORT_DIR"
