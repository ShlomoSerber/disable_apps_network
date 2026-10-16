package dev.solodev.disable_apps_network;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;

/**
 * Fallback start after boot and after an update of this app.
 * With Always-on VPN set, Android starts the service by itself; starting twice is harmless.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (VpnService.prepare(c) != null) {
            Rules.log(c, "boot: no VPN consent, blocker not started");
            return;
        }
        BlockVpnService.reload(c);
    }
}
