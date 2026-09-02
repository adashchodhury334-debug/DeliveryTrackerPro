package com.deliverytracker.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
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
import android.widget.ProgressBar;
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
import java.util.LinkedHashMap;
import java.util.Locale;

public class MainActivity extends Activity {
    SQLiteDatabase db;
    FrameLayout root;
    LinearLayout vTrk, vPrf, vHub, vCnt, vCrd, vHubCrd, vCntCrd, loadingOverlay, periodFilterRow;
    Button bT, bP, bH, bC, bSort, bShareHub, bVoiceOtp, bCatAgent, bCatKirana, bCatAll, bSubDay, bSubYearly;
    TextView tCnt, tHubOfdDel, tHubOfpPik, tHubDnpDnpc, tTopConv, tTopDnpc, tGapTarget, tPersonalBest;
    ArrayList<String[]> ords = new ArrayList<>();
    BaseAdapter adp;
    String currentCategory = "AGENT", mode = "daily", CSV = "https://docs.google.com/spreadsheets/d/1Dul38iNZ_eNmABVuYVWhrUg9F_xVMvaVvQvLIXlySj4/export?format=csv&gid=0";
    boolean isHighToLow = true;
    long lastSyncTime = 0;

    Handler autoSyncHandler = new Handler(Looper.getMainLooper());
    Runnable autoSyncRunnable = new Runnable() {
        public void run() { new Thread(() -> doSync(true)).start(); autoSyncHandler.postDelayed(this, 120000); }
    };

