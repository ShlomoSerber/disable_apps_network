package dev.solodev.disable_apps_network;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_VPN = 1;

    /** One installed app that can use the network. */
    private static final class App {
        final String pkg;
        final String label;
        final boolean system;
        final ApplicationInfo info;

        App(String pkg, String label, boolean system, ApplicationInfo info) {
            this.pkg = pkg;
            this.label = label;
            this.system = system;
            this.info = info;
        }
    }

    private final List<App> all = new ArrayList<>();
    private final List<App> shown = new ArrayList<>();
    private final Map<String, Drawable> icons = new HashMap<>();
    private Set<String> blocked;

    private TextView status;
    private TextView log;
    private EditText search;
    private CheckBox showSystem;
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.status);
        log = findViewById(R.id.log);
        search = findViewById(R.id.search);
        showSystem = findViewById(R.id.show_system);
        blocked = Rules.blocked(this);

        adapter = new Adapter();
        ListView list = findViewById(R.id.apps);
        list.setAdapter(adapter);

        Button start = findViewById(R.id.start);
        start.setOnClickListener(v -> ensureBlocker());

        Button vpnSettings = findViewById(R.id.vpn_settings);
        vpnSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_VPN_SETTINGS)));

        showSystem.setOnCheckedChangeListener((b, on) -> applyFilter());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
        }
        loadApps();
    }

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<App> found = new ArrayList<>();
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                // System UIDs (below 10000) cannot be routed into a VPN; this app is always captured.
                if (ai.uid < Process.FIRST_APPLICATION_UID || ai.packageName.equals(getPackageName())) continue;
                if (pm.checkPermission(Manifest.permission.INTERNET, ai.packageName)
                        != PackageManager.PERMISSION_GRANTED) continue;
                boolean system = (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                found.add(new App(ai.packageName, String.valueOf(ai.loadLabel(pm)), system, ai));
            }
            found.sort(Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
            runOnUiThread(() -> {
                all.clear();
                all.addAll(found);
                applyFilter();
            });
        }, "load-apps").start();
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        boolean sys = showSystem.isChecked();
        shown.clear();
        // Blocked apps first, so the current rules are always visible at the top.
        for (int pass = 0; pass < 2; pass++) {
            for (App a : all) {
                boolean isBlocked = blocked.contains(a.pkg);
                if ((pass == 0) != isBlocked) continue;
                if (a.system && !sys && !isBlocked) continue;
                if (!q.isEmpty() && !a.label.toLowerCase(Locale.ROOT).contains(q)
                        && !a.pkg.toLowerCase(Locale.ROOT).contains(q)) continue;
                shown.add(a);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void toggle(App a, boolean block) {
        Rules.setBlocked(this, a.pkg, block);
        blocked = Rules.blocked(this);
        ensureBlocker();
        refresh();
    }

    /** Asks for VPN consent if needed, then (re)starts the blocker with the current rules. */
    private void ensureBlocker() {
        Intent consent = VpnService.prepare(this);
        if (consent != null) {
            startActivityForResult(consent, REQ_VPN);
        } else {
            BlockVpnService.reload(this);
            status.postDelayed(this::refresh, 500);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_VPN) return;
        if (resultCode == RESULT_OK) {
            BlockVpnService.reload(this);
            status.postDelayed(this::refresh, 500);
        } else {
            Toast.makeText(this, R.string.toast_consent_denied, Toast.LENGTH_LONG).show();
        }
    }

    private final Runnable periodicRefresh = new Runnable() {
        @Override
        public void run() {
            refresh();
            status.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        status.removeCallbacks(periodicRefresh);
        status.post(periodicRefresh);
    }

    @Override
    protected void onPause() {
        status.removeCallbacks(periodicRefresh);
        super.onPause();
    }

    private void refresh() {
        boolean on = BlockVpnService.running;
        String s = getString(R.string.status_vpn, getString(
                        on ? R.string.status_running : R.string.status_stopped)) + '\n'
                + getString(R.string.status_always_on, getString(
                        isAlwaysOn() ? R.string.status_yes : R.string.status_no)) + '\n'
                + getString(R.string.status_blocked, on ? BlockVpnService.blockedCount : 0);
        status.setText(s);
        log.setText(Rules.readLog(this));
    }

    /** Reads the Always-on setting live; falls back to what the service saw at its last start. */
    private boolean isAlwaysOn() {
        try {
            return getPackageName().equals(
                    Settings.Secure.getString(getContentResolver(), "always_on_vpn_app"));
        } catch (SecurityException e) {
            return BlockVpnService.alwaysOn;
        }
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            if (v == null) {
                v = LayoutInflater.from(MainActivity.this).inflate(R.layout.row_app, parent, false);
            }
            App a = shown.get(i);
            Drawable icon = icons.get(a.pkg);
            if (icon == null) {
                icon = a.info.loadIcon(getPackageManager());
                icons.put(a.pkg, icon);
            }
            ((ImageView) v.findViewById(R.id.icon)).setImageDrawable(icon);
            ((TextView) v.findViewById(R.id.label)).setText(a.label);
            ((TextView) v.findViewById(R.id.pkg)).setText(a.pkg);
            Switch sw = v.findViewById(R.id.blocked);
            sw.setOnCheckedChangeListener(null);
            sw.setChecked(blocked.contains(a.pkg));
            sw.setOnCheckedChangeListener((b, on) -> toggle(a, on));
            return v;
        }
    }
}
