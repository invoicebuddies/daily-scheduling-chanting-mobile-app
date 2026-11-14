#!/usr/bin/env bash
# Captures the scene screenshot matrix for the release record:
# 4 scenes x 2 themes x day/night on the connected device (debug build, run-as).
set -euo pipefail

ADB_BIN="${ADB:-$HOME/Android/Sdk/platform-tools/adb.exe}"
# Select the target with: ANDROID_SERIAL=emulator-5556 bash scripts/capture-scenes.sh
PKG=com.chantingscheduler.app
OUT=docs/screenshots/v1.4.3
mkdir -p "$OUT"

"$ADB_BIN" root >/dev/null 2>&1 || true
sleep 2
"$ADB_BIN" wait-for-device

write_scene() {
  local scene="$1"
  printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <string name=\"motion_level\">calm</string>\n    <string name=\"art_scene\">%s</string>\n</map>\n" "$scene" \
    | "$ADB_BIN" shell "run-as $PKG sh -c 'cat > shared_prefs/motion.xml'"
}

for scene in monk lotus temple abstract; do
  write_scene "$scene"
  for theme in light dark; do
    if [ "$theme" = light ]; then "$ADB_BIN" shell cmd uimode night no; else "$ADB_BIN" shell cmd uimode night yes; fi
    for tod in day night; do
      if [ "$tod" = day ]; then "$ADB_BIN" shell "date 111413002026.00" >/dev/null; else "$ADB_BIN" shell "date 111423002026.00" >/dev/null; fi
      "$ADB_BIN" shell am force-stop $PKG
      "$ADB_BIN" shell am start -n $PKG/.MainActivity >/dev/null 2>&1
      sleep 5
      "$ADB_BIN" exec-out screencap -p > "$OUT/scene-$scene-$tod-$theme.png"
      echo "captured scene-$scene-$tod-$theme.png"
    done
  done
done

# Restore day theme + real-ish clock
"$ADB_BIN" shell cmd uimode night no
echo "matrix done"