    GradientDrawable box(int c, int r, int sCol, int sW) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c); g.setCornerRadius(r);
        if (sW > 0) g.setStroke(sW, sCol);
        return g;
    }

    TextView tv(String text, int color, float size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextColor(color); t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    boolean isKiranaAgent(String name) {
        if (name == null) return false;
        String n = name.toUpperCase(Locale.ROOT).trim();
        return n.contains("KIRANA") || n.contains("RATAN SARKAR");
    }

    String getPerformanceBadge(double conv, int ofd) {
        if (ofd == 0) return "⚪ NO OFD ASSIGNED";
        if (conv >= 96.0) return "🌟 EXCELLENT PERFORMANCE";
        if (conv >= 92.0) return "🔥 BEST PERFORMANCE (Target Met)";
        if (conv >= 88.0) return "⚠️ FOCUS PERFORMANCE";
        return "🚨 NOT ACCEPTED - IMPROVE PERFORMANCE";
    }

    int getPerformanceColor(double conv, int ofd) {
        if (ofd == 0) return Color.parseColor("#9CA3AF");
        if (conv >= 96.0) return Color.parseColor("#38BDF8");
        if (conv >= 92.0) return Color.parseColor("#00E676");
        if (conv >= 88.0) return Color.parseColor("#FBBF24");
        return Color.parseColor("#EF4444");
    }

    String getOperationalDate() {
        Calendar cal = Calendar.getInstance();
        if (cal.get(Calendar.HOUR_OF_DAY) < 2) cal.add(Calendar.DATE, -1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    String getYearStartDate(String opDate) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(opDate));
            cal.set(Calendar.DAY_OF_YEAR, 1);
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
        } catch (Exception e) { return opDate; }
    }

    String getWeekStartDate(String dateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(dateStr));
            cal.setFirstDayOfWeek(Calendar.MONDAY);
            int dow = cal.get(Calendar.DAY_OF_WEEK);
            int diff = (dow == Calendar.SUNDAY) ? 6 : (dow - Calendar.MONDAY);
            cal.add(Calendar.DAY_OF_MONTH, -diff);
            return sdf.format(cal.getTime());
        } catch (Exception e) { return dateStr; }
    }

    String getWeekEndDate(String dateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(getWeekStartDate(dateStr)));
            cal.add(Calendar.DAY_OF_MONTH, 6);
            return sdf.format(cal.getTime());
        } catch (Exception e) { return dateStr; }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        try {
            db = openOrCreateDatabase("TrackerV21.db", MODE_PRIVATE, null);
            db.execSQL("CREATE TABLE IF NOT EXISTS ord (t TEXT UNIQUE, d TEXT);");
            db.execSQL("CREATE TABLE IF NOT EXISTS prf (n TEXT, o INT, l INT, p INT, k INT, dt TEXT);");
            db.execSQL("CREATE TABLE IF NOT EXISTS hub_prf (hname TEXT, o TEXT, l TEXT, lc TEXT, p TEXT, k TEXT, kc TEXT, dnp TEXT, dnpc TEXT, tc TEXT, dt TEXT);");
            db.execSQL("CREATE TABLE IF NOT EXISTS contacts (name TEXT, role TEXT, phone TEXT);");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_prf_dt ON prf(dt);");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_prf_n ON prf(n);");
        } catch (Exception ignored) {}
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#090A0F"));
        setContentView(root);
        buildUI();
        new Thread(() -> doSync(true)).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (System.currentTimeMillis() - lastSyncTime > 120000) new Thread(() -> doSync(true)).start();
        autoSyncHandler.postDelayed(autoSyncRunnable, 120000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        autoSyncHandler.removeCallbacks(autoSyncRunnable);
    }
        void buildUI() {
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);

        LinearLayout h = new LinearLayout(this);
        h.setBackgroundColor(Color.parseColor("#12141D"));
        h.setPadding(18, 16, 18, 16);
        h.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(tv("📦 Delivery Tracker Pro", Color.WHITE, 17f, true));
        titleBox.addView(tv("⚡ Managed by Adarsh", Color.parseColor("#38BDF8"), 12f, true));
        h.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1f));

        bVoiceOtp = new Button(this);
        bVoiceOtp.setText("🎙️ OTP");
        bVoiceOtp.setBackground(box(Color.parseColor("#7C3AED"), 8, 0, 0));
        bVoiceOtp.setTextColor(Color.WHITE);
        bVoiceOtp.setTypeface(Typeface.DEFAULT_BOLD);
        bVoiceOtp.setTextSize(11.5f);
        bVoiceOtp.setOnClickListener(v -> launchVoiceOTP());
        LinearLayout.LayoutParams voLp = new LinearLayout.LayoutParams(-2, -2);
        voLp.setMargins(0, 0, 8, 0);
        h.addView(bVoiceOtp, voLp);

        Button bRef = new Button(this);
        bRef.setText("🔄 SYNC");
        bRef.setBackground(box(Color.parseColor("#00E676"), 8, 0, 0));
        bRef.setTextColor(Color.BLACK);
        bRef.setTypeface(Typeface.DEFAULT_BOLD);
        bRef.setTextSize(11.5f);
        bRef.setOnClickListener(v -> new Thread(() -> doSync(false)).start());
        h.addView(bRef);
        main.addView(h);

        LinearLayout tb = new LinearLayout(this);
        tb.setPadding(8, 8, 8, 4);
        bT = makeTabBtn("🔍 ORDER", 0);
        bP = makeTabBtn("📈 PERF", 1);
        bH = makeTabBtn("⚔️ HUBS", 2);
        bC = makeTabBtn("📞 HELPLINE", 3);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, -2, 1f);
        tLp.setMargins(2, 0, 2, 0);
        tb.addView(bT, tLp); tb.addView(bP, new LinearLayout.LayoutParams(tLp));
        tb.addView(bH, new LinearLayout.LayoutParams(tLp)); tb.addView(bC, new LinearLayout.LayoutParams(tLp));
        main.addView(tb);

        FrameLayout body = new FrameLayout(this);
        body.setPadding(12, 6, 12, 10);
        main.addView(body, new LinearLayout.LayoutParams(-1, -1));

        // 1. ORDER TAB
        vTrk = new LinearLayout(this);
        vTrk.setOrientation(LinearLayout.VERTICAL);
        EditText s = new EditText(this);
        s.setHint("🔍 Search last digits of Track ID...");
        s.setHintTextColor(Color.parseColor("#717688"));
        s.setTextColor(Color.WHITE);
        s.setBackground(box(Color.parseColor("#12141D"), 12, Color.parseColor("#00E676"), 1));
        s.setPadding(16, 14, 16, 14);
        s.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int i, int i1, int i2) {}
            public void onTextChanged(CharSequence c, int i, int i1, int i2) { qry(c.toString().trim()); }
            public void afterTextChanged(Editable e) {}
        });
        vTrk.addView(s);
        tCnt = tv("📦 Total Trackable Orders: --", Color.parseColor("#00E676"), 12.5f, true);
        tCnt.setPadding(6, 10, 6, 8);
        vTrk.addView(tCnt);

        ListView lv = new ListView(this);
        lv.setDivider(null); lv.setDividerHeight(10);
        adp = new BaseAdapter() {
            public int getCount() { return ords.size(); }
            public Object getItem(int i) { return ords.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) {
                LinearLayout c = new LinearLayout(MainActivity.this);
                c.setOrientation(LinearLayout.VERTICAL);
                c.setPadding(16, 14, 16, 14);
                c.setBackground(box(Color.parseColor("#12141D"), 12, Color.parseColor("#1E2235"), 1));
                String[] it = ords.get(i);
                c.addView(tv("📦 Track: " + it[0], Color.parseColor("#38BDF8"), 14.5f, true));
                c.addView(tv("🛒 Order: " + it[1], Color.parseColor("#00E676"), 13.5f, false));

                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setPadding(0, 8, 0, 0);
                Button bCp = new Button(MainActivity.this);
                bCp.setText("📋 Copy Order");
                bCp.setBackground(box(Color.parseColor("#1F222E"), 8, Color.parseColor("#00E676"), 1));
                bCp.setTextColor(Color.parseColor("#00E676"));
                bCp.setTextSize(11f);
                bCp.setOnClickListener(vw -> {
                    String sub = it[1].length() >= 6 ? it[1].substring(it[1].length() - 6) : it[1];
                    ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("O", sub));
                    Toast.makeText(MainActivity.this, "Copied: " + sub, Toast.LENGTH_SHORT).show();
                });
                Button bWp = new Button(MainActivity.this);
                bWp.setText("💬 WhatsApp");
                bWp.setBackground(box(Color.parseColor("#25D366"), 8, 0, 0));
                bWp.setTextColor(Color.BLACK);
                bWp.setTextSize(11f);
                bWp.setOnClickListener(vw -> {
                    String msg = "नमस्ते! आपका पार्सल (Track ID: " + it[0] + ") आज डिलीवरी के लिए निकला है। OTP तैयार रखें। - Delivery Executive";
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?text=" + Uri.encode(msg))));
                });
                LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, -2, 1f);
                bLp.setMargins(0, 0, 6, 0);
                row.addView(bCp, bLp); row.addView(bWp, new LinearLayout.LayoutParams(0, -2, 1f));
                c.addView(row);
                return c;
            }
        };
        lv.setAdapter(adp);
        vTrk.addView(lv, new LinearLayout.LayoutParams(-1, -1));
        body.addView(vTrk);

        // 2. PERF TAB
        vPrf = new LinearLayout(this);
        vPrf.setOrientation(LinearLayout.VERTICAL);
        vPrf.setVisibility(View.GONE);

        LinearLayout catRow = new LinearLayout(this);
        catRow.setPadding(0, 0, 0, 6);
        bCatAgent = makeCatBtn("👥 AGENT", "AGENT");
        bCatKirana = makeCatBtn("🏪 KIRANA", "KIRANA");
        bCatAll = makeCatBtn("🌐 ALL (MIX)", "ALL");
        catRow.addView(bCatAgent, tLp); catRow.addView(bCatKirana, new LinearLayout.LayoutParams(tLp)); catRow.addView(bCatAll, new LinearLayout.LayoutParams(tLp));
        vPrf.addView(catRow);

        periodFilterRow = new LinearLayout(this);
        periodFilterRow.setPadding(0, 0, 0, 6);
        vPrf.addView(periodFilterRow);

        tPersonalBest = tv("🏆 Hub Best: --", Color.parseColor("#FBBF24"), 12.5f, true);
        tPersonalBest.setBackground(box(Color.parseColor("#1C1E2A"), 10, Color.parseColor("#FBBF24"), 1));
        tPersonalBest.setPadding(14, 10, 14, 10);
        vPrf.addView(tPersonalBest);

        LinearLayout hubBox = new LinearLayout(this);
        hubBox.setOrientation(LinearLayout.VERTICAL);
        hubBox.setBackground(box(Color.parseColor("#12141D"), 14, Color.parseColor("#38BDF8"), 1));
        hubBox.setPadding(16, 12, 16, 12);
        LinearLayout.LayoutParams hbLp = new LinearLayout.LayoutParams(-1, -2);
        hbLp.setMargins(0, 8, 0, 8);
        hubBox.setLayoutParams(hbLp);
        hubBox.addView(tv("🏢 MALBAZARHUB_NJP", Color.parseColor("#38BDF8"), 16f, true));

        tHubOfdDel = tv("OFD/DEL = --", Color.WHITE, 13.5f, false);
        tHubOfpPik = tv("OFP/PIK = --", Color.WHITE, 13.5f, false);
        tHubDnpDnpc = tv("DNP/DNPC = --", Color.parseColor("#34D399"), 14f, true);
        tGapTarget = tv("", Color.parseColor("#FB923C"), 13f, true);
        hubBox.addView(tHubOfdDel); hubBox.addView(tHubOfpPik); hubBox.addView(tHubDnpDnpc); hubBox.addView(tGapTarget);
        vPrf.addView(hubBox);

        LinearLayout sm = new LinearLayout(this);
        sm.setPadding(0, 0, 0, 8);
        LinearLayout sc1 = makeSummaryCard("TOP CONVERSION", Color.parseColor("#00E676"), true);
        LinearLayout sc2 = makeSummaryCard("TOP DNPC", Color.parseColor("#FB923C"), false);
        sm.addView(sc1, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams sc2Lp = new LinearLayout.LayoutParams(0, -2, 1f);
        sc2Lp.setMargins(8, 0, 0, 0);
        sm.addView(sc2, sc2Lp);
        vPrf.addView(sm);

        LinearLayout actRow = new LinearLayout(this);
        bSort = new Button(this);
        bSort.setText("↕️ Sort Rate");
        bSort.setBackground(box(Color.parseColor("#1C1E2A"), 8, 0, 0));
        bSort.setTextColor(Color.WHITE);
        bSort.setTextSize(11.5f);
        bSort.setTypeface(Typeface.DEFAULT_BOLD);
        bSort.setOnClickListener(v -> { isHighToLow = !isHighToLow; load(); });

        bShareHub = new Button(this);
        bShareHub.setText("📢 Share Hub");
        bShareHub.setBackground(box(Color.parseColor("#25D366"), 8, 0, 0));
        bShareHub.setTextColor(Color.BLACK);
        bShareHub.setTextSize(11.5f);
        bShareHub.setTypeface(Typeface.DEFAULT_BOLD);
        bShareHub.setOnClickListener(v -> showHubShareChooserDialog());

        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(0, -2, 1f);
        aLp.setMargins(0, 0, 6, 8);
        actRow.addView(bSort, aLp);
        actRow.addView(bShareHub, new LinearLayout.LayoutParams(0, -2, 1f));
        vPrf.addView(actRow);

        ScrollView sv = new ScrollView(this);
        vCrd = new LinearLayout(this);
        vCrd.setOrientation(LinearLayout.VERTICAL);
        sv.addView(vCrd);
        vPrf.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        body.addView(vPrf);

        // 3. HUBS TAB
        vHub = new LinearLayout(this);
        vHub.setOrientation(LinearLayout.VERTICAL);
        vHub.setVisibility(View.GONE);
        ScrollView svHub = new ScrollView(this);
        vHubCrd = new LinearLayout(this);
        vHubCrd.setOrientation(LinearLayout.VERTICAL);
        svHub.addView(vHubCrd);
        vHub.addView(svHub, new LinearLayout.LayoutParams(-1, -1));
        body.addView(vHub);

        // 4. CONTACTS TAB
        vCnt = new LinearLayout(this);
        vCnt.setOrientation(LinearLayout.VERTICAL);
        vCnt.setVisibility(View.GONE);
        ScrollView svCnt = new ScrollView(this);
        vCntCrd = new LinearLayout(this);
        vCntCrd.setOrientation(LinearLayout.VERTICAL);
        svCnt.addView(vCntCrd);
        vCnt.addView(svCnt, new LinearLayout.LayoutParams(-1, -1));
        body.addView(vCnt);

        root.addView(main);
        loadingOverlay = new LinearLayout(this);
        loadingOverlay.setGravity(Gravity.CENTER);
        loadingOverlay.setBackgroundColor(Color.parseColor("#DD090A0F"));
        loadingOverlay.setVisibility(View.GONE);
        loadingOverlay.addView(new ProgressBar(this));
        root.addView(loadingOverlay, new FrameLayout.LayoutParams(-1, -1));

        switchCategory("AGENT");
        loadContacts();
    }

    Button makeTabBtn(String t, int idx) {
        Button b = new Button(this);
        b.setText(t); b.setTextSize(9.5f); b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setOnClickListener(v -> switchTab(idx));
        return b;
    }

    Button makeCatBtn(String t, String cat) {
        Button b = new Button(this);
        b.setText(t); b.setTextSize(10.5f); b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setOnClickListener(v -> switchCategory(cat));
        return b;
    }

    LinearLayout makeSummaryCard(String title, int accent, boolean isConv) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(box(Color.parseColor("#12141D"), 12, Color.parseColor("#1E2235"), 1));
        c.setPadding(14, 10, 14, 10);
        c.addView(tv(title, Color.parseColor("#9CA3AF"), 10.5f, true));
        TextView v = tv("--", accent, 13.5f, true);
        v.setPadding(0, 4, 0, 0);
        c.addView(v);
        if (isConv) tTopConv = v; else tTopDnpc = v;
        return c;
    }

    void switchTab(int idx) {
        vTrk.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        vPrf.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        vHub.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
        vCnt.setVisibility(idx == 3 ? View.VISIBLE : View.GONE);
        bT.setBackground(box(idx == 0 ? Color.parseColor("#00E676") : Color.parseColor("#1C1E2A"), 8, 0, 0));
        bT.setTextColor(idx == 0 ? Color.BLACK : Color.parseColor("#8E92A4"));
        bP.setBackground(box(idx == 1 ? Color.parseColor("#00E676") : Color.parseColor("#1C1E2A"), 8, 0, 0));
        bP.setTextColor(idx == 1 ? Color.BLACK : Color.parseColor("#8E92A4"));
        bH.setBackground(box(idx == 2 ? Color.parseColor("#00E676") : Color.parseColor("#1C1E2A"), 8, 0, 0));
        bH.setTextColor(idx == 2 ? Color.BLACK : Color.parseColor("#8E92A4"));
        bC.setBackground(box(idx == 3 ? Color.parseColor("#00E676") : Color.parseColor("#1C1E2A"), 8, 0, 0));
        bC.setTextColor(idx == 3 ? Color.BLACK : Color.parseColor("#8E92A4"));
        if (idx == 0) cnt(); else if (idx == 1) load(); else if (idx == 2) loadHubVsHub(); else if (idx == 3) loadContacts();
    }

    void switchCategory(String cat) {
        currentCategory = cat; mode = "daily";
        bCatAgent.setBackground(box("AGENT".equals(cat) ? Color.parseColor("#00E676") : Color.parseColor("#1C1E2A"), 8, 0, 0));
        bCatAgent.setTextColor("AGENT".equals(cat) ? Color.BLACK : Color.parseColor("#8E92A4"));
        bCatKirana.setBackground(box("KIRANA".equals(cat) ? Color.parseColor("#00E676") : Color.parseColor("#1C1E2A"), 8, 0, 0));
        bCatKirana.setTextColor("KIRANA".equals(cat) ? Color.BLACK : Color.parseColor("#8E92A4"));
        bCatAll.setBackground(box("ALL".equals(cat) ? Color.parseColor("#00E676") : Color.parseColor("#1C1E2A"), 8, 0, 0));
        bCatAll.setTextColor("ALL".equals(cat) ? Color.BLACK : Color.parseColor("#8E92A4"));
        setupPeriodButtons(); load();
    }

    void setupPeriodButtons() {
        periodFilterRow.removeAllViews();
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(0, -2, 1f);
        pLp.setMargins(1, 0, 1, 0);
        bSubDay = new Button(this);
        bSubDay.setText("📅 Day (Live)"); bSubDay.setTextSize(10f); bSubDay.setTypeface(Typeface.DEFAULT_BOLD);
        bSubDay.setOnClickListener(v -> { mode = "daily"; updatePeriodStyles(); load(); });
        String yTxt = "KIRANA".equals(currentCategory) ? "📊 Yearly (Cycles)" : "📊 Yearly (Weeks)";
        bSubYearly = new Button(this);
        bSubYearly.setText(yTxt); bSubYearly.setTextSize(10f); bSubYearly.setTypeface(Typeface.DEFAULT_BOLD);
        bSubYearly.setOnClickListener(v -> { mode = "yearly"; updatePeriodStyles(); load(); });
        periodFilterRow.addView(bSubDay, pLp); periodFilterRow.addView(bSubYearly, new LinearLayout.LayoutParams(pLp));
        updatePeriodStyles();
    }

    void updatePeriodStyles() {
        boolean d = "daily".equals(mode);
        bSubDay.setBackground(box(d ? Color.parseColor("#00E676") : Color.parseColor("#1C1E2A"), 8, 0, 0));
        bSubDay.setTextColor(d ? Color.BLACK : Color.parseColor("#8E92A4"));
        bSubYearly.setBackground(box(!d ? Color.parseColor("#00E676") : Color.parseColor("#1C1E2A"), 8, 0, 0));
        bSubYearly.setTextColor(!d ? Color.BLACK : Color.parseColor("#8E92A4"));
                           }
        void load() {
        try {
            vCrd.removeAllViews();
            String opDate = getOperationalDate();
            String w = "daily".equals(mode) ? " WHERE dt = (SELECT MAX(dt) FROM prf) " : " WHERE dt >= '" + getYearStartDate(opDate) + "' ";

            Cursor hc = db.rawQuery("SELECT SUM(o), SUM(l), SUM(p), SUM(k) FROM prf " + w, null);
            if (hc != null && hc.moveToFirst()) {
                int to = hc.getInt(0), tl = hc.getInt(1), tp = hc.getInt(2), tk = hc.getInt(3);
                int tdnp = to + tp, tdnpc = tl + tk;
                double ofdC = to > 0 ? ((double) tl / to) * 100.0 : 0.0;
                double ofpC = tp > 0 ? ((double) tk / tp) * 100.0 : 0.0;
                double dnpC = tdnp > 0 ? ((double) tdnpc / tdnp) * 100.0 : 0.0;
                tHubOfdDel.setText("OFD/DEL = " + to + "/" + tl + " = " + String.format(Locale.US, "%.1f%%", ofdC));
                tHubOfpPik.setText("OFP/PIK = " + tp + "/" + tk + " = " + String.format(Locale.US, "%.1f%%", ofpC));
                tHubDnpDnpc.setText("DNP/DNPC = " + tdnp + "/" + tdnpc + " = " + String.format(Locale.US, "%.1f%%", dnpC));
                int diff = (int) Math.ceil(0.92 * to) - tl;
                if (diff <= 0 && to > 0) {
                    tGapTarget.setText("🎯 92% Target Achieved! 🚀");
                    tGapTarget.setTextColor(Color.parseColor("#00E676"));
                } else if (to > 0) {
                    tGapTarget.setText("🎯 Gap to 92%: " + diff + " more DEL required");
                    tGapTarget.setTextColor(Color.parseColor("#FB923C"));
                } else tGapTarget.setText("");
            }
            if (hc != null) hc.close();
            updatePersonalBest();

            Cursor ac = db.rawQuery("SELECT n, SUM(o), SUM(l), SUM(p), SUM(k) FROM prf " + w + " GROUP BY n", null);
            ArrayList<String[]> list = new ArrayList<>();
            String bestConvName = "--", bestDnpcName = "--";
            double maxConv = -1; int maxDnpc = -1;

            while (ac != null && ac.moveToNext()) {
                String name = ac.getString(0);
                boolean isK = isKiranaAgent(name);
                if ("AGENT".equals(currentCategory) && isK) continue;
                if ("KIRANA".equals(currentCategory) && !isK) continue;
                int o = ac.getInt(1), l = ac.getInt(2), p = ac.getInt(3), k = ac.getInt(4);
                int dnp = o + p, dnpc = l + k;
                double r = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;
                double ofdC = o > 0 ? ((double) l / o) * 100.0 : 0.0;
                list.add(new String[]{name, String.valueOf(o), String.valueOf(l), String.valueOf(p), String.valueOf(k), String.valueOf(dnp), String.valueOf(dnpc), String.format(Locale.US, "%.1f", ofdC), String.valueOf(r)});
                if (r > maxConv && dnp > 0) { maxConv = r; bestConvName = name + "\n" + String.format(Locale.US, "%.1f%%", r); }
                if (dnpc > maxDnpc) { maxDnpc = dnpc; bestDnpcName = name + "\n" + dnpc + " Done"; }
            }
            if (ac != null) ac.close();
            tTopConv.setText(bestConvName); tTopDnpc.setText(bestDnpcName);

            Collections.sort(list, (a, b) -> isHighToLow ? Double.compare(Double.parseDouble(b[8]), Double.parseDouble(a[8])) : Double.compare(Double.parseDouble(a[8]), Double.parseDouble(b[8])));

            int currentRank = 1;
            for (String[] ag : list) {
                int[] strk = getStreakInfo(ag[0]);
                int curStrk = strk[0], prevStrk = strk[1];
                int o = Integer.parseInt(ag[1]), l = Integer.parseInt(ag[2]);
                int p = Integer.parseInt(ag[3]), k = Integer.parseInt(ag[4]);
                int dnp = Integer.parseInt(ag[5]), dnpc = Integer.parseInt(ag[6]);
                double ofdConv = Double.parseDouble(ag[7]);
                int badgeColor = getPerformanceColor(ofdConv, o);

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(box(Color.parseColor("#12141D"), 14, Color.parseColor("#1E2235"), 1));
                card.setPadding(16, 14, 16, 14);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
                clp.setMargins(0, 0, 0, 10);
                card.setLayoutParams(clp);

                // Top Line: Rank, Name + Right Box (Streak + Mini Share Underneath)
                LinearLayout topRow = new LinearLayout(this);
                topRow.setGravity(Gravity.CENTER_VERTICAL);

                String rBadge = (currentRank == 1) ? "🥇 #1" : ((currentRank == 2) ? "🥈 #2" : ((currentRank == 3) ? "🥉 #3" : "#" + currentRank));
                int rBg = (currentRank == 1) ? Color.parseColor("#EAB308") : ((currentRank == 2) ? Color.parseColor("#94A3B8") : ((currentRank == 3) ? Color.parseColor("#B45309") : Color.parseColor("#374151")));
                TextView tRnk = tv(rBadge, Color.WHITE, 11f, true);
                tRnk.setBackground(box(rBg, 6, 0, 0));
                tRnk.setPadding(8, 2, 8, 2);
                topRow.addView(tRnk);

                TextView tName = tv(" " + ag[0], badgeColor, 15.5f, true);
                topRow.addView(tName, new LinearLayout.LayoutParams(0, -2, 1f));

                // Right Side Vertical Box: Streak Tag on top, Small Share Button under it
                LinearLayout rightBox = new LinearLayout(this);
                rightBox.setOrientation(LinearLayout.VERTICAL);
                rightBox.setGravity(Gravity.END);

                String sText = (prevStrk > 0) ? "🔥 " + curStrk + "D (Prev: " + prevStrk + "D)" : "🔥 " + curStrk + "D";
                TextView tSt = tv(sText, Color.parseColor("#FBBF24"), 11f, true);
                tSt.setBackground(box(Color.parseColor("#1C1E2A"), 6, 0, 0));
                tSt.setPadding(8, 3, 8, 3);
                rightBox.addView(tSt);

                Button bMiniShare = new Button(this);
                bMiniShare.setText("📢 Share");
                bMiniShare.setBackground(box(Color.parseColor("#25D366"), 6, 0, 0));
                bMiniShare.setTextColor(Color.BLACK);
                bMiniShare.setTextSize(10f);
                bMiniShare.setTypeface(Typeface.DEFAULT_BOLD);
                bMiniShare.setPadding(10, 2, 10, 2);
                LinearLayout.LayoutParams msLp = new LinearLayout.LayoutParams(-2, -2);
                msLp.setMargins(0, 4, 0, 0);
                bMiniShare.setLayoutParams(msLp);
                final String agN = ag[0];
                bMiniShare.setOnClickListener(v -> shareSingleAgentReport(agN, o, l, p, k, dnp, dnpc, ofdConv));
                rightBox.addView(bMiniShare);

                topRow.addView(rightBox);
                card.addView(topRow);

                // Main Stats Box
                LinearLayout mBox = new LinearLayout(this);
                mBox.setOrientation(LinearLayout.VERTICAL);
                mBox.setBackground(box(Color.parseColor("#171926"), 10, Color.parseColor("#222638"), 1));
                mBox.setPadding(12, 10, 12, 10);
                LinearLayout.LayoutParams mbLp = new LinearLayout.LayoutParams(-1, -2);
                mbLp.setMargins(0, 8, 0, 8);
                mBox.setLayoutParams(mbLp);

                mBox.addView(tv("🚚 OFD / DEL = " + o + " / " + l + " ➔ " + ag[7] + "% DEL", badgeColor, 14.5f, true));
                mBox.addView(tv("📦 OFP / PIK = " + p + "/" + k + "  •  DNP = " + dnp + "/" + dnpc, Color.parseColor("#9CA3AF"), 12.5f, false));

                int diff = (int) Math.ceil(0.92 * o) - l;
                if (diff <= 0 && o > 0) {
                    mBox.addView(tv("🎯 92% Target Achieved! 🚀", Color.parseColor("#00E676"), 12f, true));
                } else if (o > 0) {
                    mBox.addView(tv("🎯 Gap to 92%: " + diff + " more DEL required", Color.parseColor("#FB923C"), 12f, true));
                }
                card.addView(mBox);

                // Problem / Miss Alert in Daily Mode
                if ("daily".equals(mode)) {
                    Cursor yc = db.rawQuery("SELECT dt, o, l, (CAST(l AS REAL)*100.0/CASE WHEN o>0 THEN o ELSE 1 END) FROM prf WHERE n = ? AND dt < ? AND o > 0 ORDER BY dt DESC LIMIT 1", new String[]{ag[0], opDate});
                    if (yc != null && yc.moveToFirst()) {
                        String yDt = yc.getString(0);
                        int yO = yc.getInt(1), yL = yc.getInt(2);
                        double yConv = yc.getDouble(3);
                        int yGap = (int) Math.ceil(0.92 * yO) - yL;
                        TextView tPrev = tv(yConv < 92.0 ? "⚠️ Prev Day (" + yDt + "): Missed 92% by " + yGap + " DEL (" + String.format(Locale.US, "%.1f%%", yConv) + ")" : "✅ Prev Day (" + yDt + "): 92% Achieved", yConv < 92.0 ? Color.parseColor("#EF4444") : Color.parseColor("#10B981"), 11.5f, true);
                        tPrev.setPadding(0, 0, 0, 4);
                        card.addView(tPrev);
                    }
                    if (yc != null) yc.close();
                }

                // In Yearly Mode Only: View Breakdown Button
                if ("yearly".equals(mode)) {
                    Button bView = new Button(this);
                    bView.setText("KIRANA".equals(currentCategory) ? "📊 View All Cycles (Click for Days)" : "📊 View All Weeks (Click for Days)");
                    bView.setBackground(box(Color.parseColor("#1F2232"), 8, Color.parseColor("#38BDF8"), 1));
                    bView.setTextColor(Color.parseColor("#38BDF8"));
                    bView.setTextSize(11f);
                    bView.setTypeface(Typeface.DEFAULT_BOLD);
                    bView.setOnClickListener(v -> showYearlyDetails(agN));
                    card.addView(bView);
                }

                vCrd.addView(card);
                currentRank++;
            }
        } catch (Exception ignored) {}
        }
        void showHubShareChooserDialog() {
        String[] options = {
            "👥 ALL AGENT (Sabhi Agent + Kirana Data)",
            "🏪 KIRANA (Sirf Kirana Data)",
            "🏢 ONLY HUB (Sirf Hub Summary - No Agents)",
            "🚚 TRUFLEX (Kirana Chod Kar Sabhi Agents)"
        };

        new AlertDialog.Builder(this)
            .setTitle("📢 Select Share Report Type")
            .setItems(options, (dialog, which) -> {
                if (which == 0) generateAndShareReport("ALL");
                else if (which == 1) generateAndShareReport("KIRANA");
                else if (which == 2) generateAndShareReport("ONLY_HUB");
                else if (which == 3) generateAndShareReport("TRUFLEX");
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    void generateAndShareReport(String type) {
        try {
            String opDate = getOperationalDate();
            StringBuilder sb = new StringBuilder();
            sb.append("📊 *MALBAZARHUB_NJP PERFORMANCE REPORT*\n");
            sb.append("📅 *Date:* ").append(opDate).append("\n");
            sb.append("📋 *Type:* ").append(type.replace("_", " ")).append("\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━\n");

            String w = "daily".equals(mode) ? " WHERE dt = (SELECT MAX(dt) FROM prf) " : " WHERE dt >= '" + getYearStartDate(opDate) + "' ";
            Cursor hc = db.rawQuery("SELECT SUM(o), SUM(l), SUM(p), SUM(k) FROM prf " + w, null);
            if (hc != null && hc.moveToFirst()) {
                int to = hc.getInt(0), tl = hc.getInt(1), tp = hc.getInt(2), tk = hc.getInt(3);
                double conv = to > 0 ? ((double) tl / to) * 100.0 : 0.0;
                sb.append("🚚 *Hub OFD / DEL:* ").append(to).append(" / ").append(tl).append(" (").append(String.format(Locale.US, "%.1f%%", conv)).append(")\n");
                sb.append("📦 *Hub OFP / PIK:* ").append(tp).append(" / ").append(tk).append("\n");
                sb.append("🔄 *Hub DNP / DNPC:* ").append(to + tp).append(" / ").append(tl + tk).append("\n");
                int diff = (int) Math.ceil(0.92 * to) - tl;
                sb.append(diff <= 0 && to > 0 ? "🎯 *Target Status:* 92% Achieved! 🚀\n" : "🎯 *Target Status:* ⚠️ Gap to 92%: " + diff + " more DEL needed\n");
            }
            if (hc != null) hc.close();

            if (!"ONLY_HUB".equals(type)) {
                sb.append("━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("🏆 *RANK-WISE SCORECARD:*\n\n");

                Cursor ac = db.rawQuery("SELECT n, SUM(o), SUM(l) FROM prf " + w + " GROUP BY n", null);
                ArrayList<String[]> list = new ArrayList<>();
                while (ac != null && ac.moveToNext()) {
                    String name = ac.getString(0);
                    boolean isK = isKiranaAgent(name);

                    if ("KIRANA".equals(type) && !isK) continue;
                    if ("TRUFLEX".equals(type) && isK) continue;

                    int o = ac.getInt(1), l = ac.getInt(2);
                    double c = o > 0 ? ((double) l / o) * 100.0 : 0.0;
                    list.add(new String[]{name, String.valueOf(o), String.valueOf(l), String.format(Locale.US, "%.1f", c), String.valueOf(c)});
                }
                if (ac != null) ac.close();

                Collections.sort(list, (a, b) -> Double.compare(Double.parseDouble(b[4]), Double.parseDouble(a[4])));

                int rank = 1;
                for (String[] ag : list) {
                    double c = Double.parseDouble(ag[4]);
                    String icon = (c >= 96.0) ? "🌟" : ((c >= 92.0) ? "🔥" : ((c >= 88.0) ? "⚠️" : "🚨"));
                    sb.append(rank).append(". ").append(icon).append(" *").append(ag[0]).append("* ➔ DEL: ").append(ag[2]).append("/").append(ag[1]).append(" (").append(ag[3]).append("%)\n");
                    rank++;
                }
            }

            sb.append("━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("⚡ _Generated via Delivery Tracker Pro | Managed by Adarsh_");

            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType("text/plain");
            it.putExtra(Intent.EXTRA_TEXT, sb.toString());
            startActivity(Intent.createChooser(it, "📢 Share Live Report"));
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    void shareSingleAgentReport(String name, int ofd, int del, int ofp, int pik, int dnp, int dnpc, double conv) {
        StringBuilder sb = new StringBuilder();
        sb.append("👤 *DELIVERY SCORECARD*\n📛 *Name:* ").append(name).append("\n📅 *Date:* ").append(getOperationalDate()).append("\n━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🚚 *OFD / DEL:* ").append(ofd).append(" / ").append(del).append(" (").append(String.format(Locale.US, "%.1f%%", conv)).append(")\n");
        sb.append("📦 *OFP / PIK:* ").append(ofp).append(" / ").append(pik).append("\n🔄 *DNP / DNPC:* ").append(dnp).append(" / ").append(dnpc).append("\n");
        int diff = (int) Math.ceil(0.92 * ofd) - del;
        sb.append(diff <= 0 && ofd > 0 ? "🎯 *Target:* 92% Achieved! 🚀\n" : "🎯 *Target Gap:* " + diff + " more DEL required\n");
        sb.append("🏆 *Rating:* ").append(getPerformanceBadge(conv, ofd)).append("\n━━━━━━━━━━━━━━━━━━━━\n⚡ _Managed by Adarsh_");
        Intent it = new Intent(Intent.ACTION_SEND);
        it.setType("text/plain"); it.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(it, "📢 Share Scorecard"));
    }

    int[] getStreakInfo(String name) {
        int cur = 0, prev = 0;
        try {
            Cursor c = db.rawQuery("SELECT DISTINCT dt FROM prf WHERE n = ? AND (o+p) > 0 ORDER BY dt DESC", new String[]{name});
            ArrayList<Calendar> dates = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            while (c != null && c.moveToNext()) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(sdf.parse(c.getString(0)));
                dates.add(cal);
            }
            if (c != null) c.close();

            if (!dates.isEmpty()) {
                cur = 1; int i = 1;
                while (i < dates.size()) {
                    Calendar prevCal = (Calendar) dates.get(i - 1).clone();
                    prevCal.add(Calendar.DAY_OF_YEAR, -1);
                    if (prevCal.get(Calendar.YEAR) == dates.get(i).get(Calendar.YEAR) && prevCal.get(Calendar.DAY_OF_YEAR) == dates.get(i).get(Calendar.DAY_OF_YEAR)) {
                        cur++; i++;
                    } else break;
                }
                while (i < dates.size()) {
                    prev = 1; i++;
                    while (i < dates.size()) {
                        Calendar prevCal = (Calendar) dates.get(i - 1).clone();
                        prevCal.add(Calendar.DAY_OF_YEAR, -1);
                        if (prevCal.get(Calendar.YEAR) == dates.get(i).get(Calendar.YEAR) && prevCal.get(Calendar.DAY_OF_YEAR) == dates.get(i).get(Calendar.DAY_OF_YEAR)) {
                            prev++; i++;
                        } else break;
                    }
                    break;
                }
            }
        } catch (Exception ignored) {}
        return new int[]{Math.max(1, cur), prev};
    }

    void updatePersonalBest() {
        try {
            Cursor c = db.rawQuery("SELECT dt, SUM(o), SUM(l), (CAST(SUM(l) AS REAL)*100.0/SUM(o)) as conv FROM prf GROUP BY dt HAVING SUM(o)>0 ORDER BY conv DESC LIMIT 1", null);
            if (c != null && c.moveToFirst()) {
                tPersonalBest.setText("🏆 Hub Best: " + String.format(Locale.US, "%.1f%%", c.getDouble(3)) + " DEL (" + c.getInt(2) + "/" + c.getInt(1) + ") (" + c.getString(0) + ")");
            }
            if (c != null) c.close();
        } catch (Exception ignored) {}
    }
        void showYearlyDetails(String name) {
        boolean isK = isKiranaAgent(name);
        LinearLayout pop = new LinearLayout(this);
        pop.setOrientation(LinearLayout.VERTICAL);
        pop.setPadding(18, 18, 18, 18);
        pop.setBackgroundColor(Color.parseColor("#090A0F"));

        pop.addView(tv((isK ? "🏪 " : "👤 ") + name + "\n(Tap Period for Day Logs)", Color.parseColor("#00E676"), 15f, true));
        ScrollView sv = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 10, 0, 0);

        if (isK) {
            Cursor mc = db.rawQuery("SELECT DISTINCT SUBSTR(dt, 1, 7) FROM prf WHERE n = ? ORDER BY dt DESC", new String[]{name});
            while (mc != null && mc.moveToNext()) {
                String ym = mc.getString(0);
                try {
                    Calendar mCal = Calendar.getInstance();
                    mCal.setTime(new SimpleDateFormat("yyyy-MM", Locale.US).parse(ym));
                    int maxD = mCal.getActualMaximum(Calendar.DAY_OF_MONTH);
                    String mName = new SimpleDateFormat("MMMM yyyy", Locale.US).format(mCal.getTime());
                    String d2S = ym + "-16", d2E = ym + String.format(Locale.US, "-%02d", maxD);
                    Cursor c2 = db.rawQuery("SELECT SUM(o), SUM(l), SUM(p), SUM(k) FROM prf WHERE n = ? AND dt >= ? AND dt <= ?", new String[]{name, d2S, d2E});
                    if (c2 != null && c2.moveToFirst() && (c2.getInt(0) + c2.getInt(2)) > 0) {
                        content.addView(makeInteractivePeriodCard(name, "🏪 " + mName + " Cyc 2 (16-" + maxD + ")", c2.getInt(0), c2.getInt(1), c2.getInt(2), c2.getInt(3), d2S, d2E));
                    }
                    if (c2 != null) c2.close();
                    String d1S = ym + "-01", d1E = ym + "-15";
                    Cursor c1 = db.rawQuery("SELECT SUM(o), SUM(l), SUM(p), SUM(k) FROM prf WHERE n = ? AND dt >= ? AND dt <= ?", new String[]{name, d1S, d1E});
                    if (c1 != null && c1.moveToFirst() && (c1.getInt(0) + c1.getInt(2)) > 0) {
                        content.addView(makeInteractivePeriodCard(name, "🏪 " + mName + " Cyc 1 (1-15)", c1.getInt(0), c1.getInt(1), c1.getInt(2), c1.getInt(3), d1S, d1E));
                    }
                    if (c1 != null) c1.close();
                } catch (Exception ignored) {}
            }
            if (mc != null) mc.close();
        } else {
            Cursor allC = db.rawQuery("SELECT dt, o, l, p, k FROM prf WHERE n = ? ORDER BY dt DESC LIMIT 90", new String[]{name});
            LinkedHashMap<String, ArrayList<String[]>> weeks = new LinkedHashMap<>();
            while (allC != null && allC.moveToNext()) {
                String d = allC.getString(0);
                String k = getWeekStartDate(d) + " to " + getWeekEndDate(d);
                if (!weeks.containsKey(k)) weeks.put(k, new ArrayList<>());
                weeks.get(k).add(new String[]{d, String.valueOf(allC.getInt(1)), String.valueOf(allC.getInt(2)), String.valueOf(allC.getInt(3)), String.valueOf(allC.getInt(4))});
            }
            if (allC != null) allC.close();

            for (String wR : weeks.keySet()) {
                int sumO = 0, sumL = 0, sumP = 0, sumK = 0;
                for (String[] day : weeks.get(wR)) {
                    sumO += Integer.parseInt(day[1]); sumL += Integer.parseInt(day[2]);
                    sumP += Integer.parseInt(day[3]); sumK += Integer.parseInt(day[4]);
                }
                String[] p = wR.split(" to ");
                content.addView(makeInteractivePeriodCard(name, "📊 Week: " + p[0] + " ➔ " + p[1], sumO, sumL, sumP, sumK, p[0], p[1]));
            }
        }
        sv.addView(content); pop.addView(sv);
        new AlertDialog.Builder(this).setView(pop).setPositiveButton("Close", null).show();
    }

    LinearLayout makeInteractivePeriodCard(String name, String title, int o, int l, int p, int k, String dS, String dE) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(box(Color.parseColor("#12141D"), 12, Color.parseColor("#38BDF8"), 1));
        card.setPadding(14, 12, 14, 12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, 10);
        card.setLayoutParams(lp);

        double ofdC = o > 0 ? ((double) l / o) * 100.0 : 0.0;
        double ofpC = p > 0 ? ((double) k / p) * 100.0 : 0.0;
        double totC = (o + p) > 0 ? ((double) (l + k) / (o + p)) * 100.0 : 0.0;

        card.addView(tv(title, Color.parseColor("#38BDF8"), 14f, true));
        card.addView(tv("🚚 OFD / DEL = " + o + " / " + l + " (" + String.format(Locale.US, "%.1f%%", ofdC) + ")", Color.WHITE, 13f, true));
        card.addView(tv("📦 OFP / PIK = " + p + " / " + k + " (" + String.format(Locale.US, "%.1f%%", ofpC) + ")  •  DNP = " + (o+p) + "/" + (l+k) + " (" + String.format(Locale.US, "%.1f%%", totC) + ")", Color.parseColor("#D1D5DB"), 12f, false));

        TextView tBadge = tv(getPerformanceBadge(ofdC, o), getPerformanceColor(ofdC, o), 11.5f, true);
        tBadge.setPadding(0, 2, 0, 8);
        card.addView(tBadge);

        Button bLogs = new Button(this);
        bLogs.setText("📅 View Day Logs");
        bLogs.setBackground(box(Color.parseColor("#1F222E"), 8, Color.parseColor("#00E676"), 1));
        bLogs.setTextColor(Color.parseColor("#00E676"));
        bLogs.setTextSize(11f);
        bLogs.setTypeface(Typeface.DEFAULT_BOLD);
        bLogs.setOnClickListener(v -> showDayByDayDialog(name, title, dS, dE));
        card.addView(bLogs);

        return card;
    }

    void showDayByDayDialog(String name, String pTitle, String dS, String dE) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat sdfD = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.US);
        LinearLayout pop = new LinearLayout(this);
        pop.setOrientation(LinearLayout.VERTICAL);
        pop.setPadding(18, 18, 18, 18);
        pop.setBackgroundColor(Color.parseColor("#090A0F"));
        pop.addView(tv("📅 " + name + "\n" + pTitle, Color.parseColor("#38BDF8"), 14.5f, true));

        ScrollView sv = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 10, 0, 0);

        Cursor c = db.rawQuery("SELECT dt, o, l, p, k FROM prf WHERE n = ? AND dt >= ? AND dt <= ? ORDER BY dt DESC", new String[]{name, dS, dE});
        while (c != null && c.moveToNext()) {
            String dt = c.getString(0);
            int o = c.getInt(1), l = c.getInt(2), p = c.getInt(3), k = c.getInt(4);
            double ofdC = o > 0 ? ((double) l / o) * 100.0 : 0.0;
            double ofpC = p > 0 ? ((double) k / p) * 100.0 : 0.0;
            String dayName = dt;
            try { Calendar cal = Calendar.getInstance(); cal.setTime(sdf.parse(dt)); dayName = sdfD.format(cal.getTime()); } catch (Exception ignored) {}

            LinearLayout dayCard = new LinearLayout(this);
            dayCard.setOrientation(LinearLayout.VERTICAL);
            dayCard.setBackground(box(Color.parseColor("#12141D"), 10, Color.parseColor("#1E2235"), 1));
            dayCard.setPadding(12, 10, 12, 10);
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(-1, -2);
            dLp.setMargins(0, 0, 0, 8);
            dayCard.setLayoutParams(dLp);

            dayCard.addView(tv("📅 " + dayName, Color.parseColor("#38BDF8"), 13f, true));
            dayCard.addView(tv("🚚 OFD/DEL: " + o + "/" + l + " (" + String.format(Locale.US, "%.1f%%", ofdC) + ")", Color.WHITE, 12.5f, false));
            dayCard.addView(tv("📦 OFP/PIK: " + p + "/" + k + " (" + String.format(Locale.US, "%.1f%%", ofpC) + ")  •  DNP: " + (o+p) + "/" + (l+k), Color.parseColor("#9CA3AF"), 11.5f, false));

            TextView tB = tv(getPerformanceBadge(ofdC, o), getPerformanceColor(ofdC, o), 11f, true);
            tB.setPadding(0, 2, 0, 6);
            dayCard.addView(tB);

            Button bDayShr = new Button(this);
            bDayShr.setText("📢 Share This Day");
            bDayShr.setBackground(box(Color.parseColor("#25D366"), 6, 0, 0));
            bDayShr.setTextColor(Color.BLACK);
            bDayShr.setTextSize(10.5f);
            bDayShr.setTypeface(Typeface.DEFAULT_BOLD);
            final String fD = dayName;
            bDayShr.setOnClickListener(v -> shareSingleAgentReport(name + " (" + fD + ")", o, l, p, k, o + p, l + k, ofdC));
            dayCard.addView(bDayShr);
            content.addView(dayCard);
        }
        if (c != null) c.close();
        sv.addView(content); pop.addView(sv);
        new AlertDialog.Builder(this).setView(pop).setPositiveButton("Close", null).show();
    }

    void showHubDetails(String hname) {
        Cursor c = db.rawQuery("SELECT dt, o, l, lc, p, k, kc, tc FROM hub_prf WHERE hname = ? ORDER BY dt DESC LIMIT 30", new String[]{hname});
        LinearLayout pop = new LinearLayout(this);
        pop.setOrientation(LinearLayout.VERTICAL);
        pop.setPadding(18, 18, 18, 18);
        pop.setBackgroundColor(Color.parseColor("#090A0F"));
        pop.addView(tv("🏢 " + hname + " History", Color.parseColor("#38BDF8"), 15f, true));
        ScrollView sv = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        while (c != null && c.moveToNext()) {
            TextView it = tv("📅 " + c.getString(0) + " | Conv: " + c.getString(7) + "\nOFD/DEL = " + c.getString(1) + "/" + c.getString(2) + " (" + c.getString(3) + ")\nOFP/PIK = " + c.getString(4) + "/" + c.getString(5) + " (" + c.getString(6) + ")", Color.WHITE, 12.5f, false);
            it.setBackground(box(Color.parseColor("#12141D"), 8, Color.parseColor("#1E2235"), 1));
            it.setPadding(12, 10, 12, 10);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, 8);
            it.setLayoutParams(lp);
            content.addView(it);
        }
        if (c != null) c.close();
        sv.addView(content); pop.addView(sv);
        new AlertDialog.Builder(this).setView(pop).setPositiveButton("Close", null).show();
    }

    void launchVoiceOTP() {
        try {
            Intent it = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            startActivityForResult(it, 101);
        } catch (Exception e) { Toast.makeText(this, "Voice recognition not available", Toast.LENGTH_SHORT).show(); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 101 && res == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) {
                String otp = r.get(0).replaceAll("[^0-9]", "");
                ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("OTP", otp));
                new AlertDialog.Builder(this).setTitle("🎙️ CAPTURED OTP").setMessage(otp + "\n\n✅ Copied to Clipboard!").setPositiveButton("OK", null).show();
            }
        }
    }

    void loadHubVsHub() {
        try {
            vHubCrd.removeAllViews();
            Cursor c = db.rawQuery("SELECT hname, o, l, lc, p, k, kc, dnp, dnpc, tc FROM hub_prf WHERE dt = (SELECT MAX(dt) FROM hub_prf) OR dt IS NULL", null);
            while (c != null && c.moveToNext()) {
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(box(Color.parseColor("#12141D"), 12, Color.parseColor("#1E2235"), 1));
                card.setPadding(16, 12, 16, 12);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
                clp.setMargins(0, 0, 0, 10);
                card.setLayoutParams(clp);
                String hname = c.getString(0);
                card.addView(tv("🏢 " + hname, Color.parseColor("#60A5FA"), 15f, true));
                card.addView(tv("OFD/DEL = " + c.getString(1) + "/" + c.getString(2) + " = " + c.getString(3), Color.parseColor("#D1D5DB"), 13f, false));
                card.addView(tv("OFP/PIK = " + c.getString(4) + "/" + c.getString(5) + " = " + c.getString(6), Color.parseColor("#D1D5DB"), 13f, false));
                card.addView(tv("DNP/DNPC = " + c.getString(7) + "/" + c.getString(8) + " = " + c.getString(9), Color.parseColor("#34D399"), 13f, true));
                card.setOnClickListener(v -> showHubDetails(hname));
                vHubCrd.addView(card);
            }
            if (c != null) c.close();
        } catch (Exception ignored) {}
    }

    void loadContacts() {
        vCntCrd.removeAllViews();
        Cursor c = db.rawQuery("SELECT name, role, phone FROM contacts", null);
        if (c != null && c.getCount() > 0) {
            while (c.moveToNext()) {
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(box(Color.parseColor("#12141D"), 12, Color.parseColor("#1E2235"), 1));
                card.setPadding(16, 12, 16, 12);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
                clp.setMargins(0, 0, 0, 8);
                card.setLayoutParams(clp);
                String phone = c.getString(2);
                card.addView(tv("👤 " + c.getString(0), Color.parseColor("#00E676"), 15f, true));
                card.addView(tv(c.getString(1) + " • 📞 " + phone, Color.parseColor("#D1D5DB"), 13f, false));
                LinearLayout r = new LinearLayout(this);
                r.setPadding(0, 6, 0, 0);
                Button bCall = new Button(this); bCall.setText("📞 Call");
                bCall.setBackground(box(Color.parseColor("#0284C7"), 6, 0, 0));
                bCall.setTextColor(Color.WHITE); bCall.setTextSize(11f);
                bCall.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone))));
                Button bWp = new Button(this); bWp.setText("💬 WhatsApp");
                bWp.setBackground(box(Color.parseColor("#25D366"), 6, 0, 0));
                bWp.setTextColor(Color.BLACK); bWp.setTextSize(11f);
                bWp.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=91" + phone))));
                LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, -2, 1f);
                bLp.setMargins(0, 0, 6, 0);
                r.addView(bCall, bLp); r.addView(bWp, new LinearLayout.LayoutParams(0, -2, 1f));
                card.addView(r);
                vCntCrd.addView(card);
            }
        } else {
            vCntCrd.addView(tv("Sheet Column R (Name), S (Role), T (Phone) भरें और SYNC दबाएं।", Color.parseColor("#9CA3AF"), 13f, false));
        }
        if (c != null) c.close();
    }

    void qry(String q) {
        ords.clear();
        if (!q.isEmpty()) {
            Cursor c = db.rawQuery("SELECT t, d FROM ord WHERE t LIKE ? LIMIT 50", new String[]{"%" + q + "%"});
            while (c != null && c.moveToNext()) ords.add(new String[]{c.getString(0), c.getString(1)});
            if (c != null) c.close();
        }
        if (adp != null) adp.notifyDataSetChanged();
    }

    void cnt() {
        try {
            Cursor c = db.rawQuery("SELECT COUNT(*) FROM ord", null);
            if (c != null && c.moveToFirst()) tCnt.setText("📦 Total Trackable Orders: " + c.getInt(0));
            if (c != null) c.close();
        } catch (Exception ignored) {}
    }

    ArrayList<String> fastSplitCsv(String line) {
        ArrayList<String> res = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\"') inQuotes = !inQuotes;
            else if (ch == ',' && !inQuotes) { res.add(sb.toString()); sb.setLength(0); }
            else sb.append(ch);
        }
        res.add(sb.toString());
        return res;
    }

    void doSync(boolean isAuto) {
        new Handler(Looper.getMainLooper()).post(() -> { if (!isAuto && loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE); });
        try {
            String targetUrl = CSV;
            HttpURLConnection conn = null;
            for (int i = 0; i < 5; i++) {
                conn = (HttpURLConnection) new URL(targetUrl).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(8000); conn.setReadTimeout(8000);
                conn.setInstanceFollowRedirects(false);
                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) targetUrl = conn.getHeaderField("Location");
                else break;
            }
            InputStream is = (conn != null) ? conn.getInputStream() : null;
            if (is == null) throw new Exception("Connect failed");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line, opDate = getOperationalDate();
            int parsed = 0;

            db.beginTransaction();
            db.execSQL("DELETE FROM ord"); db.execSQL("DELETE FROM contacts");
            db.execSQL("DELETE FROM hub_prf WHERE dt = '" + opDate + "'");
            ArrayList<ContentValues> temp = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                ArrayList<String> p = fastSplitCsv(line);
                if (p.size() >= 1) {
                    String tId = clean(p.get(0)), oId = (p.size() > 1) ? clean(p.get(1)) : "";
                    if (!tId.isEmpty() && !tId.equalsIgnoreCase("TRACKING ID") && !tId.equalsIgnoreCase("TRACK ID")) {
                        ContentValues ocv = new ContentValues();
                        ocv.put("t", tId); ocv.put("d", oId.isEmpty() ? tId : oId);
                        db.insertWithOnConflict("ord", null, ocv, SQLiteDatabase.CONFLICT_REPLACE);
                    }
                }
                if (p.size() > 2) {
                    String name = clean(p.get(2));
                    if (!name.isEmpty() && !name.equalsIgnoreCase("NAME") && !name.contains("Total") && !name.contains("#N/A")) {
                        int o = p.size() > 3 ? parseInt(p.get(3)) : 0, l = p.size() > 4 ? parseInt(p.get(4)) : 0;
                        int op = p.size() > 5 ? parseInt(p.get(5)) : 0, k = p.size() > 6 ? parseInt(p.get(6)) : 0;
                        if (o > 0 || l > 0 || op > 0 || k > 0) {
                            ContentValues cv = new ContentValues();
                            cv.put("n", name); cv.put("o", o); cv.put("l", l); cv.put("p", op); cv.put("k", k); cv.put("dt", opDate);
                            temp.add(cv); parsed++;
                        }
                    }
                }
                                if (p.size() > 8) {
                    String hname = clean(p.get(8));
                    if (!hname.isEmpty() && !hname.equalsIgnoreCase("HUB NAME")) {
                        ContentValues hcv = new ContentValues();
                        hcv.put("hname", hname);
                        hcv.put("o", p.size() > 9 ? clean(p.get(9)) : "0");
                        hcv.put("l", p.size() > 10 ? clean(p.get(10)) : "0");
                        hcv.put("lc", p.size() > 11 ? clean(p.get(11)) : "0%");
                        hcv.put("p", p.size() > 12 ? clean(p.get(12)) : "0");
                        hcv.put("k", p.size() > 13 ? clean(p.get(13)) : "0");
                        hcv.put("kc", p.size() > 14 ? clean(p.get(14)) : "0%");
                        hcv.put("tc", p.size() > 15 ? clean(p.get(15)) : "0%");
                        hcv.put("dnp", String.valueOf(parseInt(p.get(9)) + parseInt(p.get(12))));
                        hcv.put("dnpc", String.valueOf(parseInt(p.get(10)) + parseInt(p.get(13))));
                        hcv.put("dt", opDate);
                        db.insert("hub_prf", null, hcv);
                    }
                }
                if (p.size() > 19) {
                    String cN = clean(p.get(17)), cR = clean(p.get(18)), cP = clean(p.get(19));
                    if (!cN.isEmpty() && !cN.equalsIgnoreCase("NAME") && cP.matches(".*\\d+.*")) {
                        ContentValues cntCv = new ContentValues();
                        cntCv.put("name", cN); cntCv.put("role", cR.isEmpty() ? "Staff" : cR); cntCv.put("phone", cP);
                        db.insert("contacts", null, cntCv);
                    }
                }
            }
                        if (parsed > 0) {
                db.execSQL("DELETE FROM prf WHERE dt = '" + opDate + "'");
                for (ContentValues cv : temp) db.insert("prf", null, cv);
            }
            db.setTransactionSuccessful();
            db.endTransaction();
            reader.close();
            lastSyncTime = System.currentTimeMillis();

            new Handler(Looper.getMainLooper()).post(() -> {
                load(); loadHubVsHub(); loadContacts(); cnt(); qry("");
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                if (!isAuto) Toast.makeText(MainActivity.this, "✅ Synced Successfully!", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                if (!isAuto) Toast.makeText(MainActivity.this, "Sync Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    String clean(String s) { return s == null ? "" : s.replace("\"", "").trim(); }
    int parseInt(String s) { try { return Integer.parseInt(clean(s).replace("%", "")); } catch (Exception e) { return 0; } }
}
