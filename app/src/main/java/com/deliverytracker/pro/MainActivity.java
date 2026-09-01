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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;

public class MainActivity extends Activity {
    SQLiteDatabase db;
    FrameLayout root;
    LinearLayout vTrk, vPrf, vHub, vCnt, vCrd, vHubCrd, vCntCrd, loadingOverlay;
    LinearLayout periodFilterRow;
    Button bT, bP, bH, bC, bSort, bVsBattle, bVoiceOtp;
    Button bCatAgent, bCatKirana, bCatAll;
    Button bSubDay, bSubYearly;
    TextView tCnt, tHubOfdDel, tHubOfpPik, tHubDnpDnpc, tTopConv, tTopDnpc, tGapTarget, tPersonalBest;
    ArrayList<String[]> ords = new ArrayList<>();
    ArrayList<String> agentNamesList = new ArrayList<>();
    BaseAdapter adp;
    
    String currentCategory = "AGENT"; // "AGENT", "KIRANA", "ALL"
    String mode = "daily";            // "daily", "yearly"
    boolean isHighToLow = true;
    String CSV = "https://docs.google.com/spreadsheets/d/1Dul38iNZ_eNmABVuYVWhrUg9F_xVMvaVvQvLIXlySj4/export?format=csv&gid=0";
    static final int REQ_CODE_SPEECH = 101;
    long lastSyncTime = 0;

    Handler autoSyncHandler = new Handler(Looper.getMainLooper());
    Runnable autoSyncRunnable = new Runnable() {
        @Override
        public void run() {
            new Thread(() -> doSync(true)).start();
            autoSyncHandler.postDelayed(this, 120000);
        }
    };

