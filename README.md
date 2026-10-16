# disable_apps_network

Cuts all network access (Wi-Fi and cellular) of chosen apps on Android, **without root**.
One switch per installed app. Built for the user's TCL 50 Pro NXTPAPER 5G (T803E, Android 16);
nothing in it is TCL-specific.

## How it works

The app is a local VPN that has no upstream. It captures only the blocked apps
(`VpnService.Builder.addAllowedApplication`) and drops every packet they send.
Apps that are not blocked stay outside the VPN and are not touched.

Android allows **one VPN at a time**. While this app is the VPN, another VPN app
on the phone replaces it, and the blocked apps get network back (the app shows a
notification when that happens).

## Install (once)

1. Connect the phone over USB with USB debugging authorized.
2. Build and install:

       scripts/build-apk.sh      # writes ~/Downloads/disable_apps_network.apk
       scripts/install-app.sh    # installs, pre-approves the VPN, sets Always-on, opens the app

3. In **Settings > Network > VPN > Disable Apps Network (gear)**, turn on **Always-on VPN**
   (the app's "VPN settings" button opens that screen). Leave
   **Block connections without VPN** OFF: it would cut the network of every app
   whenever the blocker is down.

Then turn on the switch of each app to block. A change applies in under a second.

## What survives what

| Event | Blocks |
|---|---|
| Reboot | Kept: Android starts the Always-on VPN at boot |
| Update of a blocked app | Kept: the UID does not change |
| Reinstall of a blocked app | Kept: the rule is by package; the service rebuilds the VPN on install |
| Update of this app | Kept: Always-on restarts it |
| Another VPN app connects | **Lost** until you reopen this app and tap Start blocker |
| Uninstall of this app | Lost |

## Limits

- Apps with a system UID (below 10000) cannot be routed into a VPN and are not listed.
  Apps without the `INTERNET` permission are not listed either.
- Packages that share a UID are blocked together.
- Blocking stops new traffic. It does not delete data an app already uploaded.

## Scripts

| Script | What it does |
|---|---|
| `scripts/build-apk.sh` | Builds the debug APK into `~/Downloads/disable_apps_network.apk` |
| `scripts/install-app.sh` | Installs, grants notifications, pre-approves the VPN, sets Always-on, opens the app |
| `scripts/status.sh` | Prints Always-on settings, the VPN network and its UIDs, and the app log |
| `scripts/adb-block.sh PKG...` | Fallback without the app: kernel firewall chain `OEM_DENY_3`. Lost on reboot |
| `scripts/adb-unblock.sh PKG...` | Undoes `adb-block.sh` |
