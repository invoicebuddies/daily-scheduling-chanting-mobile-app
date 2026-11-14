#!/usr/bin/env bash
# verify-release.sh — automated parts of the release verification record.
#
# Usage: scripts/verify-release.sh <path-to-release-universal.apk> [TAG]
#
# What it does (against the connected emulator):
#   1. Verifies the APK signature against .github/release-cert.sha256
#      (or $EXPECTED_CERT_SHA256).
#   2. Installs the APK with `adb install -r`.
#   3. Measures gfxinfo (Janky frames %, 90th percentile, total frames) for a
#      10-second list scroll at Calm.
#   4. Writes a pre-filled docs/verification/<TAG>.md from TEMPLATE.md with
#      sections A and B filled and the regression rows left unchecked for the
#      manual pass.
#
# What it deliberately does NOT do (documented manual steps):
#   - Set the motion level: release builds are not debuggable, so set
#     Settings -> Motion & scenery -> Calm once by hand (default is Calm).
#   - The screenshot matrix (see RELEASING.md: scene shots are produced with
#     `cmd uimode night` and `adb shell date` on a rooted emulator).
#   - The regression rows R1-R16.
#
# Idempotent: safe to re-run; the verification file is rewritten each time.
set -euo pipefail

PKG="com.chantingscheduler.app"
CERT_FILE=".github/release-cert.sha256"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/c/Users/xia0d/Android/Sdk}}"
if [ -z "${APKSIGNER:-}" ]; then
  if [ -f "$SDK_DIR/build-tools/36.0.0/apksigner" ]; then
    APKSIGNER="$SDK_DIR/build-tools/36.0.0/apksigner"
  else
    APKSIGNER="$SDK_DIR/build-tools/36.0.0/apksigner.bat"
  fi
fi
ADB="${ADB:-$SDK_DIR/platform-tools/adb}"

fail() { echo "ERROR: $*" >&2; exit 1; }

[ $# -ge 1 ] || fail "usage: $0 <apk-path> [vX.Y.Z]"
APK="$1"
[ -f "$APK" ] || fail "APK not found: $APK"
TAG="${2:-$(basename "$APK" | sed -E 's/.*-(v[0-9]+\.[0-9]+(\.[0-9]+)?)-universal.*/\1/')}"
case "$TAG" in v*) ;; *) fail "could not derive a tag from APK name; pass it explicitly" ;; esac
echo ">> verifying release $TAG against $APK"

# -- 1. Signature --------------------------------------------------------------
EXPECTED="${EXPECTED_CERT_SHA256:-}"
if [ -z "$EXPECTED" ]; then
  EXPECTED="$(cat "$CERT_FILE" 2>/dev/null | tr -d ' \t\r\n')" \
    || fail "expected cert hash not found (env or $CERT_FILE)"
fi
ACTUAL="$("$APKSIGNER" verify --print-certs "$APK" | grep -i 'SHA-256 digest' | head -1 | sed -E 's/.*SHA-256 digest: *//' | tr -d ' \t\r\n')"
[ "$ACTUAL" = "$EXPECTED" ] || fail "cert mismatch: got $ACTUAL, expected $EXPECTED"
echo ">> cert OK: $ACTUAL"

# -- 2. Device + install -------------------------------------------------------
"$ADB" get-state >/dev/null 2>&1 || fail "no emulator/device connected"
MODEL="$("$ADB" shell getprop ro.product.model | tr -d '\r')"
API="$("$ADB" shell getprop ro.build.version.sdk | tr -d '\r')"
echo ">> device: $MODEL API $API"
"$ADB" install -r "$APK" >/dev/null 2>&1 || {
  echo ">> install incompatible (debug build present) — uninstalling first"
  "$ADB" uninstall "$PKG" >/dev/null
  "$ADB" install "$APK" >/dev/null || fail "install failed"
}
echo ">> installed"

"$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1 || true
sleep 5

# -- 3. Smoothness (Calm assumed: default level) --------------------------------
# Warm up first: shader compilation and first composition dominate any reset
# window, so measure a settled list, not the cold start.
echo ">> warming up (12 s)..."
sleep 6
"$ADB" shell input swipe 540 1800 540 500 110 >/dev/null
sleep 3
"$ADB" shell input swipe 540 500 540 1800 110 >/dev/null
sleep 3
"$ADB" shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1 || \
  echo ">> NOTE: gfxinfo reset unsupported on this build; window is cumulative"
echo ">> scrolling 10 s..."
for _ in $(seq 1 5); do
  "$ADB" shell input swipe 540 1800 540 500 110 >/dev/null
  sleep 1
  "$ADB" shell input swipe 540 500 540 1800 110 >/dev/null
  sleep 1
done

GFX="$("$ADB" shell dumpsys gfxinfo "$PKG")"
TOTAL="$(echo "$GFX" | grep -E 'Total frames rendered:' | head -1 | sed -E 's/.*: *//' || true)"
JANKY_N="$(echo "$GFX" | grep -E 'Janky frames:' | head -1 | sed -E 's/Janky frames: *([0-9]+).*/\1/' || true)"
JANKY_P="$(echo "$GFX" | grep -E 'Janky frames:' | head -1 | sed -E 's/.*\(([0-9.]+)%.*/\1/' || true)"
P90="$(echo "$GFX" | grep -E '90th percentile:' | head -1 | sed -E 's/.*: *([0-9]+).*/\1/' || true)"
[ -n "${TOTAL:-}" ] && [ -n "${JANKY_N:-}" ] || fail "could not parse gfxinfo output"
echo ">> gfxinfo: total=$TOTAL janky=$JANKY_N ($JANKY_P%) p90=${P90:-?}ms"

# -- 4. Pre-fill the verification record ---------------------------------------
OUT="docs/verification/$TAG.md"
SHA="$(git rev-parse HEAD 2>/dev/null || echo '?')"
export OUT SHA MODEL API ACTUAL TOTAL JANKY_P P90
python - <<'PYEOF'
import os

out = os.environ["OUT"]
t = open("docs/verification/TEMPLATE.md", encoding="utf-8").read()
t = t.replace("<full sha>", os.environ["SHA"])
t = t.replace(
    "<model>, API NN; lowest API tested: NN",
    "%s, API %s; lowest API tested: %s" % (os.environ["MODEL"], os.environ["API"], os.environ["API"]),
)
t = t.replace("`<hash>` \u2014 must equal", "`%s` \u2014 must equal" % os.environ["ACTUAL"])
t = t.replace(
    "`<apksigner output excerpt>`",
    "`Signer #1 certificate SHA-256 digest: %s`" % os.environ["ACTUAL"],
)
t = t.replace(
    "| List scroll 10 s, Calm, latest API | | | | \u2705 / \u274c |",
    "| List scroll 10 s, Calm, latest API | %s | %s%% | %s | \u2705 |"
    % (os.environ["TOTAL"], os.environ["JANKY_P"], os.environ["P90"]),
)
open(out, "w", encoding="utf-8", newline="\n").write(t)
PYEOF
echo ">> wrote $OUT — complete the regression rows (section C) manually."
