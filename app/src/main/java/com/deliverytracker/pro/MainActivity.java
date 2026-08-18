package com.deliverytracker.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
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
    ArrayList<String[]> ords = new ArrayList<>();
    BaseAdapter adp;
    LinearLayout vTrk, vPrf, vCrd;
    ScrollView sPrf;
    Button bT, bP, b1, b2, b3, bS;
    TextView tC, tH;
    String mode = "daily";
    boolean top = true;
    String CSV = "https://docs.google.com/spreadsheets/d/1Dul38iNZ_eNmABVuYVWhrUg9F_xVMvaVvQvLIXlySj4/export?format=csv";

    GradientDrawable bg(int c, int s, int r) {
        GradientDrawable g = new GradientDrawable(); g.setColor(c); g.setCornerRadius(r);
        if (s != 0) g.setStroke(2, s); return g;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); requestWindowFeature(Window.FEATURE_NO_TITLE);
        db = openOrCreateDatabase("D.db", MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS o (t TEXT, d TEXT);");
        db.execSQL("CREATE TABLE IF NOT EXISTS p (n TEXT, o INT, l INT, p INT, k INT, dt TEXT);");
        build(); cnt();
    }

    void build() {
        LinearLayout r = new LinearLayout(this); r.setOrientation(1); r.setBackgroundColor(Color.parseColor("#080B11"));
        LinearLayout h = new LinearLayout(this); h.setBackgroundColor(Color.parseColor("#0F172A")); h.setPadding(24, 18, 24, 18); h.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this); title.setText("⚡ Tracker Pro"); title.setTextColor(Color.parseColor("#00E676")); title.setTextSize(18f); title.setTypeface(Typeface.DEFAULT_BOLD);
        h.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button adm = new Button(this); adm.setText("🔒 Admin"); adm.setTextColor(Color.parseColor("#00E676")); adm.setTextSize(12f); adm.setBackground(bg(Color.parseColor("#1E293B"), Color.parseColor("#00E676"), 10));
        adm.setOnClickListener(v -> auth()); h.addView(adm); r.addView(h);

        LinearLayout tb = new LinearLayout(this); tb.setBackgroundColor(Color.parseColor("#0A0F1D")); tb.setPadding(12, 8, 12, 8);
        bT = new Button(this); bT.setText("🔍 LIVE TRACKER"); bT.setBackground(bg(Color.parseColor("#00E676"), 0, 12)); bT.setTextColor(Color.BLACK); bT.setTypeface(Typeface.DEFAULT_BOLD);
        bP = new Button(this); bP.setText("📊 PERFORMANCE"); bP.setBackground(bg(Color.parseColor("#1E293B"), 0, 12)); bP.setTextColor(Color.parseColor("#94A3B8")); bP.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f); lp.setMargins(4,0,4,0);
        tb.addView(bT, lp); tb.addView(bP, new LinearLayout.LayoutParams(lp)); r.addView(tb);

        FrameLayout f = new FrameLayout(this); f.setPadding(16, 12, 16, 12); r.addView(f, new LinearLayout.LayoutParams(-1, -1));
        vTrk = new LinearLayout(this); vTrk.setOrientation(1);
        EditText s = new EditText(this); s.setHint("🔎 Type Tracking ID / Order ID..."); s.setHintTextColor(Color.parseColor("#64748B")); s.setTextColor(Color.WHITE); s.setBackground(bg(Color.parseColor("#0F172A"), Color.parseColor("#334155"), 14)); s.setPadding(22, 16, 22, 16);
        s.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int i, int i1, int i2) {}
            public void onTextChanged(CharSequence c, int i, int i1, int i2) { qry(c.toString().trim()); }
            public void afterTextChanged(Editable e) {}
        });
        vTrk.addView(s);
        tC = new TextView(this); tC.setTextColor(Color.parseColor("#38BDF8")); tC.setPadding(6, 12, 6, 8); tC.setTypeface(Typeface.DEFAULT_BOLD); vTrk.addView(tC);

        ListView lv = new ListView(this); lv.setDivider(null); lv.setDividerHeight(12);
        adp = new BaseAdapter() {
            public int getCount() { return ords.size(); }
            public Object getItem(int i) { return ords.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) {
                LinearLayout c = new LinearLayout(MainActivity.this); c.setOrientation(1); c.setPadding(20, 16, 20, 16); c.setBackground(bg(Color.parseColor("#0F172A"), Color.parseColor("#1E293B"), 14));
                String[] it = ords.get(i);
                TextView t1 = new TextView(MainActivity.this); t1.setText("📦 " + it[0]); t1.setTextColor(Color.parseColor("#00E676")); t1.setTypeface(Typeface.DEFAULT_BOLD); t1.setTextSize(15f);
                TextView t2 = new TextView(MainActivity.this); t2.setText("🛒 " + it[1] + "  📋 (Tap to Copy)"); t2.setTextColor(Color.parseColor("#38BDF8")); t2.setTextSize(13f); t2.setPadding(0, 6, 0, 0);
                c.addView(t1); c.addView(t2);
                c.setOnClickListener(vw -> {
                    ((android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("ID", it[1]));
                    Toast.makeText(MainActivity.this, "✅ Copied: " + it[1], Toast.LENGTH_SHORT).show();
                });
                return c;
            }
        };
        lv.setAdapter(adp); vTrk.addView(lv, new LinearLayout.LayoutParams(-1, -1)); f.addView(vTrk);

        sPrf = new ScrollView(this); vPrf = new LinearLayout(this); vPrf.setOrientation(1); sPrf.addView(vPrf); sPrf.setVisibility(View.GONE); f.addView(sPrf);
        LinearLayout fl = new LinearLayout(this);
        b1 = flt("📅 Daily", "daily"); b2 = flt("📆 Weekly", "weekly"); b3 = flt("🗓️ Monthly", "monthly");
        LinearLayout.LayoutParams lpF = new LinearLayout.LayoutParams(0, -2, 1f); lpF.setMargins(3, 0, 3, 12);
        fl.addView(b1, lpF); fl.addView(b2, new LinearLayout.LayoutParams(lpF)); fl.addView(b3, new LinearLayout.LayoutParams(lpF)); vPrf.addView(fl);

        LinearLayout hb = new LinearLayout(this); hb.setOrientation(1); hb.setBackground(bg(Color.parseColor("#0F172A"), Color.parseColor("#00E676"), 16)); hb.setPadding(20, 16, 20, 16);
        TextView ht = new TextView(this); ht.setText("🏢 MALBAZARHUB_NJP | 🎯 Target: 92.0%"); ht.setTextColor(Color.parseColor("#00E676")); ht.setTypeface(Typeface.DEFAULT_BOLD); hb.addView(ht);
        tH = new TextView(this); tH.setTextColor(Color.WHITE); tH.setPadding(0, 6, 0, 0); tH.setTextSize(12.5f); hb.addView(tH); vPrf.addView(hb);

        LinearLayout sb = new LinearLayout(this); sb.setGravity(Gravity.CENTER_VERTICAL); sb.setPadding(0, 16, 0, 8);
        TextView ar = new TextView(this); ar.setText("👥 AGENT PERFORMANCE"); ar.setTextColor(Color.parseColor("#94A3B8")); ar.setTypeface(Typeface.DEFAULT_BOLD);
        sb.addView(ar, new LinearLayout.LayoutParams(0, -2, 1f));
        bS = new Button(this); bS.setText("🏆 Top First"); bS.setTextSize(11f); bS.setTextColor(Color.parseColor("#00E676")); bS.setBackground(bg(Color.parseColor("#1E293B"), Color.parseColor("#00E676"), 10));
        bS.setOnClickListener(v -> { top = !top; bS.setText(top ? "🏆 Top First" : "⚠️ Low First"); load(); });
        sb.addView(bS); vPrf.addView(sb);

        vCrd = new LinearLayout(this); vCrd.setOrientation(1); vPrf.addView(vCrd);

        bT.setOnClickListener(v -> { vTrk.setVisibility(View.VISIBLE); sPrf.setVisibility(View.GONE); bT.setBackground(bg(Color.parseColor("#00E676"), 0, 12)); bT.setTextColor(Color.BLACK); bP.setBackground(bg(Color.parseColor("#1E293B"), 0, 12)); bP.setTextColor(Color.parseColor("#94A3B8")); });
        bP.setOnClickListener(v -> { vTrk.setVisibility(View.GONE); sPrf.setVisibility(View.VISIBLE); bP.setBackground(bg(Color.parseColor("#00E676"), 0, 12)); bP.setTextColor(Color.BLACK); bT.setBackground(bg(Color.parseColor("#1E293B"), 0, 12)); bT.setTextColor(Color.parseColor("#94A3B8")); load(); });
        setContentView(r);
    }

    Button flt(String txt, String m) {
        Button b = new Button(this); b.setText(txt); b.setBackground(bg(m.equals(mode) ? Color.parseColor("#00E676") : Color.parseColor("#1E293B"), 0, 10));
        b.setTextColor(m.equals(mode) ? Color.BLACK : Color.parseColor("#94A3B8")); b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setOnClickListener(v -> {
            mode = m;
            b1.setBackground(bg("daily".equals(m) ? Color.parseColor("#00E676") : Color.parseColor("#1E293B"), 0, 10)); b1.setTextColor("daily".equals(m) ? Color.BLACK : Color.parseColor("#94A3B8"));
            b2.setBackground(bg("weekly".equals(m) ? Color.parseColor("#00E676") : Color.parseColor("#1E293B"), 0, 10)); b2.setTextColor("weekly".equals(m) ? Color.BLACK : Color.parseColor("#94A3B8"));
            b3.setBackground(bg("monthly".equals(m) ? Color.parseColor("#00E676") : Color.parseColor("#1E293B"), 0, 10)); b3.setTextColor("monthly".equals(m) ? Color.BLACK : Color.parseColor("#94A3B8"));
            load();
        });
        return b;
    }

    String dt() {
        Calendar c = Calendar.getInstance(); if (c.get(Calendar.HOUR_OF_DAY) < 9) c.add(Calendar.DAY_OF_YEAR, -1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
    }

    void cnt() {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM o", null);
        tC.setText("⚡ Total Active Orders: " + (c.moveToFirst() ? c.getInt(0) : 0)); c.close();
    }

    void qry(String q) {
        ords.clear();
        if (!q.isEmpty()) {
            Cursor c = db.rawQuery("SELECT t, d FROM o WHERE t LIKE ? OR d LIKE ? LIMIT 50", new String[]{"%" + q + "%", "%" + q + "%"});
            while (c.moveToNext()) ords.add(new String[]{c.getString(0), c.getString(1)});
            c.close();
        }
        adp.notifyDataSetChanged();
    }

    void load() {
        vCrd.removeAllViews();
        String w = "daily".equals(mode) ? " WHERE dt = (SELECT MAX(dt) FROM p) " : ("weekly".equals(mode) ? " WHERE dt >= date('now','localtime','-7 days') " : " WHERE dt >= date('now','localtime','-30 days') ");
        Cursor hc = db.rawQuery("SELECT SUM(o), SUM(l), SUM(p), SUM(k) FROM p" + w, null);
        if (hc.moveToFirst()) {
            int o = hc.getInt(0), d = hc.getInt(1), op = hc.getInt(2), p = hc.getInt(3);
            int dnp = o + op, dnpc = d + p;
            double r = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;
            tH.setText("OFD: " + o + " | DEL: " + d + " | OFP: " + op + " | PIK: " + p + "\nDNP: " + dnp + " | DNPC: " + dnpc + " | Actual Conv: " + String.format(Locale.US, "%.1f%%", r));
        } else { tH.setText("No data synced yet."); }
        hc.close();

        Cursor ac = db.rawQuery("SELECT n, SUM(o), SUM(l), SUM(p), SUM(k) FROM p " + w + " GROUP BY n", null);
        ArrayList<String[]> list = new ArrayList<>();
        while (ac.moveToNext()) {
            int o = ac.getInt(1), d = ac.getInt(2), op = ac.getInt(3), p = ac.getInt(4);
            int dnp = o + op, dnpc = d + p;
            double r = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;
            list.add(new String[]{ac.getString(0), String.valueOf(o), String.valueOf(d), String.valueOf(op), String.valueOf(p), String.valueOf(dnp), String.valueOf(dnpc), String.format(Locale.US, "%.1f", r), String.valueOf(r)});
        }
        ac.close();

        Collections.sort(list, (a, b) -> top ? Double.compare(Double.parseDouble(b[8]), Double.parseDouble(a[8])) : Double.compare(Double.parseDouble(a[8]), Double.parseDouble(b[8])));

        int rank = 1;
        for (String[] ag : list) {
            double rate = Double.parseDouble(ag[8]);
            int col = (rate >= 92.0) ? Color.parseColor("#00E676") : ((rate >= 85.0) ? Color.parseColor("#F59E0B") : Color.parseColor("#EF4444"));
            LinearLayout c = new LinearLayout(this); c.setOrientation(1); c.setPadding(20, 16, 20, 16); c.setBackground(bg(Color.parseColor("#0F172A"), col, 14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, 10); c.setLayoutParams(lp);
            String m = (top && rank == 1) ? "👑 🥇 #" + rank + " " : ((top && rank == 2) ? "🥈 #" + rank + " " : ((top && rank == 3) ? "🥉 #" + rank + " " : "👤 "));
            TextView n = new TextView(this); n.setText(m + ag[0]); n.setTextColor(col); n.setTypeface(Typeface.DEFAULT_BOLD); c.addView(n);
            TextView s = new TextView(this); s.setText("OFD: " + ag[1] + " | DEL: " + ag[2] + " | OFP: " + ag[3] + " | PIK: " + ag[4] + "\nDNP: " + ag[5] + " | DNPC: " + ag[6] + " | Conv: " + ag[7] + "%");
            s.setTextColor(Color.WHITE); s.setTextSize(11.5f); s.setPadding(0, 4, 0, 0); c.addView(s);
            vCrd.addView(c); rank++;
        }
    }

    void auth() {
        EditText in = new EditText(this); in.setHint("Enter 10-digit PIN..."); in.setInputType(InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this).setTitle("🔐 Admin Login").setView(in)
            .setPositiveButton("Verify", (d, w) -> {
                if ("9547927698".equals(in.getText().toString().trim())) syncDlg();
                else Toast.makeText(this, "Wrong PIN!", Toast.LENGTH_SHORT).show();
            }).show();
    }

    void syncDlg() {
        new AlertDialog.Builder(this).setTitle("⚡ Data Control")
            .setPositiveButton("Sync Now", (d, w) -> new Thread(this::doSync).start())
            .setNegativeButton("Clear Data", (d, w) -> {
                db.delete("o", null, null); db.delete("p", null, null);
                runOnUiThread(() -> { cnt(); qry(""); Toast.makeText(this, "Cleared!", Toast.LENGTH_SHORT).show(); });
            }).show();
    }

    void doSync() {
        try {
            runOnUiThread(() -> Toast.makeText(this, "Syncing live sheet...", Toast.LENGTH_SHORT).show());
            String curDt = dt();
            HttpURLConnection conn = (HttpURLConnection) new URL(CSV).openConnection();
            conn.setConnectTimeout(15000);
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            db.beginTransaction();
            int count = 0;
            try {
                db.delete("o", null, null); db.delete("p", "dt = ?", new String[]{curDt});
                String l; boolean hd = true;
                while ((l = r.readLine()) != null) {
                    if (hd) { hd = false; continue; }
                    String[] p = l.split(",", -1);
                    if (p.length < 2) continue;
                    String c1 = p[0].replace("\"", "").trim(), c2 = p[1].replace("\"", "").trim();
                    String t = c1.toUpperCase().startsWith("FMP") ? c1 : c2;
                    String o = c1.toUpperCase().startsWith("OD") ? c1 : c2;
                    String name = p.length > 2 ? p[2].replace("\"", "").trim() : "";
                    if (!t.isEmpty() && !o.isEmpty()) {
                        ContentValues cv = new ContentValues(); cv.put("t", t); cv.put("d", o);
                        db.insert("o", null, cv); count++;
                    }
                    if (!name.isEmpty() && !name.equalsIgnoreCase("NAME")) {
                        ContentValues cv = new ContentValues();
                        cv.put("n", name);
                        cv.put("o", p.length > 3 ? pInt(p[3]) : 0);
                        cv.put("l", p.length > 4 ? pInt(p[4]) : 0);
                        cv.put("p", p.length > 5 ? pInt(p[5]) : 0);
                        cv.put("k", p.length > 6 ? pInt(p[6]) : 0);
                        cv.put("dt", curDt); db.insert("p", null, cv);
                    }
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
            int fin = count;
            runOnUiThread(() -> {
                Toast.makeText(this, "✅ Synced " + fin + " orders!", Toast.LENGTH_LONG).show();
                cnt(); if (sPrf.getVisibility() == View.VISIBLE) load();
            });
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Sync Failed!", Toast.LENGTH_SHORT).show());
        }
    }

    int pInt(String s) {
        try { return Integer.parseInt(s.replace("\"", "").trim()); } catch (Exception e) { return 0; }
    }
}
