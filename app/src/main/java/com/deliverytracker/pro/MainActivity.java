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
import android.os.Looper;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;

public class MainActivity extends Activity {
    SQLiteDatabase db;
    FrameLayout root;
    LinearLayout vTrk, vPrf, vHub, vCrd, vHubCrd;
    Button bT, bP, bH, b1, b2, b3, bSort;
    TextView tCnt, tHubOfdDel, tHubOfpPik, tHubDnpDnpc, tTopConv, tTopDnpc;
    ArrayList<String[]> ords = new ArrayList<>();
    BaseAdapter adp;
    String mode = "daily";
    boolean isHighToLow = true;
    String CSV = "https://docs.google.com/spreadsheets/d/1Dul38iNZ_eNmABVuYVWhrUg9F_xVMvaVvQvLIXlySj4/export?format=csv&gid=0";

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
            db = openOrCreateDatabase("TrackerV21.db", MODE_PRIVATE, null);
            db.execSQL("CREATE TABLE IF NOT EXISTS ord (t TEXT UNIQUE, d TEXT);");
            db.execSQL("CREATE TABLE IF NOT EXISTS prf (n TEXT, o INT, l INT, p INT, k INT, dt TEXT);");
            db.execSQL("CREATE TABLE IF NOT EXISTS hub_prf (hname TEXT, o TEXT, l TEXT, lc TEXT, p TEXT, k TEXT, kc TEXT, dnp TEXT, dnpc TEXT, tc TEXT);");
        } catch (Exception ignored) {}

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0F1015"));
        setContentView(root);
        buildUI();
        new Thread(() -> doSync(true)).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        new Thread(() -> doSync(true)).start();
    }

    void buildUI() {
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);

        LinearLayout h = new LinearLayout(this);
        h.setBackgroundColor(Color.parseColor("#181920"));
        h.setPadding(20, 16, 20, 16);
        h.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText("📊 Delivery Tracker");
        t.setTextColor(Color.WHITE);
        t.setTextSize(16f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        h.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));

        Button bRef = new Button(this);
        bRef.setText("🔄 SYNC");
        bRef.setBackground(box(Color.parseColor("#00E676"), 8, 0, 0));
        bRef.setTextColor(Color.BLACK);
        bRef.setTypeface(Typeface.DEFAULT_BOLD);
        bRef.setOnClickListener(v -> new Thread(() -> doSync(false)).start());
        h.addView(bRef);
        main.addView(h);

        LinearLayout tb = new LinearLayout(this);
        tb.setPadding(12, 8, 12, 4);

        bT = new Button(this);
        bT.setText("🔍 ORDER ID");
        bT.setBackground(box(Color.parseColor("#00E676"), 10, 0, 0));
        bT.setTextColor(Color.BLACK);
        bT.setTypeface(Typeface.DEFAULT_BOLD);
        bT.setTextSize(11f);

        bP = new Button(this);
        bP.setText("📈 PERFORMANCE");
        bP.setBackground(box(Color.parseColor("#232634"), 10, 0, 0));
        bP.setTextColor(Color.parseColor("#8E92A4"));
        bP.setTextSize(11f);

        bH = new Button(this);
        bH.setText("⚔️ HUB VS HUB");
        bH.setBackground(box(Color.parseColor("#232634"), 10, 0, 0));
        bH.setTextColor(Color.parseColor("#8E92A4"));
        bH.setTextSize(11f);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(2, 0, 2, 0);
        tb.addView(bT, lp);
        tb.addView(bP, new LinearLayout.LayoutParams(lp));
        tb.addView(bH, new LinearLayout.LayoutParams(lp));
        main.addView(tb);

        FrameLayout body = new FrameLayout(this);
        body.setPadding(14, 6, 14, 10);
        main.addView(body, new LinearLayout.LayoutParams(-1, -1));

        // 1. ORDER ID TAB
        vTrk = new LinearLayout(this);
        vTrk.setOrientation(LinearLayout.VERTICAL);
        vTrk.setVisibility(View.VISIBLE);

        EditText s = new EditText(this);
        s.setHint("🔍 Search last 4-5 digits or full ID...");
        s.setHintTextColor(Color.parseColor("#717688"));
        s.setTextColor(Color.WHITE);
        s.setBackground(box(Color.parseColor("#181920"), 14, Color.parseColor("#00E676"), 1));
        s.setPadding(20, 16, 20, 16);
        s.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int i, int i1, int i2) {}
            public void onTextChanged(CharSequence c, int i, int i1, int i2) { qry(c.toString().trim()); }
            public void afterTextChanged(Editable e) {}
        });
        vTrk.addView(s);

        tCnt = new TextView(this);
        tCnt.setTextColor(Color.parseColor("#00E676"));
        tCnt.setPadding(6, 8, 6, 6);
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
                c.setPadding(18, 14, 18, 14);
                c.setBackground(box(Color.parseColor("#181920"), 14, Color.parseColor("#2A2D3D"), 1));
                String[] it = ords.get(i);

                TextView t1 = new TextView(MainActivity.this);
                t1.setText("📦 Track ID: " + it[0]);
                t1.setTextColor(Color.parseColor("#38BDF8"));
                t1.setTypeface(Typeface.DEFAULT_BOLD);

                TextView t2 = new TextView(MainActivity.this);
                t2.setText("🛒 Order ID: " + it[1] + "  📋 (Tap to Copy Last 6 Digits)");
                t2.setTextColor(Color.parseColor("#00E676"));
                t2.setPadding(0, 6, 0, 0);

                c.addView(t1);
                c.addView(t2);
                c.setOnClickListener(vw -> {
                    String fullOrd = it[1].trim();
                    String last6 = (fullOrd.length() >= 6) ? fullOrd.substring(fullOrd.length() - 6) : fullOrd;
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("Last6Digits", last6));
                    Toast.makeText(MainActivity.this, "Copied: " + last6, Toast.LENGTH_SHORT).show();
                });
                return c;
            }
        };
        lv.setAdapter(adp);
        vTrk.addView(lv, new LinearLayout.LayoutParams(-1, -1));
        body.addView(vTrk);

        // 2. PERFORMANCE TAB
        vPrf = new LinearLayout(this);
        vPrf.setOrientation(LinearLayout.VERTICAL);
        vPrf.setVisibility(View.GONE);

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

        LinearLayout hubBox = new LinearLayout(this);
        hubBox.setOrientation(LinearLayout.VERTICAL);
        hubBox.setBackground(box(Color.parseColor("#181920"), 14, Color.parseColor("#38BDF8"), 1));
        hubBox.setPadding(16, 14, 16, 14);

        TextView hTitle = new TextView(this);
        hTitle.setText("MALBAZARHUB_NJP");
        hTitle.setTextColor(Color.parseColor("#38BDF8"));
        hTitle.setTextSize(16f);
        hTitle.setTypeface(Typeface.DEFAULT_BOLD);
        hubBox.addView(hTitle);

        tHubOfdDel = new TextView(this);
        tHubOfdDel.setTextColor(Color.WHITE);
        tHubOfdDel.setTextSize(13.5f);
        tHubOfdDel.setPadding(0, 4, 0, 0);
        hubBox.addView(tHubOfdDel);

        tHubOfpPik = new TextView(this);
        tHubOfpPik.setTextColor(Color.WHITE);
        tHubOfpPik.setTextSize(13.5f);
        tHubOfpPik.setPadding(0, 2, 0, 0);
        hubBox.addView(tHubOfpPik);

        tHubDnpDnpc = new TextView(this);
        tHubDnpDnpc.setTextColor(Color.parseColor("#34D399"));
        tHubDnpDnpc.setTextSize(14f);
        tHubDnpDnpc.setTypeface(Typeface.DEFAULT_BOLD);
        tHubDnpDnpc.setPadding(0, 2, 0, 0);
        hubBox.addView(tHubDnpDnpc);
        vPrf.addView(hubBox, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout sm = new LinearLayout(this);
        sm.setPadding(0, 8, 0, 6);
        LinearLayout sc1 = makeSummaryCard("TOP CONVERSION", Color.parseColor("#181920"), Color.parseColor("#00E676"), true);
        LinearLayout sc2 = makeSummaryCard("TOP DNPC", Color.parseColor("#181920"), Color.parseColor("#FB923C"), false);
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(0, -2, 1f);
        scLp.setMargins(0, 0, 6, 0);
        sm.addView(sc1, scLp);
        sm.addView(sc2, new LinearLayout.LayoutParams(0, -2, 1f));
        vPrf.addView(sm);

        bSort = new Button(this);
        bSort.setText("↕️ Sort: High to Low");
        bSort.setBackground(box(Color.parseColor("#232634"), 10, 0, 0));
        bSort.setTextColor(Color.WHITE);
        bSort.setOnClickListener(v -> {
            isHighToLow = !isHighToLow;
            bSort.setText(isHighToLow ? "↕️ Sort: High to Low" : "↕️ Sort: Low to High");
            load();
        });
        LinearLayout.LayoutParams sortLp = new LinearLayout.LayoutParams(-1, -2);
        sortLp.setMargins(0, 2, 0, 8);
        vPrf.addView(bSort, sortLp);

        TextView apTitle = new TextView(this);
        apTitle.setText("AGENT PERFORMANCE");
        apTitle.setTextColor(Color.parseColor("#9CA3AF"));
        apTitle.setTextSize(13f);
        apTitle.setTypeface(Typeface.DEFAULT_BOLD);
        apTitle.setPadding(2, 4, 2, 6);
        vPrf.addView(apTitle);

        ScrollView sv = new ScrollView(this);
        vCrd = new LinearLayout(this);
        vCrd.setOrientation(LinearLayout.VERTICAL);
        sv.addView(vCrd);
        vPrf.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        body.addView(vPrf);

        // 3. HUB VS HUB TAB
        vHub = new LinearLayout(this);
        vHub.setOrientation(LinearLayout.VERTICAL);
        vHub.setVisibility(View.GONE);

        TextView hvhTitle = new TextView(this);
        hvhTitle.setText("⚔️ HUB VS HUB");
        hvhTitle.setTextColor(Color.parseColor("#60A5FA"));
        hvhTitle.setTextSize(15f);
        hvhTitle.setTypeface(Typeface.DEFAULT_BOLD);
        hvhTitle.setPadding(4, 4, 4, 8);
        vHub.addView(hvhTitle);

        ScrollView svHub = new ScrollView(this);
        vHubCrd = new LinearLayout(this);
        vHubCrd.setOrientation(LinearLayout.VERTICAL);
        svHub.addView(vHubCrd);
        vHub.addView(svHub, new LinearLayout.LayoutParams(-1, -1));
        body.addView(vHub);

        bT.setOnClickListener(v -> switchTab(0));
        bP.setOnClickListener(v -> switchTab(1));
        bH.setOnClickListener(v -> switchTab(2));

        root.addView(main);
        load();
        cnt();
                        }
        void switchTab(int index) {
        vTrk.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        vPrf.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        vHub.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        bT.setBackground(box(index == 0 ? Color.parseColor("#00E676") : Color.parseColor("#232634"), 10, 0, 0));
        bT.setTextColor(index == 0 ? Color.BLACK : Color.parseColor("#8E92A4"));

        bP.setBackground(box(index == 1 ? Color.parseColor("#00E676") : Color.parseColor("#232634"), 10, 0, 0));
        bP.setTextColor(index == 1 ? Color.BLACK : Color.parseColor("#8E92A4"));

        bH.setBackground(box(index == 2 ? Color.parseColor("#00E676") : Color.parseColor("#232634"), 10, 0, 0));
        bH.setTextColor(index == 2 ? Color.BLACK : Color.parseColor("#8E92A4"));

        if (index == 0) cnt();
        if (index == 1) load();
        if (index == 2) loadHubVsHub();
    }

    Button flt(String text, String m) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11.5f);
        b.setBackground(box("daily".equals(m) ? Color.parseColor("#00E676") : Color.parseColor("#232634"), 8, 0, 0));
        b.setTextColor("daily".equals(m) ? Color.BLACK : Color.parseColor("#8E92A4"));
        b.setOnClickListener(v -> {
            mode = m;
            b1.setBackground(box("daily".equals(m) ? Color.parseColor("#00E676") : Color.parseColor("#232634"), 8, 0, 0));
            b1.setTextColor("daily".equals(m) ? Color.BLACK : Color.parseColor("#8E92A4"));
            b2.setBackground(box("weekly".equals(m) ? Color.parseColor("#00E676") : Color.parseColor("#232634"), 8, 0, 0));
            b2.setTextColor("weekly".equals(m) ? Color.BLACK : Color.parseColor("#8E92A4"));
            b3.setBackground(box("monthly".equals(m) ? Color.parseColor("#00E676") : Color.parseColor("#232634"), 8, 0, 0));
            b3.setTextColor("monthly".equals(m) ? Color.BLACK : Color.parseColor("#8E92A4"));
            load();
        });
        return b;
    }

    LinearLayout makeSummaryCard(String title, int bgCol, int accent, boolean isConv) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(box(bgCol, 14, Color.parseColor("#2A2D3D"), 1));
        c.setPadding(14, 12, 14, 12);
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

    void load() {
        try {
            vCrd.removeAllViews();
            String w = "daily".equals(mode) ? " WHERE dt = (SELECT MAX(dt) FROM prf) " : ("weekly".equals(mode) ? " WHERE dt >= date('now','localtime','-7 days') " : " WHERE dt >= date('now','localtime','-30 days') ");

            Cursor hc = db.rawQuery("SELECT SUM(o), SUM(l), SUM(p), SUM(k) FROM prf " + w, null);
            if (hc != null && hc.moveToFirst()) {
                int to = hc.getInt(0), tl = hc.getInt(1), tp = hc.getInt(2), tk = hc.getInt(3);
                int tdnp = to + tp, tdnpc = tl + tk;
                double ofdConv = to > 0 ? ((double) tl / to) * 100.0 : 0.0;
                double ofpConv = tp > 0 ? ((double) tk / tp) * 100.0 : 0.0;
                double dnpConv = tdnp > 0 ? ((double) tdnpc / tdnp) * 100.0 : 0.0;

                tHubOfdDel.setText("OFD/DEL = " + to + "/" + tl + " = " + String.format(Locale.US, "%.1f%%", ofdConv));
                tHubOfpPik.setText("OFP/PIKED = " + tp + "/" + tk + " = " + String.format(Locale.US, "%.1f%%", ofpConv));
                tHubDnpDnpc.setText("DNP/DNPC = " + tdnp + "/" + tdnpc + " = " + String.format(Locale.US, "%.1f%%", dnpConv));
            }
            if (hc != null) hc.close();

            Cursor ac = db.rawQuery("SELECT n, SUM(o), SUM(l), SUM(p), SUM(k) FROM prf " + w + " GROUP BY n", null);
            ArrayList<String[]> list = new ArrayList<>();
            String bestConvName = "--", bestDnpcName = "--";
            double maxConv = -1;
            int maxDnpc = -1;

            while (ac != null && ac.moveToNext()) {
                String name = ac.getString(0);
                int o = ac.getInt(1), l = ac.getInt(2), p = ac.getInt(3), k = ac.getInt(4);
                int dnp = o + p, dnpc = l + k;
                double r = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;
                double ofdC = o > 0 ? ((double) l / o) * 100.0 : 0.0;
                double ofpC = p > 0 ? ((double) k / p) * 100.0 : 0.0;

                list.add(new String[]{name, String.valueOf(o), String.valueOf(l), String.valueOf(p), String.valueOf(k), String.valueOf(dnp), String.valueOf(dnpc), String.format(Locale.US, "%.1f", r), String.valueOf(r), String.format(Locale.US, "%.1f", ofdC), String.format(Locale.US, "%.1f", ofpC)});

                if (r > maxConv && dnp > 0) {
                    maxConv = r;
                    bestConvName = name + "\n" + String.format(Locale.US, "%.1f%%", r);
                }
                if (dnpc > maxDnpc) {
                    maxDnpc = dnpc;
                    bestDnpcName = name + "\n" + dnpc + " Done";
                }
            }
            if (ac != null) ac.close();

            tTopConv.setText(bestConvName);
            tTopDnpc.setText(bestDnpcName);

            Collections.sort(list, (a, b) -> isHighToLow ? Double.compare(Double.parseDouble(b[8]), Double.parseDouble(a[8])) : Double.compare(Double.parseDouble(a[8]), Double.parseDouble(b[8])));

            for (String[] ag : list) {
                double rate = Double.parseDouble(ag[8]);
                int badgeColor = (rate >= 90.0) ? Color.parseColor("#00E676") : ((rate >= 60.0) ? Color.parseColor("#FBBF24") : Color.parseColor("#EF4444"));

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(box(Color.parseColor("#181920"), 12, Color.parseColor("#2A2D3D"), 1));
                card.setPadding(16, 14, 16, 14);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
                clp.setMargins(0, 0, 0, 10);
                card.setLayoutParams(clp);

                TextView n = new TextView(this);
                n.setText("👤 " + ag[0]);
                n.setTextColor(badgeColor);
                n.setTypeface(Typeface.DEFAULT_BOLD);
                n.setTextSize(15f);
                card.addView(n);

                TextView t1 = new TextView(this);
                t1.setText("OFD/DEL = " + ag[1] + "/" + ag[2] + " = " + ag[9] + "%");
                t1.setTextColor(Color.parseColor("#D1D5DB"));
                t1.setTextSize(13f);
                t1.setPadding(0, 4, 0, 0);
                card.addView(t1);

                TextView t2 = new TextView(this);
                t2.setText("OFP/PIKED = " + ag[3] + "/" + ag[4] + " = " + ag[10] + "%");
                t2.setTextColor(Color.parseColor("#D1D5DB"));
                t2.setTextSize(13f);
                t2.setPadding(0, 2, 0, 0);
                card.addView(t2);

                TextView t3 = new TextView(this);
                t3.setText("DNP/DNPC = " + ag[5] + "/" + ag[6] + " = " + ag[7] + "%");
                t3.setTextColor(Color.parseColor("#34D399"));
                t3.setTextSize(13f);
                t3.setTypeface(Typeface.DEFAULT_BOLD);
                t3.setPadding(0, 2, 0, 0);
                card.addView(t3);

                TextView t4 = new TextView(this);
                t4.setText("");
                t4.setVisibility(View.GONE);
                card.addView(t4);

                final String agName = ag[0];
                card.setOnClickListener(v -> showDetails(agName));
                vCrd.addView(card);
            }
        } catch (Exception ignored) {}
    }

    void loadHubVsHub() {
        try {
            vHubCrd.removeAllViews();
            Cursor c = db.rawQuery("SELECT hname, o, l, lc, p, k, kc, dnp, dnpc, tc FROM hub_prf", null);
            while (c != null && c.moveToNext()) {
                String name = c.getString(0);
                String o = c.getString(1), l = c.getString(2), lc = c.getString(3);
                String p = c.getString(4), k = c.getString(5), kc = c.getString(6);
                String dnp = c.getString(7), dnpc = c.getString(8), tc = c.getString(9);

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(box(Color.parseColor("#181920"), 12, Color.parseColor("#2A2D3D"), 1));
                card.setPadding(16, 14, 16, 14);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
                clp.setMargins(0, 0, 0, 10);
                card.setLayoutParams(clp);

                TextView hName = new TextView(this);
                hName.setText("🏢 " + name);
                hName.setTextColor(Color.parseColor("#60A5FA"));
                hName.setTypeface(Typeface.DEFAULT_BOLD);
                hName.setTextSize(15f);
                card.addView(hName);

                TextView t1 = new TextView(this);
                t1.setText("OFD/DEL = " + o + "/" + l + " = " + lc);
                t1.setTextColor(Color.parseColor("#D1D5DB"));
                t1.setTextSize(13f);
                t1.setPadding(0, 4, 0, 0);
                card.addView(t1);

                TextView t2 = new TextView(this);
                t2.setText("OFP/PIKED = " + p + "/" + k + " = " + kc);
                t2.setTextColor(Color.parseColor("#D1D5DB"));
                t2.setTextSize(13f);
                t2.setPadding(0, 2, 0, 0);
                card.addView(t2);

                TextView t3 = new TextView(this);
                t3.setText("DNP/DNPC = " + dnp + "/" + dnpc + " = " + tc);
                t3.setTextColor(Color.parseColor("#34D399"));
                t3.setTextSize(13f);
                t3.setTypeface(Typeface.DEFAULT_BOLD);
                t3.setPadding(0, 2, 0, 0);
                card.addView(t3);

                vHubCrd.addView(card);
            }
            if (c != null) c.close();
        } catch (Exception ignored) {}
    }

    void showDetails(String name) {
        Cursor c = db.rawQuery("SELECT dt, o, l, p, k FROM prf WHERE n = ? AND dt >= date('now','localtime','-30 days') ORDER BY dt DESC", new String[]{name});
        LinearLayout pop = new LinearLayout(this);
        pop.setOrientation(LinearLayout.VERTICAL);
        pop.setPadding(20, 20, 20, 20);
        pop.setBackgroundColor(Color.parseColor("#0F1015"));

        TextView h = new TextView(this);
        h.setText("👤 " + name + " (Last 30 Days)");
        h.setTextColor(Color.parseColor("#00E676"));
        h.setTextSize(15f);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, 0, 0, 10);
        pop.addView(h);

        ScrollView sv = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        while (c != null && c.moveToNext()) {
            int o = c.getInt(1), l = c.getInt(2), p = c.getInt(3), k = c.getInt(4);
            int dnp = o + p, dnpc = l + k;
            double cr = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;

            TextView item = new TextView(this);
            item.setText("📅 " + c.getString(0) + "  |  Conv: " + String.format(Locale.US, "%.1f%%", cr) + "\nOFD: " + o + " | DEL: " + l + " | OFP: " + p + " | PIK: " + k);
            item.setTextColor(Color.WHITE);
            item.setTextSize(13f);
            item.setPadding(12, 10, 12, 10);
            item.setBackground(box(Color.parseColor("#181920"), 10, Color.parseColor("#2A2D3D"), 1));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, 8);
            item.setLayoutParams(lp);
            content.addView(item);
        }
        if (c != null) c.close();
        sv.addView(content);
        pop.addView(sv);

        new AlertDialog.Builder(this)
            .setView(pop)
            .setPositiveButton("Close", null)
            .show();
    }

    void qry(String q) {
        ords.clear();
        Cursor c = q.isEmpty() ? db.rawQuery("SELECT t, d FROM ord LIMIT 50", null) : db.rawQuery("SELECT t, d FROM ord WHERE t LIKE ? OR d LIKE ? LIMIT 50", new String[]{"%" + q + "%", "%" + q + "%"});
        while (c != null && c.moveToNext()) {
            ords.add(new String[]{c.getString(0), c.getString(1)});
        }
        if (c != null) c.close();
        if (adp != null) adp.notifyDataSetChanged();
    }

    void cnt() {
        try {
            Cursor c = db.rawQuery("SELECT COUNT(*) FROM ord", null);
            if (c != null && c.moveToFirst()) {
                tCnt.setText("📦 Total Trackable Orders: " + c.getInt(0));
            }
            if (c != null) c.close();
        } catch (Exception ignored) {}
    }

    void doSync(boolean isAuto) {
        try {
            String targetUrl = CSV;
            HttpURLConnection conn = null;
            for (int i = 0; i < 5; i++) {
                conn = (HttpURLConnection) new URL(targetUrl).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setInstanceFollowRedirects(false);
                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    targetUrl = conn.getHeaderField("Location");
                } else {
                    break;
                }
            }

            InputStream is = (conn != null) ? conn.getInputStream() : null;
            if (is == null) throw new Exception("Unable to connect");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime());

            db.beginTransaction();
            db.execSQL("DELETE FROM hub_prf");
            db.execSQL("DELETE FROM prf WHERE dt = '" + today + "'");

            while ((line = reader.readLine()) != null) {
                String[] p = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                // ORDER TRACKER DATA (Col A & B)
                if (p.length >= 2) {
                    String trackId = clean(p[0]);
                    String ordId = clean(p[1]);
                    if (trackId.matches(".*\\d+.*") && ordId.matches(".*\\d+.*") && !trackId.equalsIgnoreCase("TRACKING ID")) {
                        ContentValues ocv = new ContentValues();
                        ocv.put("t", trackId);
                        ocv.put("d", ordId);
                        db.insertWithOnConflict("ord", null, ocv, SQLiteDatabase.CONFLICT_REPLACE);
                    }
                }

                // AGENT PERFORMANCE (Col C = Name, Col D = OFD, Col E = DEL, Col F = OFP, Col G = PIKED)
                if (p.length > 2) {
                    String name = clean(p[2]);
                    if (!name.isEmpty() && !name.equalsIgnoreCase("NAME") && !name.equalsIgnoreCase("Total") && !name.contains("Total")) {
                        int o = (p.length > 3) ? parseInt(p[3]) : 0;
                        int l = (p.length > 4) ? parseInt(p[4]) : 0;
                        int op = (p.length > 5) ? parseInt(p[5]) : 0;
                        int k = (p.length > 6) ? parseInt(p[6]) : 0;

                        ContentValues cv = new ContentValues();
                        cv.put("n", name);
                        cv.put("o", o);
                        cv.put("l", l);
                        cv.put("p", op);
                        cv.put("k", k);
                        cv.put("dt", today);
                        db.insert("prf", null, cv);
                    }
                }

                // HUB VS HUB DATA (Col I = Hub Name, Col J = OFD, Col K = DEL, Col L = OFD CON, Col M = OFP, Col N = PIKED, Col O = PIK CON, Col P = Total CON)
                if (p.length > 8) {
                    String hname = clean(p[8]);
                    if (!hname.isEmpty() && !hname.equalsIgnoreCase("HUB NAME")) {
                        String o = (p.length > 9) ? clean(p[9]) : "0";
                        String l = (p.length > 10) ? clean(p[10]) : "0";
                        String lc = (p.length > 11) ? clean(p[11]) : "0%";
                        String ofp = (p.length > 12) ? clean(p[12]) : "0";
                        String pik = (p.length > 13) ? clean(p[13]) : "0";
                        String kc = (p.length > 14) ? clean(p[14]) : "0%";
                        String tc = (p.length > 15) ? clean(p[15]) : "0%";

                        int dnp = parseInt(o) + parseInt(ofp);
                        int dnpc = parseInt(l) + parseInt(pik);

                        ContentValues hcv = new ContentValues();
                        hcv.put("hname", hname);
                        hcv.put("o", o);
                        hcv.put("l", l);
                        hcv.put("lc", lc);
                        hcv.put("p", ofp);
                        hcv.put("k", pik);
                        hcv.put("kc", kc);
                        hcv.put("dnp", String.valueOf(dnp));
                        hcv.put("dnpc", String.valueOf(dnpc));
                        hcv.put("tc", tc);
                        db.insert("hub_prf", null, hcv);
                    }
                }
            }
            db.setTransactionSuccessful();
            db.endTransaction();
            reader.close();

            new Handler(Looper.getMainLooper()).post(() -> {
                load();
                loadHubVsHub();
                cnt();
                qry("");
                if (!isAuto) Toast.makeText(MainActivity.this, "✅ Synced Successfully!", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!isAuto) Toast.makeText(MainActivity.this, "Sync Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    String clean(String s) {
        if (s == null) return "";
        return s.replace("\"", "").trim();
    }

    int parseInt(String s) {
        try {
            return Integer.parseInt(clean(s).replace("%", ""));
        } catch (Exception e) {
            return 0;
        }
    }
                                    }
            
