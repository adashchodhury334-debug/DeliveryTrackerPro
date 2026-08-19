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
    LinearLayout vTrk, vPrf, vCrd;
    Button bT, bP, b1, b2, b3;
    TextView tTopConv, tTopDnpc, tCnt, tHubData;
    ArrayList<String[]> ords = new ArrayList<>();
    BaseAdapter adp;
    String mode = "daily";
    String CSV = "https://docs.google.com/spreadsheets/d/1SxsB-1srlfIv3AN5H2ZbMJDEyteJ6LIDTV4EI7rbxjw/export?format=csv";

    GradientDrawable box(int c, int r, int sCol, int sW) {
        GradientDrawable g = new GradientDrawable(); g.setColor(c); g.setCornerRadius(r);
        if (sW > 0) g.setStroke(sW, sCol); return g;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); requestWindowFeature(Window.FEATURE_NO_TITLE);
        try {
            db = openOrCreateDatabase("TrackerV12.db", MODE_PRIVATE, null);
            db.execSQL("CREATE TABLE IF NOT EXISTS ord (t TEXT UNIQUE, d TEXT);");
            db.execSQL("CREATE TABLE IF NOT EXISTS prf (n TEXT, o INT, l INT, p INT, k INT, dt TEXT);");
        } catch (Exception ignored) {}
        root = new FrameLayout(this); root.setBackgroundColor(Color.parseColor("#0F1015"));
        setContentView(root); new Thread(() -> doSync(true)).start();
        new Handler().postDelayed(this::buildUI, 1500);
    }

    @Override protected void onResume() { super.onResume(); new Thread(() -> doSync(true)).start(); }

    void buildUI() {
        LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL);
        LinearLayout h = new LinearLayout(this); h.setBackgroundColor(Color.parseColor("#181920")); h.setPadding(20, 16, 20, 16); h.setGravity(Gravity.CENTER_VERTICAL);
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

        vTrk = new LinearLayout(this); vTrk.setOrientation(LinearLayout.VERTICAL); vTrk.setVisibility(View.GONE);
        EditText s = new EditText(this); s.setHint("🔍 Search ID..."); s.setTextColor(Color.WHITE); s.setBackground(box(Color.parseColor("#181920"), 14, Color.parseColor("#00E676"), 1)); s.setPadding(22, 18, 22, 18);
        s.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence c, int i, int i1, int i2) {} public void onTextChanged(CharSequence c, int i, int i1, int i2) { qry(c.toString().trim()); } public void afterTextChanged(Editable e) {} });
        vTrk.addView(s); tCnt = new TextView(this); tCnt.setTextColor(Color.parseColor("#00E676")); vTrk.addView(tCnt);
        ListView lv = new ListView(this); lv.setDivider(null); lv.setDividerHeight(10);
        adp = new BaseAdapter() {
            public int getCount() { return ords.size(); } public Object getItem(int i) { return ords.get(i); } public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) {
                LinearLayout c = new LinearLayout(MainActivity.this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(20, 16, 20, 16); c.setBackground(box(Color.parseColor("#181920"), 14, Color.parseColor("#2A2D3D"), 1));
                String[] it = ords.get(i);
                TextView t1 = new TextView(MainActivity.this); t1.setText("📦 Track: " + it[0]); t1.setTextColor(Color.parseColor("#38BDF8")); t1.setTypeface(Typeface.DEFAULT_BOLD);
                TextView t2 = new TextView(MainActivity.this); t2.setText("🛒 Order: " + it[1]); t2.setTextColor(Color.parseColor("#00E676")); c.addView(t1); c.addView(t2);
                c.setOnClickListener(vw -> { ((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("ID", it[1])); Toast.makeText(MainActivity.this, "Copied", Toast.LENGTH_SHORT).show(); });
                return c;
            }
        }; lv.setAdapter(adp); vTrk.addView(lv, new LinearLayout.LayoutParams(-1, -1)); body.addView(vTrk);
                vPrf = new LinearLayout(this); vPrf.setOrientation(LinearLayout.VERTICAL);
        LinearLayout fl = new LinearLayout(this);
        b1 = flt("📅 Daily", "daily"); b2 = flt("📆 Weekly", "weekly"); b3 = flt("🗓️ Monthly", "monthly");
        LinearLayout.LayoutParams lpF = new LinearLayout.LayoutParams(0, -2, 1f); lpF.setMargins(2, 0, 2, 8); fl.addView(b1, lpF); fl.addView(b2, new LinearLayout.LayoutParams(lpF)); fl.addView(b3, new LinearLayout.LayoutParams(lpF));
        vPrf.addView(fl);

        // New Hub Details
        LinearLayout hubBox = new LinearLayout(this); hubBox.setOrientation(LinearLayout.VERTICAL); hubBox.setBackground(box(Color.parseColor("#181920"), 14, Color.parseColor("#38BDF8"), 1)); hubBox.setPadding(20, 20, 20, 20);
        TextView hTitle = new TextView(this); hTitle.setText("🏢 HUB: MALBAZARHUB_NJP"); hTitle.setTextColor(Color.parseColor("#38BDF8")); hTitle.setTypeface(Typeface.DEFAULT_BOLD); hubBox.addView(hTitle);
        tHubData = new TextView(this); tHubData.setTextColor(Color.WHITE); tHubData.setTextSize(13f); hubBox.addView(tHubData);
        vPrf.addView(hubBox, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv = new ScrollView(this); vCrd = new LinearLayout(this); vCrd.setOrientation(LinearLayout.VERTICAL); sv.addView(vCrd); vPrf.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        body.addView(vPrf);
        bT.setOnClickListener(v -> { vTrk.setVisibility(View.VISIBLE); vPrf.setVisibility(View.GONE); bT.setBackground(box(Color.parseColor("#00E676"), 12, 0, 0)); bP.setBackground(box(Color.parseColor("#232634"), 12, 0, 0)); cnt(); });
        bP.setOnClickListener(v -> { vTrk.setVisibility(View.GONE); vPrf.setVisibility(View.VISIBLE); bP.setBackground(box(Color.parseColor("#00E676"), 12, 0, 0)); bT.setBackground(box(Color.parseColor("#232634"), 12, 0, 0)); load(); });
        root.addView(main); load(); cnt();
    }

    void load() {
        try {
            vCrd.removeAllViews();
            String w = "daily".equals(mode) ? " WHERE dt = (SELECT MAX(dt) FROM prf) " : ("weekly".equals(mode) ? " WHERE dt >= date('now','localtime','-7 days') " : " WHERE dt >= date('now','localtime','-30 days') ");
            Cursor hc = db.rawQuery("SELECT SUM(o), SUM(l), SUM(p), SUM(k) FROM prf " + w, null);
            if (hc != null && hc.moveToFirst()) {
                int to=hc.getInt(0), tl=hc.getInt(1), tp=hc.getInt(2), tk=hc.getInt(3);
                tHubData.setText("OFD/DEL: " + to + "/" + tl + " (Conv: " + (to>0?(tl*100/to):0) + "%)\nOFP/PIKED: " + tp + "/" + tk + "\nDNP/DNPC: " + (to+tp) + "/" + (tl+tk));
            }
            if (hc != null) hc.close();

            Cursor ac = db.rawQuery("SELECT n, SUM(o), SUM(l), SUM(p), SUM(k) FROM prf " + w + " GROUP BY n", null);
            ArrayList<String[]> list = new ArrayList<>();
            while (ac != null && ac.moveToNext()) {
                int o=ac.getInt(1), l=ac.getInt(2), p=ac.getInt(3), k=ac.getInt(4);
                list.add(new String[]{ac.getString(0), ac.getString(1), ac.getString(2), ac.getString(3), ac.getString(4), String.valueOf(o+p), String.valueOf(l+k), String.format(Locale.US, "%.1f", (o+p)>0?(double)(l+k)/(o+p)*100:0)});
            }
            if (ac != null) ac.close();
            Collections.sort(list, (a, b) -> Double.compare(Double.parseDouble(b[7]), Double.parseDouble(a[7])));
                        for (String[] ag : list) {
                LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(box(Color.parseColor("#181920"), 14, 0, 0)); card.setPadding(22, 16, 16, 16);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2); clp.setMargins(0,0,0,12); card.setLayoutParams(clp);
                TextView n = new TextView(this); n.setText("👤 " + ag[0]); n.setTextColor(Color.parseColor("#00E676")); n.setTypeface(Typeface.DEFAULT_BOLD); card.addView(n);
                LinearLayout r1 = new LinearLayout(this);
                r1.addView(makePill("OFD/DEL", ag[1]+"/"+ag[2], Color.parseColor("#60A5FA")), new LinearLayout.LayoutParams(0, -2, 1f));
                r1.addView(makePill("OFP/PIK", ag[3]+"/"+ag[4], Color.parseColor("#FBBF24")), new LinearLayout.LayoutParams(0, -2, 1f));
                card.addView(r1);
                TextView r2 = new TextView(this); r2.setText("DNP/DNPC: " + ag[5] + "/" + ag[6] + "  |  Conv: " + ag[7] + "%"); r2.setTextColor(Color.WHITE); r2.setPadding(0, 10, 0, 0); card.addView(r2);
                card.setOnClickListener(v -> showDetails(ag[0])); vCrd.addView(card);
            }
        } catch (Exception ignored) {}
    }

    void showDetails(String name) {
        Cursor c = db.rawQuery("SELECT dt, o, l, p, k FROM prf WHERE n = ? ORDER BY dt DESC LIMIT 10", new String[]{name});
        LinearLayout pop = new LinearLayout(this); pop.setOrientation(LinearLayout.VERTICAL); pop.setPadding(20, 20, 20, 20); pop.setBackgroundColor(Color.parseColor("#0F1015"));
        while (c != null && c.moveToNext()) {
            TextView item = new TextView(this); item.setText(c.getString(0) + " | OFD:" + c.getInt(1) + " DEL:" + c.getInt(2) + " OFP:" + c.getInt(3) + " PIK:" + c.getInt(4));
            item.setTextColor(Color.WHITE); item.setPadding(10, 10, 10, 10); pop.addView(item);
        }
        if (c != null) c.close();
        new AlertDialog.Builder(this).setView(svWrap(pop)).setPositiveButton("Close", null).show();
    }
    ScrollView svWrap(View v) { ScrollView sv = new ScrollView(this); sv.addView(v); return sv; }
    Button flt(String txt, String m) { Button b = new Button(this); b.setText(txt); b.setBackground(box(m.equals(mode) ? Color.parseColor("#00E676") : Color.parseColor("#181920"), 10, 0, 0)); b.setOnClickListener(v -> { mode = m; load(); }); return b; }
    View makePill(String l, String v, int c) { LinearLayout p = new LinearLayout(this); TextView t = new TextView(this); t.setText(l + ": " + v); t.setTextColor(c); p.addView(t); return p; }
    void cnt() { Cursor c = db.rawQuery("SELECT COUNT(*) FROM ord", null); tCnt.setText("📦 Active Orders: " + (c.moveToFirst() ? c.getInt(0) : 0)); c.close(); }
    void qry(String q) { ords.clear(); if (!q.isEmpty()) { Cursor c = db.rawQuery("SELECT DISTINCT t, d FROM ord WHERE t LIKE ? OR d LIKE ? LIMIT 30", new String[]{"%" + q, "%" + q}); while (c.moveToNext()) ords.add(new String[]{c.getString(0), c.getString(1)}); c.close(); } adp.notifyDataSetChanged(); }
    void doSync(boolean isAuto) { try { HttpURLConnection conn = (HttpURLConnection) new URL(CSV).openConnection(); BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream())); db.beginTransaction(); try { db.delete("prf", "dt < date('now', 'localtime', '-30 days')", null); String l; boolean hd = true; while ((l = r.readLine()) != null) { if (hd) { hd = false; continue; } String[] p = l.split(",", -1); if (p.length < 2) continue; String t = p[0].replace("\"","").trim(), o = p[1].replace("\"","").trim(), name = p.length > 2 ? p[2].replace("\"","").trim() : ""; if (!t.isEmpty()) { ContentValues cv = new ContentValues(); cv.put("t", t); cv.put("d", o); db.insertWithOnConflict("ord", null, cv, SQLiteDatabase.CONFLICT_REPLACE); } if (!name.isEmpty() && !name.equalsIgnoreCase("NAME")) { ContentValues cv = new ContentValues(); cv.put("n", name); cv.put("o", pInt(p[3])); cv.put("l", pInt(p[4])); cv.put("p", pInt(p[5])); cv.put("k", pInt(p[6])); cv.put("dt", getDt()); db.insert("prf", null, cv); } } db.setTransactionSuccessful(); } finally { db.endTransaction(); } runOnUiThread(() -> { load(); cnt(); }); } catch (Exception ignored) {} }
    String getDt() { Calendar c = Calendar.getInstance(); if (c.get(Calendar.HOUR_OF_DAY) < 9) c.add(Calendar.DAY_OF_YEAR, -1); return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime()); }
    int pInt(String s) { try { return Integer.parseInt(s.replace("\"","").trim()); } catch (Exception e) { return 0; } }
}
