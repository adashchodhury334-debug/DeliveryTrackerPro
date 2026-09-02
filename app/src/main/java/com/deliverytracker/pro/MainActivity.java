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
    Button bT, bP, bH, bC, bSort, bVsBattle, bShareReport, bVoiceOtp;
    Button bCatAgent, bCatKirana, bCatAll;
    Button bSubDay, bSubYearly;
    TextView tCnt, tHubOfdDel, tHubOfpPik, tHubDnpDnpc, tTopConv, tTopDnpc, tGapTarget, tPersonalBest;
    ArrayList<String[]> ords = new ArrayList<>();
    ArrayList<String> agentNamesList = new ArrayList<>();
    BaseAdapter adp;
    
    String currentCategory = "AGENT";
    String mode = "daily";
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
        void shareSingleAgentReport(String name, int ofd, int del, int ofp, int pik, int dnp, int dnpc, double delConv) {
        try {
            String opDate = getOperationalDate();
            StringBuilder sb = new StringBuilder();
            sb.append("👤 *DELIVERY PERFORMANCE SCORECARD*\n");
            sb.append("📛 *Name:* ").append(name).append("\n");
            sb.append("📅 *Date:* ").append(opDate).append("\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("🚚 *OFD / DEL:* ").append(ofd).append(" / ").append(del).append(" (").append(String.format(Locale.US, "%.1f%%", delConv)).append(")\n");
            double ofpConv = ofp > 0 ? ((double) pik / ofp) * 100.0 : 0.0;
            sb.append("📦 *OFP / PIK:* ").append(ofp).append(" / ").append(pik).append(" (").append(String.format(Locale.US, "%.1f%%", ofpConv)).append(")\n");
            double totConv = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;
            sb.append("🔄 *DNP / DNPC:* ").append(dnp).append(" / ").append(dnpc).append(" (").append(String.format(Locale.US, "%.1f%%", totConv)).append(")\n");

            int targetNeeded = (int) Math.ceil(0.92 * ofd);
            int diff = targetNeeded - del;
            if (diff <= 0 && ofd > 0) {
                sb.append("🎯 *Target Status:* 92% Achieved! 🚀\n");
            } else if (ofd > 0) {
                sb.append("🎯 *Target Status:* ⚠️ Gap to 92%: ").append(diff).append(" DEL required\n");
            }
            sb.append("🏆 *Rating:* ").append(getPerformanceBadge(delConv, ofd)).append("\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("⚡ _Managed by Adarsh | Delivery Tracker Pro_");

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
            startActivity(Intent.createChooser(shareIntent, "📢 Share Agent Report"));
        } catch (Exception e) {
            Toast.makeText(this, "Error sharing: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    void sharePeriodReport(String name, String periodTitle, int ofd, int del, int ofp, int pik, int dnp, int dnpc, double delConv) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("📊 *PERIOD PERFORMANCE REPORT*\n");
            sb.append("👤 *Agent:* ").append(name).append("\n");
            sb.append("📆 *Period:* ").append(periodTitle).append("\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("🚚 *Total OFD / DEL:* ").append(ofd).append(" / ").append(del).append(" (").append(String.format(Locale.US, "%.1f%%", delConv)).append(")\n");
            double ofpConv = ofp > 0 ? ((double) pik / ofp) * 100.0 : 0.0;
            sb.append("📦 *Total OFP / PIK:* ").append(ofp).append(" / ").append(pik).append(" (").append(String.format(Locale.US, "%.1f%%", ofpConv)).append(")\n");
            double totConv = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;
            sb.append("🔄 *Total DNP / DNPC:* ").append(dnp).append(" / ").append(dnpc).append(" (").append(String.format(Locale.US, "%.1f%%", totConv)).append(")\n");
            sb.append("🏆 *Rating:* ").append(getPerformanceBadge(delConv, ofd)).append("\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("⚡ _Managed by Adarsh | Delivery Tracker Pro_");

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
            startActivity(Intent.createChooser(shareIntent, "📢 Share Period Report"));
        } catch (Exception e) {
            Toast.makeText(this, "Error sharing: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        root.setBackgroundColor(Color.parseColor("#0C0D12"));
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
        h.setBackgroundColor(Color.parseColor("#151722"));
        h.setPadding(18, 16, 18, 16);
        h.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(this);
        t.setText("📦 Delivery Tracker Pro");
        t.setTextColor(Color.WHITE);
        t.setTextSize(17f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        titleBox.addView(t);

        TextView tSub = new TextView(this);
        tSub.setText("⚡ Managed by Adarsh");
        tSub.setTextColor(Color.parseColor("#38BDF8"));
        tSub.setTextSize(12f);
        tSub.setTypeface(Typeface.DEFAULT_BOLD);
        titleBox.addView(tSub);

        h.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1f));

        bVoiceOtp = new Button(this);
        bVoiceOtp.setText("🎙️ OTP");
        bVoiceOtp.setBackground(box(Color.parseColor("#7C3AED"), 10, 0, 0));
        bVoiceOtp.setTextColor(Color.WHITE);
        bVoiceOtp.setTypeface(Typeface.DEFAULT_BOLD);
        bVoiceOtp.setTextSize(12f);
        bVoiceOtp.setPadding(14, 8, 14, 8);
        bVoiceOtp.setOnClickListener(v -> launchVoiceOTP());
        LinearLayout.LayoutParams vOtpLp = new LinearLayout.LayoutParams(-2, -2);
        vOtpLp.setMargins(0, 0, 10, 0);
        h.addView(bVoiceOtp, vOtpLp);

        Button bRef = new Button(this);
        bRef.setText("🔄 SYNC");
        bRef.setBackground(box(Color.parseColor("#00E676"), 10, 0, 0));
        bRef.setTextColor(Color.BLACK);
        bRef.setTypeface(Typeface.DEFAULT_BOLD);
        bRef.setTextSize(12f);
        bRef.setPadding(14, 8, 14, 8);
        bRef.setOnClickListener(v -> new Thread(() -> doSync(false)).start());
        h.addView(bRef);
        main.addView(h);
                LinearLayout tb = new LinearLayout(this);
        tb.setPadding(10, 10, 10, 6);

        bT = new Button(this);
        bT.setText("🔍 ORDER");
        bT.setBackground(box(Color.parseColor("#00E676"), 10, 0, 0));
        bT.setTextColor(Color.BLACK);
        bT.setTypeface(Typeface.DEFAULT_BOLD);
        bT.setTextSize(11f);

        bP = new Button(this);
        bP.setText("📈 PERF");
        bP.setBackground(box(Color.parseColor("#1C1E2A"), 10, 0, 0));
        bP.setTextColor(Color.parseColor("#8E92A4"));
        bP.setTextSize(11f);

        bH = new Button(this);
        bH.setText("⚔️ HUBS");
        bH.setBackground(box(Color.parseColor("#1C1E2A"), 10, 0, 0));
        bH.setTextColor(Color.parseColor("#8E92A4"));
        bH.setTextSize(11f);

        bC = new Button(this);
        bC.setText("📞 HELPLINE");
        bC.setBackground(box(Color.parseColor("#1C1E2A"), 10, 0, 0));
        bC.setTextColor(Color.parseColor("#8E92A4"));
        bC.setTextSize(11f);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(3, 0, 3, 0);
        tb.addView(bT, lp);
        tb.addView(bP, new LinearLayout.LayoutParams(lp));
        tb.addView(bH, new LinearLayout.LayoutParams(lp));
        tb.addView(bC, new LinearLayout.LayoutParams(lp));
        main.addView(tb);

        FrameLayout body = new FrameLayout(this);
        body.setPadding(12, 6, 12, 12);
        main.addView(body, new LinearLayout.LayoutParams(-1, -1));

        // 1. ORDER ID TAB
        vTrk = new LinearLayout(this);
        vTrk.setOrientation(LinearLayout.VERTICAL);
        vTrk.setVisibility(View.VISIBLE);

        EditText s = new EditText(this);
        s.setHint("🔍 Search last digits of Track ID...");
        s.setHintTextColor(Color.parseColor("#717688"));
        s.setTextColor(Color.WHITE);
        s.setBackground(box(Color.parseColor("#161824"), 14, Color.parseColor("#00E676"), 1));
        s.setPadding(20, 16, 20, 16);
        s.setTextSize(15f);
        s.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int i, int i1, int i2) {}
            public void onTextChanged(CharSequence c, int i, int i1, int i2) { qry(c.toString().trim()); }
            public void afterTextChanged(Editable e) {}
        });
        vTrk.addView(s);

        tCnt = new TextView(this);
        tCnt.setTextColor(Color.parseColor("#00E676"));
        tCnt.setPadding(6, 12, 6, 8);
        tCnt.setTextSize(13f);
        tCnt.setTypeface(Typeface.DEFAULT_BOLD);
        vTrk.addView(tCnt);
                ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setDividerHeight(12);
        adp = new BaseAdapter() {
            public int getCount() { return ords.size(); }
            public Object getItem(int i) { return ords.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) {
                LinearLayout c = new LinearLayout(MainActivity.this);
                c.setOrientation(LinearLayout.VERTICAL);
                c.setPadding(18, 16, 18, 16);
                c.setBackground(box(Color.parseColor("#161824"), 14, Color.parseColor("#2A2D3D"), 1));
                String[] it = ords.get(i);

                TextView t1 = new TextView(MainActivity.this);
                t1.setText("📦 Track ID: " + it[0]);
                t1.setTextColor(Color.parseColor("#38BDF8"));
                t1.setTypeface(Typeface.DEFAULT_BOLD);
                t1.setTextSize(15f);
                c.addView(t1);

                TextView t2 = new TextView(MainActivity.this);
                t2.setText("🛒 Order ID: " + it[1]);
                t2.setTextColor(Color.parseColor("#00E676"));
                t2.setTextSize(14f);
                t2.setPadding(0, 4, 0, 12);
                c.addView(t2);

                LinearLayout btnRow = new LinearLayout(MainActivity.this);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);

                Button bCpTrack = new Button(MainActivity.this);
                bCpTrack.setText("📋 Track");
                bCpTrack.setTextSize(11.5f);
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
                bCpOrder.setTextSize(11.5f);
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
                bWhatsApp.setTextSize(11.5f);
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
                bLp.setMargins(0, 0, 6, 0);
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
        catRow.setPadding(0, 0, 0, 8);
        
        bCatAgent = new Button(this);
        bCatAgent.setText("👥 AGENT");
        bCatAgent.setTextSize(11.5f);
        bCatAgent.setTypeface(Typeface.DEFAULT_BOLD);
        
        bCatKirana = new Button(this);
        bCatKirana.setText("🏪 KIRANA");
        bCatKirana.setTextSize(11.5f);
        bCatKirana.setTypeface(Typeface.DEFAULT_BOLD);

        bCatAll = new Button(this);
        bCatAll.setText("🌐 ALL (MIX)");
        bCatAll.setTextSize(11.5f);
        bCatAll.setTypeface(Typeface.DEFAULT_BOLD);

        LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(0, -2, 1f);
        catLp.setMargins(2, 0, 2, 0);
        catRow.addView(bCatAgent, catLp);
        catRow.addView(bCatKirana, new LinearLayout.LayoutParams(catLp));
        catRow.addView(bCatAll, new LinearLayout.LayoutParams(catLp));
        vPrf.addView(catRow);

        periodFilterRow = new LinearLayout(this);
        periodFilterRow.setPadding(0, 0, 0, 8);
        vPrf.addView(periodFilterRow);

        bCatAgent.setOnClickListener(v -> switchCategory("AGENT"));
        bCatKirana.setOnClickListener(v -> switchCategory("KIRANA"));
        bCatAll.setOnClickListener(v -> switchCategory("ALL"));

        tPersonalBest = new TextView(this);
        tPersonalBest.setText("🏆 Hub Best: -- DEL");
        tPersonalBest.setTextColor(Color.parseColor("#FBBF24"));
        tPersonalBest.setBackground(box(Color.parseColor("#1B1E29"), 12, Color.parseColor("#FBBF24"), 1));
        tPersonalBest.setPadding(16, 12, 16, 12);
        tPersonalBest.setTextSize(13.5f);
        tPersonalBest.setTypeface(Typeface.DEFAULT_BOLD);
        vPrf.addView(tPersonalBest);

        LinearLayout hubBox = new LinearLayout(this);
        hubBox.setOrientation(LinearLayout.VERTICAL);
        hubBox.setBackground(box(Color.parseColor("#151724"), 16, Color.parseColor("#38BDF8"), 1));
        hubBox.setPadding(18, 14, 18, 14);
        LinearLayout.LayoutParams hbLp = new LinearLayout.LayoutParams(-1, -2);
        hbLp.setMargins(0, 10, 0, 0);
        hubBox.setLayoutParams(hbLp);

        TextView hTitle = new TextView(this);
        hTitle.setText("MALBAZARHUB_NJP");
        hTitle.setTextColor(Color.parseColor("#38BDF8"));
        hTitle.setTextSize(18f);
        hTitle.setTypeface(Typeface.DEFAULT_BOLD);
        hubBox.addView(hTitle);

        tHubOfdDel = new TextView(this);
        tHubOfdDel.setTextColor(Color.WHITE);
        tHubOfdDel.setTextSize(14.5f);
        tHubOfdDel.setPadding(0, 6, 0, 0);
        hubBox.addView(tHubOfdDel);

        tHubOfpPik = new TextView(this);
        tHubOfpPik.setTextColor(Color.WHITE);
        tHubOfpPik.setTextSize(14.5f);
        tHubOfpPik.setPadding(0, 3, 0, 0);
        hubBox.addView(tHubOfpPik);

        tHubDnpDnpc = new TextView(this);
        tHubDnpDnpc.setTextColor(Color.parseColor("#34D399"));
        tHubDnpDnpc.setTextSize(15f);
        tHubDnpDnpc.setTypeface(Typeface.DEFAULT_BOLD);
        tHubDnpDnpc.setPadding(0, 3, 0, 0);
        hubBox.addView(tHubDnpDnpc);

        tGapTarget = new TextView(this);
        tGapTarget.setTextColor(Color.parseColor("#FB923C"));
        tGapTarget.setTextSize(14f);
        tGapTarget.setTypeface(Typeface.DEFAULT_BOLD);
        tGapTarget.setPadding(0, 8, 0, 0);
        hubBox.addView(tGapTarget);
        vPrf.addView(hubBox);

        LinearLayout sm = new LinearLayout(this);
        sm.setPadding(0, 10, 0, 8);
        LinearLayout sc1 = makeSummaryCard("TOP CONVERSION", Color.parseColor("#151724"), Color.parseColor("#00E676"), true);
        LinearLayout sc2 = makeSummaryCard("TOP DNPC", Color.parseColor("#151724"), Color.parseColor("#FB923C"), false);
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(0, -2, 1f);
        scLp.setMargins(0, 0, 8, 0);
        sm.addView(sc1, scLp);
        sm.addView(sc2, new LinearLayout.LayoutParams(0, -2, 1f));
        vPrf.addView(sm);

        LinearLayout actRow = new LinearLayout(this);
        bSort = new Button(this);
        bSort.setText("↕️ Sort");
        bSort.setBackground(box(Color.parseColor("#1C1E2A"), 10, 0, 0));
        bSort.setTextColor(Color.WHITE);
        bSort.setTextSize(11.5f);
        bSort.setTypeface(Typeface.DEFAULT_BOLD);
        bSort.setOnClickListener(v -> {
            isHighToLow = !isHighToLow;
            load();
        });

        bVsBattle = new Button(this);
        bVsBattle.setText("⚔️ Arena");
        bVsBattle.setBackground(box(Color.parseColor("#7C3AED"), 10, 0, 0));
        bVsBattle.setTextColor(Color.WHITE);
        bVsBattle.setTextSize(11.5f);
        bVsBattle.setTypeface(Typeface.DEFAULT_BOLD);
        bVsBattle.setOnClickListener(v -> showAgentVsAgentDialog());

        bShareReport = new Button(this);
        bShareReport.setText("📢 Share Hub");
        bShareReport.setBackground(box(Color.parseColor("#25D366"), 10, 0, 0));
        bShareReport.setTextColor(Color.BLACK);
        bShareReport.setTextSize(11.5f);
        bShareReport.setTypeface(Typeface.DEFAULT_BOLD);
        bShareReport.setOnClickListener(v -> shareLiveReportToGroup());

        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(0, -2, 1f);
        aLp.setMargins(0, 2, 4, 10);
        actRow.addView(bSort, aLp);
        actRow.addView(bVsBattle, new LinearLayout.LayoutParams(aLp));
        actRow.addView(bShareReport, new LinearLayout.LayoutParams(0, -2, 1f));
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

        bCatAgent.setBackground(box("AGENT".equals(cat) ? Color.parseColor("#00E676") : Color.parseColor("#1C1E2A"), 10, 0, 0));
        bCatAgent.setTextColor("AGENT".equals(cat) ? Color.BLACK : Color.parseColor("#8E92A4"));

        bCatKirana.setBackground(box("KIRANA".equals(cat) ? Color.parseColor("#00E676") : Color.parseColor("#1C1E2A"), 10, 0, 0));
        bCatKirana.setTextColor("KIRANA".equals(cat) ? Color.BLACK : Color.parseColor("#8E92A4"));

        bCatAll.setBackground(box("ALL".equals(cat) ? Color.parseColor("#00E676") : Color.parseColor("#1C1E2A"), 10, 0, 0));
        bCatAll.setTextColor("ALL".equals(cat) ? Color.BLACK : Color.parseColor("#8E92A4"));

        setupPeriodButtons();
        load();
    }

    void setupPeriodButtons() {
        periodFilterRow.removeAllViews();
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(0, -2, 1f);
        pLp.setMargins(2, 0, 2, 0);

        bSubDay = new Button(this);
        bSubDay.setText("📅 Day (Live)");
        bSubDay.setTextSize(11f);
        bSubDay.setTypeface(Typeface.DEFAULT_BOLD);
        bSubDay.setOnClickListener(v -> {
            mode = "daily";
            updatePeriodBtnStyles();
            load();
        });

        String yearlyTitle = "KIRANA".equals(currentCategory) ? "📊 Yearly (All Cycles)" : "📊 Yearly (All Weeks)";
        bSubYearly = new Button(this);
        bSubYearly.setText(yearlyTitle);
        bSubYearly.setTextSize(11f);
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
            bSubDay.setBackground(box(active ? Color.parseColor("#00E676") : Color.parseColor("#1C1E2A"), 10, 0, 0));
            bSubDay.setTextColor(active ? Color.BLACK : Color.parseColor("#8E92A4"));
        }
        if (bSubYearly != null) {
            boolean active = "yearly".equals(mode);
            bSubYearly.setBackground(box(active ? Color.parseColor("#00E676") : Color.parseColor("#1C1E2A"), 10, 0, 0));
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

        bT.setBackground(box(index == 0 ? Color.parseColor("#00E676") : Color.parseColor("#1C1E2A"), 10, 0, 0));
        bT.setTextColor(index == 0 ? Color.BLACK : Color.parseColor("#8E92A4"));

        bP.setBackground(box(index == 1 ? Color.parseColor("#00E676") : Color.parseColor("#1C1E2A"), 10, 0, 0));
        bP.setTextColor(index == 1 ? Color.BLACK : Color.parseColor("#8E92A4"));

        bH.setBackground(box(index == 2 ? Color.parseColor("#00E676") : Color.parseColor("#1C1E2A"), 10, 0, 0));
        bH.setTextColor(index == 2 ? Color.BLACK : Color.parseColor("#8E92A4"));

        bC.setBackground(box(index == 3 ? Color.parseColor("#00E676") : Color.parseColor("#1C1E2A"), 10, 0, 0));
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
        c.setPadding(16, 14, 16, 14);
        TextView h = new TextView(this);
        h.setText(title);
        h.setTextColor(Color.parseColor("#9CA3AF"));
        h.setTextSize(11f);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(h);
        TextView v = new TextView(this);
        v.setTextColor(accent);
        v.setTextSize(14f);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, 6, 0, 0);
        c.addView(v);
        if (isConv) tTopConv = v; else tTopDnpc = v;
        return c;
    }
        void load() {
        try {
            vCrd.removeAllViews();
            agentNamesList.clear();
            String opDate = getOperationalDate();

            String w = "daily".equals(mode) ? " WHERE dt = (SELECT MAX(dt) FROM prf) " : " WHERE dt >= '" + getYearStartDate(opDate) + "' ";

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

                double ofdRate = Double.parseDouble(ag[9]);
                int ofdVal = Integer.parseInt(ag[1]);
                int delVal = Integer.parseInt(ag[2]);
                int ofpVal = Integer.parseInt(ag[3]);
                int pikVal = Integer.parseInt(ag[4]);
                int dnpVal = Integer.parseInt(ag[5]);
                int dnpcVal = Integer.parseInt(ag[6]);

                int badgeColor = getPerformanceColor(ofdRate, ofdVal);

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(box(Color.parseColor("#151724"), 16, Color.parseColor("#2A2D3D"), 1));
                card.setPadding(18, 16, 18, 16);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
                clp.setMargins(0, 0, 0, 14);
                card.setLayoutParams(clp);

                // Top Header Row
                LinearLayout nameRow = new LinearLayout(this);
                nameRow.setOrientation(LinearLayout.HORIZONTAL);
                nameRow.setGravity(Gravity.CENTER_VERTICAL);

                String rankBadge = (currentRank == 1) ? "🥇 #1" : ((currentRank == 2) ? "🥈 #2" : ((currentRank == 3) ? "🥉 #3" : "#" + currentRank));
                int rankBg = (currentRank == 1) ? Color.parseColor("#EAB308") : ((currentRank == 2) ? Color.parseColor("#94A3B8") : ((currentRank == 3) ? Color.parseColor("#B45309") : Color.parseColor("#374151")));

                TextView tRnk = new TextView(this);
                tRnk.setText(rankBadge);
                tRnk.setTextColor(Color.WHITE);
                tRnk.setTextSize(11.5f);
                tRnk.setTypeface(Typeface.DEFAULT_BOLD);
                tRnk.setBackground(box(rankBg, 8, 0, 0));
                tRnk.setPadding(10, 4, 10, 4);
                nameRow.addView(tRnk);

                TextView n = new TextView(this);
                n.setText("  " + ag[0]);
                n.setTextColor(badgeColor);
                n.setTypeface(Typeface.DEFAULT_BOLD);
                n.setTextSize(17f);
                LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(0, -2, 1f);
                nameRow.addView(n, nLp);

                String streakDisplay = (prevStrk > 0) ? "🔥 " + curStrk + "D (Prev: " + prevStrk + "D)" : "🔥 " + curStrk + "D";
                TextView st = new TextView(this);
                st.setText(streakDisplay);
                st.setTextColor(Color.parseColor("#FBBF24"));
                st.setTextSize(11.5f);
                st.setTypeface(Typeface.DEFAULT_BOLD);
                st.setBackground(box(Color.parseColor("#1F222E"), 8, 0, 0));
                st.setPadding(10, 5, 10, 5);
                nameRow.addView(st);
                card.addView(nameRow);

                // Stats Section
                LinearLayout statsBox = new LinearLayout(this);
                statsBox.setOrientation(LinearLayout.VERTICAL);
                statsBox.setBackground(box(Color.parseColor("#1B1D2C"), 12, Color.parseColor("#26293B"), 1));
                statsBox.setPadding(14, 12, 14, 12);
                LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(-1, -2);
                sbLp.setMargins(0, 10, 0, 8);
                statsBox.setLayoutParams(sbLp);

                TextView t1 = new TextView(this);
                t1.setText("🚚 OFD / DEL = " + ofdVal + " / " + delVal + "  ➔  " + ag[9] + "% DEL");
                t1.setTextColor(badgeColor);
                t1.setTextSize(14.5f);
                t1.setTypeface(Typeface.DEFAULT_BOLD);
                statsBox.addView(t1);

                TextView t2 = new TextView(this);
                t2.setText("📦 OFP / PIK = " + ofpVal + " / " + pikVal + " (" + ag[10] + "%)  •  DNP = " + dnpVal + "/" + dnpcVal + " (" + ag[7] + "%)");
                t2.setTextColor(Color.parseColor("#9CA3AF"));
                t2.setTextSize(12.5f);
                t2.setPadding(0, 4, 0, 0);
                statsBox.addView(t2);

                int agTargetNeeded = (int) Math.ceil(0.92 * ofdVal);
                int agDiff = agTargetNeeded - delVal;

                TextView tAgGap = new TextView(this);
                if (agDiff <= 0 && ofdVal > 0) {
                    tAgGap.setText("🎯 92% Target Achieved! 🚀");
                    tAgGap.setTextColor(Color.parseColor("#00E676"));
                } else if (ofdVal > 0) {
                    tAgGap.setText("🎯 Gap to 92%: " + agDiff + " more DEL required");
                    tAgGap.setTextColor(Color.parseColor("#FB923C"));
                } else {
                    tAgGap.setText("");
                }
                tAgGap.setTextSize(13f);
                tAgGap.setTypeface(Typeface.DEFAULT_BOLD);
                tAgGap.setPadding(0, 4, 0, 0);
                statsBox.addView(tAgGap);
                card.addView(statsBox);

                // Previous Miss Notification
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
                    tPrevMiss.setTextSize(12f);
                    tPrevMiss.setTypeface(Typeface.DEFAULT_BOLD);
                    tPrevMiss.setPadding(0, 0, 0, 6);
                    card.addView(tPrevMiss);
                }
                if (yc != null) yc.close();

                // Card Action Row: Details & Single Share
                LinearLayout btnRow = new LinearLayout(this);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);

                Button bDetails = new Button(this);
                bDetails.setText("📊 View Weeks / Day Logs");
                bDetails.setBackground(box(Color.parseColor("#1F2232"), 8, Color.parseColor("#38BDF8"), 1));
                bDetails.setTextColor(Color.parseColor("#38BDF8"));
                bDetails.setTextSize(11.5f);
                bDetails.setTypeface(Typeface.DEFAULT_BOLD);
                final String agName = ag[0];
                bDetails.setOnClickListener(v -> showDetails(agName));

                Button bSingleShare = new Button(this);
                bSingleShare.setText("📢 Share Card");
                bSingleShare.setBackground(box(Color.parseColor("#25D366"), 8, 0, 0));
                bSingleShare.setTextColor(Color.BLACK);
                bSingleShare.setTextSize(11.5f);
                bSingleShare.setTypeface(Typeface.DEFAULT_BOLD);
                bSingleShare.setOnClickListener(v -> shareSingleAgentReport(agName, ofdVal, delVal, ofpVal, pikVal, dnpVal, dnpcVal, ofdRate));

                LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(0, -2, 1.2f);
                dLp.setMargins(0, 0, 6, 0);
                btnRow.addView(bDetails, dLp);
                btnRow.addView(bSingleShare, new LinearLayout.LayoutParams(0, -2, 0.8f));
                card.addView(btnRow);

                vCrd.addView(card);
                currentRank++;
            }
        } catch (Exception ignored) {}
        }
        void shareLiveReportToGroup() {
        try {
            String opDate = getOperationalDate();
            StringBuilder sb = new StringBuilder();
            sb.append("📊 *MALBAZARHUB_NJP - LIVE PERFORMANCE REPORT*\n");
            sb.append("📅 *Date:* ").append(opDate).append(" | *Category:* ").append(currentCategory).append("\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━\n");

            String w = "daily".equals(mode) ? " WHERE dt = (SELECT MAX(dt) FROM prf) " : " WHERE dt >= '" + getYearStartDate(opDate) + "' ";
            Cursor hc = db.rawQuery("SELECT SUM(o), SUM(l), SUM(p), SUM(k) FROM prf " + w, null);
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
                    sb.append("🎯 *Target Status:* 92% Achieved! 🚀\n");
                } else if (to > 0) {
                    sb.append("🎯 *Target Status:* ⚠️ Gap to 92%: ").append(diff).append(" DEL required\n");
                }
            }
            if (hc != null) hc.close();

            sb.append("━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("🏆 *AGENT SCORECARD (RANK-WISE):*\n\n");

            Cursor ac = db.rawQuery("SELECT n, SUM(o), SUM(l), SUM(p), SUM(k) FROM prf " + w + " GROUP BY n", null);
            ArrayList<String[]> list = new ArrayList<>();
            while (ac != null && ac.moveToNext()) {
                String name = ac.getString(0);
                boolean isKirana = isKiranaAgent(name);
                if ("AGENT".equals(currentCategory) && isKirana) continue;
                if ("KIRANA".equals(currentCategory) && !isKirana) continue;

                int o = ac.getInt(1), l = ac.getInt(2), p = ac.getInt(3), k = ac.getInt(4);
                int dnp = o + p, dnpc = l + k;
                double r = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;
                double ofdC = o > 0 ? ((double) l / o) * 100.0 : 0.0;
                list.add(new String[]{name, String.valueOf(o), String.valueOf(l), String.format(Locale.US, "%.1f", ofdC), String.valueOf(r)});
            }
            if (ac != null) ac.close();

            Collections.sort(list, (a, b) -> Double.compare(Double.parseDouble(b[4]), Double.parseDouble(a[4])));

            int rank = 1;
            for (String[] ag : list) {
                double conv = Double.parseDouble(ag[3]);
                String badgeIcon = (conv >= 96.0) ? "🌟" : ((conv >= 92.0) ? "🔥" : ((conv >= 88.0) ? "⚠️" : "🚨"));
                String medal = (rank == 1) ? "🥇" : ((rank == 2) ? "🥈" : ((rank == 3) ? "🥉" : rank + "."));

                sb.append(medal).append(" ").append(badgeIcon).append(" *").append(ag[0]).append("*\n");
                sb.append("   ➔ DEL: ").append(ag[2]).append("/").append(ag[1]).append(" (").append(ag[3]).append("%)\n\n");
                rank++;
            }

            sb.append("━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("⚡ _Generated via Delivery Tracker Pro | Managed by Adarsh_");

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
            startActivity(Intent.createChooser(shareIntent, "📢 Share Live Report to Group"));
        } catch (Exception e) {
            Toast.makeText(this, "Error sharing: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
        title.setTextSize(17f);
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
            vsTitle.setTextSize(15f);
            resBox.addView(vsTitle);

            String r1 = getAgentStats(ag1);
            String r2 = getAgentStats(ag2);

            TextView stats = new TextView(MainActivity.this);
            stats.setText("\n👤 " + ag1 + ":\n" + r1 + "\n\n👤 " + ag2 + ":\n" + r2);
            stats.setTextColor(Color.WHITE);
            stats.setTextSize(14f);
            resBox.addView(stats);
        });
        dLayout.addView(bComp);

        new AlertDialog.Builder(this)
                .setView(dLayout)
                .setPositiveButton("Close", null)
                .show();
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

    String getAgentStats(String name) {
        String opDate = getOperationalDate();
        String w = "daily".equals(mode) ? " WHERE n = ? AND dt = (SELECT MAX(dt) FROM prf) " : " WHERE n = ? AND dt >= '" + getYearStartDate(opDate) + "' ";

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
        boolean isKirana = isKiranaAgent(name);

        LinearLayout pop = new LinearLayout(this);
        pop.setOrientation(LinearLayout.VERTICAL);
        pop.setPadding(20, 20, 20, 20);
        pop.setBackgroundColor(Color.parseColor("#0F1015"));

        TextView h = new TextView(this);
        h.setText((isKirana ? "🏪 " : "👤 ") + name + " (Click Week / Cycle for Days)");
        h.setTextColor(Color.parseColor("#00E676"));
        h.setTextSize(16f);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, 0, 0, 12);
        pop.addView(h);

        ScrollView sv = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        if (isKirana) {
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
                        String tTitle = "🏪 " + mName + " Cyc 2 (16-" + maxD + ")";
                        final int oVal = c2.getInt(0), lVal = c2.getInt(1), pVal = c2.getInt(2), kVal = c2.getInt(3);
                        content.addView(makeInteractivePeriodCard(name, tTitle, oVal, lVal, pVal, kVal, d2Start, d2End));
                    }
                    if (c2 != null) c2.close();

                    String d1Start = ym + "-01", d1End = ym + "-15";
                    Cursor c1 = db.rawQuery("SELECT SUM(o), SUM(l), SUM(p), SUM(k) FROM prf WHERE n = ? AND dt >= ? AND dt <= ?", new String[]{name, d1Start, d1End});
                    if (c1 != null && c1.moveToFirst() && (c1.getInt(0) + c1.getInt(2)) > 0) {
                        String tTitle = "🏪 " + mName + " Cyc 1 (1-15)";
                        final int oVal = c1.getInt(0), lVal = c1.getInt(1), pVal = c1.getInt(2), kVal = c1.getInt(3);
                        content.addView(makeInteractivePeriodCard(name, tTitle, oVal, lVal, pVal, kVal, d1Start, d1End));
                    }
                    if (c1 != null) c1.close();
                } catch (Exception ignored) {}
            }
            if (mc != null) mc.close();
            if (!foundAny) content.addView(makeEmptyText("No Kirana records found."));
        } else {
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
                    content.addView(makeInteractivePeriodCard(name, wHeaderTitle, sumO, sumL, sumP, sumK, parts[0], parts[1]));
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
        LinearLayout makeInteractivePeriodCard(String agentName, String title, int o, int l, int p, int k, String dStart, String dEnd) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(box(Color.parseColor("#171927"), 14, Color.parseColor("#38BDF8"), 1));
        card.setPadding(16, 14, 16, 14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, 12);
        card.setLayoutParams(lp);

        int dnp = o + p, dnpc = l + k;
        double ofdC = o > 0 ? ((double) l / o) * 100.0 : 0.0;
        double totC = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;

        TextView tHead = new TextView(this);
        tHead.setText(title);
        tHead.setTextColor(Color.parseColor("#38BDF8"));
        tHead.setTextSize(14.5f);
        tHead.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tHead);

        TextView t1 = new TextView(this);
        t1.setText("OFD/DEL = " + o + "/" + l + " (" + String.format(Locale.US, "%.1f%%", ofdC) + ")  •  DNP = " + dnp + "/" + dnpc + " (" + String.format(Locale.US, "%.1f%%", totC) + ")");
        t1.setTextColor(Color.WHITE);
        t1.setTextSize(13f);
        t1.setPadding(0, 4, 0, 0);
        card.addView(t1);

        String badgeText = getPerformanceBadge(ofdC, o);
        int badgeCol = getPerformanceColor(ofdC, o);
        TextView tBadge = new TextView(this);
        tBadge.setText("🏆 " + badgeText);
        tBadge.setTextColor(badgeCol);
        tBadge.setTextSize(12f);
        tBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tBadge.setPadding(0, 4, 0, 10);
        card.addView(tBadge);

        // Buttons: Day logs & Share
        LinearLayout bRow = new LinearLayout(this);
        bRow.setOrientation(LinearLayout.HORIZONTAL);

        Button bDays = new Button(this);
        bDays.setText("📅 View Day Logs");
        bDays.setBackground(box(Color.parseColor("#1F2232"), 8, Color.parseColor("#00E676"), 1));
        bDays.setTextColor(Color.parseColor("#00E676"));
        bDays.setTextSize(11.5f);
        bDays.setTypeface(Typeface.DEFAULT_BOLD);
        bDays.setOnClickListener(v -> showDayByDayDialog(agentName, title, dStart, dEnd));

        Button bShare = new Button(this);
        bShare.setText("📢 Share");
        bShare.setBackground(box(Color.parseColor("#25D366"), 8, 0, 0));
        bShare.setTextColor(Color.BLACK);
        bShare.setTextSize(11.5f);
        bShare.setTypeface(Typeface.DEFAULT_BOLD);
        bShare.setOnClickListener(v -> sharePeriodReport(agentName, title, o, l, p, k, dnp, dnpc, ofdC));

        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(0, -2, 1.2f);
        dLp.setMargins(0, 0, 6, 0);
        bRow.addView(bDays, dLp);
        bRow.addView(bShare, new LinearLayout.LayoutParams(0, -2, 0.8f));
        card.addView(bRow);

        return card;
    }

    void showDayByDayDialog(String name, String periodTitle, String dStart, String dEnd) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat sdfDayName = new SimpleDateFormat("EEEE, dd MMM yyyy", Locale.US);

        LinearLayout pop = new LinearLayout(this);
        pop.setOrientation(LinearLayout.VERTICAL);
        pop.setPadding(20, 20, 20, 20);
        pop.setBackgroundColor(Color.parseColor("#0F1015"));

        TextView h = new TextView(this);
        h.setText("📅 Day-by-Day Logs: " + name + "\n" + periodTitle);
        h.setTextColor(Color.parseColor("#38BDF8"));
        h.setTextSize(15f);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, 0, 0, 12);
        pop.addView(h);

        ScrollView sv = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        Cursor c = db.rawQuery("SELECT dt, o, l, p, k FROM prf WHERE n = ? AND dt >= ? AND dt <= ? ORDER BY dt DESC", new String[]{name, dStart, dEnd});
        boolean hasData = false;
        while (c != null && c.moveToNext()) {
            hasData = true;
            String dStr = c.getString(0);
            int o = c.getInt(1), l = c.getInt(2), p = c.getInt(3), k = c.getInt(4);
            int dnp = o + p, dnpc = l + k;
            double ofdC = o > 0 ? ((double) l / o) * 100.0 : 0.0;
            double totC = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;

            String dayTitle = dStr;
            try {
                Calendar dCal = Calendar.getInstance();
                dCal.setTime(sdf.parse(dStr));
                dayTitle = sdfDayName.format(dCal.getTime());
            } catch (Exception ignored) {}

            LinearLayout dayCard = new LinearLayout(this);
            dayCard.setOrientation(LinearLayout.VERTICAL);
            dayCard.setBackground(box(Color.parseColor("#151724"), 12, Color.parseColor("#2A2D3D"), 1));
            dayCard.setPadding(14, 12, 14, 12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, 8);
            dayCard.setLayoutParams(lp);

            TextView tD = new TextView(this);
            tD.setText("📅 " + dayTitle);
            tD.setTextColor(Color.parseColor("#38BDF8"));
            tD.setTextSize(13.5f);
            tD.setTypeface(Typeface.DEFAULT_BOLD);
            dayCard.addView(tD);

            TextView t1 = new TextView(this);
            t1.setText("OFD/DEL = " + o + "/" + l + " (" + String.format(Locale.US, "%.1f%%", ofdC) + ")  •  DNP = " + dnp + "/" + dnpc + " (" + String.format(Locale.US, "%.1f%%", totC) + ")");
            t1.setTextColor(Color.WHITE);
            t1.setTextSize(12.5f);
            t1.setPadding(0, 2, 0, 0);
            dayCard.addView(t1);

            int badgeCol = getPerformanceColor(ofdC, o);
            TextView tB = new TextView(this);
            tB.setText(getPerformanceBadge(ofdC, o));
            tB.setTextColor(badgeCol);
            tB.setTextSize(11.5f);
            tB.setTypeface(Typeface.DEFAULT_BOLD);
            tB.setPadding(0, 2, 0, 6);
            dayCard.addView(tB);

            Button bDayShare = new Button(this);
            bDayShare.setText("📢 Share This Day");
            bDayShare.setBackground(box(Color.parseColor("#25D366"), 6, 0, 0));
            bDayShare.setTextColor(Color.BLACK);
            bDayShare.setTextSize(11f);
            bDayShare.setTypeface(Typeface.DEFAULT_BOLD);
            final String fDayTitle = dayTitle;
            bDayShare.setOnClickListener(v -> shareSingleAgentReport(name + " (" + fDayTitle + ")", o, l, p, k, dnp, dnpc, ofdC));
            dayCard.addView(bDayShare);

            content.addView(dayCard);
        }
        if (c != null) c.close();

        if (!hasData) content.addView(makeEmptyText("No days recorded for this period."));
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
        h.setTextSize(16f);
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
            item.setTextSize(13.5f);
            item.setPadding(14, 12, 14, 12);
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
                h.setTextSize(15f);
                d.addView(h);

                TextView otpVal = new TextView(this);
                otpVal.setText(spoken);
                otpVal.setTextColor(Color.parseColor("#00E676"));
                otpVal.setTextSize(34f);
                otpVal.setTypeface(Typeface.DEFAULT_BOLD);
                otpVal.setGravity(Gravity.CENTER);
                otpVal.setPadding(0, 16, 0, 16);
                d.addView(otpVal);

                TextView note = new TextView(this);
                note.setText("✅ Copied to Clipboard! Ready to paste in FieldX.");
                note.setTextColor(Color.parseColor("#9CA3AF"));
                note.setTextSize(13f);
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
                card.setBackground(box(Color.parseColor("#151724"), 14, Color.parseColor("#2A2D3D"), 1));
                card.setPadding(18, 14, 18, 14);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
                clp.setMargins(0, 0, 0, 10);
                card.setLayoutParams(clp);

                TextView hName = new TextView(this);
                hName.setText("🏢 " + name);
                hName.setTextColor(Color.parseColor("#60A5FA"));
                hName.setTypeface(Typeface.DEFAULT_BOLD);
                hName.setTextSize(16f);
                card.addView(hName);

                TextView t1 = new TextView(this);
                t1.setText("OFD/DEL = " + o + "/" + l + " = " + lc);
                t1.setTextColor(Color.parseColor("#D1D5DB"));
                t1.setTextSize(13.5f);
                t1.setPadding(0, 4, 0, 0);
                card.addView(t1);

                TextView t2 = new TextView(this);
                t2.setText("OFP/PIKED = " + p + "/" + k + " = " + kc);
                t2.setTextColor(Color.parseColor("#D1D5DB"));
                t2.setTextSize(13.5f);
                t2.setPadding(0, 2, 0, 0);
                card.addView(t2);

                TextView t3 = new TextView(this);
                t3.setText("DNP/DNPC = " + dnp + "/" + dnpc + " = " + tc);
                t3.setTextColor(Color.parseColor("#34D399"));
                t3.setTextSize(13.5f);
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
        if (c != null) c.close();
    }

    void addContactHeader(String text) {
        TextView th = new TextView(this);
        th.setText(text);
        th.setTextColor(Color.parseColor("#9CA3AF"));
        th.setTextSize(13f);
        th.setTypeface(Typeface.DEFAULT_BOLD);
        th.setPadding(4, 16, 4, 8);
        vCntCrd.addView(th);
    }

    void addContactItem(String name, String role, String phone, String colorHex) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(box(Color.parseColor("#151724"), 14, Color.parseColor("#2A2D3D"), 1));
        card.setPadding(18, 14, 18, 14);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.setMargins(0, 0, 0, 10);
        card.setLayoutParams(clp);

        TextView tN = new TextView(this);
        tN.setText("👤 " + name);
        tN.setTextColor(Color.parseColor(colorHex));
        tN.setTextSize(15.5f);
        tN.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tN);

        TextView tR = new TextView(this);
        tR.setText(role + "  •  📞 " + phone);
        tR.setTextColor(Color.parseColor("#D1D5DB"));
        tR.setTextSize(13.5f);
        tR.setPadding(0, 2, 0, 10);
        card.addView(tR);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button bCall = new Button(this);
        bCall.setText("📞 Call");
        bCall.setTextSize(11.5f);
        bCall.setBackground(box(Color.parseColor("#0284C7"), 8, 0, 0));
        bCall.setTextColor(Color.WHITE);
        bCall.setTypeface(Typeface.DEFAULT_BOLD);
        bCall.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
            startActivity(intent);
        });

        Button bWp = new Button(this);
        bWp.setText("💬 WhatsApp");
        bWp.setTextSize(11.5f);
        bWp.setBackground(box(Color.parseColor("#25D366"), 8, 0, 0));
        bWp.setTextColor(Color.BLACK);
        bWp.setTypeface(Typeface.DEFAULT_BOLD);
        bWp.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=91" + phone));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "WhatsApp error", Toast.LENGTH_SHORT).show();
            }
        });

        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, -2, 1f);
        bLp.setMargins(0, 0, 6, 0);
        row.addView(bCall, bLp);
        row.addView(bWp, new LinearLayout.LayoutParams(0, -2, 1f));

        card.addView(row);
        vCntCrd.addView(card);
    }

    void qry(String q) {
        ords.clear();
        if (!q.isEmpty()) {
            Cursor c = db.rawQuery("SELECT t, d FROM ord WHERE t LIKE ? LIMIT 50", new String[]{"%" + q + "%"});
            while (c != null && c.moveToNext()) {
                ords.add(new String[]{c.getString(0), c.getString(1)});
            }
            if (c != null) c.close();
        }
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

    ArrayList<String> fastSplitCsv(String line) {
        ArrayList<String> res = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                res.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(ch);
            }
        }
        res.add(sb.toString());
        return res;
    }

    void doSync(boolean isAuto) {
        if (!isAuto) showLoading(true, "⏳ Syncing Live Data...\nPlease wait");
        try {
            String targetUrl = CSV;
            HttpURLConnection conn = null;
            for (int i = 0; i < 5; i++) {
                conn = (HttpURLConnection) new URL(targetUrl).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
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
            String opDate = getOperationalDate();
            int parsedAgentsInSheet = 0;

            db.beginTransaction();
            db.execSQL("DELETE FROM ord");
            db.execSQL("DELETE FROM contacts");
            db.execSQL("DELETE FROM hub_prf WHERE dt = '" + opDate + "'");

            ArrayList<ContentValues> tempAgentData = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                ArrayList<String> p = fastSplitCsv(line);

                if (p.size() >= 1) {
                    String trackId = clean(p.get(0));
                    String ordId = (p.size() > 1) ? clean(p.get(1)) : "";
                    if (!trackId.isEmpty() && !trackId.equalsIgnoreCase("TRACKING ID") && !trackId.equalsIgnoreCase("TRACK ID") && !trackId.equalsIgnoreCase("WAYBILL") && !trackId.equalsIgnoreCase("NAME")) {
                        ContentValues ocv = new ContentValues();
                        ocv.put("t", trackId);
                        ocv.put("d", ordId.isEmpty() ? trackId : ordId);
                        db.insertWithOnConflict("ord", null, ocv, SQLiteDatabase.CONFLICT_REPLACE);
                    }
                }

                if (p.size() > 2) {
                    String name = clean(p.get(2));
                    if (!name.isEmpty() && !name.equalsIgnoreCase("NAME") && !name.equalsIgnoreCase("Total") && !name.contains("Total") && !name.equalsIgnoreCase("#N/A") && !name.equalsIgnoreCase("N/A")) {
                        int o = (p.size() > 3) ? parseInt(p.get(3)) : 0;
                        int l = (p.size() > 4) ? parseInt(p.get(4)) : 0;
                        int op = (p.size() > 5) ? parseInt(p.get(5)) : 0;
                        int k = (p.size() > 6) ? parseInt(p.get(6)) : 0;

                        if (o > 0 || l > 0 || op > 0 || k > 0) {
                            ContentValues cv = new ContentValues();
                            cv.put("n", name);
                            cv.put("o", o);
                            cv.put("l", l);
                            cv.put("p", op);
                            cv.put("k", k);
                            cv.put("dt", opDate);
                            tempAgentData.add(cv);
                            parsedAgentsInSheet++;
                        }
                    }
                }

                if (p.size() > 8) {
                    String hname = clean(p.get(8));
                    if (!hname.isEmpty() && !hname.equalsIgnoreCase("HUB NAME")) {
                        String o = (p.size() > 9) ? clean(p.get(9)) : "0";
                        String l = (p.size() > 10) ? clean(p.get(10)) : "0";
                        String lc = (p.size() > 11) ? clean(p.get(11)) : "0%";
                        String ofp = (p.size() > 12) ? clean(p.get(12)) : "0";
                        String pik = (p.size() > 13) ? clean(p.get(13)) : "0";
                        String kc = (p.size() > 14) ? clean(p.get(14)) : "0%";
                        String tc = (p.size() > 15) ? clean(p.get(15)) : "0%";

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
                        hcv.put("dt", opDate);
                        db.insert("hub_prf", null, hcv);
                    }
                }

                if (p.size() > 19) {
                    String cName = clean(p.get(17));
                    String cRole = clean(p.get(18));
                    String cPhone = clean(p.get(19));
                    if (!cName.isEmpty() && !cName.equalsIgnoreCase("NAME") && !cPhone.equalsIgnoreCase("PHONE") && cPhone.matches(".*\\d+.*")) {
                        ContentValues cntCv = new ContentValues();
                        cntCv.put("name", cName);
                        cntCv.put("role", cRole.isEmpty() ? "Staff" : cRole);
                        cntCv.put("phone", cPhone);
                        db.insert("contacts", null, cntCv);
                    }
                }
            }

            SharedPreferences prefs = getSharedPreferences("DeliveryTrackerPrefs", MODE_PRIVATE);
            if (parsedAgentsInSheet == 0) {
                Cursor lastDayC = db.rawQuery("SELECT dt FROM prf GROUP BY dt HAVING SUM(o) > 0 ORDER BY dt DESC LIMIT 1", null);
                if (lastDayC != null && lastDayC.moveToFirst()) {
                    String completedDay = lastDayC.getString(0);
                    boolean alreadyReported = prefs.getBoolean("eod_reported_" + completedDay, false);
                    if (!alreadyReported) {
                        prefs.edit().putBoolean("eod_reported_" + completedDay, true).apply();
                        new Handler(Looper.getMainLooper()).post(() -> triggerAutomaticEODReport(completedDay));
                    }
                }
                if (lastDayC != null) lastDayC.close();
            } else {
                db.execSQL("DELETE FROM prf WHERE dt = '" + opDate + "'");
                for (ContentValues cv : tempAgentData) {
                    db.insert("prf", null, cv);
                }
            }

            db.setTransactionSuccessful();
            db.endTransaction();
            reader.close();
            lastSyncTime = System.currentTimeMillis();

                        new Handler(Looper.getMainLooper()).post(() -> {
                load();
                loadHubVsHub();
                loadContacts();
                cnt();
                qry("");
                if (!isAuto) {
                    showLoading(false, null);
                    Toast.makeText(MainActivity.this, "✅ Synced Successfully!", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).post(() -> {
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
