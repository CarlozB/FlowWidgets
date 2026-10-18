#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -f /workspace/toolchains/activate.sh ]; then
  source /workspace/toolchains/activate.sh
fi
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest --console=plain
"${ANDROID_HOME:?Set ANDROID_HOME}/build-tools/36.0.0/apksigner" verify --verbose app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c 4 app/build/outputs/apk/debug/app-debug.apk
