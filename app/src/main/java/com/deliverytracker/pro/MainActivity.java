package com.deliverytracker.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;

public class MainActivity extends Activity {
    SQLiteDatabase db;
    FrameLayout root;
    LinearLayout vTrk, vPrf, vCrd;
    Button bT, bP, b1, b2, b3;
    TextView tTopConv, tTopDnpc, tCnt;
    ArrayList<String[]> ords = new ArrayList<>();
    BaseAdapter adp;
    String mode = "daily";
    String CSV = "https://docs.google.com/spreadsheets/d/1Dul38iNZ_eNmABVuYVWhrUg9F_xVMvaVvQvLIXlySj4/export?format=csv";

    GradientDrawable box(int c, int r, int sCol, int sW) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c);
        g.setCornerRadius(r);
        if (sW > 0) g.setStroke(sW, sCol);
        return g;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        try {
            db = openOrCreateDatabase("TrackerV10.db", MODE_PRIVATE, null);
            db.execSQL("CREATE TABLE IF NOT EXISTS ord (t TEXT UNIQUE, d TEXT);");
            db.execSQL("CREATE TABLE IF NOT EXISTS prf (n TEXT, o INT, l INT, p INT, k INT, dt TEXT);");
        } catch (Exception ignored) {}

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0F1015"));

        // Exact Middle Splash Screen
        LinearLayout splash = new LinearLayout(this);
        splash.setOrientation(LinearLayout.VERTICAL);
        splash.setGravity(Gravity.CENTER);
        splash.setBackgroundColor(Color.parseColor("#0F1015"));
        splash.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

        TextView icon = new TextView(this);
        icon.setText("⚡");
        icon.setTextSize(48f);
        icon.setGravity(Gravity.CENTER);
        splash.addView(icon);

        TextView title = new TextView(this);
        title.setText("Delivery Tracker Pro");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 12, 0, 8);
        splash.addView(title);

        TextView mg = new TextView(this);
        mg.setText("MANAGED BY ADARSH");
        mg.setTextColor(Color.parseColor("#00E676"));
        mg.setTextSize(14f);
        mg.setTypeface(Typeface.DEFAULT_BOLD);
        mg.setGravity(Gravity.CENTER);
        splash.addView(mg);

        root.addView(splash);
        setContentView(root);

        // Auto Sync on fresh open / new install
        new Thread(() -> doSync(true)).start();

        new Handler().postDelayed(() -> {
            try {
                root.removeView(splash);
                buildUI();
            } catch (Exception ignored) {}
        }, 1800);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Auto Sync whenever app is reopened from background
        new Thread(() -> doSync(true)).start();
    }

    void buildUI() {
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);

        // Header: Title + One-Click Public Sync (No Password)
        LinearLayout h = new LinearLayout(this);
        h.setBackgroundColor(Color.parseColor("#181920"));
        h.setPadding(20, 16, 20, 16);
        h.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText("📊 Performance & Leaderboard");
        t.setTextColor(Color.WHITE);
        t.setTextSize(16f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        h.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));

        Button bRef = new Button(this);
        bRef.setText("🔄 Sync");
        bRef.setTextSize(12f);
        bRef.setTextColor(Color.BLACK);
        bRef.setTypeface(Typeface.DEFAULT_BOLD);
        bRef.setBackground(box(Color.parseColor("#00E676"), 10, 0, 0));
        bRef.setOnClickListener(v -> new Thread(() -> doSync(false)).start());
        h.addView(bRef);
        main.addView(h);

        // Tabs
        LinearLayout tb = new LinearLayout(this);
        tb.setPadding(16, 8, 16, 4);
        bT = new Button(this);
        bT.setText("🔍 Tracker");
        bT.setBackground(box(Color.parseColor("#232634"), 12, 0, 0));
        bT.setTextColor(Color.parseColor("#8E92A4"));

        bP = new Button(this);
        bP.setText("📈 Performance");
        bP.setBackground(box(Color.parseColor("#00E676"), 12, 0, 0));
        bP.setTextColor(Color.BLACK);
        bP.setTypeface(Typeface.DEFAULT_BOLD);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(3, 0, 3, 0);
        tb.addView(bT, lp);
        tb.addView(bP, new LinearLayout.LayoutParams(lp));
        main.addView(tb);

        FrameLayout body = new FrameLayout(this);
        body.setPadding(16, 6, 16, 10);
        main.addView(body, new LinearLayout.LayoutParams(-1, -1));

        // Tracker Tab (Blue Track ID & Green Order ID)
        vTrk = new LinearLayout(this);
        vTrk.setOrientation(LinearLayout.VERTICAL);
        vTrk.setVisibility(View.GONE);

        EditText s = new EditText(this);
        s.setHint("🔍 Search full or last 4-5 digits...");
        s.setHintTextColor(Color.parseColor("#717688"));
        s.setTextColor(Color.WHITE);
        s.setTextSize(16f);
        s.setBackground(box(Color.parseColor("#181920"), 14, Color.parseColor("#00E676"), 1));
        s.setPadding(22, 18, 22, 18);
        s.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int i, int i1, int i2) {}
            public void onTextChanged(CharSequence c, int i, int i1, int i2) { qry(c.toString().trim()); }
            public void afterTextChanged(Editable e) {}
        });
        vTrk.addView(s);

        tCnt = new TextView(this);
        tCnt.setTextColor(Color.parseColor("#00E676"));
        tCnt.setPadding(6, 10, 6, 8);
        tCnt.setTextSize(13f);
        tCnt.setTypeface(Typeface.DEFAULT_BOLD);
        vTrk.addView(tCnt);

        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setDividerHeight(10);
        adp = new BaseAdapter() {
            public int getCount() { return ords.size(); }
            public Object getItem(int i) { return ords.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) {
                LinearLayout c = new LinearLayout(MainActivity.this);
                c.setOrientation(LinearLayout.VERTICAL);
                c.setPadding(20, 16, 20, 16);
                c.setBackground(box(Color.parseColor("#181920"), 14, Color.parseColor("#2A2D3D"), 1));
                String[] it = ords.get(i);

                TextView t1 = new TextView(MainActivity.this);
                t1.setText("📦 Track ID: " + it[0]);
                t1.setTextColor(Color.parseColor("#38BDF8")); // Blue
                t1.setTypeface(Typeface.DEFAULT_BOLD);
                t1.setTextSize(14.5f);

                TextView t2 = new TextView(MainActivity.this);
                t2.setText("🛒 Order ID: " + it[1] + "  📋 (Tap to Copy)");
                t2.setTextColor(Color.parseColor("#00E676")); // Green
                t2.setTextSize(13.5f);
                t2.setPadding(0, 6, 0, 0);

                c.addView(t1);
                c.addView(t2);
                c.setOnClickListener(vw -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("ID", it[1]));
                    Toast.makeText(MainActivity.this, "Copied: " + it[1], Toast.LENGTH_SHORT).show();
                });
                return c;
            }
        };
        lv.setAdapter(adp);
        vTrk.addView(lv, new LinearLayout.LayoutParams(-1, -1));
        body.addView(vTrk);

        vPrf = new LinearLayout(this);
        vPrf.setOrientation(LinearLayout.VERTICAL);

        LinearLayout fl = new LinearLayout(this);
        b1 = flt("📅 Daily", "daily");
        b2 = flt("📆 Weekly", "weekly");
        b3 = flt("🗓️ Monthly", "monthly");
        LinearLayout.LayoutParams lpF = new LinearLayout.LayoutParams(0, -2, 1f);
        lpF.setMargins(2, 0, 2, 8);
        fl.addView(b1, lpF);
        fl.addView(b2, new LinearLayout.LayoutParams(lpF));
        fl.addView(b3, new LinearLayout.LayoutParams(lpF));
        vPrf.addView(fl);

        LinearLayout sm = new LinearLayout(this);
        sm.setPadding(0, 2, 0, 8);
        LinearLayout sc1 = makeSummaryCard("🏆 HIGHEST CONVERSION", Color.parseColor("#181920"), Color.parseColor("#00E676"), true);
        LinearLayout sc2 = makeSummaryCard("📦 HIGHEST DNPC", Color.parseColor("#181920"), Color.parseColor("#FB923C"), false);
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(0, -2, 1f);
        scLp.setMargins(3, 0, 3, 0);
        sm.addView(sc1, scLp);
        sm.addView(sc2, new LinearLayout.LayoutParams(scLp));
        vPrf.addView(sm);

        ScrollView sv = new ScrollView(this);
        vCrd = new LinearLayout(this);
        vCrd.setOrientation(LinearLayout.VERTICAL);
        sv.addView(vCrd);
        vPrf.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        body.addView(vPrf);

        bT.setOnClickListener(v -> {
            vTrk.setVisibility(View.VISIBLE);
            vPrf.setVisibility(View.GONE);
            bT.setBackground(box(Color.parseColor("#00E676"), 12, 0, 0));
            bT.setTextColor(Color.BLACK);
            bP.setBackground(box(Color.parseColor("#232634"), 12, 0, 0));
            bP.setTextColor(Color.parseColor("#8E92A4"));
            cnt();
        });
        bP.setOnClickListener(v -> {
            vTrk.setVisibility(View.GONE);
            vPrf.setVisibility(View.VISIBLE);
            bP.setBackground(box(Color.parseColor("#00E676"), 12, 0, 0));
            bP.setTextColor(Color.BLACK);
            bT.setBackground(box(Color.parseColor("#232634"), 12, 0, 0));
            bT.setTextColor(Color.parseColor("#8E92A4"));
            load();
        });

        root.addView(main);
        load();
        cnt();
                            }
    LinearLayout makeSummaryCard(String title, int bgCol, int accent, boolean isConv) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(box(bgCol, 14, Color.parseColor("#2A2D3D"), 1));
        c.setPadding(16, 14, 16, 14);
        TextView h = new TextView(this);
        h.setText(title);
        h.setTextColor(Color.parseColor("#9CA3AF"));
        h.setTextSize(10.5f);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(h);
        TextView v = new TextView(this);
        v.setTextColor(accent);
        v.setTextSize(13f);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, 4, 0, 0);
        c.addView(v);
        if (isConv) tTopConv = v; else tTopDnpc = v;
        return c;
    }

    Button flt(String txt, String m) {
        Button b = new Button(this);
        b.setText(txt);
        b.setBackground(box(m.equals(mode) ? Color.parseColor("#00E676") : Color.parseColor("#181920"), 10, 0, 0));
        b.setTextColor(m.equals(mode) ? Color.BLACK : Color.parseColor("#9CA3AF"));
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setOnClickListener(v -> {
            mode = m;
            b1.setBackground(box("daily".equals(m) ? Color.parseColor("#00E676") : Color.parseColor("#181920"), 10, 0, 0));
            b1.setTextColor("daily".equals(m) ? Color.BLACK : Color.parseColor("#9CA3AF"));
            b2.setBackground(box("weekly".equals(m) ? Color.parseColor("#00E676") : Color.parseColor("#181920"), 10, 0, 0));
            b2.setTextColor("weekly".equals(m) ? Color.BLACK : Color.parseColor("#9CA3AF"));
            b3.setBackground(box("monthly".equals(m) ? Color.parseColor("#00E676") : Color.parseColor("#181920"), 10, 0, 0));
            b3.setTextColor("monthly".equals(m) ? Color.BLACK : Color.parseColor("#9CA3AF"));
            load();
        });
        return b;
    }

    View makePill(String label, String val, int valColor) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setBackground(box(Color.parseColor("#232634"), 8, 0, 0));
        p.setPadding(12, 8, 12, 8);
        p.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = new TextView(this);
        l.setText(label + ": ");
        l.setTextColor(Color.parseColor("#9CA3AF"));
        l.setTextSize(12f);
        p.addView(l);
        TextView v = new TextView(this);
        v.setText(val);
        v.setTextColor(valColor);
        v.setTextSize(12.5f);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        p.addView(v);
        return p;
    }

    void load() {
        try {
            vCrd.removeAllViews();
            String w = "daily".equals(mode) ? " WHERE dt = (SELECT MAX(dt) FROM prf) " : ("weekly".equals(mode) ? " WHERE dt >= date('now','localtime','-7 days') " : " WHERE dt >= date('now','localtime','-30 days') ");
            Cursor ac = db.rawQuery("SELECT n, SUM(o), SUM(l), SUM(p), SUM(k) FROM prf " + w + " GROUP BY n", null);
            ArrayList<String[]> list = new ArrayList<>();
            String bestConvName = "None", bestDnpcName = "None";
            double maxConv = -1;
            int maxDnpc = -1;

            while (ac != null && ac.moveToNext()) {
                String name = ac.getString(0);
                int o = ac.getInt(1), l = ac.getInt(2), p = ac.getInt(3), k = ac.getInt(4);
                int dnp = o + p, dnpc = l + k;
                double r = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;
                list.add(new String[]{name, String.valueOf(o), String.valueOf(l), String.valueOf(p), String.valueOf(k), String.valueOf(dnp), String.valueOf(dnpc), String.format(Locale.US, "%.1f", r), String.valueOf(r)});
                if (r > maxConv && dnp > 0) { maxConv = r; bestConvName = name + "\n" + String.format(Locale.US, "%.1f%%", r); }
                if (dnpc > maxDnpc) { maxDnpc = dnpc; bestDnpcName = name + "\n" + dnpc + " Done"; }
            }
            if (ac != null) ac.close();

            tTopConv.setText(bestConvName.equals("None") ? "--" : bestConvName);
            tTopDnpc.setText(bestDnpcName.equals("None") ? "--" : bestDnpcName);
            Collections.sort(list, (a, b) -> Double.compare(Double.parseDouble(b[8]), Double.parseDouble(a[8])));

            int rank = 1;
            for (String[] ag : list) {
                double rate = Double.parseDouble(ag[8]);
                int badgeColor = (rate >= 92.0) ? Color.parseColor("#00E676") : ((rate >= 85.0) ? Color.parseColor("#FBBF24") : Color.parseColor("#EF4444"));

                FrameLayout wrap = new FrameLayout(this);
                LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, -2);
                wlp.setMargins(0, 0, 0, 12);
                wrap.setLayoutParams(wlp);

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(box(Color.parseColor("#181920"), 14, 0, 0));
                card.setPadding(22, 16, 16, 16);
                View bar = new View(this);
                bar.setBackground(box(badgeColor, 6, 0, 0));
                wrap.addView(card, new FrameLayout.LayoutParams(-1, -2));
                wrap.addView(bar, new FrameLayout.LayoutParams(8, -1));

                LinearLayout topH = new LinearLayout(this);
                topH.setGravity(Gravity.CENTER_VERTICAL);
                TextView n = new TextView(this);
                n.setText("👤 " + ag[0]);
                n.setTextColor(badgeColor);
                n.setTypeface(Typeface.DEFAULT_BOLD);
                n.setTextSize(14f);
                topH.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));

                TextView rk = new TextView(this);
                rk.setText((rank == 1 ? "🥇 #1 Performer" : (rank == 2 ? "🥈 #2" : (rank == 3 ? "🥉 #3" : "#" + rank))));
                rk.setTextColor(Color.parseColor("#FBBF24"));
                rk.setTextSize(11f);
                rk.setTypeface(Typeface.DEFAULT_BOLD);
                rk.setBackground(box(Color.parseColor("#232634"), 8, 0, 0));
                rk.setPadding(10, 4, 10, 4);
                topH.addView(rk);
                card.addView(topH);

                LinearLayout.LayoutParams lpP = new LinearLayout.LayoutParams(0, -2, 1f);
                lpP.setMargins(0, 0, 6, 0);

                LinearLayout r1 = new LinearLayout(this);
                r1.setPadding(0, 10, 0, 4);
                r1.addView(makePill("OFD", ag[1], Color.parseColor("#60A5FA")), lpP);
                r1.addView(makePill("DEL", ag[2], Color.parseColor("#34D399")), new LinearLayout.LayoutParams(0, -2, 1f));
                card.addView(r1);

                LinearLayout r2 = new LinearLayout(this);
                r2.setPadding(0, 2, 0, 4);
                r2.addView(makePill("OFP", ag[3], Color.parseColor("#FBBF24")), lpP);
                r2.addView(makePill("PIKED", ag[4], Color.parseColor("#A78BFA")), new LinearLayout.LayoutParams(0, -2, 1f));
                card.addView(r2);

                LinearLayout r3 = new LinearLayout(this);
                r3.setPadding(0, 2, 0, 4);
                r3.addView(makePill("DNP", ag[5], Color.WHITE), lpP);
                r3.addView(makePill("DNPC", ag[6], Color.parseColor("#38BDF8")), new LinearLayout.LayoutParams(0, -2, 1f));
                card.addView(r3);

                LinearLayout r4 = new LinearLayout(this);
                r4.setPadding(0, 2, 0, 2);
                r4.addView(makePill("CONVERSION", ag[7] + "%", badgeColor), new LinearLayout.LayoutParams(-1, -2));
                card.addView(r4);

                final String agName = ag[0];
                wrap.setOnClickListener(v -> showAgent30Days(agName));

                vCrd.addView(wrap);
                rank++;
            }
        } catch (Exception ignored) {}
                                 }
        void showAgent30Days(String agentName) {
        Cursor c = db.rawQuery("SELECT dt, o, l, p, k FROM prf WHERE n = ? AND dt >= date('now','localtime','-30 days') ORDER BY dt DESC", new String[]{agentName});
        int totO = 0, totL = 0, totP = 0, totK = 0;
        LinearLayout listLay = new LinearLayout(this);
        listLay.setOrientation(LinearLayout.VERTICAL);
        listLay.setPadding(12, 10, 12, 10);

        while (c != null && c.moveToNext()) {
            String d = c.getString(0);
            int o = c.getInt(1), l = c.getInt(2), p = c.getInt(3), k = c.getInt(4);
            int dnp = o + p, dnpc = l + k;
            double cr = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;
            totO += o; totL += l; totP += p; totK += k;

            TextView item = new TextView(this);
            item.setText("📅 " + d + "  |  Conv: " + String.format(Locale.US, "%.1f%%", cr) + "\nOFD: " + o + " | DEL: " + l + " | OFP: " + p + " | PIK: " + k);
            item.setTextColor(Color.WHITE);
            item.setTextSize(11.5f);
            item.setBackground(box(Color.parseColor("#181920"), 8, Color.parseColor("#2A2D3D"), 1));
            item.setPadding(14, 10, 14, 10);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, 6);
            listLay.addView(item, lp);
        }
        if (c != null) c.close();

        int totDnp = totO + totP, totDnpc = totL + totK;
        double overallConv = totDnp > 0 ? ((double) totDnpc / totDnp) * 100.0 : 0.0;

        ScrollView sv = new ScrollView(this);
        LinearLayout pop = new LinearLayout(this);
        pop.setOrientation(LinearLayout.VERTICAL);
        pop.setPadding(20, 20, 20, 20);
        pop.setBackgroundColor(Color.parseColor("#0F1015"));

        TextView h = new TextView(this);
        h.setText("👤 " + agentName + " (Last 30 Days)");
        h.setTextColor(Color.parseColor("#00E676"));
        h.setTextSize(16f);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, 0, 0, 10);
        pop.addView(h);

        TextView sum = new TextView(this);
        sum.setText("📊 30-Day Total Performance:\nOFD: " + totO + " | DEL: " + totL + "\nOFP: " + totP + " | PIKED: " + totK + "\nDNP: " + totDnp + " | DNPC: " + totDnpc + "\n⚡ OVERALL CONV: " + String.format(Locale.US, "%.1f%%", overallConv));
        sum.setTextColor(Color.WHITE);
        sum.setTextSize(12.5f);
        sum.setBackground(box(Color.parseColor("#181920"), 10, Color.parseColor("#00E676"), 1));
        sum.setPadding(14, 12, 14, 12);
        pop.addView(sum);

        TextView dth = new TextView(this);
        dth.setText("📋 Date-Wise History:");
        dth.setTextColor(Color.parseColor("#9CA3AF"));
        dth.setTextSize(12f);
        dth.setTypeface(Typeface.DEFAULT_BOLD);
        dth.setPadding(0, 14, 0, 6);
        pop.addView(dth);

        pop.addView(listLay);
        sv.addView(pop);

        new AlertDialog.Builder(this).setView(sv).setPositiveButton("Close", null).show();
    }

    String getDt() {
        Calendar c = Calendar.getInstance();
        if (c.get(Calendar.HOUR_OF_DAY) < 9) c.add(Calendar.DAY_OF_YEAR, -1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
    }

    void cnt() {
        try {
            Cursor c = db.rawQuery("SELECT COUNT(*) FROM ord", null);
            tCnt.setText("📦 Active Search Orders: " + (c.moveToFirst() ? c.getInt(0) : 0));
            c.close();
        } catch (Exception ignored) {}
    }

    void qry(String q) {
        try {
            ords.clear();
            if (!q.isEmpty()) {
                Cursor c = db.rawQuery("SELECT DISTINCT t, d FROM ord WHERE t LIKE ? OR t LIKE ? OR d LIKE ? ORDER BY CASE WHEN t LIKE ? THEN 1 WHEN t LIKE ? THEN 2 ELSE 3 END LIMIT 30", 
                    new String[]{"%" + q, "%" + q + "%", "%" + q + "%", q, "%" + q});
                while (c.moveToNext()) ords.add(new String[]{c.getString(0), c.getString(1)});
                c.close();
            }
            adp.notifyDataSetChanged();
        } catch (Exception ignored) {}
    }

    void doSync(boolean isAuto) {
        try {
            if (!isAuto) {
                runOnUiThread(() -> Toast.makeText(this, "Syncing live sheet...", Toast.LENGTH_SHORT).show());
            }
            String curDt = getDt();
            HttpURLConnection conn = (HttpURLConnection) new URL(CSV).openConnection();
            conn.setConnectTimeout(15000);
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            db.beginTransaction();
            int count = 0;
            HashSet<String> seenOrd = new HashSet<>();
            try {
                db.delete("ord", null, null);
                db.delete("prf", "dt = ?", new String[]{curDt});
                db.delete("prf", "dt < date('now', 'localtime', '-30 days')", null);

                String l;
                boolean hd = true;
                while ((l = r.readLine()) != null) {
                    if (hd) { hd = false; continue; }
                    String[] p = l.split(",", -1);
                    if (p.length < 2) continue;
                    String c1 = p[0].replace("\"", "").trim(), c2 = p[1].replace("\"", "").trim();
                    
                    String t, o;
                    if (c1.toUpperCase().matches("^[A-Z]{4}\\d{10}$") || c1.toUpperCase().startsWith("FMP")) {
                        t = c1; o = c2;
                    } else if (c2.toUpperCase().matches("^[A-Z]{4}\\d{10}$") || c2.toUpperCase().startsWith("FMP")) {
                        t = c2; o = c1;
                    } else {
                        t = c1; o = c2;
                    }

                    String name = p.length > 2 ? p[2].replace("\"", "").trim() : "";
                    if (!t.isEmpty() && !o.isEmpty() && seenOrd.add(t)) {
                        ContentValues cv = new ContentValues();
                        cv.put("t", t);
                        cv.put("d", o);
                        db.insertWithOnConflict("ord", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                        count++;
                    }
                    if (!name.isEmpty() && !name.equalsIgnoreCase("NAME")) {
                        ContentValues cv = new ContentValues();
                        cv.put("n", name);
                        cv.put("o", p.length > 3 ? pInt(p[3]) : 0);
                        cv.put("l", p.length > 4 ? pInt(p[4]) : 0);
                        cv.put("p", p.length > 5 ? pInt(p[5]) : 0);
                        cv.put("k", p.length > 6 ? pInt(p[6]) : 0);
                        cv.put("dt", curDt);
                        db.insert("prf", null, cv);
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            final int fin = count;
            runOnUiThread(() -> {
                if (!isAuto) {
                    Toast.makeText(this, "Synced " + fin + " orders!", Toast.LENGTH_LONG).show();
                }
                cnt();
                load();
            });
        } catch (Exception e) {
            if (!isAuto) {
                runOnUiThread(() -> Toast.makeText(this, "Sync Failed! Check internet.", Toast.LENGTH_SHORT).show());
            }
        }
    }

    int pInt(String s) {
        try { return Integer.parseInt(s.replace("\"", "").trim()); } catch (Exception e) { return 0; }
    }
}


    
