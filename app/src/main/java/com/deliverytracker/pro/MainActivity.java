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
    ArrayList<String[]> orders = new ArrayList<String[]>();
    BaseAdapter adapter;
    LinearLayout vTrack, vPerf, vCards;
    ScrollView sPerf;
    Button bT, bP, b1, b2, b3, bSort;
    TextView tCnt, tHub;
    String mode = "daily";
    boolean topHigh = true;
    String CSV = "https://docs.google.com/spreadsheets/d/1Dul38iNZ_eNmABVuYVWhrUg9F_xVMvaVvQvLIXlySj4/export?format=csv";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        db = openOrCreateDatabase("TrackerApp.db", MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS ord (track_id TEXT, order_id TEXT);");
        db.execSQL("CREATE TABLE IF NOT EXISTS prf (name TEXT, mob TEXT, ofd INT, del INT, ofp INT, pik INT, dt TEXT);");
        initUI();
        count();
    }

    GradientDrawable box(int col, int stroke, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(col);
        g.setCornerRadius(r);
        if (stroke != 0) g.setStroke(2, stroke);
        return g;
    }

    void initUI() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setBackgroundColor(Color.parseColor("#0a0e17"));
        LinearLayout top = new LinearLayout(this);
        top.setBackgroundColor(Color.parseColor("#131c2e"));
        top.setPadding(28, 20, 28, 20);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView h = new TextView(this);
        h.setText("⚡ Delivery Tracker Pro");
        h.setTextColor(Color.parseColor("#00E676"));
        h.setTextSize(18f);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(h, new LinearLayout.LayoutParams(0, -2, 1f));
        Button bAdm = new Button(this);
        bAdm.setText("🔒 Admin");
        bAdm.setTextColor(Color.parseColor("#00E676"));
        bAdm.setBackground(box(Color.parseColor("#1f2d47"), Color.parseColor("#00E676"), 12));
        bAdm.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { adm(); }
        });
        top.addView(bAdm);
        r.addView(top);
        LinearLayout tb = new LinearLayout(this);
        tb.setBackgroundColor(Color.parseColor("#101726"));
        tb.setPadding(12, 10, 12, 10);
        bT = new Button(this); bT.setText("🔍 Tracker");
        bT.setBackground(box(Color.parseColor("#00E676"), 0, 14)); bT.setTextColor(Color.BLACK);
        bP = new Button(this); bP.setText("📊 Performance");
        bP.setBackground(box(Color.parseColor("#1a2333"), 0, 14)); bP.setTextColor(Color.parseColor("#8fa0bc"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(6, 0, 6, 0);
        tb.addView(bT, lp); tb.addView(bP, new LinearLayout.LayoutParams(lp));
        r.addView(tb);
        FrameLayout bdy = new FrameLayout(this);
        bdy.setPadding(20, 16, 20, 16);
        r.addView(bdy, new LinearLayout.LayoutParams(-1, -1));
        vTrack = new LinearLayout(this);
        vTrack.setOrientation(LinearLayout.VERTICAL);
        EditText src = new EditText(this);
        src.setHint("Search Tracking ID / Order ID...");
        src.setTextColor(Color.WHITE);
        src.setBackground(box(Color.parseColor("#141d2d"), Color.parseColor("#2a3b5c"), 16));
        src.setPadding(24, 20, 24, 20);
        src.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
            public void onTextChanged(CharSequence s, int i, int i1, int i2) { find(s.toString().trim()); }
            public void afterTextChanged(Editable s) {}
        });
        vTrack.addView(src);
        tCnt = new TextView(this);
        tCnt.setTextColor(Color.parseColor("#8fa0bc"));
        tCnt.setPadding(4, 16, 4, 12);
        vTrack.addView(tCnt);
        ListView lv = new ListView(this);
        lv.setDivider(null); lv.setDividerHeight(12);
        adapter = new BaseAdapter() {
            public int getCount() { return orders.size(); }
            public Object getItem(int i) { return orders.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) {
                LinearLayout card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(24, 18, 24, 18);
                card.setBackground(box(Color.parseColor("#141d2d"), Color.parseColor("#23334d"), 16));
                final String[] itm = orders.get(i);
                TextView t1 = new TextView(MainActivity.this);
                t1.setText("📦 Track ID: " + itm[0]); t1.setTextColor(Color.parseColor("#00E676"));
                TextView t2 = new TextView(MainActivity.this);
                t2.setText("🛒 Order ID: " + itm[1] + " (Tap to Copy)");
                t2.setTextColor(Color.parseColor("#64B5F6"));
                t2.setPadding(0, 6, 0, 0);
                card.addView(t1); card.addView(t2);
                card.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View vw) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("ID", itm[1]));
                        Toast.makeText(MainActivity.this, "Copied: " + itm[1], Toast.LENGTH_SHORT).show();
                    }
                });
                return card;
            }
        };
        lv.setAdapter(adapter);
        vTrack.addView(lv, new LinearLayout.LayoutParams(-1, -1));
        bdy.addView(vTrack);
        sPerf = new ScrollView(this);
        vPerf = new LinearLayout(this);
        vPerf.setOrientation(LinearLayout.VERTICAL);
        sPerf.addView(vPerf);
        sPerf.setVisibility(View.GONE);
        bdy.addView(sPerf);
        LinearLayout flt = new LinearLayout(this);
        b1 = mkBtn("📅 Daily", "daily");
        b2 = mkBtn("📆 Weekly", "weekly");
        b3 = mkBtn("🗓️ Monthly", "monthly");
        LinearLayout.LayoutParams lpF = new LinearLayout.LayoutParams(0, -2, 1f);
        lpF.setMargins(4, 0, 4, 14);
        flt.addView(b1, lpF); flt.addView(b2, new LinearLayout.LayoutParams(lpF)); flt.addView(b3, new LinearLayout.LayoutParams(lpF));
        vPerf.addView(flt);
        LinearLayout hb = new LinearLayout(this);
        hb.setOrientation(LinearLayout.VERTICAL);
        hb.setBackground(box(Color.parseColor("#132338"), Color.parseColor("#00E676"), 16));
        hb.setPadding(22, 18, 22, 18);
        TextView hbT = new TextView(this);
        hbT.setText("🏢 MALBAZARHUB_NJP | 🎯 Target: 92.0%");
        hbT.setTextColor(Color.parseColor("#00E676"));
        hb.addView(hbT);
        tHub = new TextView(this);
        tHub.setTextColor(Color.WHITE);
        tHub.setPadding(0, 8, 0, 0);
        hb.addView(tHub);
        vPerf.addView(hb);
        LinearLayout sortBar = new LinearLayout(this);
        sortBar.setPadding(0, 20, 0, 10);
        TextView at = new TextView(this);
        at.setText("👥 Delivery Agents Report");
        at.setTextColor(Color.parseColor("#8fa0bc"));
        sortBar.addView(at, new LinearLayout.LayoutParams(0, -2, 1f));
        bSort = new Button(this); bSort.setText("🏆 Top First");
        bSort.setTextColor(Color.parseColor("#00E676"));
        bSort.setBackground(box(Color.parseColor("#1f2d47"), Color.parseColor("#00E676"), 12));
        bSort.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { topHigh = !topHigh; bSort.setText(topHigh ? "🏆 Top First" : "⚠️ Low First"); loadP(); }
        });
        sortBar.addView(bSort);
        vPerf.addView(sortBar);
        vCards = new LinearLayout(this);
        vCards.setOrientation(LinearLayout.VERTICAL);
        vPerf.addView(vCards);
        bT.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vTrack.setVisibility(View.VISIBLE); sPerf.setVisibility(View.GONE);
                bT.setBackground(box(Color.parseColor("#00E676"), 0, 14)); bT.setTextColor(Color.BLACK);
                bP.setBackground(box(Color.parseColor("#1a2333"), 0, 14)); bP.setTextColor(Color.parseColor("#8fa0bc"));
            }
        });
        bP.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vTrack.setVisibility(View.GONE); sPerf.setVisibility(View.VISIBLE);
                bP.setBackground(box(Color.parseColor("#00E676"), 0, 14)); bP.setTextColor(Color.BLACK);
                bT.setBackground(box(Color.parseColor("#1a2333"), 0, 14)); bT.setTextColor(Color.parseColor("#8fa0bc"));
                loadP();
            }
        });
        setContentView(r);
    }
    Button mkBtn(String t, final String m) {
        Button b = new Button(this);
        b.setText(t);
        b.setBackground(box(m.equals(mode) ? Color.parseColor("#238636") : Color.parseColor("#1a2333"), 0, 12));
        b.setTextColor(m.equals(mode) ? Color.WHITE : Color.parseColor("#8fa0bc"));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mode = m;
                b1.setBackground(box("daily".equals(m) ? Color.parseColor("#238636") : Color.parseColor("#1a2333"), 0, 12));
                b2.setBackground(box("weekly".equals(m) ? Color.parseColor("#238636") : Color.parseColor("#1a2333"), 0, 12));
                b3.setBackground(box("monthly".equals(m) ? Color.parseColor("#238636") : Color.parseColor("#1a2333"), 0, 12));
                loadP();
            }
        });
        return b;
    }
    String getDt() {
        Calendar c = Calendar.getInstance();
        if (c.get(Calendar.HOUR_OF_DAY) < 9) c.add(Calendar.DAY_OF_YEAR, -1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
    }
    void count() {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM ord", null);
        int n = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        tCnt.setText("📦 Total Active Orders: " + n);
    }
    void find(String q) {
        orders.clear();
        if (!q.isEmpty()) {
            Cursor c = db.rawQuery("SELECT track_id, order_id FROM ord WHERE track_id LIKE ? OR order_id LIKE ? LIMIT 50", new String[]{"%" + q + "%", "%" + q + "%"});
            while (c.moveToNext()) orders.add(new String[]{c.getString(0), c.getString(1)});
            c.close();
        }
        adp.notifyDataSetChanged();
    }
    void loadP() {
        vCards.removeAllViews();
        String cond = "daily".equals(mode) ? " WHERE dt = (SELECT MAX(dt) FROM prf) " : ("weekly".equals(mode) ? " WHERE dt >= date('now','localtime','-7 days') " : " WHERE dt >= date('now','localtime','-30 days') ");
        Cursor hc = db.rawQuery("SELECT SUM(ofd), SUM(del), SUM(ofp), SUM(pik) FROM prf" + cond, null);
        if (hc.moveToFirst()) {
            int o = hc.getInt(0), d = hc.getInt(1), op = hc.getInt(2), p = hc.getInt(3);
            int dnp = o + op, dnpc = d + p;
            double r = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;
            tHub.setText("OFD: " + o + " | DEL: " + d + " | OFP: " + op + " | PIK: " + p + "\nActual Conv: " + String.format(Locale.US, "%.1f%%", r));
        }
        hc.close();
        Cursor ac = db.rawQuery("SELECT name, mob, SUM(ofd), SUM(del), SUM(ofp), SUM(pik) FROM prf " + cond + " GROUP BY name, mob", null);
        ArrayList<String[]> agList = new ArrayList<String[]>();
        while (ac.moveToNext()) {
            int o = ac.getInt(2), d = ac.getInt(3), op = ac.getInt(4), p = ac.getInt(5);
            int dnp = o + op, dnpc = d + p;
            double r = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;
            agList.add(new String[]{ac.getString(0), ac.getString(1), String.valueOf(o), String.valueOf(d), String.valueOf(op), String.valueOf(p), String.valueOf(dnp), String.valueOf(dnpc), String.format(Locale.US, "%.1f", r), String.valueOf(r)});
        }
        ac.close();
        Collections.sort(agList, new Comparator<String[]>() {
            public int compare(String[] a, String[] b) {
                double r1 = Double.parseDouble(a[9]), r2 = Double.parseDouble(b[9]);
                return topHigh ? Double.compare(r2, r1) : Double.compare(r1, r2);
            }
        });
        int rank = 1;
        for (String[] ag : agList) {
            double rate = Double.parseDouble(ag[9]);
            int col = (rate >= 92.0) ? Color.parseColor("#00E676") : ((rate >= 85.0) ? Color.parseColor("#FFB300") : Color.parseColor("#FF5252"));
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setPadding(22, 16, 22, 16);
            c.setBackground(box(Color.parseColor("#141d2d"), col, 16));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, 12);
            c.setLayoutParams(lp);
            String medal = (topHigh && rank == 1) ? "👑 🥇 #" + rank + " " : ((topHigh && rank == 2) ? "🥈 #" + rank + " " : "👤 ");
            TextView n = new TextView(this); n.setText(medal + ag[0]); n.setTextColor(col); n.setTypeface(Typeface.DEFAULT_BOLD);
            c.addView(n);
            TextView s = new TextView(this); s.setText("DNP: " + ag[6] + " | DNPC: " + ag[7] + " | Conv: " + ag[8] + "%");
            s.setTextColor(Color.WHITE); s.setTextSize(12f);
            c.addView(s);
            vCards.addView(c);
            rank++;
        }
    }
    void adm() {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this).setTitle("🔒 Admin").setView(in).setPositiveButton("Verify", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                if ("9547927698".equals(in.getText().toString().trim())) syncDlg();
                else Toast.makeText(MainActivity.this, "Wrong!", Toast.LENGTH_SHORT).show();
            }
        }).show();
    }
    void syncDlg() {
        new AlertDialog.Builder(this).setTitle("Sync").setPositiveButton("Sync Now", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) { new Thread(new Runnable() { public void run() { doSync(); } }).start(); }
        }).show();
    }
    void doSync() {
        try {
            String dt = getDt();
            HttpURLConnection conn = (HttpURLConnection) new URL(CSV).openConnection();
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            db.beginTransaction();
            db.delete("ord", null, null); db.delete("prf", "dt = ?", new String[]{dt});
            String l; boolean hd = true;
            while ((l = r.readLine()) != null) {
                if (hd) { hd = false; continue; }
                String[] p = l.split(",", -1);
                if (p.length < 2) continue;
                String c1 = p[0].replace("\"", "").trim();
                String c2 = p[1].replace("\"", "").trim();
                String t = c1.toUpperCase().contains("FMP") ? c1 : c2;
                String o = c1.toUpperCase().contains("OD") ? c1 : c2;
                if (!t.isEmpty() && !o.isEmpty()) {
                    ContentValues cv = new ContentValues(); cv.put("track_id", t); cv.put("order_id", o);
                    db.insert("ord", null, cv);
                }
                if (p.length > 2 && !p[2].isEmpty()) {
                    ContentValues cv = new ContentValues(); cv.put("name", p[2].replace("\"", "").trim());
                    cv.put("mob", p.length > 3 ? p[3].replace("\"", "").trim() : "");
                    cv.put("ofd", p.length > 4 ? pInt(p[4]) : 0);
                    cv.put("del", p.length > 5 ? pInt(p[5]) : 0);
                    cv.put("ofp", p.length > 6 ? pInt(p[6]) : 0);
                    cv.put("pik", p.length > 7 ? pInt(p[7]) : 0);
                    cv.put("dt", dt); db.insert("prf", null, cv);
                }
            }
            db.setTransactionSuccessful(); db.endTransaction();
            runOnUiThread(new Runnable() { public void run() { count(); find(""); Toast.makeText(MainActivity.this, "Synced!", Toast.LENGTH_SHORT).show(); } });
        } catch (Exception e) {}
    }
    int pInt(String s) { try { return Integer.parseInt(s.replace("\"", "").trim()); } catch (Exception e) { return 0; } }
                                                                                    }