    GradientDrawable box(int c, int r, int sCol, int sW) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c);
        g.setCornerRadius(r);
        if (sW > 0) g.setStroke(sW, sCol);
        return g;
    }

    boolean isKiranaAgent(String name) {
        if (name == null) return false;
        String n = name.toUpperCase(Locale.ROOT).trim();
        return n.contains("KIRANA") || n.contains("RATAN SARKAR") || n.equals("RATAN SARKAR");
    }

    String getPerformanceBadge(double delConv, int ofd) {
        if (ofd == 0) return "⚪ NO OFD ASSIGNED";
        if (delConv >= 96.0) return "🌟 EXCELLENT PERFORMANCE";
        if (delConv >= 92.0) return "🔥 BEST PERFORMANCE (Target Met)";
        if (delConv >= 88.0) return "⚠️ FOCUS PERFORMANCE";
        return "🚨 NOT ACCEPTED - IMPROVE YOUR PERFORMANCE";
    }

    int getPerformanceColor(double delConv, int ofd) {
        if (ofd == 0) return Color.parseColor("#9CA3AF");
        if (delConv >= 96.0) return Color.parseColor("#38BDF8");
        if (delConv >= 92.0) return Color.parseColor("#00E676");
        if (delConv >= 88.0) return Color.parseColor("#FBBF24");
        return Color.parseColor("#EF4444");
    }

    String getOperationalDate() {
        Calendar cal = Calendar.getInstance();
        if (cal.get(Calendar.HOUR_OF_DAY) < 2) cal.add(Calendar.DATE, -1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    String getYearStartDate(String opDateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(opDateStr));
            cal.set(Calendar.DAY_OF_YEAR, 1);
            return sdf.format(cal.getTime());
        } catch (Exception e) {
            return opDateStr;
        }
    }

    String getWeekStartDate(String dateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(dateStr));
            cal.setFirstDayOfWeek(Calendar.MONDAY);
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            int diff = (dayOfWeek == Calendar.SUNDAY) ? 6 : (dayOfWeek - Calendar.MONDAY);
            cal.add(Calendar.DAY_OF_MONTH, -diff);
            return sdf.format(cal.getTime());
        } catch (Exception e) {
            return dateStr;
        }
    }

    String getWeekEndDate(String dateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(getWeekStartDate(dateStr)));
            cal.add(Calendar.DAY_OF_MONTH, 6);
            return sdf.format(cal.getTime());
        } catch (Exception e) {
            return dateStr;
        }
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
            try { db.execSQL("ALTER TABLE hub_prf ADD COLUMN dt TEXT;"); } catch (Exception ignored) {}
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_ord_t ON ord(t);");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_prf_dt ON prf(dt);");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_prf_n ON prf(n);");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_hub_dt ON hub_prf(dt);");
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
        if (System.currentTimeMillis() - lastSyncTime > 120000) {
            new Thread(() -> doSync(true)).start();
        }
        autoSyncHandler.removeCallbacks(autoSyncRunnable);
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
        h.setBackgroundColor(Color.parseColor("#181920"));
        h.setPadding(16, 12, 16, 12);
        h.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(this);
        t.setText("📦 Delivery Tracker Pro");
        t.setTextColor(Color.WHITE);
        t.setTextSize(15f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        titleBox.addView(t);

        TextView tSub = new TextView(this);
        tSub.setText("⚡ Managed by Adarsh");
        tSub.setTextColor(Color.parseColor("#38BDF8"));
        tSub.setTextSize(11f);
        tSub.setTypeface(Typeface.DEFAULT_BOLD);
        titleBox.addView(tSub);

        h.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1f));

        bVoiceOtp = new Button(this);
        bVoiceOtp.setText("🎙️ OTP");
        bVoiceOtp.setBackground(box(Color.parseColor("#7C3AED"), 8, 0, 0));
        bVoiceOtp.setTextColor(Color.WHITE);
        bVoiceOtp.setTypeface(Typeface.DEFAULT_BOLD);
        bVoiceOtp.setTextSize(11f);
        bVoiceOtp.setOnClickListener(v -> launchVoiceOTP());
        LinearLayout.LayoutParams vOtpLp = new LinearLayout.LayoutParams(-2, -2);
        vOtpLp.setMargins(0, 0, 8, 0);
        h.addView(bVoiceOtp, vOtpLp);

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

        bT = new Button(this);
        bT.setText("🔍 ORDER");
        bT.setBackground(box(Color.parseColor("#00E676"), 8, 0, 0));
        bT.setTextColor(Color.BLACK);
        bT.setTypeface(Typeface.DEFAULT_BOLD);
        bT.setTextSize(9.5f);

        bP = new Button(this);
        bP.setText("📈 PERF");
        bP.setBackground(box(Color.parseColor("#232634"), 8, 0, 0));
        bP.setTextColor(Color.parseColor("#8E92A4"));
        bP.setTextSize(9.5f);

        bH = new Button(this);
        bH.setText("⚔️ HUBS");
        bH.setBackground(box(Color.parseColor("#232634"), 8, 0, 0));
        bH.setTextColor(Color.parseColor("#8E92A4"));
        bH.setTextSize(9.5f);

        bC = new Button(this);
        bC.setText("📞 HELPLINE");
        bC.setBackground(box(Color.parseColor("#232634"), 8, 0, 0));
        bC.setTextColor(Color.parseColor("#8E92A4"));
        bC.setTextSize(9.5f);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(2, 0, 2, 0);
        tb.addView(bT, lp);
        tb.addView(bP, new LinearLayout.LayoutParams(lp));
        tb.addView(bH, new LinearLayout.LayoutParams(lp));
        tb.addView(bC, new LinearLayout.LayoutParams(lp));
        main.addView(tb);

        FrameLayout body = new FrameLayout(this);
        body.setPadding(12, 6, 12, 10);
        main.addView(body, new LinearLayout.LayoutParams(-1, -1));

        // 1. ORDER ID TAB
        vTrk = new LinearLayout(this);
        vTrk.setOrientation(LinearLayout.VERTICAL);
        vTrk.setVisibility(View.VISIBLE);

        EditText s = new EditText(this);
        s.setHint("🔍 Search last digits of Track ID...");
        s.setHintTextColor(Color.parseColor("#717688"));
        s.setTextColor(Color.WHITE);
        s.setBackground(box(Color.parseColor("#181920"), 14, Color.parseColor("#00E676"), 1));
        s.setPadding(18, 14, 18, 14);
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
                c.setPadding(16, 14, 16, 14);
                c.setBackground(box(Color.parseColor("#181920"), 14, Color.parseColor("#2A2D3D"), 1));
                String[] it = ords.get(i);

                TextView t1 = new TextView(MainActivity.this);
                t1.setText("📦 Track ID: " + it[0]);
                t1.setTextColor(Color.parseColor("#38BDF8"));
                t1.setTypeface(Typeface.DEFAULT_BOLD);
                t1.setTextSize(14f);
                c.addView(t1);

                TextView t2 = new TextView(MainActivity.this);
                t2.setText("🛒 Order ID: " + it[1]);
                t2.setTextColor(Color.parseColor("#00E676"));
                t2.setTextSize(13.5f);
                t2.setPadding(0, 4, 0, 10);
                c.addView(t2);

                LinearLayout btnRow = new LinearLayout(MainActivity.this);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);

                Button bCpTrack = new Button(MainActivity.this);
                bCpTrack.setText("📋 Track");
                bCpTrack.setTextSize(11f);
                bCpTrack.setBackground(box(Color.parseColor("#0284C7"), 8, 0, 0));
                bCpTrack.setTextColor(Color.WHITE);
                bCpTrack.setTypeface(Typeface.DEFAULT_BOLD);
                bCpTrack.setOnClickListener(vw -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("TrackID", it[0]));
                    Toast.makeText(MainActivity.this, "Copied Track ID!", Toast.LENGTH_SHORT).show();
                });

                Button bCpOrder = new Button(MainActivity.this);
                bCpOrder.setText("📋 Order");
                bCpOrder.setTextSize(11f);
                bCpOrder.setBackground(box(Color.parseColor("#232634"), 8, Color.parseColor("#00E676"), 1));
                bCpOrder.setTextColor(Color.parseColor("#00E676"));
                bCpOrder.setTypeface(Typeface.DEFAULT_BOLD);
                bCpOrder.setOnClickListener(vw -> {
                    String toCopy = it[1].length() >= 6 ? it[1].substring(it[1].length() - 6) : it[1];
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("OrderID", toCopy));
                    Toast.makeText(MainActivity.this, "Copied: " + toCopy, Toast.LENGTH_SHORT).show();
                });

                Button bWhatsApp = new Button(MainActivity.this);
                bWhatsApp.setText("💬 Notify");
                bWhatsApp.setTextSize(11f);
                bWhatsApp.setBackground(box(Color.parseColor("#25D366"), 8, 0, 0));
                bWhatsApp.setTextColor(Color.BLACK);
                bWhatsApp.setTypeface(Typeface.DEFAULT_BOLD);
                bWhatsApp.setOnClickListener(vw -> {
                    try {
                        String msg = "नमस्ते! आपका पार्सल (Track ID: " + it[0] + ") आज डिलीवरी के लिए निकला है। कृपया OTP तैयार रखें। - Delivery Executive";
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?text=" + Uri.encode(msg)));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
                    }
                });

                LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, -2, 1f);
                bLp.setMargins(0, 0, 4, 0);
                btnRow.addView(bCpTrack, bLp);
                btnRow.addView(bCpOrder, new LinearLayout.LayoutParams(bLp));
                btnRow.addView(bWhatsApp, new LinearLayout.LayoutParams(0, -2, 1f));

                c.addView(btnRow);
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

        LinearLayout catRow = new LinearLayout(this);
        catRow.setPadding(0, 0, 0, 6);
        
        bCatAgent = new Button(this);
        bCatAgent.setText("👥 AGENT");
        bCatAgent.setTextSize(10.5f);
        bCatAgent.setTypeface(Typeface.DEFAULT_BOLD);
        
        bCatKirana = new Button(this);
        bCatKirana.setText("🏪 KIRANA");
        bCatKirana.setTextSize(10.5f);
        bCatKirana.setTypeface(Typeface.DEFAULT_BOLD);

        bCatAll = new Button(this);
        bCatAll.setText("🌐 ALL (MIX)");
        bCatAll.setTextSize(10.5f);
        bCatAll.setTypeface(Typeface.DEFAULT_BOLD);

        LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(0, -2, 1f);
        catLp.setMargins(1, 0, 1, 0);
        catRow.addView(bCatAgent, catLp);
        catRow.addView(bCatKirana, new LinearLayout.LayoutParams(catLp));
        catRow.addView(bCatAll, new LinearLayout.LayoutParams(catLp));
        vPrf.addView(catRow);

        periodFilterRow = new LinearLayout(this);
        periodFilterRow.setPadding(0, 0, 0, 6);
        vPrf.addView(periodFilterRow);

        bCatAgent.setOnClickListener(v -> switchCategory("AGENT"));
        bCatKirana.setOnClickListener(v -> switchCategory("KIRANA"));
        bCatAll.setOnClickListener(v -> switchCategory("ALL"));

        tPersonalBest = new TextView(this);
        tPersonalBest.setText("🏆 Hub Best: -- DEL");
        tPersonalBest.setTextColor(Color.parseColor("#FBBF24"));
        tPersonalBest.setBackground(box(Color.parseColor("#232634"), 10, Color.parseColor("#FBBF24"), 1));
        tPersonalBest.setPadding(14, 10, 14, 10);
        tPersonalBest.setTextSize(12.5f);
        tPersonalBest.setTypeface(Typeface.DEFAULT_BOLD);
        vPrf.addView(tPersonalBest);

        LinearLayout hubBox = new LinearLayout(this);
        hubBox.setOrientation(LinearLayout.VERTICAL);
        hubBox.setBackground(box(Color.parseColor("#181920"), 14, Color.parseColor("#38BDF8"), 1));
        hubBox.setPadding(16, 12, 16, 12);
        LinearLayout.LayoutParams hbLp = new LinearLayout.LayoutParams(-1, -2);
        hbLp.setMargins(0, 8, 0, 0);
        hubBox.setLayoutParams(hbLp);

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

        tGapTarget = new TextView(this);
        tGapTarget.setTextColor(Color.parseColor("#FB923C"));
        tGapTarget.setTextSize(13f);
        tGapTarget.setTypeface(Typeface.DEFAULT_BOLD);
        tGapTarget.setPadding(0, 6, 0, 0);
        hubBox.addView(tGapTarget);
        vPrf.addView(hubBox);

        LinearLayout sm = new LinearLayout(this);
        sm.setPadding(0, 8, 0, 6);
        LinearLayout sc1 = makeSummaryCard("TOP CONVERSION", Color.parseColor("#181920"), Color.parseColor("#00E676"), true);
        LinearLayout sc2 = makeSummaryCard("TOP DNPC", Color.parseColor("#181920"), Color.parseColor("#FB923C"), false);
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(0, -2, 1f);
        scLp.setMargins(0, 0, 6, 0);
        sm.addView(sc1, scLp);
        sm.addView(sc2, new LinearLayout.LayoutParams(0, -2, 1f));
        vPrf.addView(sm);

        LinearLayout actRow = new LinearLayout(this);
        bSort = new Button(this);
        bSort.setText("↕️ Sort Rate");
        bSort.setBackground(box(Color.parseColor("#232634"), 10, 0, 0));
        bSort.setTextColor(Color.WHITE);
        bSort.setTextSize(11f);
        bSort.setOnClickListener(v -> {
            isHighToLow = !isHighToLow;
            load();
        });

        bVsBattle = new Button(this);
        bVsBattle.setText("⚔️ Agent VS Agent");
        bVsBattle.setBackground(box(Color.parseColor("#7C3AED"), 10, 0, 0));
        bVsBattle.setTextColor(Color.WHITE);
        bVsBattle.setTextSize(11f);
        bVsBattle.setTypeface(Typeface.DEFAULT_BOLD);
        bVsBattle.setOnClickListener(v -> showAgentVsAgentDialog());

        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(0, -2, 1f);
        aLp.setMargins(0, 2, 4, 8);
        actRow.addView(bSort, aLp);
        actRow.addView(bVsBattle, new LinearLayout.LayoutParams(0, -2, 1f));
        vPrf.addView(actRow);

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
        ScrollView svHub = new ScrollView(this);
        vHubCrd = new LinearLayout(this);
        vHubCrd.setOrientation(LinearLayout.VERTICAL);
        svHub.addView(vHubCrd);
        vHub.addView(svHub, new LinearLayout.LayoutParams(-1, -1));
        body.addView(vHub);

        // 4. CONTACTS / HELPLINE TAB
        vCnt = new LinearLayout(this);
        vCnt.setOrientation(LinearLayout.VERTICAL);
        vCnt.setVisibility(View.GONE);
        ScrollView svCnt = new ScrollView(this);
        vCntCrd = new LinearLayout(this);
        vCntCrd.setOrientation(LinearLayout.VERTICAL);
        svCnt.addView(vCntCrd);
        vCnt.addView(svCnt, new LinearLayout.LayoutParams(-1, -1));
        body.addView(vCnt);

        bT.setOnClickListener(v -> switchTab(0));
        bP.setOnClickListener(v -> switchTab(1));
        bH.setOnClickListener(v -> switchTab(2));
        bC.setOnClickListener(v -> switchTab(3));
        root.addView(main);

        loadingOverlay = new LinearLayout(this);
        loadingOverlay.setOrientation(LinearLayout.VERTICAL);
        loadingOverlay.setGravity(Gravity.CENTER);
        loadingOverlay.setBackgroundColor(Color.parseColor("#EE0F1015"));
        loadingOverlay.setClickable(true);
        loadingOverlay.setVisibility(View.GONE);
        ProgressBar pb = new ProgressBar(this);
        loadingOverlay.addView(pb);
        root.addView(loadingOverlay, new FrameLayout.LayoutParams(-1, -1));

        switchCategory("AGENT");
        cnt();
        loadContacts();
    }

    void switchCategory(String cat) {
        currentCategory = cat;
        mode = "daily";

        bCatAgent.setBackground(box("AGENT".equals(cat) ? Color.parseColor("#00E676") : Color.parseColor("#232634"), 8, 0, 0));
        bCatAgent.setTextColor("AGENT".equals(cat) ? Color.BLACK : Color.parseColor("#8E92A4"));

        bCatKirana.setBackground(box("KIRANA".equals(cat) ? Color.parseColor("#00E676") : Color.parseColor("#232634"), 8, 0, 0));
        bCatKirana.setTextColor("KIRANA".equals(cat) ? Color.BLACK : Color.parseColor("#8E92A4"));

        bCatAll.setBackground(box("ALL".equals(cat) ? Color.parseColor("#00E676") : Color.parseColor("#232634"), 8, 0, 0));
        bCatAll.setTextColor("ALL".equals(cat) ? Color.BLACK : Color.parseColor("#8E92A4"));

        setupPeriodButtons();
        load();
    }

    void setupPeriodButtons() {
        periodFilterRow.removeAllViews();
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(0, -2, 1f);
        pLp.setMargins(1, 0, 1, 0);

        bSubDay = new Button(this);
        bSubDay.setText("📅 Day (Live)");
        bSubDay.setTextSize(10f);
        bSubDay.setTypeface(Typeface.DEFAULT_BOLD);
        bSubDay.setOnClickListener(v -> {
            mode = "daily";
            updatePeriodBtnStyles();
            load();
        });

        String yearlyTitle = "KIRANA".equals(currentCategory) ? "📊 Yearly (All Cycles)" : "📊 Yearly (All Weeks)";
        bSubYearly = new Button(this);
        bSubYearly.setText(yearlyTitle);
        bSubYearly.setTextSize(10f);
        bSubYearly.setTypeface(Typeface.DEFAULT_BOLD);
        bSubYearly.setOnClickListener(v -> {
            mode = "yearly";
            updatePeriodBtnStyles();
            load();
        });

        periodFilterRow.addView(bSubDay, pLp);
        periodFilterRow.addView(bSubYearly, new LinearLayout.LayoutParams(pLp));
        updatePeriodBtnStyles();
    }

    void updatePeriodBtnStyles() {
        if (bSubDay != null) {
            boolean active = "daily".equals(mode);
            bSubDay.setBackground(box(active ? Color.parseColor("#00E676") : Color.parseColor("#232634"), 8, 0, 0));
            bSubDay.setTextColor(active ? Color.BLACK : Color.parseColor("#8E92A4"));
        }
        if (bSubYearly != null) {
            boolean active = "yearly".equals(mode);
            bSubYearly.setBackground(box(active ? Color.parseColor("#00E676") : Color.parseColor("#232634"), 8, 0, 0));
            bSubYearly.setTextColor(active ? Color.BLACK : Color.parseColor("#8E92A4"));
        }
    }

    void showLoading(boolean show, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (loadingOverlay != null) loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        });
    }

    void switchTab(int index) {
        vTrk.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        vPrf.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        vHub.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        vCnt.setVisibility(index == 3 ? View.VISIBLE : View.GONE);

        bT.setBackground(box(index == 0 ? Color.parseColor("#00E676") : Color.parseColor("#232634"), 8, 0, 0));
        bT.setTextColor(index == 0 ? Color.BLACK : Color.parseColor("#8E92A4"));

        bP.setBackground(box(index == 1 ? Color.parseColor("#00E676") : Color.parseColor("#232634"), 8, 0, 0));
        bP.setTextColor(index == 1 ? Color.BLACK : Color.parseColor("#8E92A4"));

        bH.setBackground(box(index == 2 ? Color.parseColor("#00E676") : Color.parseColor("#232634"), 8, 0, 0));
        bH.setTextColor(index == 2 ? Color.BLACK : Color.parseColor("#8E92A4"));

        bC.setBackground(box(index == 3 ? Color.parseColor("#00E676") : Color.parseColor("#232634"), 8, 0, 0));
        bC.setTextColor(index == 3 ? Color.BLACK : Color.parseColor("#8E92A4"));

        if (index == 0) cnt();
        if (index == 1) load();
        if (index == 2) loadHubVsHub();
        if (index == 3) loadContacts();
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
            agentNamesList.clear();
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
                double ofdConv = to > 0 ? ((double) tl / to) * 100.0 : 0.0;
                double ofpConv = tp > 0 ? ((double) tk / tp) * 100.0 : 0.0;
                double dnpConv = tdnp > 0 ? ((double) tdnpc / tdnp) * 100.0 : 0.0;

                tHubOfdDel.setText("OFD/DEL = " + to + "/" + tl + " = " + String.format(Locale.US, "%.1f%%", ofdConv));
                tHubOfpPik.setText("OFP/PIKED = " + tp + "/" + tk + " = " + String.format(Locale.US, "%.1f%%", ofpConv));
                tHubDnpDnpc.setText("DNP/DNPC = " + tdnp + "/" + tdnpc + " = " + String.format(Locale.US, "%.1f%%", dnpConv));

                int targetNeeded = (int) Math.ceil(0.92 * to);
                int diff = targetNeeded - tl;
                if (diff <= 0 && to > 0) {
                    tGapTarget.setText("🎯 92% Target Achieved! 🚀");
                    tGapTarget.setTextColor(Color.parseColor("#00E676"));
                } else if (to > 0) {
                    tGapTarget.setText("🎯 Gap to 92%: " + diff + " more DEL required");
                    tGapTarget.setTextColor(Color.parseColor("#FB923C"));
                } else {
                    tGapTarget.setText("");
                }
            }
            if (hc != null) hc.close();

            updatePersonalBest();

            Cursor ac = db.rawQuery("SELECT n, SUM(o), SUM(l), SUM(p), SUM(k) FROM prf " + w + " GROUP BY n", null);
            ArrayList<String[]> list = new ArrayList<>();
            String bestConvName = "--", bestDnpcName = "--";
            double maxConv = -1;
            int maxDnpc = -1;

            while (ac != null && ac.moveToNext()) {
                String name = ac.getString(0);
                boolean isKirana = isKiranaAgent(name);

                if ("AGENT".equals(currentCategory) && isKirana) continue;
                if ("KIRANA".equals(currentCategory) && !isKirana) continue;

                agentNamesList.add(name);
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

            int currentRank = 1;
            for (String[] ag : list) {
                int[] strkInfo = getStreakInfo(ag[0]);
                int curStrk = strkInfo[0];
                int prevStrk = strkInfo[1];

                double rate = Double.parseDouble(ag[8]);
                int badgeColor = (rate >= 90.0) ? Color.parseColor("#00E676") : ((rate >= 60.0) ? Color.parseColor("#FBBF24") : Color.parseColor("#EF4444"));

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(box(Color.parseColor("#181920"), 12, Color.parseColor("#2A2D3D"), 1));
                card.setPadding(16, 14, 16, 14);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
                clp.setMargins(0, 0, 0, 10);
                card.setLayoutParams(clp);

                LinearLayout nameRow = new LinearLayout(this);
                nameRow.setOrientation(LinearLayout.HORIZONTAL);
                nameRow.setGravity(Gravity.CENTER_VERTICAL);

                String rankBadge = (currentRank == 1) ? "🥇 Rank #1" : ((currentRank == 2) ? "🥈 Rank #2" : ((currentRank == 3) ? "🥉 Rank #3" : "🎖️ Rank #" + currentRank));
                int rankBg = (currentRank == 1) ? Color.parseColor("#EAB308") : ((currentRank == 2) ? Color.parseColor("#94A3B8") : ((currentRank == 3) ? Color.parseColor("#B45309") : Color.parseColor("#374151")));

                TextView tRnk = new TextView(this);
                tRnk.setText(rankBadge);
                tRnk.setTextColor(Color.WHITE);
                tRnk.setTextSize(10.5f);
                tRnk.setTypeface(Typeface.DEFAULT_BOLD);
                tRnk.setBackground(box(rankBg, 6, 0, 0));
                tRnk.setPadding(8, 2, 8, 2);
                nameRow.addView(tRnk);

                TextView n = new TextView(this);
                n.setText(" " + ag[0]);
                n.setTextColor(badgeColor);
                n.setTypeface(Typeface.DEFAULT_BOLD);
                n.setTextSize(14.5f);
                LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(0, -2, 1f);
                nLp.setMargins(6, 0, 0, 0);
                nameRow.addView(n, nLp);

                String streakDisplay = (prevStrk > 0) ? "🔥 " + curStrk + "D (Prev: " + prevStrk + "D)" : "🔥 " + curStrk + "D (Prev: --)";
                TextView st = new TextView(this);
                st.setText(streakDisplay);
                st.setTextColor(Color.parseColor("#FBBF24"));
                st.setTextSize(10.5f);
                st.setTypeface(Typeface.DEFAULT_BOLD);
                st.setBackground(box(Color.parseColor("#232634"), 8, 0, 0));
                st.setPadding(8, 4, 8, 4);
                nameRow.addView(st);
                card.addView(nameRow);

                TextView t1 = new TextView(this);
                t1.setText("OFD/DEL = " + ag[1] + "/" + ag[2] + " = " + ag[9] + "%");
                t1.setTextColor(Color.parseColor("#D1D5DB"));
                t1.setTextSize(13f);
                t1.setPadding(0, 6, 0, 0);
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

                int agOfd = Integer.parseInt(ag[1]);
                int agDel = Integer.parseInt(ag[2]);
                int agTargetNeeded = (int) Math.ceil(0.92 * agOfd);
                int agDiff = agTargetNeeded - agDel;

                TextView tAgGap = new TextView(this);
                if (agDiff <= 0 && agOfd > 0) {
                    tAgGap.setText("🎯 92% Target Achieved! 🚀");
                    tAgGap.setTextColor(Color.parseColor("#00E676"));
                } else if (agOfd > 0) {
                    tAgGap.setText("🎯 Gap to 92%: " + agDiff + " more DEL required");
                    tAgGap.setTextColor(Color.parseColor("#FB923C"));
                } else {
                    tAgGap.setText("");
                }
                tAgGap.setTextSize(12f);
                tAgGap.setTypeface(Typeface.DEFAULT_BOLD);
                tAgGap.setPadding(0, 2, 0, 0);
                card.addView(tAgGap);

                Cursor yc = db.rawQuery("SELECT dt, o, l, (CAST(l AS REAL)*100.0/CASE WHEN o>0 THEN o ELSE 1 END) FROM prf WHERE n = ? AND dt < ? AND o > 0 ORDER BY dt DESC LIMIT 1", new String[]{ag[0], opDate});
                if (yc != null && yc.moveToFirst()) {
                    String yDt = yc.getString(0);
                    int yO = yc.getInt(1);
                    int yL = yc.getInt(2);
                    double yConv = yc.getDouble(3);
                    int yGap = (int) Math.ceil(0.92 * yO) - yL;

                    TextView tPrevMiss = new TextView(this);
                    if (yConv < 92.0) {
                        tPrevMiss.setText("⚠️ Prev Day (" + yDt + "): " + String.format(Locale.US, "%.1f%%", yConv) + " (Missed 92% by " + yGap + " DEL)");
                        tPrevMiss.setTextColor(Color.parseColor("#EF4444"));
                    } else {
                        tPrevMiss.setText("✅ Prev Day (" + yDt + "): " + String.format(Locale.US, "%.1f%%", yConv) + " (92% Achieved)");
                        tPrevMiss.setTextColor(Color.parseColor("#10B981"));
                    }
                    tPrevMiss.setTextSize(11.5f);
                    tPrevMiss.setTypeface(Typeface.DEFAULT_BOLD);
                    tPrevMiss.setPadding(0, 2, 0, 0);
                    card.addView(tPrevMiss);
                }
                if (yc != null) yc.close();

                final String agName = ag[0];
                card.setOnClickListener(v -> showDetails(agName));
                vCrd.addView(card);
                currentRank++;
            }
        } catch (Exception ignored) {}
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
                cur = 1;
                int i = 1;
                while (i < dates.size()) {
                    Calendar prevCal = (Calendar) dates.get(i - 1).clone();
                    prevCal.add(Calendar.DAY_OF_YEAR, -1);
                    if (prevCal.get(Calendar.YEAR) == dates.get(i).get(Calendar.YEAR) &&
                        prevCal.get(Calendar.DAY_OF_YEAR) == dates.get(i).get(Calendar.DAY_OF_YEAR)) {
                        cur++;
                        i++;
                    } else {
                        break;
                    }
                }
                while (i < dates.size()) {
                    prev = 1;
                    i++;
                    while (i < dates.size()) {
                        Calendar prevCal = (Calendar) dates.get(i - 1).clone();
                        prevCal.add(Calendar.DAY_OF_YEAR, -1);
                        if (prevCal.get(Calendar.YEAR) == dates.get(i).get(Calendar.YEAR) &&
                            prevCal.get(Calendar.DAY_OF_YEAR) == dates.get(i).get(Calendar.DAY_OF_YEAR)) {
                            prev++;
                            i++;
                        } else {
                            break;
                        }
                    }
                    break;
                }
            }
        } catch (Exception ignored) {}
        return new int[]{Math.max(1, cur), prev};
    }

    void updatePersonalBest() {
        try {
            Cursor c = db.rawQuery(
                "SELECT dt, SUM(o), SUM(l), (CAST(SUM(l) AS REAL) * 100.0 / SUM(o)) as conv " +
                "FROM prf GROUP BY dt HAVING SUM(o) > 0 " +
                "ORDER BY conv DESC, SUM(l) DESC LIMIT 1", null);
            if (c != null && c.moveToFirst()) {
                String bestDt = c.getString(0);
                int bestOfd = c.getInt(1);
                int bestDel = c.getInt(2);
                double bestConv = c.getDouble(3);
                tPersonalBest.setText("🏆 Hub Best: " + String.format(Locale.US, "%.1f%%", bestConv) + " DEL (" + bestDel + "/" + bestOfd + ") (" + bestDt + ")");
            }
            if (c != null) c.close();
        } catch (Exception ignored) {}
    }

    void triggerAutomaticEODReport(String completedDate) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("🏁 *MALBAZARHUB_NJP DELIVERY COMPLETED REPORT*\n");
            sb.append("📅 *Completed Date:* ").append(completedDate).append("\n");
            sb.append("━━━━━━━━━━━━━━━━━━━\n");

            Cursor hc = db.rawQuery("SELECT SUM(o), SUM(l), SUM(p), SUM(k) FROM prf WHERE dt = ?", new String[]{completedDate});
            if (hc != null && hc.moveToFirst()) {
                int to = hc.getInt(0), tl = hc.getInt(1), tp = hc.getInt(2), tk = hc.getInt(3);
                int tdnp = to + tp, tdnpc = tl + tk;
                double ofdConv = to > 0 ? ((double) tl / to) * 100.0 : 0.0;
                double ofpConv = tp > 0 ? ((double) tk / tp) * 100.0 : 0.0;
                double dnpConv = tdnp > 0 ? ((double) tdnpc / tdnp) * 100.0 : 0.0;

                sb.append("🚚 *Total OFD / DEL:* ").append(to).append(" / ").append(tl).append(" (").append(String.format(Locale.US, "%.1f%%", ofdConv)).append(")\n");
                sb.append("📦 *Total OFP / PIK:* ").append(tp).append(" / ").append(tk).append(" (").append(String.format(Locale.US, "%.1f%%", ofpConv)).append(")\n");
                sb.append("🔄 *Total DNP / DNPC:* ").append(tdnp).append(" / ").append(tdnpc).append(" (").append(String.format(Locale.US, "%.1f%%", dnpConv)).append(")\n");

                int targetNeeded = (int) Math.ceil(0.92 * to);
                int diff = targetNeeded - tl;
                if (diff <= 0 && to > 0) {
                    sb.append("🎯 *Target 92%:* Achieved! 🚀\n");
                } else if (to > 0) {
                    sb.append("🎯 *Target 92%:* Gap ").append(diff).append(" DEL required\n");
                }
            }
            if (hc != null) hc.close();

            sb.append("━━━━━━━━━━━━━━━━━━━\n");
            sb.append("📊 *ALL AGENTS (LOW TO HIGH DEL CONV):*\n");

            Cursor ac = db.rawQuery(
                "SELECT n, SUM(o), SUM(l), (CAST(SUM(l) AS REAL) * 100.0 / CASE WHEN SUM(o) > 0 THEN SUM(o) ELSE 1 END) as del_conv " +
                "FROM prf WHERE dt = ? GROUP BY n HAVING (SUM(o) + SUM(p)) > 0 " +
                "ORDER BY del_conv ASC, SUM(l) ASC", new String[]{completedDate});

            int rank = 1;
            while (ac != null && ac.moveToNext()) {
                String agN = ac.getString(0);
                int agOfd = ac.getInt(1);
                int agDel = ac.getInt(2);
                double agConv = ac.getDouble(3);
                String tag = (agConv < 75.0) ? "⚠️" : ((agConv >= 92.0) ? "✅" : "⚡");

                sb.append(rank).append(". ").append(tag).append(" ").append(agN)
                  .append(" ➔ ").append(String.format(Locale.US, "%.1f%%", agConv))
                  .append(" (DEL: ").append(agDel).append("/").append(agOfd).append(")\n");
                rank++;
            }
            if (ac != null) ac.close();

            sb.append("━━━━━━━━━━━━━━━━━━━\n");
            sb.append("⚡ _Auto-Generated EOD Report | Managed by Adarsh_");

            String url = "https://api.whatsapp.com/send?phone=918337868714&text=" + Uri.encode(sb.toString());
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    void showAgentVsAgentDialog() {
        if (agentNamesList.size() < 2) {
            Toast.makeText(this, "Need at least 2 agents to compare", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout dLayout = new LinearLayout(this);
        dLayout.setOrientation(LinearLayout.VERTICAL);
        dLayout.setPadding(24, 20, 24, 20);
        dLayout.setBackgroundColor(Color.parseColor("#0F1015"));

        TextView title = new TextView(this);
        title.setText("⚔️ AGENT BATTLE ARENA");
        title.setTextColor(Color.parseColor("#A855F7"));
        title.setTextSize(16f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        dLayout.addView(title);

        Spinner sp1 = new Spinner(this);
        Spinner sp2 = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, agentNamesList);
        sp1.setAdapter(ad);
        sp2.setAdapter(ad);
        if (agentNamesList.size() > 1) sp2.setSelection(1);

        dLayout.addView(sp1);
        dLayout.addView(sp2);

        LinearLayout resBox = new LinearLayout(this);
        resBox.setOrientation(LinearLayout.VERTICAL);
        resBox.setPadding(0, 16, 0, 0);
        dLayout.addView(resBox);

        Button bComp = new Button(this);
        bComp.setText("⚡ COMPARE NOW");
        bComp.setBackground(box(Color.parseColor("#7C3AED"), 8, 0, 0));
        bComp.setTextColor(Color.WHITE);
        bComp.setTypeface(Typeface.DEFAULT_BOLD);
        bComp.setOnClickListener(v -> {
            String ag1 = (String) sp1.getSelectedItem();
            String ag2 = (String) sp2.getSelectedItem();
            resBox.removeAllViews();

            TextView vsTitle = new TextView(MainActivity.this);
            vsTitle.setText("📊 " + ag1 + "  VS  " + ag2);
            vsTitle.setTextColor(Color.parseColor("#38BDF8"));
            vsTitle.setTypeface(Typeface.DEFAULT_BOLD);
            vsTitle.setTextSize(14f);
            resBox.addView(vsTitle);

            String r1 = getAgentStats(ag1);
            String r2 = getAgentStats(ag2);

            TextView stats = new TextView(MainActivity.this);
            stats.setText("\n👤 " + ag1 + ":\n" + r1 + "\n\n👤 " + ag2 + ":\n" + r2);
            stats.setTextColor(Color.WHITE);
            stats.setTextSize(13f);
            resBox.addView(stats);
        });
        dLayout.addView(bComp);

        new AlertDialog.Builder(this)
                .setView(dLayout)
                .setPositiveButton("Close", null)
                .show();
    }

    String getAgentStats(String name) {
        String opDate = getOperationalDate();
        String w;
        if ("daily".equals(mode)) {
            w = " WHERE n = ? AND dt = (SELECT MAX(dt) FROM prf) ";
        } else {
            w = " WHERE n = ? AND dt >= '" + getYearStartDate(opDate) + "' ";
        }

        Cursor c = db.rawQuery("SELECT SUM(o), SUM(l), SUM(p), SUM(k) FROM prf " + w, new String[]{name});
        String res = "No data";
        if (c != null && c.moveToFirst()) {
            int o = c.getInt(0), l = c.getInt(1), p = c.getInt(2), k = c.getInt(3);
            int dnp = o + p, dnpc = l + k;
            double cr = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;
            res = "• OFD: " + o + " | DEL: " + l + "\n• Conv: " + String.format(Locale.US, "%.1f%%", cr);
        }
        if (c != null) c.close();
        return res;
    }
        void showDetails(String name) {
        String opDate = getOperationalDate();
        boolean isKirana = isKiranaAgent(name);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat sdfDayName = new SimpleDateFormat("EEEE, dd MMM yyyy", Locale.US);

        LinearLayout pop = new LinearLayout(this);
        pop.setOrientation(LinearLayout.VERTICAL);
        pop.setPadding(20, 20, 20, 20);
        pop.setBackgroundColor(Color.parseColor("#0F1015"));

        TextView h = new TextView(this);
        h.setTextColor(Color.parseColor("#00E676"));
        h.setTextSize(15f);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, 0, 0, 10);

        ScrollView sv = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        if (isKirana) {
            h.setText("🏪 " + name + " (All Cycles Breakdown)");

            Cursor mc = db.rawQuery("SELECT DISTINCT SUBSTR(dt, 1, 7) FROM prf WHERE n = ? ORDER BY dt DESC", new String[]{name});
            boolean foundAny = false;
            while (mc != null && mc.moveToNext()) {
                foundAny = true;
                String ym = mc.getString(0);
                try {
                    Calendar mCal = Calendar.getInstance();
                    mCal.setTime(new SimpleDateFormat("yyyy-MM", Locale.US).parse(ym));
                    int maxD = mCal.getActualMaximum(Calendar.DAY_OF_MONTH);
                    String mName = new SimpleDateFormat("MMMM yyyy", Locale.US).format(mCal.getTime());

                    String d2Start = ym + "-16", d2End = ym + String.format(Locale.US, "-%02d", maxD);
                    Cursor c2 = db.rawQuery("SELECT SUM(o), SUM(l), SUM(p), SUM(k) FROM prf WHERE n = ? AND dt >= ? AND dt <= ?", new String[]{name, d2Start, d2End});
                    if (c2 != null && c2.moveToFirst() && (c2.getInt(0) + c2.getInt(2)) > 0) {
                        content.addView(makeSummaryHeaderCard("🏪 " + mName + " Cyc 2 (16-" + maxD + ")", c2.getInt(0), c2.getInt(1), c2.getInt(2), c2.getInt(3)));
                        
                        Cursor dc2 = db.rawQuery("SELECT dt, o, l, p, k FROM prf WHERE n = ? AND dt >= ? AND dt <= ? ORDER BY dt DESC", new String[]{name, d2Start, d2End});
                        while (dc2 != null && dc2.moveToNext()) {
                            content.addView(makeDetailItemCard("📅 " + dc2.getString(0), dc2.getInt(1), dc2.getInt(2), dc2.getInt(3), dc2.getInt(4)));
                        }
                        if (dc2 != null) dc2.close();
                    }
                    if (c2 != null) c2.close();

                    String d1Start = ym + "-01", d1End = ym + "-15";
                    Cursor c1 = db.rawQuery("SELECT SUM(o), SUM(l), SUM(p), SUM(k) FROM prf WHERE n = ? AND dt >= ? AND dt <= ?", new String[]{name, d1Start, d1End});
                    if (c1 != null && c1.moveToFirst() && (c1.getInt(0) + c1.getInt(2)) > 0) {
                        content.addView(makeSummaryHeaderCard("🏪 " + mName + " Cyc 1 (1-15)", c1.getInt(0), c1.getInt(1), c1.getInt(2), c1.getInt(3)));
                        
                        Cursor dc1 = db.rawQuery("SELECT dt, o, l, p, k FROM prf WHERE n = ? AND dt >= ? AND dt <= ? ORDER BY dt DESC", new String[]{name, d1Start, d1End});
                        while (dc1 != null && dc1.moveToNext()) {
                            content.addView(makeDetailItemCard("📅 " + dc1.getString(0), dc1.getInt(1), dc1.getInt(2), dc1.getInt(3), dc1.getInt(4)));
                        }
                        if (dc1 != null) dc1.close();
                    }
                    if (c1 != null) c1.close();
                } catch (Exception ignored) {}
            }
            if (mc != null) mc.close();

            if (!foundAny) content.addView(makeEmptyText("No Kirana records found."));
        } else {
            h.setText("👤 " + name + " (All Weeks & Day Breakdown)");

            Cursor allDaysC = db.rawQuery("SELECT dt, o, l, p, k FROM prf WHERE n = ? ORDER BY dt DESC LIMIT 90", new String[]{name});
            LinkedHashMap<String, ArrayList<String[]>> weekGroups = new LinkedHashMap<>();

            while (allDaysC != null && allDaysC.moveToNext()) {
                String dStr = allDaysC.getString(0);
                String wStart = getWeekStartDate(dStr);
                String wEnd = getWeekEndDate(dStr);
                String wKey = wStart + " to " + wEnd;

                if (!weekGroups.containsKey(wKey)) {
                    weekGroups.put(wKey, new ArrayList<>());
                }
                weekGroups.get(wKey).add(new String[]{dStr, String.valueOf(allDaysC.getInt(1)), String.valueOf(allDaysC.getInt(2)), String.valueOf(allDaysC.getInt(3)), String.valueOf(allDaysC.getInt(4))});
            }
            if (allDaysC != null) allDaysC.close();

            if (weekGroups.isEmpty()) {
                content.addView(makeEmptyText("No records found for this agent."));
            } else {
                for (String wRange : weekGroups.keySet()) {
                    ArrayList<String[]> daysInWeek = weekGroups.get(wRange);
                    int sumO = 0, sumL = 0, sumP = 0, sumK = 0;
                    for (String[] dData : daysInWeek) {
                        sumO += Integer.parseInt(dData[1]);
                        sumL += Integer.parseInt(dData[2]);
                        sumP += Integer.parseInt(dData[3]);
                        sumK += Integer.parseInt(dData[4]);
                    }

                    String[] parts = wRange.split(" to ");
                    String wHeaderTitle = "📊 Week: " + parts[0] + " ➔ " + parts[1];
                    content.addView(makeSummaryHeaderCard(wHeaderTitle, sumO, sumL, sumP, sumK));

                    for (String[] dData : daysInWeek) {
                        String dayTitle = dData[0];
                        try {
                            Calendar dCal = Calendar.getInstance();
                            dCal.setTime(sdf.parse(dData[0]));
                            dayTitle = sdfDayName.format(dCal.getTime());
                        } catch (Exception ignored) {}
                        content.addView(makeDetailItemCard("📅 " + dayTitle, Integer.parseInt(dData[1]), Integer.parseInt(dData[2]), Integer.parseInt(dData[3]), Integer.parseInt(dData[4])));
                    }
                }
            }
        }

        pop.addView(h);
        sv.addView(content);
        pop.addView(sv);

        new AlertDialog.Builder(this)
            .setView(pop)
            .setPositiveButton("Close", null)
            .show();
    }

    TextView makeEmptyText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#9CA3AF"));
        tv.setPadding(10, 10, 10, 10);
        return tv;
    }

    LinearLayout makeSummaryHeaderCard(String title, int o, int l, int p, int k) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(box(Color.parseColor("#1E1B4B"), 12, Color.parseColor("#818CF8"), 1));
        card.setPadding(14, 12, 14, 12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 8, 0, 8);
        card.setLayoutParams(lp);

        int dnp = o + p, dnpc = l + k;
        double ofdC = o > 0 ? ((double) l / o) * 100.0 : 0.0;
        double ofpC = p > 0 ? ((double) k / p) * 100.0 : 0.0;
        double totC = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;

        TextView tHead = new TextView(this);
        tHead.setText(title);
        tHead.setTextColor(Color.parseColor("#A5B4FC"));
        tHead.setTextSize(13.5f);
        tHead.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tHead);

        TextView t1 = new TextView(this);
        t1.setText("OFD/DEL = " + o + "/" + l + " = " + String.format(Locale.US, "%.1f%%", ofdC));
        t1.setTextColor(Color.WHITE);
        t1.setTextSize(12.5f);
        t1.setPadding(0, 4, 0, 0);
        card.addView(t1);

        TextView t2 = new TextView(this);
        t2.setText("OFP/PIK = " + p + "/" + k + " = " + String.format(Locale.US, "%.1f%%", ofpC));
        t2.setTextColor(Color.WHITE);
        t2.setTextSize(12.5f);
        t2.setPadding(0, 2, 0, 0);
        card.addView(t2);

        TextView t3 = new TextView(this);
        t3.setText("DNP/DNPC = " + dnp + "/" + dnpc + " = " + String.format(Locale.US, "%.1f%%", totC));
        t3.setTextColor(Color.parseColor("#34D399"));
        t3.setTextSize(12.5f);
        t3.setTypeface(Typeface.DEFAULT_BOLD);
        t3.setPadding(0, 2, 0, 0);
        card.addView(t3);

        String badgeText = getPerformanceBadge(ofdC, o);
        int badgeCol = getPerformanceColor(ofdC, o);
        TextView tBadge = new TextView(this);
        tBadge.setText("🏆 " + badgeText);
        tBadge.setTextColor(badgeCol);
        tBadge.setTextSize(12f);
        tBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tBadge.setPadding(0, 4, 0, 0);
        card.addView(tBadge);

        return card;
    }

    LinearLayout makeDetailItemCard(String title, int o, int l, int p, int k) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setBackground(box(Color.parseColor("#181920"), 10, Color.parseColor("#2A2D3D"), 1));
        item.setPadding(12, 10, 12, 10);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, 8);
        item.setLayoutParams(lp);

        int dnp = o + p, dnpc = l + k;
        double ofdC = o > 0 ? ((double) l / o) * 100.0 : 0.0;
        double ofpC = p > 0 ? ((double) k / p) * 100.0 : 0.0;
        double totC = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;

        TextView tTitle = new TextView(this);
        tTitle.setText(title);
        tTitle.setTextColor(Color.parseColor("#38BDF8"));
        tTitle.setTextSize(13f);
        tTitle.setTypeface(Typeface.DEFAULT_BOLD);
        item.addView(tTitle);

        TextView t1 = new TextView(this);
        t1.setText("OFD/DEL = " + o + "/" + l + " = " + String.format(Locale.US, "%.1f%%", ofdC));
        t1.setTextColor(Color.parseColor("#D1D5DB"));
        t1.setTextSize(12f);
        t1.setPadding(0, 2, 0, 0);
        item.addView(t1);

        TextView t2 = new TextView(this);
        t2.setText("OFP/PIK = " + p + "/" + k + " = " + String.format(Locale.US, "%.1f%%", ofpC));
        t2.setTextColor(Color.parseColor("#D1D5DB"));
        t2.setTextSize(12f);
        t2.setPadding(0, 2, 0, 0);
        item.addView(t2);

        TextView t3 = new TextView(this);
        t3.setText("DNP/DNPC = " + dnp + "/" + dnpc + " = " + String.format(Locale.US, "%.1f%%", totC));
        t3.setTextColor(Color.parseColor("#34D399"));
        t3.setTextSize(12f);
        t3.setTypeface(Typeface.DEFAULT_BOLD);
        t3.setPadding(0, 2, 0, 0);
        item.addView(t3);

        String badgeText = getPerformanceBadge(ofdC, o);
        int badgeCol = getPerformanceColor(ofdC, o);
        TextView tBadge = new TextView(this);
        tBadge.setText(badgeText);
        tBadge.setTextColor(badgeCol);
        tBadge.setTextSize(11.5f);
        tBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tBadge.setPadding(0, 2, 0, 0);
        item.addView(tBadge);

        return item;
    }

    void showHubDetails(String hname) {
        String opDate = getOperationalDate();
        Cursor c = db.rawQuery("SELECT dt, o, l, lc, p, k, kc, dnp, dnpc, tc FROM hub_prf WHERE hname = ? ORDER BY dt DESC LIMIT 30", new String[]{hname});
        LinearLayout pop = new LinearLayout(this);
        pop.setOrientation(LinearLayout.VERTICAL);
        pop.setPadding(20, 20, 20, 20);
        pop.setBackgroundColor(Color.parseColor("#0F1015"));

        TextView h = new TextView(this);
        h.setText("🏢 " + hname + " (History)");
        h.setTextColor(Color.parseColor("#38BDF8"));
        h.setTextSize(15f);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, 0, 0, 10);
        pop.addView(h);

        ScrollView sv = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        while (c != null && c.moveToNext()) {
            String dt = c.getString(0) != null ? c.getString(0) : opDate;
            String o = c.getString(1), l = c.getString(2), lc = c.getString(3);
            String p = c.getString(4), k = c.getString(5), kc = c.getString(6);
            String tc = c.getString(9);

            TextView item = new TextView(this);
            item.setText("📅 " + dt + "  |  Overall Conv: " + tc + "\nOFD/DEL = " + o + "/" + l + " (" + lc + ")\nOFP/PIK = " + p + "/" + k + " (" + kc + ")");
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

    void launchVoiceOTP() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "🗣️ Speak Customer OTP (e.g. 5 4 8 2)...");
            startActivityForResult(intent, REQ_CODE_SPEECH);
        } catch (Exception e) {
            Toast.makeText(this, "Voice recognition not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CODE_SPEECH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> res = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (res != null && !res.isEmpty()) {
                String spoken = res.get(0).replaceAll("[^0-9]", "");
                if (spoken.isEmpty()) spoken = res.get(0);

                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("OTP", spoken));

                LinearLayout d = new LinearLayout(this);
                d.setOrientation(LinearLayout.VERTICAL);
                d.setPadding(24, 20, 24, 20);
                d.setBackgroundColor(Color.parseColor("#0F1015"));

                TextView h = new TextView(this);
                h.setText("🎙️ CAPTURED OTP");
                h.setTextColor(Color.parseColor("#A855F7"));
                h.setTypeface(Typeface.DEFAULT_BOLD);
                h.setTextSize(14f);
                d.addView(h);

                TextView otpVal = new TextView(this);
                otpVal.setText(spoken);
                otpVal.setTextColor(Color.parseColor("#00E676"));
                otpVal.setTextSize(32f);
                otpVal.setTypeface(Typeface.DEFAULT_BOLD);
                otpVal.setGravity(Gravity.CENTER);
                otpVal.setPadding(0, 16, 0, 16);
                d.addView(otpVal);

                TextView note = new TextView(this);
                note.setText("✅ Copied to Clipboard! Ready to paste in FieldX.");
                note.setTextColor(Color.parseColor("#9CA3AF"));
                note.setTextSize(12f);
                d.addView(note);

                new AlertDialog.Builder(this)
                        .setView(d)
                        .setPositiveButton("OK", null)
                        .show();
            }
        }
    }

    void loadHubVsHub() {
        try {
            vHubCrd.removeAllViews();
            Cursor c = db.rawQuery("SELECT hname, o, l, lc, p, k, kc, dnp, dnpc, tc FROM hub_prf WHERE dt = (SELECT MAX(dt) FROM hub_prf) OR dt IS NULL", null);
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

                final String hubName = name;
                card.setOnClickListener(v -> showHubDetails(hubName));

                vHubCrd.addView(card);
            }
            if (c != null) c.close();
        } catch (Exception ignored) {}
    }

    void loadContacts() {
        vCntCrd.removeAllViews();
        Cursor c = db.rawQuery("SELECT name, role, phone FROM contacts", null);
        if (c != null && c.getCount() > 0) {
            addContactHeader("📋 LIVE CONTACT DIRECTORY (FROM SHEET)");
            while (c.moveToNext()) {
                String name = c.getString(0);
                String role = c.getString(1);
                String phone = c.getString(2);
                String colorHex = "#00E676";
                String rLower = role.toLowerCase(Locale.ROOT);
                if (rLower.contains("manager") || rLower.contains("tl") || rLower.contains("lead")) colorHex = "#38BDF8";
                else if (rLower.contains("office") || rLower.contains("return") || rLower.contains("gate")) colorHex = "#FBBF24";
                addContactItem(name, role, phone, colorHex);
            }
        } else {
            addContactHeader("ℹ️ NO CONTACTS FOUND");
            TextView emptyTxt = new TextView(this);
            emptyTxt.setText("Sheet में Column R (Name), S (Role), T (Phone) भरें और SYNC दबाएं।");
            emptyTxt.setTextColor(Color.parseColor("#9CA3AF"));
            emptyTxt.setPadding(10, 10, 10, 10);
            vCntCrd.addView(emptyTxt);
        }
        if (!isAuto) {
                    showLoading(false, null);
                    Toast.makeText(MainActivity.this, "Sync Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
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
