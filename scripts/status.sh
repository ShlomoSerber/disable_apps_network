#!/bin/bash
# Prints the VPN state, the Always-on setting and which UIDs the blocker captures.
set -euo pipefail
PKG=dev.solodev.disable_apps_network
adb wait-for-device
if ! adb shell pm list packages | grep -q "package:$PKG\$"; then
  echo "app: not installed (run scripts/install-app.sh)"
  exit 0
fi
echo "app: installed, version $(adb shell dumpsys package $PKG | sed -n 's/.*versionName=//p' | head -1)"
echo "VPN appop: $(adb shell appops get $PKG ACTIVATE_VPN)"
echo "always_on_vpn_app=$(adb shell settings get secure always_on_vpn_app)"
echo "always_on_vpn_lockdown=$(adb shell settings get secure always_on_vpn_lockdown)"
echo "VPN network:"
VPN=$(adb shell dumpsys connectivity | grep -E "NetworkAgentInfo\{.*Transports: [A-Z|]*VPN" || true)
if [ -n "$VPN" ]; then
  echo "$VPN" | grep -oE "InterfaceName: [a-z0-9]+|Uids: <[^>]*>|OwnerUid: [0-9]+" | sed 's/^/  /'
else
  echo "  none (blocker NOT running)"
fi
echo "log (newest first):"
adb logcat -d -s DisableAppsNetwork | tail -10
