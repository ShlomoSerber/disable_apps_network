#!/bin/bash
# Fallback without the app: blocks all network of the given packages using the
# kernel firewall chain OEM_DENY_3. Lost on reboot. Undo with adb-unblock.sh.
# Usage: scripts/adb-block.sh com.example.app [more packages...]
set -euo pipefail
[ $# -ge 1 ] || { echo "usage: $0 PACKAGE..."; exit 1; }
adb wait-for-device
adb shell cmd connectivity set-chain3-enabled true
for p in "$@"; do
  adb shell am force-stop "$p"
  adb shell cmd connectivity set-package-networking-enabled false "$p"
done
