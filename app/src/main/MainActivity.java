package com.deliverytracker.pro;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    SQLiteDatabase db;
    ArrayList<String[]> ords = new ArrayList<>(), prfs = new ArrayList<>();
    BaseAdapter adp;
    LinearLayout vT, vP, vC;
    ScrollView sP;
    Button bT, bP, b1, b2, b3, bS;
    TextView tC, tH;
    String mode = "daily";
    boolean top = true;
    String CSV = "https://docs.google.com/spreadsheets/d/1Dul38iNZ_eNmABVuYVWhrUg9F_xVMvaVvQvLIXlySj4/export?format=csv";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        db = openOrCreateDatabase("D.db", MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS o (t TEXT, d TEXT);");
        db.execSQL("CREATE TABLE IF NOT EXISTS p (n TEXT, m TEXT, o INT, l INT, p INT, k INT, dt TEXT);");
        build();
        cnt();
    }

    GradientDrawable bg(int c, int s, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c); g.setCornerRadius(r);
        if (s != 0) g.setStroke(2, s);
        return g;
    }

    void build() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setBackgroundColor(Color.parseColor("#0a0e17"));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setBackgroundColor(Color.parseColor("#131c2e"));
        topBar.setPadding(24, 16, 24, 16);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView h = new TextView(this);
        h.setText("⚡ Delivery Tracker Pro");
        h.setTextColor(Color.parseColor("#00E676"));
        h.setTextSize(17f);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        topBar.addView(h, new LinearLayout.LayoutParams(0, -2, 1f));

        Button adm = new Button(this);
        adm.setText("🔒 Admin");
        adm.setTextColor(Color.parseColor("#00E676"));
        adm.setBackground(bg(Color.parseColor("#1f2d47"), Color.parseColor("#00E676"), 10));
        adm.setOnClickListener(v -> auth());
        topBar.addView(adm);
        r.addView(topBar);

        LinearLayout tb = new LinearLayout(this);
        tb.setBackgroundColor(Color.parseColor("#101726"));
        tb.setPadding(10, 6, 10, 6);
        bT = new Button(this); bT.setText("🔍 Tracker");
        bT.setBackground(bg(Color.parseColor("#00E676"), 0, 10)); bT.setTextColor(Color.BLACK);
        bP = new Button(this); bP.setText("📊 Performance");
        bP.setBackground(bg(Color.parseColor("#1a2333"), 0, 10)); bP.setTextColor(Color.parseColor("#8fa0bc"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(4, 0, 4, 0);
        tb.addView(bT, lp); tb.addView(bP, new LinearLayout.LayoutParams(lp));
        r.addView(tb);

        FrameLayout f = new FrameLayout(this);
        f.setPadding(16, 12, 16, 12);
        r.addView(f, new LinearLayout.LayoutParams(-1, -1));

        vT = new LinearLayout(this);
        vT.setOrientation(LinearLayout.VERTICAL);
        EditText s = new EditText(this);
        s.setHint("Search Tracking ID / Order ID...");
        s.setHintTextColor(Color.parseColor("#657795"));
        s.setTextColor(Color.WHITE);
        s.setBackground(bg(Color.parseColor("#141d2d"), Color.parseColor("#2a3b5c"), 12));
        s.setPadding(18, 14, 18, 14);
        s.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int i, int i1, int i2) {}
            public void onTextChanged(CharSequence c, int i, int i1, int i2) { qry(c.toString().trim()); }
            public void afterTextChanged(Editable e) {}
        });
        vT.addView(s);

        tC = new TextView(this);
        tC.setTextColor(Color.parseColor("#8fa0bc"));
        tC.setPadding(4, 10, 4, 8);
        vT.addView(tC);

        ListView lv = new ListView(this);
        lv.setDivider(null); lv.setDividerHeight(10);
        adp = new BaseAdapter() {
            public int getCount() { return ords.size(); }
            public Object getItem(int i) { return ords.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) {
                LinearLayout c = new LinearLayout(MainActivity.this);
                c.setOrientation(LinearLayout.VERTICAL);
                c.setPadding(18, 14, 18, 14);
                c.setBackground(bg(Color.parseColor("#141d2d"), Color.parseColor("#23334d"), 12));
                String[] it = ords.get(i);
                TextView t1 = new TextView(MainActivity.this);
                t1.setText("📦 Track ID: " + it[0]); t1.setTextColor(Color.parseColor("#00E676"));
                t1.setTypeface(Typeface.DEFAULT_BOLD);
                TextView t2 = new TextView(MainActivity.this);
                t2.setText("🛒 Order ID: " + it[1] + " (Tap to Copy)");
                t2.setTextColor(Color.parseColor("#64B5F6"));
                t2.setPadding(0, 4, 0, 0);
                c.addView(t1); c.addView(t2);
                c.setOnClickListener(vw -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("ID", it[1]));
                    Toast.makeText(MainActivity.this, "Copied: " + it[1], Toast.LENGTH_SHORT).show();
                });
                return c;
            }
        };
        lv.setAdapter(adp);
        vT.addView(lv, new LinearLayout.LayoutParams(-1, -1));
        f.addView(vT);

        sP = new ScrollView(this);
        vP = new LinearLayout(this);
        vP.setOrientation(LinearLayout.VERTICAL);
        sP.addView(vP); sP.setVisibility(View.GONE);
        f.addView(sP);

        LinearLayout fl = new LinearLayout(this);
        b1 = flt("📅 Daily", "daily"); b2 = flt("📆 Weekly", "weekly"); b3 = flt("🗓️ Monthly", "monthly");
        LinearLayout.LayoutParams lpF = new LinearLayout.LayoutParams(0, -2, 1f);
        lpF.setMargins(2, 0, 2, 10);
        fl.addView(b1, lpF); fl.addView(b2, new LinearLayout.LayoutParams(lpF)); fl.addView(b3, new LinearLayout.LayoutParams(lpF));
        vP.addView(fl);

        LinearLayout hb = new LinearLayout(this);
        hb.setOrientation(LinearLayout.VERTICAL);
        hb.setBackground(bg(Color.parseColor("#132338"), Color.parseColor("#00E676"), 12));
        hb.setPadding(18, 14, 18, 14);
        TextView ht = new TextView(this);
        ht.setText("🏢 MALBAZARHUB_NJP | 🎯 Target: 92.0%");
        ht.setTextColor(Color.parseColor("#00E676")); ht.setTypeface(Typeface.DEFAULT_BOLD);
        hb.addView(ht);
        tH = new TextView(this); tH.setTextColor(Color.WHITE); tH.setPadding(0, 4, 0, 0);
        hb.addView(tH);
        vP.addView(hb);

        LinearLayout sb = new LinearLayout(this);
        sb.setGravity(Gravity.CENTER_VERTICAL); sb.setPadding(0, 14, 0, 8);
        TextView ar = new TextView(this); ar.setText("👥 Delivery Agents Report");
        ar.setTextColor(Color.parseColor("#8fa0bc")); ar.setTypeface(Typeface.DEFAULT_BOLD);
        sb.addView(ar, new LinearLayout.LayoutParams(0, -2, 1f));

        bS = new Button(this); bS.setText("🏆 Top First"); bS.setTextSize(11f);
        bS.setTextColor(Color.parseColor("#00E676"));
        bS.setBackground(bg(Color.parseColor("#1f2d47"), Color.parseColor("#00E676"), 10));
        bS.setOnClickListener(v -> { top = !top; bS.setText(top ? "🏆 Top First" : "⚠️ Low First"); load(); });
        sb.addView(bS);
        vP.addView(sb);

        vC = new LinearLayout(this);
        vC.setOrientation(LinearLayout.VERTICAL);
        vP.addView(vC);

        bT.setOnClickListener(v -> {
            vT.setVisibility(View.VISIBLE); sP.setVisibility(View.GONE);
            bT.setBackground(bg(Color.parseColor("#00E676"), 0, 10)); bT.setTextColor(Color.BLACK);
            bP.setBackground(bg(Color.parseColor("#1a2333"), 0, 10)); bP.setTextColor(Color.parseColor("#8fa0bc"));
        });
        bP.setOnClickListener(v -> {
            vT.setVisibility(View.GONE); sP.setVisibility(View.VISIBLE);
            bP.setBackground(bg(Color.parseColor("#00E676"), 0, 10)); bP.setTextColor(Color.BLACK);
            bT.setBackground(bg(Color.parseColor("#1a2333"), 0, 10)); bT.setTextColor(Color.parseColor("#8fa0bc"));
            load();
        });

        setContentView(r);
    }

    Button flt(String txt, String m) {
        Button b = new Button(this);
        b.setText(txt);
        b.setBackground(bg(m.equals(mode) ? Color.parseColor("#238636") : Color.parseColor("#1a2333"), 0, 10));
        b.setTextColor(m.equals(mode) ? Color.WHITE : Color.parseColor("#8fa0bc"));
        b.setOnClickListener(v -> {
            mode = m;
            b1.setBackground(bg("daily".equals(m) ? Color.parseColor("#238636") : Color.parseColor("#1a2333"), 0, 10));
            b1.setTextColor("daily".equals(m) ? Color.WHITE : Color.parseColor("#8fa0bc"));
            b2.setBackground(bg("weekly".equals(m) ? Color.parseColor("#238636") : Color.parseColor("#1a2333"), 0, 10));
            b2.setTextColor("weekly".equals(m) ? Color.WHITE : Color.parseColor("#8fa0bc"));
            b3.setBackground(bg("monthly".equals(m) ? Color.parseColor("#238636") : Color.parseColor("#1a2333"), 0, 10));
            b3.setTextColor("monthly".equals(m) ? Color.WHITE : Color.parseColor("#8fa0bc"));
            load();
        });
        return b;
    }

    String dt() {
        Calendar c = Calendar.getInstance();
        if (c.get(Calendar.HOUR_OF_DAY) < 9) c.add(Calendar.DAY_OF_YEAR, -1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
    }

    void cnt() {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM o", null);
        tC.setText("📦 Active Orders: " + (c.moveToFirst() ? c.getInt(0) : 0));
        c.close();
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
        vC.removeAllViews();
        String w = "daily".equals(mode) ? " WHERE dt = (SELECT MAX(dt) FROM p) " : ("weekly".equals(mode) ? " WHERE dt >= date('now','localtime','-7 days') " : " WHERE dt >= date('now','localtime','-30 days') ");
        Cursor hc = db.rawQuery("SELECT SUM(o), SUM(l), SUM(p), SUM(k) FROM p" + w, null);
        if (hc.moveToFirst()) {
            int o = hc.getInt(0), d = hc.getInt(1), op = hc.getInt(2), p = hc.getInt(3);
            int dnp = o + op, dnpc = d + p;
            double r = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;
            tH.setText("OFD: " + o + " | DEL: " + d + " | OFP: " + op + " | PIK: " + p + "\nDNP: " + dnp + " | DNPC: " + dnpc + " | Conv: " + String.format(Locale.US, "%.1f%%", r));
        } else { tH.setText("No data synced yet."); }
        hc.close();

        Cursor ac = db.rawQuery("SELECT n, m, SUM(o), SUM(l), SUM(p), SUM(k) FROM p " + w + " GROUP BY n, m", null);
        ArrayList<String[]> list = new ArrayList<>();
        while (ac.moveToNext()) {
            int o = ac.getInt(2), d = ac.getInt(3), op = ac.getInt(4), p = ac.getInt(5);
            int dnp = o + op, dnpc = d + p;
            double r = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;
            list.add(new String[]{ac.getString(0), ac.getString(1), String.valueOf(o), String.valueOf(d), String.valueOf(op), String.valueOf(p), String.valueOf(dnp), String.valueOf(dnpc), String.format(Locale.US, "%.1f", r), String.valueOf(r)});
        }
        ac.close();

        Collections.sort(list, (a, b) -> top ? Double.compare(Double.parseDouble(b[9]), Double.parseDouble(a[9])) : Double.compare(Double.parseDouble(a[9]), Double.parseDouble(b[9])));

        int rank = 1;
        for (String[] ag : list) {
            double rate = Double.parseDouble(ag[9]);
            int col = (rate >= 92.0) ? Color.parseColor("#00E676") : ((rate >= 85.0) ? Color.parseColor("#FFB300") : Color.parseColor("#FF5252"));
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setPadding(18, 12, 18, 12);
            c.setBackground(bg(Color.parseColor("#141d2d"), col, 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, 10);
            c.setLayoutParams(lp);

            String m = (top && rank == 1) ? "👑 🥇 #" + rank + " " : ((top && rank == 2) ? "🥈 #" + rank + " " : ((top && rank == 3) ? "🥉 #" + rank + " " : "👤 "));
            TextView n = new TextView(this);
            n.setText(m + ag[0] + " (" + ag[1] + ")");
            n.setTextColor(col); n.setTypeface(Typeface.DEFAULT_BOLD);
            c.addView(n);

            TextView s = new TextView(this);
            s.setText("OFD: " + ag[2] + " | DEL: " + ag[3] + " | OFP: " + ag[4] + " | PIK: " + ag[5] + "\nDNP: " + ag[6] + " | DNPC: " + ag[7] + " | Conv: " + ag[8] + "%");
            s.setTextColor(Color.WHITE); s.setTextSize(11f); s.setPadding(0, 4, 0, 0);
            c.addView(s);
            vC.addView(c);
            rank++;
        }
    }

    void auth() {
        EditText in = new EditText(this);
        in.setHint("PIN..."); in.setInputType(InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this).setTitle("🔐 Admin").setView(in)
            .setPositiveButton("Verify", (d, w) -> {
                if ("9547927698".equals(in.getText().toString().trim())) syncDlg();
                else Toast.makeText(this, "Wrong PIN!", Toast.LENGTH_SHORT).show();
            }).show();
    }

    void syncDlg() {
        new AlertDialog.Builder(this).setTitle("⚡ Sync Options")
            .setPositiveButton("Sync Now", (d, w) -> new Thread(this::doSync).start())
            .setNegativeButton("Clear Data", (d, w) -> {
                db.delete("o", null, null); db.delete("p", null, null);
                runOnUiThread(() -> { cnt(); qry(""); Toast.makeText(this, "Cleared!", Toast.LENGTH_SHORT).show(); });
            }).show();
    }

    void doSync() {
        try {
            runOnUiThread(() -> Toast.makeText(this, "Syncing...", Toast.LENGTH_SHORT).show());
            String curDt = dt();
            HttpURLConnection conn = (HttpURLConnection) new URL(CSV).openConnection();
            conn.setConnectTimeout(15000);
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            db.beginTransaction();
            int count = 0;
            try {
                db.delete("o", null, null);
                db.delete("p", "dt = ?", new String[]{curDt});
                String l; boolean hd = true;
                while ((l = r.readLine()) != null) {
                    if (hd) { hd = false; continue; }
                    String[] p = l.split(",", -1);
                    if (p.length < 2) continue;
                    String c1 = p[0].replace("\"", "").trim(), c2 = p[1].replace("\"", "").trim();
                    String t = c1.toUpperCase().startsWith("FMP") ? c1 : c2;
                    String o = c1.toUpperCase().startsWith("OD") ? c1 : c2;
                    String name = p.length > 2 ? p[2].replace("\"", "").trim() : "";
                    String mob = p.length > 3 ? p[3].replace("\"", "").trim() : "";
                    if (!t.isEmpty() && !o.isEmpty()) {
                        ContentValues cv = new ContentValues();
                        cv.put("t", t); cv.put("d", o);
                        db.insert("o", null, cv);
                        count++;
                    }
                    if (!name.isEmpty() && !name.equalsIgnoreCase("NAME")) {
                        ContentValues cv = new ContentValues();
                        cv.put("n", name); cv.put("m", mob);
                        cv.put("o", p.length > 4 ? pInt(p[4]) : 0);
                        cv.put("l", p.length > 5 ? pInt(p[5]) : 0);
                        cv.put("p", p.length > 6 ? pInt(p[6]) : 0);
                        cv.put("k", p.length > 7 ? pInt(p[7]) : 0);
                        cv.put("dt", curDt);
                        db.insert("p", null, cv);
                    }
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
            int fin = count;
            runOnUiThread(() -> {
                Toast.makeText(this, "Synced " + fin + " items!", Toast.LENGTH_LONG).show();
                cnt();
                if (sP.getVisibility() == View.VISIBLE) load();
            });
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Sync Failed!", Toast.LENGTH_SHORT).show());
        }
    }

    int pInt(String s) {
        try { return Integer.parseInt(s.replace("\"", "").trim()); } catch (Exception e) { return 0; }
    }
                                     }
        
