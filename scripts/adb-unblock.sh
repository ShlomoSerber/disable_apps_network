#!/bin/bash
# Undoes adb-block.sh for the given packages.
# Usage: scripts/adb-unblock.sh com.example.app [more packages...]
set -euo pipefail
[ $# -ge 1 ] || { echo "usage: $0 PACKAGE..."; exit 1; }
adb wait-for-device
for p in "$@"; do
  adb shell cmd connectivity set-package-networking-enabled true "$p"
done
