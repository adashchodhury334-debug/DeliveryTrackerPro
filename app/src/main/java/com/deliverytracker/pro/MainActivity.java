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
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
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
    Button bT, bP, bH, bC, bSort, bShareHub, bVoiceOtp, bCalc, bCatAgent, bCatKirana, bCatAll, bSubDay, bSubYearly;
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

        bCalc = new Button(this);
        bCalc.setText("🧮 CALC");
        bCalc.setBackground(box(Color.parseColor("#0284C7"), 8, 0, 0));
        bCalc.setTextColor(Color.WHITE);
        bCalc.setTypeface(Typeface.DEFAULT_BOLD);
        bCalc.setTextSize(11f);
        bCalc.setOnClickListener(v -> showConversionCalculatorDialog());
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-2, -2);
        cLp.setMargins(0, 0, 6, 0);
        h.addView(bCalc, cLp);

        bVoiceOtp = new Button(this);
        bVoiceOtp.setText("🎙️ OTP");
        bVoiceOtp.setBackground(box(Color.parseColor("#7C3AED"), 8, 0, 0));
        bVoiceOtp.setTextColor(Color.WHITE);
        bVoiceOtp.setTypeface(Typeface.DEFAULT_BOLD);
        bVoiceOtp.setTextSize(11f);
        bVoiceOtp.setOnClickListener(v -> launchVoiceOTP());
        LinearLayout.LayoutParams voLp = new LinearLayout.LayoutParams(-2, -2);
        voLp.setMargins(0, 0, 6, 0);
        h.addView(bVoiceOtp, voLp);

        Button bRef = new Button(this);
        bRef.setText("🔄 SYNC");
        bRef.setBackground(box(Color.parseColor("#00E676"), 8, 0, 0));
        bRef.setTextColor(Color.BLACK);
        bRef.setTypeface(Typeface.DEFAULT_BOLD);
        bRef.setTextSize(11f);
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

            String w;
            if ("daily".equals(mode)) {
                w = " WHERE dt = (SELECT MAX(dt) FROM prf) ";
            } else {
                w = " WHERE dt >= '" + getYearStartDate(opDate) + "' ";
            }

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
                double ofpC = p > 0 ? ((double) k / p) * 100.0 : 0.0;

                list.add(new String[]{name, String.valueOf(o), String.valueOf(l), String.valueOf(p), String.valueOf(k), String.valueOf(dnp), String.valueOf(dnpc), String.format(Locale.US, "%.1f", ofdC), String.valueOf(r), String.format(Locale.US, "%.1f", ofpC)});
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
                double dnpConv = Double.parseDouble(ag[8]);
                double ofpConv = Double.parseDouble(ag[9]);
                int badgeColor = getPerformanceColor(ofdConv, o);

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(box(Color.parseColor("#12141D"), 14, Color.parseColor("#00E676"), 1));
                card.setPadding(0, 0, 0, 0);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
                clp.setMargins(0, 0, 0, 14);
                card.setLayoutParams(clp);

                LinearLayout topRow = new LinearLayout(this);
                topRow.setOrientation(LinearLayout.HORIZONTAL);
                topRow.setGravity(Gravity.CENTER_VERTICAL);
                topRow.setBackground(box(Color.parseColor("#171926"), 0, 0, 0));

                LinearLayout box1 = new LinearLayout(this);
                box1.setOrientation(LinearLayout.HORIZONTAL);
                box1.setGravity(Gravity.CENTER_VERTICAL);
                box1.setPadding(14, 12, 12, 12);

                String rBadge = (currentRank == 1) ? "🥇 #1" : ((currentRank == 2) ? "🥈 #2" : ((currentRank == 3) ? "🥉 #3" : "#" + currentRank));
                int rBg = (currentRank == 1) ? Color.parseColor("#EAB308") : ((currentRank == 2) ? Color.parseColor("#94A3B8") : ((currentRank == 3) ? Color.parseColor("#B45309") : Color.parseColor("#374151")));
                TextView tRnk = tv(rBadge, Color.WHITE, 11f, true);
                tRnk.setBackground(box(rBg, 6, 0, 0));
                tRnk.setPadding(8, 3, 8, 3);
                box1.addView(tRnk);

                TextView tName = tv(" " + ag[0], badgeColor, 15f, true);
                box1.addView(tName);
                topRow.addView(box1, new LinearLayout.LayoutParams(0, -1, 1f));

                View vDivTop = new View(this);
                vDivTop.setBackgroundColor(Color.parseColor("#00E676"));
                topRow.addView(vDivTop, new LinearLayout.LayoutParams(2, -1));

                LinearLayout box2 = new LinearLayout(this);
                box2.setOrientation(LinearLayout.VERTICAL);
                box2.setGravity(Gravity.CENTER);
                box2.setPadding(12, 10, 12, 10);

                String sCur = "🔥 " + curStrk + "D";
                TextView tCur = tv(sCur, Color.parseColor("#FBBF24"), 11f, true);
                tCur.setBackground(box(Color.parseColor("#1C1E2A"), 6, 0, 0));
                tCur.setPadding(8, 2, 8, 2);
                box2.addView(tCur);

                if (prevStrk > 0) {
                    TextView tPrv = tv("Prev: " + prevStrk + "D", Color.parseColor("#9CA3AF"), 10f, false);
                    tPrv.setPadding(0, 2, 0, 0);
                    box2.addView(tPrv);
                }
                topRow.addView(box2, new LinearLayout.LayoutParams(-2, -1));
                card.addView(topRow);

                View hDiv = new View(this);
                hDiv.setBackgroundColor(Color.parseColor("#00E676"));
                card.addView(hDiv, new LinearLayout.LayoutParams(-1, 2));

                LinearLayout bottomRow = new LinearLayout(this);
                bottomRow.setOrientation(LinearLayout.HORIZONTAL);
                bottomRow.setGravity(Gravity.CENTER_VERTICAL);

                LinearLayout box3 = new LinearLayout(this);
                box3.setOrientation(LinearLayout.VERTICAL);
                box3.setPadding(14, 12, 12, 12);

                box3.addView(tv("🚚 OFD / DEL: " + o + " / " + l + " ➔ " + ag[7] + "% DEL", badgeColor, 13.5f, true));
                box3.addView(tv("📦 OFP / PIK: " + p + " / " + k + " ➔ " + String.format(Locale.US, "%.1f%%", ofpConv) + " PIK", Color.parseColor("#38BDF8"), 12.5f, true));
                box3.addView(tv("🔄 DNP / DNPC: " + dnp + " / " + dnpc + " ➔ " + String.format(Locale.US, "%.1f%%", dnpConv) + " DNP", Color.parseColor("#34D399"), 12.5f, true));

                int diff = (int) Math.ceil(0.92 * o) - l;
                if (diff <= 0 && o > 0) {
                    box3.addView(tv("🎯 92% Target Achieved! 🚀", Color.parseColor("#00E676"), 11.5f, true));
                } else if (o > 0) {
                    box3.addView(tv("🎯 Gap to 92%: " + diff + " more DEL required", Color.parseColor("#FB923C"), 11.5f, true));
                }

                if ("daily".equals(mode)) {
                    Cursor yc = db.rawQuery("SELECT dt, o, l, (CAST(l AS REAL)*100.0/CASE WHEN o>0 THEN o ELSE 1 END) FROM prf WHERE n = ? AND dt < ? AND o > 0 ORDER BY dt DESC LIMIT 1", new String[]{ag[0], opDate});
                    if (yc != null && yc.moveToFirst()) {
                        String yDt = yc.getString(0);
                        int yO = yc.getInt(1), yL = yc.getInt(2);
                        double yConv = yc.getDouble(3);
                        int yGap = (int) Math.ceil(0.92 * yO) - yL;
                        TextView tPrev = tv(yConv < 92.0 ? "⚠️ Prev Day (" + yDt + "): Missed by " + yGap + " DEL (" + String.format(Locale.US, "%.1f%%", yConv) + ")" : "✅ Prev Day (" + yDt + "): 92% Achieved", yConv < 92.0 ? Color.parseColor("#EF4444") : Color.parseColor("#10B981"), 11f, true);
                        tPrev.setPadding(0, 2, 0, 0);
                        box3.addView(tPrev);
                    }
                    if (yc != null) yc.close();
                }
                bottomRow.addView(box3, new LinearLayout.LayoutParams(0, -2, 1f));

                View vDivBottom = new View(this);
                vDivBottom.setBackgroundColor(Color.parseColor("#00E676"));
                bottomRow.addView(vDivBottom, new LinearLayout.LayoutParams(2, -1));

                LinearLayout box4 = new LinearLayout(this);
                box4.setOrientation(LinearLayout.VERTICAL);
                box4.setGravity(Gravity.CENTER);
                box4.setPadding(12, 10, 12, 10);
                final String agN = ag[0];

                if ("yearly".equals(mode)) {
                    Button bView = new Button(this);
                    bView.setText("📊 View");
                    bView.setBackground(box(Color.parseColor("#1F2232"), 6, Color.parseColor("#38BDF8"), 1));
                    bView.setTextColor(Color.parseColor("#38BDF8"));
                    bView.setTextSize(10f);
                    bView.setTypeface(Typeface.DEFAULT_BOLD);
                    bView.setPadding(10, 4, 10, 4);
                    LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(-2, -2);
                    vLp.setMargins(0, 0, 0, 6);
                    bView.setLayoutParams(vLp);
                    bView.setOnClickListener(v -> showYearlyDetails(agN));
                    box4.addView(bView);
                }

                Button bShr = new Button(this);
                bShr.setText("📢 Share");
                bShr.setBackground(box(Color.parseColor("#25D366"), 6, 0, 0));
                bShr.setTextColor(Color.BLACK);
                bShr.setTextSize(10.5f);
                bShr.setTypeface(Typeface.DEFAULT_BOLD);
                bShr.setPadding(12, 6, 12, 6);
                bShr.setOnClickListener(v -> shareSingleAgentReport(agN, o, l, p, k, dnp, dnpc, ofdConv, ofpConv, dnpConv));
                box4.addView(bShr);

                bottomRow.addView(box4, new LinearLayout.LayoutParams(-2, -1));
                card.addView(bottomRow);

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
                double ofpC = tp > 0 ? ((double) tk / tp) * 100.0 : 0.0;
                double dnpC = (to + tp) > 0 ? ((double) (tl + tk) / (to + tp)) * 100.0 : 0.0;
                sb.append("🚚 *Hub OFD / DEL:* ").append(to).append(" / ").append(tl).append(" (").append(String.format(Locale.US, "%.1f%%", conv)).append(")\n");
                sb.append("📦 *Hub OFP / PIK:* ").append(tp).append(" / ").append(tk).append(" (").append(String.format(Locale.US, "%.1f%%", ofpC)).append(")\n");
                sb.append("🔄 *Hub DNP / DNPC:* ").append(to + tp).append(" / ").append(tl + tk).append(" (").append(String.format(Locale.US, "%.1f%%", dnpC)).append(")\n");
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

    void shareSingleAgentReport(String name, int ofd, int del, int ofp, int pik, int dnp, int dnpc, double ofdC, double ofpC, double dnpC) {
        StringBuilder sb = new StringBuilder();
        sb.append("👤 *DELIVERY SCORECARD*\n📛 *Name:* ").append(name).append("\n📅 *Date:* ").append(getOperationalDate()).append("\n━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🚚 *OFD / DEL:* ").append(ofd).append(" / ").append(del).append(" (").append(String.format(Locale.US, "%.1f%%", ofdC)).append(")\n");
        sb.append("📦 *OFP / PIK:* ").append(ofp).append(" / ").append(pik).append(" (").append(String.format(Locale.US, "%.1f%%", ofpC)).append(")\n");
        sb.append("🔄 *DNP / DNPC:* ").append(dnp).append(" / ").append(dnpc).append(" (").append(String.format(Locale.US, "%.1f%%", dnpC)).append(")\n");
        int diff = (int) Math.ceil(0.92 * ofd) - del;
        sb.append(diff <= 0 && ofd > 0 ? "🎯 *Target:* 92% Achieved! 🚀\n" : "🎯 *Target Gap:* " + diff + " more DEL required\n");
        sb.append("🏆 *Rating:* ").append(getPerformanceBadge(ofdC, ofd)).append("\n━━━━━━━━━━━━━━━━━━━━\n⚡ _Managed by Adarsh_");
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
                    if (prevCal.get(Calendar.YEAR) == dates.get(i).get(Calendar.YEAR) &&
                        prevCal.get(Calendar.DAY_OF_YEAR) == dates.get(i).get(Calendar.DAY_OF_YEAR)) {
                        cur++; i++;
                    } else break;
                }
                while (i < dates.size()) {
                    prev = 1; i++;
                    while (i < dates.size()) {
                        Calendar prevCal = (Calendar) dates.get(i - 1).clone();
                        prevCal.add(Calendar.DAY_OF_YEAR, -1);
                        if (prevCal.get(Calendar.YEAR) == dates.get(i).get(Calendar.YEAR) &&
                            prevCal.get(Calendar.DAY_OF_YEAR) == dates.get(i).get(Calendar.DAY_OF_YEAR)) {
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
         void showConversionCalculatorDialog() {
        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.VERTICAL);
        d.setPadding(20, 18, 20, 18);
        d.setBackgroundColor(Color.parseColor("#0F1015"));

        d.addView(tv("🧮 CONV & TARGET CALCULATOR", Color.parseColor("#38BDF8"), 15f, true));

        EditText etSearch = new EditText(this);
        etSearch.setHint("🔍 Search Agent / Kirana (A-Z)...");
        etSearch.setHintTextColor(Color.parseColor("#717688"));
        etSearch.setTextColor(Color.WHITE);
        etSearch.setBackground(box(Color.parseColor("#161824"), 10, Color.parseColor("#38BDF8"), 1));
        etSearch.setPadding(14, 10, 14, 10);
        etSearch.setTextSize(13f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 8, 0, 6);
        etSearch.setLayoutParams(lp);
        d.addView(etSearch);

        Spinner spNames = new Spinner(this);
        ArrayList<String> namesList = new ArrayList<>();
        namesList.add("-- Select Active Agent / Kirana --");
        
        Cursor c = db.rawQuery("SELECT DISTINCT n FROM prf ORDER BY n ASC", null);
        while (c != null && c.moveToNext()) {
            String n = c.getString(0);
            if (n != null && !n.isEmpty()) namesList.add(n);
        }
        if (c != null) c.close();

        ArrayAdapter<String> nameAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, namesList);
        spNames.setAdapter(nameAdapter);
        d.addView(spNames);

        EditText etOfdTotal = new EditText(this);
        etOfdTotal.setHint("OFD Total (Assigned)");
        etOfdTotal.setHintTextColor(Color.parseColor("#717688"));
        etOfdTotal.setTextColor(Color.WHITE);
        etOfdTotal.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etOfdTotal.setBackground(box(Color.parseColor("#161824"), 10, Color.parseColor("#00E676"), 1));
        etOfdTotal.setPadding(14, 10, 14, 10);
        etOfdTotal.setLayoutParams(lp);
        d.addView(etOfdTotal);

        EditText etDelDone = new EditText(this);
        etDelDone.setHint("DEL Done (Delivered)");
        etDelDone.setHintTextColor(Color.parseColor("#717688"));
        etDelDone.setTextColor(Color.WHITE);
        etDelDone.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etDelDone.setBackground(box(Color.parseColor("#161824"), 10, Color.parseColor("#00E676"), 1));
        etDelDone.setPadding(14, 10, 14, 10);
        etDelDone.setLayoutParams(lp);
        d.addView(etDelDone);

        EditText etOfpTotal = new EditText(this);
        etOfpTotal.setHint("OFP Total (Assigned)");
        etOfpTotal.setHintTextColor(Color.parseColor("#717688"));
        etOfpTotal.setTextColor(Color.WHITE);
        etOfpTotal.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etOfpTotal.setBackground(box(Color.parseColor("#161824"), 10, Color.parseColor("#38BDF8"), 1));
        etOfpTotal.setPadding(14, 10, 14, 10);
        etOfpTotal.setLayoutParams(lp);
        d.addView(etOfpTotal);

        EditText etPikDone = new EditText(this);
        etPikDone.setHint("PIK Done (Picked)");
        etPikDone.setHintTextColor(Color.parseColor("#717688"));
        etPikDone.setTextColor(Color.WHITE);
        etPikDone.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etPikDone.setBackground(box(Color.parseColor("#161824"), 10, Color.parseColor("#38BDF8"), 1));
        etPikDone.setPadding(14, 10, 14, 10);
        etPikDone.setLayoutParams(lp);
        d.addView(etPikDone);

        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase(Locale.ROOT).trim();
                ArrayList<String> filtered = new ArrayList<>();
                filtered.add("-- Select Active Agent / Kirana --");
                Cursor fc = db.rawQuery("SELECT DISTINCT n FROM prf ORDER BY n ASC", null);
                while (fc != null && fc.moveToNext()) {
                    String n = fc.getString(0);
                    if (n != null && n.toLowerCase(Locale.ROOT).contains(query)) {
                        filtered.add(n);
                    }
                }
                if (fc != null) fc.close();
                ArrayAdapter<String> fAdapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, filtered);
                spNames.setAdapter(fAdapter);
            }
            public void afterTextChanged(Editable s) {}
        });

        spNames.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selectedName = (String) spNames.getSelectedItem();
                if (selectedName != null && !selectedName.startsWith("--")) {
                    Cursor sc = db.rawQuery("SELECT o, l, p, k FROM prf WHERE n = ? ORDER BY dt DESC LIMIT 1", new String[]{selectedName});
                    if (sc != null && sc.moveToFirst()) {
                        etOfdTotal.setText(String.valueOf(sc.getInt(0)));
                        etDelDone.setText(String.valueOf(sc.getInt(1)));
                        etOfpTotal.setText(String.valueOf(sc.getInt(2)));
                        etPikDone.setText(String.valueOf(sc.getInt(3)));
                    }
                    if (sc != null) sc.close();
                }
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        ScrollView sv = new ScrollView(this);
        LinearLayout resBox = new LinearLayout(this);
        resBox.setOrientation(LinearLayout.VERTICAL);
        resBox.setPadding(0, 8, 0, 8);
        sv.addView(resBox);
        d.addView(sv, new LinearLayout.LayoutParams(-1, 240));

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button bCalcRes = new Button(this);
        bCalcRes.setText("⚡ CALCULATE");
        bCalcRes.setBackground(box(Color.parseColor("#00E676"), 8, 0, 0));
        bCalcRes.setTextColor(Color.BLACK);
        bCalcRes.setTypeface(Typeface.DEFAULT_BOLD);
        bCalcRes.setTextSize(11f);

        Button bShareCalc = new Button(this);
        bShareCalc.setText("📢 SHARE RESULT");
        bShareCalc.setBackground(box(Color.parseColor("#25D366"), 8, 0, 0));
        bShareCalc.setTextColor(Color.BLACK);
        bShareCalc.setTypeface(Typeface.DEFAULT_BOLD);
        bShareCalc.setTextSize(11f);

        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, -2, 1f);
        bLp.setMargins(0, 4, 4, 0);
        btnRow.addView(bCalcRes, bLp);
        btnRow.addView(bShareCalc, new LinearLayout.LayoutParams(0, -2, 1f));
        d.addView(btnRow);

        bCalcRes.setOnClickListener(v -> {
            try {
                int ofd = parseInt(etOfdTotal.getText().toString());
                int del = parseInt(etDelDone.getText().toString());
                int ofp = parseInt(etOfpTotal.getText().toString());
                int pik = parseInt(etPikDone.getText().toString());

                resBox.removeAllViews();
                if (ofd <= 0 && ofp <= 0) {
                    resBox.addView(tv("⚠️ Please enter valid totals", Color.parseColor("#EF4444"), 13f, true));
                    return;
                }

                double ofdC = ofd > 0 ? ((double) del / ofd) * 100.0 : 0.0;
                double ofpC = ofp > 0 ? ((double) pik / ofp) * 100.0 : 0.0;
                int totDnp = ofd + ofp;
                int totDnpc = del + pik;
                double totC = totDnp > 0 ? ((double) totDnpc / totDnp) * 100.0 : 0.0;

                int targetNeeded = (int) Math.ceil(0.92 * ofd);
                int gap = targetNeeded - del;

                resBox.addView(tv("📊 Calculation Results:", Color.parseColor("#38BDF8"), 13f, true));
                resBox.addView(tv("• OFD/DEL Conv: " + String.format(Locale.US, "%.1f%%", ofdC), Color.parseColor("#00E676"), 13.5f, true));
                resBox.addView(tv("• OFP/PIK Conv: " + String.format(Locale.US, "%.1f%%", ofpC), Color.parseColor("#38BDF8"), 13.5f, true));
                resBox.addView(tv("• Total DNP Conv: " + String.format(Locale.US, "%.1f%%", totC), Color.parseColor("#34D399"), 13.5f, true));

                if (ofd > 0) {
                    if (gap <= 0) {
                        resBox.addView(tv("• 92% Target: Achieved! 🚀", Color.parseColor("#00E676"), 13f, true));
                    } else {
                        resBox.addView(tv("• 92% Target Gap: Need " + gap + " more DEL", Color.parseColor("#FB923C"), 13f, true));
                    }
                }
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Enter valid numbers", Toast.LENGTH_SHORT).show();
            }
        });

        bShareCalc.setOnClickListener(v -> {
            try {
                int ofd = parseInt(etOfdTotal.getText().toString());
                int del = parseInt(etDelDone.getText().toString());
                int ofp = parseInt(etOfpTotal.getText().toString());
                int pik = parseInt(etPikDone.getText().toString());
                double ofdC = ofd > 0 ? ((double) del / ofd) * 100.0 : 0.0;
                double ofpC = ofp > 0 ? ((double) pik / ofp) * 100.0 : 0.0;
                int totDnp = ofd + ofp;
                int totDnpc = del + pik;
                double totC = totDnp > 0 ? ((double) totDnpc / totDnp) * 100.0 : 0.0;
                int gap = (int) Math.ceil(0.92 * ofd) - del;

                String selName = (String) spNames.getSelectedItem();
                String agentTitle = (selName != null && !selName.startsWith("--")) ? selName : "Manual Calculation";

                StringBuilder sb = new StringBuilder();
                sb.append("🧮 *CONVERSION & TARGET REPORT*\n");
                sb.append("👤 *Name:* ").append(agentTitle).append("\n");
                sb.append("📅 *Date:* ").append(getOperationalDate()).append("\n");
                sb.append("━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("🚚 *OFD / DEL:* ").append(ofd).append(" / ").append(del).append(" (").append(String.format(Locale.US, "%.1f%%", ofdC)).append(")\n");
                sb.append("📦 *OFP / PIK:* ").append(ofp).append(" / ").append(pik).append(" (").append(String.format(Locale.US, "%.1f%%", ofpC)).append(")\n");
                sb.append("🔄 *Total DNP:* ").append(totDnp).append(" / ").append(totDnpc).append(" (").append(String.format(Locale.US, "%.1f%%", totC)).append(")\n");
                sb.append(gap <= 0 && ofd > 0 ? "🎯 *Target:* 92% Achieved! 🚀\n" : "🎯 *Target Gap:* " + gap + " more DEL required\n");
                sb.append("━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("⚡ _Calculated via Delivery Tracker Pro_");

                Intent it = new Intent(Intent.ACTION_SEND);
                it.setType("text/plain");
                it.putExtra(Intent.EXTRA_TEXT, sb.toString());
                startActivity(Intent.createChooser(it, "📢 Share Calculation"));
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Please calculate first", Toast.LENGTH_SHORT).show();
            }
        });

        new AlertDialog.Builder(this)
            .setView(d)
            .setPositiveButton("Close", null)
            .show();
    }

    String clean(String s) { return s == null ? "" : s.replace("\"", "").trim(); }
    int parseInt(String s) { try { return Integer.parseInt(clean(s).replace("%", "")); } catch (Exception e) { return 0; } }
}
