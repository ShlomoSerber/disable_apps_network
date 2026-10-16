#!/bin/bash
# Builds the debug APK and copies it to ~/Downloads/disable_apps_network.apk.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew --quiet assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk ~/Downloads/disable_apps_network.apk
echo "APK: ~/Downloads/disable_apps_network.apk"
