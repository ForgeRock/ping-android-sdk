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
cd .github/scripts/push-e2e
npm install --no-audit --no-fund --ignore-scripts
node poster-service.mjs > /tmp/poster-service.log 2>&1 &

for i in $(seq 1 30); do
  curl -sf http://127.0.0.1:4724/health >/dev/null && break
  [ "$i" = 30 ] && { echo "::error::poster service did not start"; cat /tmp/poster-service.log; exit 1; }
  sleep 1
done
echo "poster service up"

# --- flows ---------------------------------------------------------------------
# Mint a fresh admin bearer token from the service account (no long-lived
# secret to rotate): JWT assertion -> OAuth2 exchange. SVCACCT_ID/SVCACCT_JWK
# come from the step env; the tenant host comes from PUSH_E2E_HOST.
export PUSH_E2E_ADMIN_TOKEN="$(
  AIC_HOST="${PUSH_E2E_HOST:?}" \
    node .github/scripts/push-e2e/mint-admin-token.mjs
)"

exec node .github/scripts/push-e2e/run-push-flows.mjs
