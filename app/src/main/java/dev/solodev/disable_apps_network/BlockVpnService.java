package dev.solodev.disable_apps_network;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Set;

/**
 * A local VPN that captures only the blocked apps and drops every packet they send.
 * The VPN has no upstream: nothing is forwarded, so captured apps have no network at all.
 * Apps that are not blocked stay outside the VPN and use Wi-Fi or cellular as usual.
 */
public class BlockVpnService extends VpnService {
    static final String ACTION_RELOAD = "dev.solodev.disable_apps_network.RELOAD";
    private static final String CHANNEL = "alerts";
    private static final int NOTIF_REVOKED = 1;

    static volatile boolean running;
    static volatile boolean alwaysOn;
    static volatile int blockedCount;

    private ParcelFileDescriptor tun;
    private Drain drain;

    /** Reinstalling a blocked app gives it a new UID; the VPN must be rebuilt to cover it. */
    private final BroadcastReceiver packageAdded = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Uri data = i.getData();
            if (data != null && Rules.isBlocked(c, data.getSchemeSpecificPart())) {
                establish("package added: " + data.getSchemeSpecificPart());
            }
        }
    };

    static void reload(Context c) {
        c.startService(new Intent(c, BlockVpnService.class).setAction(ACTION_RELOAD));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter f = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        f.addDataScheme("package");
        registerReceiver(packageAdded, f, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (prepare(this) != null) {
            Rules.log(this, "no VPN consent; open the app and tap Start blocker");
            stopSelf();
            return START_NOT_STICKY;
        }
        String why = intent == null ? "restart" : String.valueOf(intent.getAction());
        establish(why);
        return START_STICKY;
    }

    private synchronized void establish(String why) {
        Set<String> blocked = Rules.blocked(this);
        Builder b = new Builder()
                .setSession(getString(R.string.app_name))
                .addAddress("10.111.222.1", 32)
                .addAddress("fd00:6461:6e00::1", 128)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("10.111.222.2")
                .setBlocking(false);
        int n = 0;
        try {
            // Never leave the allow list empty: an empty list captures every app on the phone.
            b.addAllowedApplication(getPackageName());
        } catch (PackageManager.NameNotFoundException impossible) {
            throw new IllegalStateException(impossible);
        }
        for (String pkg : blocked) {
            try {
                b.addAllowedApplication(pkg);
                n++;
            } catch (PackageManager.NameNotFoundException e) {
                // Not installed now; keep the rule so a reinstall stays blocked.
            }
        }

        ParcelFileDescriptor next = b.establish();
        if (next == null) {
            Rules.log(this, "establish failed (no consent?)");
            running = false;
            return;
        }
        ParcelFileDescriptor old = tun;
        Drain oldDrain = drain;
        tun = next;
        drain = new Drain(next.getFileDescriptor());
        new Thread(drain, "drain").start();
        if (oldDrain != null) oldDrain.stopped = true;
        closeQuietly(old);

        running = true;
        alwaysOn = isAlwaysOn();
        blockedCount = n;
        getSystemService(NotificationManager.class).cancel(NOTIF_REVOKED);
        Rules.log(this, "blocking " + n + " app(s) [" + why + "]");
    }

    @Override
    public void onRevoke() {
        Rules.log(this, "VPN revoked (another VPN or the user turned it off)");
        shutdown();
        notifyRevoked();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(packageAdded);
        shutdown();
        super.onDestroy();
    }

    private synchronized void shutdown() {
        running = false;
        if (drain != null) drain.stopped = true;
        drain = null;
        closeQuietly(tun);
        tun = null;
    }

    private void notifyRevoked() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_HIGH));
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(getString(R.string.notif_revoked_title))
                .setContentText(getString(R.string.notif_revoked_text))
                .setStyle(new Notification.BigTextStyle().bigText(getString(R.string.notif_revoked_text)))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        nm.notify(NOTIF_REVOKED, n);
    }

    private static void closeQuietly(ParcelFileDescriptor pfd) {
        if (pfd == null) return;
        try {
            pfd.close();
        } catch (IOException ignored) {
        }
    }

    /** Reads and discards packets so the tun queue never fills up. Stops when told or when the fd closes. */
    private static final class Drain implements Runnable {
        private final FileDescriptor fd;
        volatile boolean stopped;

        Drain(FileDescriptor fd) {
            this.fd = fd;
        }

        @Override
        public void run() {
            byte[] buf = new byte[32767];
            StructPollfd p = new StructPollfd();
            p.fd = fd;
            p.events = (short) OsConstants.POLLIN;
            StructPollfd[] fds = {p};
            while (!stopped) {
                try {
                    if (Os.poll(fds, 1000) > 0 && (p.revents & OsConstants.POLLIN) != 0) {
                        while (Os.read(fd, buf, 0, buf.length) > 0) {
                            // Dropped.
                        }
                    }
                    if ((p.revents & (OsConstants.POLLERR | OsConstants.POLLHUP | OsConstants.POLLNVAL)) != 0) {
                        return;
                    }
                } catch (ErrnoException e) {
                    if (e.errno != OsConstants.EAGAIN && e.errno != OsConstants.EINTR) return;
                } catch (java.io.InterruptedIOException e) {
                    return;
                }
            }
        }
    }
}
