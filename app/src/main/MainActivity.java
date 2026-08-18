package com.deliverytracker.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
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
import java.util.Comparator;
import java.util.Locale;

public class MainActivity extends Activity {
    SQLiteDatabase db;
    ArrayList<String[]> ords = new ArrayList<String[]>();
    BaseAdapter adp;
    LinearLayout vTrk, vPrf, vCrd;
    ScrollView sPrf;
    Button bT, bP, b1, b2, b3, bS;
    TextView tC, tH;
    String mode = "daily";
    boolean top = true;
    String URL = "https://docs.google.com/spreadsheets/d/1Dul38iNZ_eNmABVuYVWhrUg9F_xVMvaVvQvLIXlySj4/export?format=csv";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        db = openOrCreateDatabase("D.db", MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS o (t TEXT, d TEXT);");
        db.execSQL("CREATE TABLE IF NOT EXISTS p (n TEXT, m TEXT, o INT, l INT, p INT, k INT, dt TEXT);");
        ui();
        cnt();
    }

    GradientDrawable bg(int c, int s, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c);
        g.setCornerRadius(r);
        if (s != 0) g.setStroke(2, s);
        return g;
    }

    void ui() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setBackgroundColor(Color.parseColor("#0a0e17"));

        LinearLayout h = new LinearLayout(this);
        h.setBackgroundColor(Color.parseColor("#131c2e"));
        h.setPadding(24, 18, 24, 18);
        h.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText("⚡ Delivery Tracker Pro");
        t.setTextColor(Color.parseColor("#00E676"));
        t.setTextSize(17f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        h.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));

        Button adm = new Button(this);
        adm.setText("🔒 Admin");
        adm.setTextColor(Color.parseColor("#00E676"));
        adm.setBackground(bg(Color.parseColor("#1f2d47"), Color.parseColor("#00E676"), 12));
        adm.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { auth(); }
        });
        h.addView(adm);
        r.addView(h);

        LinearLayout tb = new LinearLayout(this);
        tb.setBackgroundColor(Color.parseColor("#101726"));
        tb.setPadding(12, 8, 12, 8);
        bT = new Button(this); bT.setText("🔍 Tracker");
        bT.setBackground(bg(Color.parseColor("#00E676"), 0, 12));
        bT.setTextColor(Color.BLACK);
        bT.setTypeface(Typeface.DEFAULT_BOLD);

        bP = new Button(this); bP.setText("📊 Performance");
        bP.setBackground(bg(Color.parseColor("#1a2333"), 0, 12));
        bP.setTextColor(Color.parseColor("#8fa0bc"));
        bP.setTypeface(Typeface.DEFAULT_BOLD);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(6, 0, 6, 0);
        tb.addView(bT, lp);
        tb.addView(bP, new LinearLayout.LayoutParams(lp));
        r.addView(tb);

        FrameLayout f = new FrameLayout(this);
        f.setPadding(18, 14, 18, 14);
        r.addView(f, new LinearLayout.LayoutParams(-1, -1));

        vTrk = new LinearLayout(this);
        vTrk.setOrientation(LinearLayout.VERTICAL);
        EditText s = new EditText(this);
        s.setHint("Search Tracking ID / Order ID...");
        s.setHintTextColor(Color.parseColor("#657795"));
        s.setTextColor(Color.WHITE);
        s.setBackground(bg(Color.parseColor("#141d2d"), Color.parseColor("#2a3b5c"), 14));
        s.setPadding(20, 16, 20, 16);
        s.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int i, int i1, int i2) {}
            public void onTextChanged(CharSequence c, int i, int i1, int i2) { qry(c.toString().trim()); }
            public void afterTextChanged(Editable e) {}
        });
        vTrk.addView(s);

        tC = new TextView(this);
        tC.setTextColor(Color.parseColor("#8fa0bc"));
        tC.setPadding(4, 12, 4, 10);
        tC.setTypeface(Typeface.DEFAULT_BOLD);
        vTrk.addView(tC);

        ListView lv = new ListView(this);
        lv.setDivider(null); lv.setDividerHeight(10);
        adp = new BaseAdapter() {
            public int getCount() { return ords.size(); }
            public Object getItem(int i) { return ords.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) {
                LinearLayout c = new LinearLayout(MainActivity.this);
                c.setOrientation(LinearLayout.VERTICAL);
                c.setPadding(20, 16, 20, 16);
                c.setBackground(bg(Color.parseColor("#141d2d"), Color.parseColor("#23334d"), 14));
                final String[] it = ords.get(i);
                TextView t1 = new TextView(MainActivity.this);
                t1.setText("📦 Track ID: " + it[0]);
                t1.setTextColor(Color.parseColor("#00E676"));
                t1.setTypeface(Typeface.DEFAULT_BOLD);
                t1.setTextSize(14f);
                TextView t2 = new TextView(MainActivity.this);
                t2.setText("🛒 Order ID: " + it[1] + " (Tap to Copy)");
                t2.setTextColor(Color.parseColor("#64B5F6"));
                t2.setTextSize(13f);
                t2.setPadding(0, 6, 0, 0);
                c.addView(t1); c.addView(t2);
                c.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View vw) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("ID", it[1]));
                        Toast.makeText(MainActivity.this, "Copied: " + it[1], Toast.LENGTH_SHORT).show();
                    }
                });
                return c;
            }
        };
        lv.setAdapter(adp);
        vTrk.addView(lv, new LinearLayout.LayoutParams(-1, -1));
        f.addView(vTrk);

        sPrf = new ScrollView(this);
        vPrf = new LinearLayout(this);
        vPrf.setOrientation(LinearLayout.VERTICAL);
        sPrf.addView(vPrf);
        sPrf.setVisibility(View.GONE);
        f.addView(sPrf);

        LinearLayout fl = new LinearLayout(this);
        b1 = flt("📅 Daily", "daily");
        b2 = flt("📆 Weekly", "weekly");
        b3 = flt("🗓️ Monthly", "monthly");
        LinearLayout.LayoutParams lpF = new LinearLayout.LayoutParams(0, -2, 1f);
        lpF.setMargins(3, 0, 3, 12);
        fl.addView(b1, lpF); fl.addView(b2, new LinearLayout.LayoutParams(lpF)); fl.addView(b3, new LinearLayout.LayoutParams(lpF));
        vPrf.addView(fl);

        LinearLayout hb = new LinearLayout(this);
        hb.setOrientation(LinearLayout.VERTICAL);
        hb.setBackground(bg(Color.parseColor("#132338"), Color.parseColor("#00E676"), 14));
        hb.setPadding(20, 16, 20, 16);
        TextView ht = new TextView(this);
        ht.setText("🏢 MALBAZARHUB_NJP | 🎯 Target: 92.0%");
        ht.setTextColor(Color.parseColor("#00E676"));
        ht.setTypeface(Typeface.DEFAULT_BOLD);
        hb.addView(ht);
        tH = new TextView(this);
        tH.setTextColor(Color.WHITE);
        tH.setPadding(0, 6, 0, 0);
        tH.setTextSize(12f);
        hb.addView(tH);
        vPrf.addView(hb);

        LinearLayout sb = new LinearLayout(this);
        sb.setGravity(Gravity.CENTER_VERTICAL);
        sb.setPadding(0, 16, 0, 8);
        TextView ar = new TextView(this);
        ar.setText("👥 Delivery Agents Report");
        ar.setTextColor(Color.parseColor("#8fa0bc"));
        ar.setTypeface(Typeface.DEFAULT_BOLD);
        sb.addView(ar, new LinearLayout.LayoutParams(0, -2, 1f));

        bS = new Button(this);
        bS.setText("🏆 Top First");
        bS.setTextSize(11f);
        bS.setTextColor(Color.parseColor("#00E676"));
        bS.setBackground(bg(Color.parseColor("#1f2d47"), Color.parseColor("#00E676"), 10));
        bS.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                top = !top;
                bS.setText(top ? "🏆 Top First" : "⚠️ Low First");
                load();
            }
        });
        sb.addView(bS);
        vPrf.addView(sb);

        vCrd = new LinearLayout(this);
        vCrd.setOrientation(LinearLayout.VERTICAL);
        vPrf.addView(vCrd);

        bT.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vTrk.setVisibility(View.VISIBLE); sPrf.setVisibility(View.GONE);
                bT.setBackground(bg(Color.parseColor("#00E676"), 0, 12)); bT.setTextColor(Color.BLACK);
                bP.setBackground(bg(Color.parseColor("#1a2333"), 0, 12)); bP.setTextColor(Color.parseColor("#8fa0bc"));
            }
        });
        bP.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vTrk.setVisibility(View.GONE); sPrf.setVisibility(View.VISIBLE);
                bP.setBackground(bg(Color.parseColor("#00E676"), 0, 12)); bP.setTextColor(Color.BLACK);
                bT.setBackground(bg(Color.parseColor("#1a2333"), 0, 12)); bT.setTextColor(Color.parseColor("#8fa0bc"));
                load();
            }
        });

        setContentView(r);
    }

    Button flt(String txt, final String m) {
        Button b = new Button(this);
        b.setText(txt);
        b.setBackground(bg(m.equals(mode) ? Color.parseColor("#238636") : Color.parseColor("#1a2333"), 0, 10));
        b.setTextColor(m.equals(mode) ? Color.WHITE : Color.parseColor("#8fa0bc"));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mode = m;
                b1.setBackground(bg("daily".equals(m) ? Color.parseColor("#238636") : Color.parseColor("#1a2333"), 0, 10));
                b1.setTextColor("daily".equals(m) ? Color.WHITE : Color.parseColor("#8fa0bc"));
                b2.setBackground(bg("weekly".equals(m) ? Color.parseColor("#238636") : Color.parseColor("#1a2333"), 0, 10));
                b2.setTextColor("weekly".equals(m) ? Color.WHITE : Color.parseColor("#8fa0bc"));
                b3.setBackground(bg("monthly".equals(m) ? Color.parseColor("#238636") : Color.parseColor("#1a2333"), 0, 10));
                b3.setTextColor("monthly".equals(m) ? Color.WHITE : Color.parseColor("#8fa0bc"));
                load();
            }
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
        int n = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        tC.setText("📦 Active Orders: " + n);
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
            tH.setText("OFD: " + o + " | DEL: " + d + " | OFP: " + op + " | PIK: " + p + "\nDNP: " + dnp + " | DNPC: " + dnpc + " | Conv: " + String.format(Locale.US, "%.1f%%", r));
        } else { tH.setText("No data synced yet."); }
        hc.close();

        Cursor ac = db.rawQuery("SELECT n, m, SUM(o), SUM(l), SUM(p), SUM(k) FROM p " + w + " GROUP BY n, m", null);
        ArrayList<String[]> list = new ArrayList<String[]>();
        while (ac.moveToNext()) {
            int o = ac.getInt(2), d = ac.getInt(3), op = ac.getInt(4), p = ac.getInt(5);
            int dnp = o + op, dnpc = d + p;
            double r = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;
            list.add(new String[]{ac.getString(0), ac.getString(1), String.valueOf(o), String.valueOf(d), String.valueOf(op), String.valueOf(p), String.valueOf(dnp), String.valueOf(dnpc), String.format(Locale.US, "%.1f", r), String.valueOf(r)});
        }
        ac.close();

        Collections.sort(list, new Comparator<String[]>() {
            public int compare(String[] a, String[] b) {
                double r1 = Double.parseDouble(a[9]), r2 = Double.parseDouble(b[9]);
                return top ? Double.compare(r2, r1) : Double.compare(r1, r2);
            }
        });

        int rank = 1;
        for (String[] ag : list) {
            double rate = Double.parseDouble(ag[9]);
            int col = (rate >= 92.0) ? Color.parseColor("#00E676") : ((rate >= 85.0) ? Color.parseColor("#FFB300") : Color.parseColor("#FF5252"));
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setPadding(20, 14, 20, 14);
            c.setBackground(bg(Color.parseColor("#141d2d"), col, 14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, 10);
            c.setLayoutParams(lp);

            String m = (top && rank == 1) ? "👑 🥇 #" + rank + " " : ((top && rank == 2) ? "🥈 #" + rank + " " : ((top && rank == 3) ? "🥉 #" + rank + " " : "👤 "));
            TextView n = new TextView(this);
            n.setText(m + ag[0] + " (" + ag[1] + ")");
            n.setTextColor(col);
            n.setTypeface(Typeface.DEFAULT_BOLD);
            c.addView(n);

            TextView s = new TextView(this);
            s.setText("OFD: " + ag[2] + " | DEL: " + ag[3] + " | OFP: " + ag[4] + " | PIK: " + ag[5] + "\nDNP: " + ag[6] + " | DNPC: " + ag[7] + " | Conv: " + ag[8] + "%");
            s.setTextColor(Color.WHITE);
            s.setTextSize(11f);
            s.setPadding(0, 4, 0, 0);
            c.addView(s);
            vCrd.addView(c);
            rank++;
        }
    }

    void auth() {
        final EditText in = new EditText(this);
        in.setHint("PIN...");
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this).setTitle("🔐 Admin Login").setView(in)
            .setPositiveButton("Verify", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if ("9547927698".equals(in.getText().toString().trim())) syncDlg();
                    else Toast.makeText(MainActivity.this, "Wrong PIN!", Toast.LENGTH_SHORT).show();
                }
            }).show();
    }

    void syncDlg() {
        new AlertDialog.Builder(this).setTitle("⚡ Sync Options")
            .setPositiveButton("Sync Now", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    new Thread(new Runnable() { public void run() { doSync(); } }).start();
                }
            })
            .setNegativeButton("Clear Data", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    db.delete("o", null, null); db.delete("p", null, null);
                    runOnUiThread(new Runnable() {
                        public void run() { cnt(); qry(""); Toast.makeText(MainActivity.this, "Cleared!", Toast.LENGTH_SHORT).show(); }
                    });
                }
            }).show();
    }

    void doSync() {
        try {
            runOnUiThread(new Runnable() {
                public void run() { Toast.makeText(MainActivity.this, "Syncing...", Toast.LENGTH_SHORT).show(); }
            });
            String curDt = dt();
            HttpURLConnection conn = (HttpURLConnection) new URL(URL).openConnection();
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
                    String c1 = p[0].replace("\"", "").trim();
                    String c2 = p[1].replace("\"", "").trim();
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
            } finally {
                db.endTransaction();
            }
            final int fin = count;
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(MainActivity.this, "Synced " + fin + " items!", Toast.LENGTH_LONG).show();
                    cnt();
                    if (sPrf.getVisibility() == View.VISIBLE) load();
                }
            });
        } catch (Exception e) {
            runOnUiThread(new Runnable() {
                public void run() { Toast.makeText(MainActivity.this, "Sync Failed!", Toast.LENGTH_SHORT).show(); }
            });
        }
    }

    int pInt(String s) {
        try { return Integer.parseInt(s.replace("\"", "").trim()); } catch (Exception e) { return 0; }
    }
}
