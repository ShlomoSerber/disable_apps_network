#!/bin/bash
# Installs (or updates) the app on the connected phone and sets it up:
# - grants notifications,
# - pre-approves the VPN (no consent dialog),
# - registers it as the Always-on VPN without lockdown. Android reads this setting
#   at boot; to turn it on right now, use Settings > VPN > gear > Always-on VPN.
# Everything survives reboots and updates of this app, not an uninstall.
set -euo pipefail
PKG=dev.solodev.disable_apps_network
APK=${1:-$HOME/Downloads/disable_apps_network.apk}
adb wait-for-device
adb install -r "$APK"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell appops set $PKG ACTIVATE_VPN allow
adb shell settings put secure always_on_vpn_app $PKG
adb shell settings put secure always_on_vpn_lockdown 0
adb shell am start -n $PKG/.MainActivity >/dev/null
echo "Installed. Turn on the switch of each app to block, and Always-on VPN in Settings > VPN."
