#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
gradle_wrapper="$repo_root/gradlew"
debug_apk="$repo_root/app/build/outputs/apk/debug/app-debug.apk"
launch_target="com.chesspoints.app/.MainActivity"

step() {
  echo "==> $1"
}

fail() {
  echo "ERROR: $1" >&2
  exit 1
}

java_major_version() {
  local java_executable="$1"
  "$java_executable" -version 2>&1 | awk -F '"' '/version/ {split($2, v, "."); print v[1]; exit}'
}

ensure_java_home() {
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    local current_major
    current_major="$(java_major_version "${JAVA_HOME}/bin/java")"
    if [[ -n "$current_major" && "$current_major" -ge 11 ]]; then
      step "Using JAVA_HOME at $JAVA_HOME"
      return
    fi
  fi

  local candidates=(
    "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
    "/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
    "/usr/lib/jvm/temurin-17-jdk-amd64"
    "/usr/lib/jvm/java-17-openjdk-amd64"
    "/usr/lib/jvm/java-17-openjdk"
    "/usr/lib/jvm/default-java"
  )

  for candidate in "${candidates[@]}"; do
    if [[ ! -x "$candidate/bin/java" ]]; then
      continue
    fi

    local major
    major="$(java_major_version "$candidate/bin/java")"
    if [[ -n "$major" && "$major" -ge 11 ]]; then
      export JAVA_HOME="$candidate"
      export PATH="$JAVA_HOME/bin:$PATH"
      step "Using detected JDK at $JAVA_HOME"
      return
    fi
  done

  fail "JAVA_HOME is not set to a JDK 11+ installation and no fallback JDK was found. Install JDK 17, set JAVA_HOME, and retry."
}

local_properties_value() {
  local property_name="$1"
  local properties_file="$repo_root/local.properties"
  [[ -f "$properties_file" ]] || return 1

  local raw_value
  raw_value="$(grep -E "^${property_name}=" "$properties_file" | head -n 1 | cut -d= -f2- || true)"
  [[ -n "$raw_value" ]] || return 1

  raw_value="${raw_value//\\:/:}"
  raw_value="${raw_value//\\\\/\\}"
  printf '%s\n' "$raw_value"
}

android_sdk_root() {
  if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}" ]]; then
    printf '%s\n' "$ANDROID_HOME"
    return
  fi

  if [[ -n "${ANDROID_SDK_ROOT:-}" && -d "${ANDROID_SDK_ROOT}" ]]; then
    printf '%s\n' "$ANDROID_SDK_ROOT"
    return
  fi

  local sdk_dir
  sdk_dir="$(local_properties_value "sdk.dir" || true)"
  if [[ -n "$sdk_dir" && -d "$sdk_dir" ]]; then
    printf '%s\n' "$sdk_dir"
    return
  fi

  fail "Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT, or add sdk.dir to local.properties. Then retry with USB debugging enabled on your device."
}

step "Checking Java"
ensure_java_home

step "Locating adb"
sdk_root="$(android_sdk_root)"
adb_path="$sdk_root/platform-tools/adb"
[[ -x "$adb_path" ]] || fail "adb was not found at $adb_path. Install Android SDK platform-tools and retry."
step "Using adb at $adb_path"

step "Checking for a connected Android device"
"$adb_path" start-server >/dev/null
device_output="$("$adb_path" devices)"
if grep -q '[[:space:]]unauthorized$' <<<"$device_output"; then
  fail "A device is connected but unauthorized. Unlock it, accept the USB debugging prompt, and retry."
fi

device_lines="$(grep '[[:space:]]device$' <<<"$device_output" | grep -v '^List of devices attached' || true)"
[[ -n "$device_lines" ]] || fail "No connected Android device was found. Connect a device, enable Developer Options and USB debugging, then retry."
step "Detected device(s):"
printf '%s\n' "$device_lines"

[[ -x "$gradle_wrapper" ]] || chmod +x "$gradle_wrapper"

step "Building the debug APK"
(
  cd "$repo_root"
  "$gradle_wrapper" :app:assembleDebug --console=plain
)

[[ -f "$debug_apk" ]] || fail "Debug APK not found at $debug_apk after the build completed."

step "Installing the debug APK"
"$adb_path" install -r "$debug_apk"

step "Launching ChessPoints"
"$adb_path" shell am start -n "$launch_target"

step "Done. ChessPoints should now be running on the connected device."
