package dev.solodev.disable_apps_network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** The block list (package names) and a small log ring, both in SharedPreferences. */
final class Rules {
    static final String TAG = "DisableAppsNetwork";
    private static final String PREFS = "rules";
    private static final String KEY_BLOCKED = "blocked";
    private static final String KEY_LOG = "log";
    private static final int LOG_LINES = 40;

    private Rules() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Set<String> blocked(Context c) {
        return new HashSet<>(prefs(c).getStringSet(KEY_BLOCKED, new HashSet<>()));
    }

    static boolean isBlocked(Context c, String pkg) {
        return blocked(c).contains(pkg);
    }

    static void setBlocked(Context c, String pkg, boolean block) {
        Set<String> s = blocked(c);
        if (block ? s.add(pkg) : s.remove(pkg)) {
            prefs(c).edit().putStringSet(KEY_BLOCKED, s).commit();
            log(c, (block ? "block " : "allow ") + pkg);
        }
    }

    static synchronized void log(Context c, String msg) {
        Log.i(TAG, msg);
        String ts = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
        String old = prefs(c).getString(KEY_LOG, "");
        String all = ts + "  " + msg + (old.isEmpty() ? "" : "\n" + old);
        String[] lines = all.split("\n");
        if (lines.length > LOG_LINES) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < LOG_LINES; i++) {
                if (i > 0) sb.append('\n');
                sb.append(lines[i]);
            }
            all = sb.toString();
        }
        prefs(c).edit().putString(KEY_LOG, all).apply();
    }

    static String readLog(Context c) {
        return prefs(c).getString(KEY_LOG, "");
    }
}
