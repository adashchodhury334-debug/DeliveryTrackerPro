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
import android.widget.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    SQLiteDatabase db;
    FrameLayout root;
    LinearLayout vTrk, vPrf, vCrd, hubStat;
    Button bT, bP, b1, b2, b3, bSort;
    TextView tTopConv, tTopDnpc, tCnt, tHubConv;
    ArrayList<String[]> ords = new ArrayList<>();
    BaseAdapter adp;
    String mode = "daily";
    boolean isHighToLow = true;
    String CSV = "https://docs.google.com/spreadsheets/d/1SxsB-1srlfIv3AN5H2ZbMJDEyteJ6LIDTV4EI7rbxjw/export?format=csv";

    GradientDrawable box(int c, int r, int sCol, int sW) {
        GradientDrawable g = new GradientDrawable(); g.setColor(c); g.setCornerRadius(r);
        if (sW > 0) g.setStroke(sW, sCol); return g;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); requestWindowFeature(Window.FEATURE_NO_TITLE);
        try {
            db = openOrCreateDatabase("TrackerV11.db", MODE_PRIVATE, null);
            db.execSQL("CREATE TABLE IF NOT EXISTS ord (t TEXT UNIQUE, d TEXT);");
            db.execSQL("CREATE TABLE IF NOT EXISTS prf (n TEXT, o INT, l INT, p INT, k INT, dt TEXT);");
        } catch (Exception ignored) {}

        root = new FrameLayout(this); root.setBackgroundColor(Color.parseColor("#0F1015"));
        setContentView(root); new Thread(() -> doSync(true)).start();
        new Handler().postDelayed(this::buildUI, 1500);
    }

    void buildUI() {
        LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL);
        LinearLayout h = new LinearLayout(this); h.setBackgroundColor(Color.parseColor("#181920")); h.setPadding(24, 16, 24, 16); h.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this); t.setText("📊 Delivery Tracker"); t.setTextColor(Color.WHITE); t.setTextSize(16f); t.setTypeface(Typeface.DEFAULT_BOLD);
        h.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        Button bRef = new Button(this); bRef.setText("🔄 Sync"); bRef.setBackground(box(Color.parseColor("#00E676"), 8, 0, 0)); bRef.setOnClickListener(v -> new Thread(() -> doSync(false)).start()); h.addView(bRef);
        main.addView(h);

        LinearLayout tb = new LinearLayout(this); tb.setPadding(16, 8, 16, 4);
        bT = new Button(this); bT.setText("🔍 Tracker"); bT.setBackground(box(Color.parseColor("#232634"), 12, 0, 0)); bT.setTextColor(Color.parseColor("#8E92A4"));
        bP = new Button(this); bP.setText("📈 Performance"); bP.setBackground(box(Color.parseColor("#00E676"), 12, 0, 0)); bP.setTextColor(Color.BLACK);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f); lp.setMargins(3,0,3,0);
        tb.addView(bT, lp); tb.addView(bP, new LinearLayout.LayoutParams(lp)); main.addView(tb);

        FrameLayout body = new FrameLayout(this); body.setPadding(16, 6, 16, 10); main.addView(body, new LinearLayout.LayoutParams(-1, -1));

        // Tracker View
        vTrk = new LinearLayout(this); vTrk.setOrientation(LinearLayout.VERTICAL); vTrk.setVisibility(View.GONE);
        EditText s = new EditText(this); s.setHint("🔍 Search..."); s.setTextColor(Color.WHITE); s.setBackground(box(Color.parseColor("#181920"), 14, Color.parseColor("#00E676"), 1)); s.setPadding(22, 18, 22, 18);
        s.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence c, int i, int i1, int i2) {} public void onTextChanged(CharSequence c, int i, int i1, int i2) { qry(c.toString().trim()); } public void afterTextChanged(Editable e) {} });
        vTrk.addView(s);
        tCnt = new TextView(this); tCnt.setTextColor(Color.parseColor("#00E676")); tCnt.setPadding(6, 10, 6, 8); vTrk.addView(tCnt);
        ListView lv = new ListView(this); lv.setDivider(null); lv.setDividerHeight(10);
        adp = new BaseAdapter() {
            public int getCount() { return ords.size(); } public Object getItem(int i) { return ords.get(i); } public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) {
                LinearLayout c = new LinearLayout(MainActivity.this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(20, 16, 20, 16); c.setBackground(box(Color.parseColor("#181920"), 14, Color.parseColor("#2A2D3D"), 1));
                String[] it = ords.get(i);
                TextView t1 = new TextView(MainActivity.this); t1.setText("📦 Track ID: " + it[0]); t1.setTextColor(Color.parseColor("#38BDF8")); t1.setTypeface(Typeface.DEFAULT_BOLD);
                TextView t2 = new TextView(MainActivity.this); t2.setText("🛒 Order ID: " + it[1] + "  📋"); t2.setTextColor(Color.parseColor("#00E676")); c.addView(t1); c.addView(t2);
                c.setOnClickListener(vw -> { ((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("ID", it[1])); Toast.makeText(MainActivity.this, "Copied", Toast.LENGTH_SHORT).show(); });
                return c;
            }
        };
        lv.setAdapter(adp); vTrk.addView(lv, new LinearLayout.LayoutParams(-1, -1)); body.addView(vTrk);

        // Performance View
        vPrf = new LinearLayout(this); vPrf.setOrientation(LinearLayout.VERTICAL);
        LinearLayout fl = new LinearLayout(this);
        b1 = flt("📅 Daily", "daily"); b2 = flt("📆 Weekly", "weekly"); b3 = flt("🗓️ Monthly", "monthly");
        LinearLayout.LayoutParams lpF = new LinearLayout.LayoutParams(0, -2, 1f); lpF.setMargins(2, 0, 2, 8); fl.addView(b1, lpF); fl.addView(b2, new LinearLayout.LayoutParams(lpF)); fl.addView(b3, new LinearLayout.LayoutParams(lpF));
        vPrf.addView(fl);

        // Hub Stats
        hubStat = new LinearLayout(this); hubStat.setOrientation(LinearLayout.VERTICAL); hubStat.setPadding(0, 10, 0, 10);
        tHubConv = new TextView(this); tHubConv.setTextColor(Color.parseColor("#38BDF8")); tHubConv.setTypeface(Typeface.DEFAULT_BOLD); tHubConv.setPadding(10, 10, 10, 10); hubStat.addView(tHubConv);
        vPrf.addView(hubStat);
                bSort = new Button(this); bSort.setText("↕️ Sort: High to Low"); bSort.setBackground(box(Color.parseColor("#232634"), 10, 0, 0)); bSort.setTextColor(Color.WHITE);
        bSort.setOnClickListener(v -> { isHighToLow = !isHighToLow; bSort.setText(isHighToLow ? "↕️ Sort: High to Low" : "↕️ Sort: Low to High"); load(); });
        vPrf.addView(bSort, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv = new ScrollView(this);
        vCrd = new LinearLayout(this); vCrd.setOrientation(LinearLayout.VERTICAL);
        sv.addView(vCrd); vPrf.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        body.addView(vPrf);

        bT.setOnClickListener(v -> { vTrk.setVisibility(View.VISIBLE); vPrf.setVisibility(View.GONE); bT.setBackground(box(Color.parseColor("#00E676"), 12, 0, 0)); bP.setBackground(box(Color.parseColor("#232634"), 12, 0, 0)); cnt(); });
        bP.setOnClickListener(v -> { vTrk.setVisibility(View.GONE); vPrf.setVisibility(View.VISIBLE); bP.setBackground(box(Color.parseColor("#00E676"), 12, 0, 0)); bT.setBackground(box(Color.parseColor("#232634"), 12, 0, 0)); load(); });
        root.addView(main); load(); cnt();
    }

    void load() {
        try {
            vCrd.removeAllViews();
            String w = "daily".equals(mode) ? " WHERE dt = (SELECT MAX(dt) FROM prf) " : ("weekly".equals(mode) ? " WHERE dt >= date('now','localtime','-7 days') " : " WHERE dt >= date('now','localtime','-30 days') ");
            
            // Hub Totals
            Cursor hc = db.rawQuery("SELECT SUM(o), SUM(l), SUM(p), SUM(k) FROM prf " + w, null);
            if (hc != null && hc.moveToFirst()) {
                int to = hc.getInt(0), tl = hc.getInt(1), tp = hc.getInt(2), tk = hc.getInt(3);
                int tdnp = to + tp, tdnpc = tl + tk;
                double tconv = tdnp > 0 ? (double)tdnpc/tdnp*100 : 0;
                tHubConv.setText("🏢 HUB TOTAL: " + String.format(Locale.US, "%.1f%% Conversion (OFD:%d | DEL:%d | OFP:%d | PIK:%d)", tconv, to, tl, tp, tk));
            }
            if (hc != null) hc.close();

            Cursor ac = db.rawQuery("SELECT n, SUM(o), SUM(l), SUM(p), SUM(k) FROM prf " + w + " GROUP BY n", null);
            ArrayList<String[]> list = new ArrayList<>();
            while (ac != null && ac.moveToNext()) {
                String name = ac.getString(0);
                int o = ac.getInt(1), l = ac.getInt(2), p = ac.getInt(3), k = ac.getInt(4);
                int dnp = o + p, dnpc = l + k;
                double r = dnp > 0 ? (double)dnpc/dnp*100 : 0.0;
                list.add(new String[]{name, String.valueOf(o), String.valueOf(l), String.valueOf(p), String.valueOf(k), String.valueOf(dnp), String.valueOf(dnpc), String.format(Locale.US, "%.1f", r), String.valueOf(r)});
            }
            if (ac != null) ac.close();

            Collections.sort(list, (a, b) -> isHighToLow ? Double.compare(Double.parseDouble(b[8]), Double.parseDouble(a[8])) : Double.compare(Double.parseDouble(a[8]), Double.parseDouble(b[8])));

            for (String[] ag : list) {
                int strk = getStreak(ag[0]);
                double rate = Double.parseDouble(ag[8]);
                int badgeColor = (rate >= 92.0) ? Color.parseColor("#00E676") : ((rate >= 85.0) ? Color.parseColor("#FBBF24") : Color.parseColor("#EF4444"));

                LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(box(Color.parseColor("#181920"), 14, 0, 0)); card.setPadding(22, 16, 16, 16);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2); clp.setMargins(0,0,0,12); card.setLayoutParams(clp);

                TextView n = new TextView(this); n.setText("👤 " + ag[0] + "  |  🔥 Streak: " + strk + " Days"); n.setTextColor(badgeColor); n.setTypeface(Typeface.DEFAULT_BOLD);
                card.addView(n);

                LinearLayout r1 = new LinearLayout(this); r1.setPadding(0, 10, 0, 4);
                r1.addView(makePill("OFD", ag[1], Color.parseColor("#60A5FA")), new LinearLayout.LayoutParams(0, -2, 1f));
                r1.addView(makePill("DEL", ag[2], Color.parseColor("#34D399")), new LinearLayout.LayoutParams(0, -2, 1f));
                card.addView(r1);

                LinearLayout r2 = new LinearLayout(this); r2.setPadding(0, 2, 0, 4);
                r2.addView(makePill("CONV", ag[7] + "%", badgeColor), new LinearLayout.LayoutParams(-1, -2));
                card.addView(r2);
                
                final String agName = ag[0];
                card.setOnClickListener(v -> showDetails(agName));
                vCrd.addView(card);
            }
        } catch (Exception ignored) {}
    }    int getStreak(String name) {
        int streak = 0;
        Cursor c = db.rawQuery("SELECT dt FROM prf WHERE n = ? AND (o+p) > 0 GROUP BY dt ORDER BY dt DESC", new String[]{name});
        String lastDt = null;
        while (c != null && c.moveToNext()) {
            String dt = c.getString(0);
            if (lastDt == null) { streak = 1; }
            else {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    Calendar cal1 = Calendar.getInstance(); cal1.setTime(sdf.parse(lastDt));
                    Calendar cal2 = Calendar.getInstance(); cal2.setTime(sdf.parse(dt));
                    cal2.add(Calendar.DAY_OF_YEAR, 1);
                    if (cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)) streak++;
                    else break;
                } catch (Exception e) { break; }
            }
            lastDt = dt;
        }
        if (c != null) c.close();
        return streak;
    }

    void showDetails(String name) {
        Cursor c = db.rawQuery("SELECT dt, o, l, p, k FROM prf WHERE n = ? AND dt >= date('now','localtime','-30 days') ORDER BY dt DESC", new String[]{name});
        LinearLayout pop = new LinearLayout(this); pop.setOrientation(LinearLayout.VERTICAL); pop.setPadding(20, 20, 20, 20); pop.setBackgroundColor(Color.parseColor("#0F1015"));
        while (c != null && c.moveToNext()) {
            TextView item = new TextView(this);
            item.setText("📅 " + c.getString(0) + "  | OFD:" + c.getInt(1) + " DEL:" + c.getInt(2) + " OFP:" + c.getInt(3) + " PIK:" + c.getInt(4));
            item.setTextColor(Color.WHITE); item.setBackground(box(Color.parseColor("#181920"), 8, Color.parseColor("#2A2D3D"), 1)); item.setPadding(14, 10, 14, 10);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, 6); pop.addView(item, lp);
        }
        if (c != null) c.close();
        ScrollView sv = new ScrollView(this); sv.addView(pop);
        new AlertDialog.Builder(this).setView(sv).setTitle("History: " + name).setPositiveButton("Close", null).show();
    }

    LinearLayout makeSummaryCard(String title, int bgCol, int accent, boolean isConv) {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setBackground(box(bgCol, 14, Color.parseColor("#2A2D3D"), 1)); c.setPadding(16, 14, 16, 14);
        TextView h = new TextView(this); h.setText(title); h.setTextColor(Color.parseColor("#9CA3AF")); h.setTextSize(10.5f); h.setTypeface(Typeface.DEFAULT_BOLD); c.addView(h);
        TextView v = new TextView(this); v.setTextColor(accent); v.setTextSize(13f); v.setTypeface(Typeface.DEFAULT_BOLD); v.setPadding(0, 4, 0, 0); c.addView(v);
        if (isConv) tTopConv = v; else tTopDnpc = v; return c;
    }

    Button flt(String txt, String m) {
        Button b = new Button(this); b.setText(txt); b.setBackground(box(m.equals(mode) ? Color.parseColor("#00E676") : Color.parseColor("#181920"), 10, 0, 0));
        b.setOnClickListener(v -> { mode = m; b1.setBackground(box("daily".equals(m) ? Color.parseColor("#00E676") : Color.parseColor("#181920"), 10, 0, 0)); b2.setBackground(box("weekly".equals(m) ? Color.parseColor("#00E676") : Color.parseColor("#181920"), 10, 0, 0)); b3.setBackground(box("monthly".equals(m) ? Color.parseColor("#00E676") : Color.parseColor("#181920"), 10, 0, 0)); load(); });
        return b;
    }

    View makePill(String l, String v, int c) {
        LinearLayout p = new LinearLayout(this); p.setBackground(box(Color.parseColor("#232634"), 8, 0, 0)); p.setPadding(12, 8, 12, 8);
        TextView t = new TextView(this); t.setText(l + ": " + v); t.setTextColor(c); t.setTypeface(Typeface.DEFAULT_BOLD); p.addView(t); return p;
    }

    void cnt() { Cursor c = db.rawQuery("SELECT COUNT(*) FROM ord", null); tCnt.setText("📦 Active Search Orders: " + (c.moveToFirst() ? c.getInt(0) : 0)); c.close(); }
    void qry(String q) { ords.clear(); if (!q.isEmpty()) { Cursor c = db.rawQuery("SELECT DISTINCT t, d FROM ord WHERE t LIKE ? OR d LIKE ? LIMIT 30", new String[]{"%" + q, "%" + q}); while (c.moveToNext()) ords.add(new String[]{c.getString(0), c.getString(1)}); c.close(); } adp.notifyDataSetChanged(); }

    void doSync(boolean isAuto) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(CSV).openConnection();
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            db.beginTransaction();
            try {
                db.delete("ord", null, null); db.delete("prf", "dt < date('now', 'localtime', '-30 days')", null);
                String l; boolean hd = true;
                while ((l = r.readLine()) != null) {
                    if (hd) { hd = false; continue; }
                    String[] p = l.split(",", -1);
                    if (p.length < 2) continue;
                    String c1 = p[0].replace("\"", "").trim(), c2 = p[1].replace("\"", "").trim(), name = p.length > 2 ? p[2].replace("\"", "").trim() : "";
                    String t = c1.toUpperCase().matches("^[A-Z]{4}\\d{10}$") || c1.toUpperCase().startsWith("FMP") ? c1 : c2;
                    String o = t.equals(c1) ? c2 : c1;
                    if (!t.isEmpty()) { ContentValues cv = new ContentValues(); cv.put("t", t); cv.put("d", o); db.insertWithOnConflict("ord", null, cv, SQLiteDatabase.CONFLICT_REPLACE); }
                    if (!name.isEmpty() && !name.equalsIgnoreCase("NAME")) { ContentValues cv = new ContentValues(); cv.put("n", name); cv.put("o", pInt(p[3])); cv.put("l", pInt(p[4])); cv.put("p", pInt(p[5])); cv.put("k", pInt(p[6])); cv.put("dt", getDt()); db.insert("prf", null, cv); }
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
            runOnUiThread(() -> { load(); cnt(); if(!isAuto) Toast.makeText(this, "Synced!", Toast.LENGTH_SHORT).show(); });
        } catch (Exception ignored) {}
    }
    String getDt() { Calendar c = Calendar.getInstance(); if (c.get(Calendar.HOUR_OF_DAY) < 9) c.add(Calendar.DAY_OF_YEAR, -1); return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime()); }
    int pInt(String s) { try { return Integer.parseInt(s.replace("\"", "").trim()); } catch (Exception e) { return 0; } }
}

    
