# disable_apps_network — harness notes

Read `README.md` first for user-facing behavior.

Goal: per-app switch that cuts all network of any installed app on the user's
TCL T803E (Android 16), **without root**.

## Why a VPN

The kernel firewall commands (`cmd connectivity set-package-networking-enabled`,
chain `OEM_DENY_3`) work from adb but are lost on reboot. The APIs behind them need
`NETWORK_SETTINGS` / `MANAGE_NETWORK_POLICY`, which are `prot=signature` on this phone,
so an app cannot reapply them. A blackhole `VpnService` set as Always-on is the only
persistent no-root mechanism. `adb shell cmd vpn_management` has no shell commands.

## Layout

| Path | What |
|---|---|
| `app/src/main/java/dev/solodev/disable_apps_network/Rules.java` | Block list (package names) and log ring in SharedPreferences |
| `.../BlockVpnService.java` | Builds the VPN with the blocked apps, drains and drops packets, rebuilds on reload and on `PACKAGE_ADDED` |
| `.../BootReceiver.java` | Fallback start on boot and on update of this app |
| `.../MainActivity.java` | App list with switches, search, system-apps filter, status, log |
| `app/build.gradle.kts` | **Version lives here** |
| `scripts/` | build, install, status, adb fallback |

## Invariants

1. **No root.**
2. **The allow list is never empty.** The service always adds its own package. An empty
   `addAllowedApplication` list would capture (and black-hole) every app on the phone.
3. **Route both `0.0.0.0/0` and `::/0`**, or blocked apps leak over IPv6.
4. **Never call `allowBypass()`.**
5. **Never enable lockdown** (`always_on_vpn_lockdown`): it cuts every app when the VPN is down.
6. **No `INTERNET` permission** for this app.
7. Keep the package name `dev.solodev.disable_apps_network`.

Conventions (same as `../disable_nxtpaper_key`): plain Java, no AndroidX; UI text only in
`strings.xml`; everything in English; scripts `set -euo pipefail`; bump `versionCode` and
`versionName` for every build the user installs. The user writes in Spanish (voseo).

## Verify a block

```
scripts/status.sh                                  # Uids: <{...}> must include the app's appId
adb shell dumpsys package PKG | grep appId
adb shell cat /proc/net/dev | grep tun0            # tx packets grow when a blocked app tries
adb logcat -d -s DisableAppsNetwork
```
Verified 2026-10-16 with `com.inotesapp.inotes` (appId 10209): with the adb firewall off,
opening the app sent 13 packets into tun0 and zero bytes over Wi-Fi or cellular.
