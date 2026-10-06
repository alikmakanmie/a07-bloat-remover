package id.dhodi.bloatremover;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // Status paket
    static final int AKTIF = 0, NONAKTIF = 1, TERCOPOT = 2, TIDAK_ADA = 3;

    static class Entry {
        final String pkg;
        final int tier;
        int state = TIDAK_ADA;
        String label = null;
        Entry(String pkg, int tier) { this.pkg = pkg; this.tier = tier; }
    }

    // Daftar bloat terkurasi (sama dgn module A07 Debloat).
    // Tier 1 = aman. Tier 2 = opsional.
    static final String[][] TIER1 = {
        {"com.samsung.android.bixby.agent", "Bixby Voice"},
        {"com.samsung.android.bixby.agent.dummy", "Bixby Voice (dummy)"},
        {"com.samsung.android.visionintelligence", "Bixby Vision"},
        {"com.samsung.android.bixbyvision.framework", "Bixby Vision Framework"},
        {"com.samsung.android.arzone", "AR Zone"},
        {"com.samsung.android.ardrawing", "AR Doodle"},
        {"com.samsung.android.aremoji", "AR Emoji"},
        {"com.samsung.android.aremoji.editor", "AR Emoji Editor"},
        {"com.samsung.android.arsticker", "AR Emoji Sticker"},
        {"com.samsung.android.livelens", "Deco Pic"},
        {"com.samsung.android.app.spage", "Samsung Free"},
        {"com.samsung.android.app.tips", "Samsung Tips"},
        {"com.samsung.android.visitin", "Samsung Visit In"},
        {"com.samsung.android.kidsinstaller", "Samsung Kids (pemasang)"},
        {"com.facebook.system", "Facebook App Installer (stub)"},
        {"com.facebook.appmanager", "Facebook App Manager (stub)"},
        {"com.facebook.services", "Facebook Services (stub)"},
        {"com.netflix.partner.activation", "Netflix Activation (stub)"},
    };
    static final String[][] TIER2 = {
        {"com.samsung.android.app.routines", "Modes and Routines"},
        {"com.samsung.android.themestore", "Galaxy Themes"},
        {"com.samsung.android.oneconnect", "SmartThings"},
        {"com.samsung.android.beaconmanager", "SmartThings Beacon"},
        {"com.microsoft.appmanager", "Link to Windows"},
        {"com.samsung.android.mdx", "Link to Windows (MDX)"},
        {"com.microsoft.skydrive", "OneDrive"},
        {"com.samsung.android.scloud", "Samsung Cloud"},
        {"com.sec.spp.push", "Samsung Push Service"},
        {"com.samsung.android.game.gamehome", "Game Launcher"},
        {"com.samsung.android.game.gametools", "Game Tools"},
        {"com.sec.android.app.shealth", "Samsung Health"},
        {"com.samsung.android.app.watchmanager", "Galaxy Wearable"},
        {"com.samsung.oh", "Samsung Members"},
        {"com.sec.android.app.sbrowser", "Samsung Internet"},
        {"com.google.android.apps.magazines", "Google News"},
        {"com.google.android.videos", "Google TV"},
    };

    // Penjaga: paket ini TIDAK PERNAH boleh disentuh aplikasi ini.
    static final String[] LARANGAN = {
        "com.samsung.android.lool", "com.samsung.android.honeyboard",
        "com.sec.android.app.launcher", "com.android.settings",
        "com.android.systemui", "com.android.phone",
        "com.samsung.android.fmm", "com.sec.android.app.samsungapps",
        "com.samsung.android.samsungpass", "com.samsung.android.authfw",
        "com.samsung.android.game.gos", "com.android.packageinstaller",
        "com.google.android.packageinstaller", "id.dhodi.bloatremover"
    };

    final List<Entry> entries = new ArrayList<>();
    final ExecutorService exec = Executors.newSingleThreadExecutor();
    ArrayAdapter<Entry> adapter;
    TextView tvRoot;
    ProgressBar progress;
    boolean rootOk = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvRoot = findViewById(R.id.tvRoot);
        progress = findViewById(R.id.progress);
        ListView list = findViewById(R.id.list);

        for (String[] r : TIER1) entries.add(new Entry(r[0], 1));
        for (String[] r : TIER2) entries.add(new Entry(r[0], 2));

        adapter = new ArrayAdapter<Entry>(this, 0, entries) {
            @Override
            public View getView(int pos, View cv, ViewGroup parent) {
                if (cv == null) cv = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_row, parent, false);
                Entry e = getItem(pos);
                TextView tvName = cv.findViewById(R.id.tvName);
                TextView tvPkg = cv.findViewById(R.id.tvPkg);
                TextView tvStatus = cv.findViewById(R.id.tvStatus);
                Button btnMain = cv.findViewById(R.id.btnMain);
                Button btnRemove = cv.findViewById(R.id.btnRemove);
                tvName.setText(e.label != null ? e.label : e.pkg);
                tvPkg.setText(e.pkg + "  -  Tier " + e.tier + (e.tier == 1 ? " (aman)" : " (opsional)"));
                String st; int color;
                switch (e.state) {
                    case AKTIF: st = "Status: Aktif"; color = Color.rgb(46, 125, 50); break;
                    case NONAKTIF: st = "Status: Nonaktif"; color = Color.rgb(230, 126, 34); break;
                    case TERCOPOT: st = "Status: Tercopot dari pengguna"; color = Color.rgb(192, 57, 43); break;
                    default: st = "Status: Tidak terpasang"; color = Color.GRAY;
                }
                tvStatus.setText(st);
                tvStatus.setTextColor(color);
                btnMain.setEnabled(rootOk && e.state != TIDAK_ADA);
                btnRemove.setVisibility((e.state == AKTIF || e.state == NONAKTIF) ? View.VISIBLE : View.GONE);
                btnRemove.setEnabled(rootOk);
                switch (e.state) {
                    case AKTIF: btnMain.setText("Nonaktifkan"); break;
                    case NONAKTIF: btnMain.setText("Aktifkan"); break;
                    case TERCOPOT: btnMain.setText("Pasang Lagi"); break;
                    default: btnMain.setText("-");
                }
                btnMain.setOnClickListener(v -> aksiUtama(e));
                btnRemove.setOnClickListener(v -> konfirmasiCopot(e));
                return cv;
            }
        };
        list.setAdapter(adapter);

        findViewById(R.id.btnRefresh).setOnClickListener(v -> muat());
        findViewById(R.id.btnTier1).setOnClickListener(v -> konfirmasiBatch(true));
        findViewById(R.id.btnRestore).setOnClickListener(v -> konfirmasiBatch(false));
        muat();
    }

    boolean terlarang(String pkg) {
        for (String l : LARANGAN) if (l.equals(pkg)) return true;
        return pkg.contains("knox");
    }

    void sibuk(boolean b) {
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
    }

    void muat() {
        sibuk(true);
        exec.execute(() -> {
            // Cek root
            boolean ok = false;
            try {
                String out = runSu("id");
                ok = out.contains("uid=0");
            } catch (Exception ignored) {}
            rootOk = ok;
            // Kumpulkan status paket
            Set<String> semua = new HashSet<>(), utkUser = new HashSet<>(), nonaktif = new HashSet<>();
            try {
                semua.addAll(baris(runSu("pm list packages -u")));
                utkUser.addAll(baris(runSu("pm list packages")));
                nonaktif.addAll(baris(runSu("pm list packages -d")));
            } catch (Exception ignored) {}
            PackageManager pm = getPackageManager();
            for (Entry e : entries) {
                if (!semua.contains(e.pkg)) e.state = TIDAK_ADA;
                else if (!utkUser.contains(e.pkg)) e.state = TERCOPOT;
                else if (nonaktif.contains(e.pkg)) e.state = NONAKTIF;
                else e.state = AKTIF;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(e.pkg,
                            PackageManager.MATCH_UNINSTALLED_PACKAGES);
                    e.label = pm.getApplicationLabel(ai).toString();
                } catch (Exception ignored) {
                    if (e.label == null) {
                        for (String[] r : TIER1) if (r[0].equals(e.pkg)) e.label = r[1];
                        for (String[] r : TIER2) if (r[0].equals(e.pkg)) e.label = r[1];
                    }
                }
            }
            runOnUiThread(() -> {
                tvRoot.setText(rootOk
                        ? "Root: AKTIF (KernelSU) - aplikasi siap bekerja."
                        : "Root: TIDAK AKTIF - buka KernelSU, pastikan status Berfungsi, lalu izinkan aplikasi ini di tab Superuser.");
                tvRoot.setTextColor(rootOk ? Color.rgb(46, 125, 50) : Color.rgb(192, 57, 43));
                adapter.notifyDataSetChanged();
                sibuk(false);
            });
        });
    }

    static Set<String> baris(String out) {
        Set<String> s = new HashSet<>();
        if (out == null) return s;
        for (String l : out.split("\n")) {
            l = l.trim();
            if (l.startsWith("package:")) s.add(l.substring(8).trim());
        }
        return s;
    }

    static String runSu(String cmd) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        p.waitFor();
        return sb.toString();
    }

    void jalankan(String cmd, String pesan) {
        sibuk(true);
        exec.execute(() -> {
            String hasil;
            try { hasil = runSu(cmd); } catch (Exception ex) { hasil = "ERROR: " + ex.getMessage(); }
            final String h = hasil;
            runOnUiThread(() -> {
                Toast.makeText(this, pesan, Toast.LENGTH_SHORT).show();
                if (h.contains("Exception") || h.contains("ERROR"))
                    Toast.makeText(this, h.trim(), Toast.LENGTH_LONG).show();
                muat();
            });
        });
    }

    void aksiUtama(Entry e) {
        if (terlarang(e.pkg)) { Toast.makeText(this, "Paket dilindungi.", Toast.LENGTH_SHORT).show(); return; }
        switch (e.state) {
            case AKTIF:
                jalankan("pm disable " + e.pkg, "Dinonaktifkan: " + e.pkg);
                break;
            case NONAKTIF:
                jalankan("pm enable " + e.pkg, "Diaktifkan: " + e.pkg);
                break;
            case TERCOPOT:
                jalankan("pm install-existing " + e.pkg, "Dipasang lagi: " + e.pkg);
                break;
        }
    }

    void konfirmasiCopot(Entry e) {
        new AlertDialog.Builder(this)
                .setTitle("Copot dari pengguna?")
                .setMessage(e.pkg + "\n\nAplikasi dicopot untuk pengguna ini (hilang dari HP), tapi file aslinya tetap di sistem dan bisa dipasang lagi dari aplikasi ini kapan pun.")
                .setPositiveButton("Copot", (d, w) ->
                        jalankan("pm uninstall --user 0 " + e.pkg, "Dicopot: " + e.pkg))
                .setNegativeButton("Batal", null)
                .show();
    }

    void konfirmasiBatch(boolean nonaktifkanTier1) {
        String judul = nonaktifkanTier1 ? "Nonaktifkan semua Tier 1?" : "Pulihkan semua paket?";
        String isi = nonaktifkanTier1
                ? "Semua aplikasi Tier 1 (aman) yang terpasang & aktif akan dinonaktifkan. Bisa dikembalikan dengan tombol Pulihkan Semua."
                : "Semua paket di daftar yang tercopot akan dipasang lagi, dan yang nonaktif akan diaktifkan kembali.";
        new AlertDialog.Builder(this)
                .setTitle(judul)
                .setMessage(isi)
                .setPositiveButton("Ya", (d, w) -> {
                    StringBuilder cmd = new StringBuilder();
                    for (Entry e : entries) {
                        if (terlarang(e.pkg)) continue;
                        if (nonaktifkanTier1) {
                            if (e.tier == 1 && e.state == AKTIF)
                                cmd.append("pm disable ").append(e.pkg).append("; ");
                        } else {
                            cmd.append("pm install-existing ").append(e.pkg).append(" >/dev/null 2>&1; ");
                            cmd.append("pm enable ").append(e.pkg).append(" >/dev/null 2>&1; ");
                        }
                    }
                    if (cmd.length() == 0) {
                        Toast.makeText(this, "Tidak ada yang perlu dilakukan.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    jalankan(cmd.toString(), nonaktifkanTier1 ? "Tier 1 dinonaktifkan." : "Semua dipulihkan.");
                })
                .setNegativeButton("Batal", null)
                .show();
    }
}
