#!/usr/bin/env bash
#
# Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
#
# This software may be modified and distributed under the terms
# of the MIT license. See the LICENSE file for details.
#

# Post-boot harness for the push e2e emulator step.
#
# The android-emulator-runner action executes its `script` input LINE BY LINE,
# each line as a separate `sh -c` process: no state survives between lines and
# multi-line constructs (if/for/heredocs) are syntactically impossible. The
# workflow therefore invokes this file as a single line; everything needing a
# real shell lives here. Inputs arrive via the step env:
#   SAMPLE_APP_APK      path to the built sample APK
#   SAMPLE_APP_PKG      sample app package name (job env)
#   PUSH_E2E_*          tenant/realm/user/trees (job env)
#   PUSH_E2E_PASSWORD   zero-page login password (secret)
#   SVCACCT_ID/JWK      service account credentials for the admin token (secret)

set -euo pipefail

cd "${GITHUB_WORKSPACE:-$PWD}"

# Kill the emulator AND the poster service on the way out, whatever the exit path.
# Without this the emulator-runner action's post-run cleanup can hang for 20-35
# minutes waiting on leftover processes (runs 5-7 sat silent after the script's
# own failure until the step timeout / manual cancel). The emulator kill alone
# was not enough — run 8 still hung — so also stop the poster service (a
# background child that survives script exit) and any adb server wedge.
POSTER_PID=""
cleanup() {
  [ -n "$POSTER_PID" ] && kill "$POSTER_PID" 2>/dev/null || true
  adb -s "${ANDROID_SERIAL:-emulator-5554}" emu kill >/dev/null 2>&1 || true
}
trap cleanup EXIT

# --- install the sample app -----------------------------------------------
adb install -r "${SAMPLE_APP_APK:?SAMPLE_APP_APK is not set}"
adb shell pm grant "${SAMPLE_APP_PKG:?}" android.permission.CAMERA
adb shell pm grant "$SAMPLE_APP_PKG" android.permission.POST_NOTIFICATIONS

# --- resolve the SDK root ---------------------------------------------------
# Fall back through the known runner locations so the geometry patch and the
# poster service can find Toren1BD.posters even if ANDROID_HOME is not exported.
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
export ANDROID_HOME="$SDK_ROOT"
export EMULATOR_RESOURCES_DIR="${SDK_ROOT}/emulator/resources"

# --- virtual-scene poster geometry -------------------------------------------
# Patch the Toren1BD scene so the `table` poster sits flat, centered, close to
# the camera (the QR must fill the scanner's view). Keep a backup; the emulator
# reads this file at boot.
RESOURCES="${EMULATOR_RESOURCES_DIR}/Toren1BD.posters"
if [ ! -f "$RESOURCES" ]; then
  echo "::error::Toren1BD.posters not found at $RESOURCES"
  ls "${EMULATOR_RESOURCES_DIR}" || true
  exit 1
fi
cp "$RESOURCES" "$RESOURCES.bak"
python3 .github/scripts/push-e2e/patch-poster-geometry.py

# --- poster service -----------------------------------------------------------
# npm install in a subshell (failure tolerated: node_modules may be cached),
# then start the service as a direct background child so its PID is the node
# process itself and cleanup() can kill it reliably.
( cd .github/scripts/push-e2e && npm install --no-audit --no-fund --ignore-scripts ) || true
( cd .github/scripts/push-e2e && exec node poster-service.mjs > /tmp/poster-service.log 2>&1 ) &
POSTER_PID=$!

for i in $(seq 1 30); do
  curl -sf http://127.0.0.1:4724/health >/dev/null && break
  [ "$i" = 30 ] && { echo "::error::poster service did not start"; cat /tmp/poster-service.log; exit 1; }
  sleep 1
done
echo "poster service up"

# --- flows ---------------------------------------------------------------------
if [ -n "${PUSH_E2E_RUN_USER:-}" ]; then
  # Per-run test user: tenant-user.mjs created it before this step (and deletes it
  # after), so it starts with no devices. No server-side device cleanup is needed,
  # hence no admin token either.
  export PUSH_E2E_USER="$PUSH_E2E_RUN_USER"
  export PUSH_E2E_PASSWORD="${PUSH_E2E_RUN_PASSWORD:?PUSH_E2E_RUN_PASSWORD is not set}"
  unset PUSH_E2E_ADMIN_TOKEN
  echo "using per-run test user ${PUSH_E2E_USER}"
else
  # Fixed test user: mint a fresh admin bearer token from the service account (no
  # long-lived secret to rotate) so the runner can delete the user's leftover push
  # devices. SVCACCT_ID/SVCACCT_JWK come from the step env; the host from PUSH_E2E_HOST.
  export PUSH_E2E_ADMIN_TOKEN="$(
    AIC_HOST="${PUSH_E2E_HOST:?}" \
      node .github/scripts/push-e2e/mint-admin-token.mjs
  )"
fi

# Run the flows in the foreground (NOT exec: exec replaces this shell and the EXIT
# trap — the cleanup — would never fire). The flows' own timeouts bound the run.
# A continuous logcat capture runs alongside: the at-failure `logcat -d` tail
# loses the interesting camera lines to uiautomator's own buffer noise, so the
# evidence has to be streamed to a file the whole run.
# NOTE: under `set -e` a failing top-level command exits the script immediately, so
# the runner's status is captured with `|| STATUS=$?` — the kill + pull below MUST
# run even when the flows fail, or the logcat witness never reaches the artifact
# (that is exactly what happened on the first per-run-user CI run).
adb logcat -c 2>/dev/null || true
adb logcat -v time -f /sdcard/push-e2e-logcat.txt > /dev/null 2>&1 &
LOGCAT_PID=$!
STATUS=0
node .github/scripts/push-e2e/run-push-flows.mjs || STATUS=$?
kill "$LOGCAT_PID" 2>/dev/null || true
adb pull /sdcard/push-e2e-logcat.txt push-e2e-logcat.txt > /dev/null 2>&1 || true
exit "$STATUS"