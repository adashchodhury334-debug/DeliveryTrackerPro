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
import java.util.Locale;

public class MainActivity extends Activity {
    SQLiteDatabase db;
    FrameLayout root;
    LinearLayout vTrk, vPrf, vCrd;
    Button bT, bP, b1, b2, b3, bSort;
    TextView tCnt, tHubData;
    ArrayList<String[]> ords = new ArrayList<>();
    BaseAdapter adp;
    String mode = "daily";
    boolean isHighToLow = true;
    String CSV = "https://docs.google.com/spreadsheets/d/1Dul38iNZ_eNmABVuYVWhrUg9F_xVMvaVvQvLIXlySj4/export?format=csv";

    GradientDrawable box(int c, int r, int sCol, int sW) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c);
        g.setCornerRadius(r);
        if (sW > 0) g.setStroke(sW, sCol);
        return g;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        try {
            db = openOrCreateDatabase("TrackerV15.db", MODE_PRIVATE, null);
            db.execSQL("CREATE TABLE IF NOT EXISTS ord (t TEXT UNIQUE, d TEXT);");
            db.execSQL("CREATE TABLE IF NOT EXISTS prf (n TEXT, o INT, l INT, p INT, k INT, dt TEXT);");
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
        new Thread(() -> doSync(true)).start();
    }

    void buildUI() {
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);

        LinearLayout h = new LinearLayout(this);
        h.setBackgroundColor(Color.parseColor("#181920"));
        h.setPadding(20, 16, 20, 16);
        h.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText("📊 Delivery Tracker");
        t.setTextColor(Color.WHITE);
        t.setTextSize(16f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        h.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));

        Button bRef = new Button(this);
        bRef.setText("🔄 Sync");
        bRef.setBackground(box(Color.parseColor("#00E676"), 8, 0, 0));
        bRef.setTextColor(Color.BLACK);
        bRef.setTypeface(Typeface.DEFAULT_BOLD);
        bRef.setOnClickListener(v -> new Thread(() -> doSync(false)).start());
        h.addView(bRef);
        main.addView(h);

        LinearLayout tb = new LinearLayout(this);
        tb.setPadding(16, 8, 16, 4);
        bT = new Button(this);
        bT.setText("🔍 Tracker");
        bT.setBackground(box(Color.parseColor("#232634"), 12, 0, 0));
        bT.setTextColor(Color.parseColor("#8E92A4"));

        bP = new Button(this);
        bP.setText("📈 Performance");
        bP.setBackground(box(Color.parseColor("#00E676"), 12, 0, 0));
        bP.setTextColor(Color.BLACK);
        bP.setTypeface(Typeface.DEFAULT_BOLD);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(3, 0, 3, 0);
        tb.addView(bT, lp);
        tb.addView(bP, new LinearLayout.LayoutParams(lp));
        main.addView(tb);

        FrameLayout body = new FrameLayout(this);
        body.setPadding(16, 6, 16, 10);
        main.addView(body, new LinearLayout.LayoutParams(-1, -1));

        // Tracker View
        vTrk = new LinearLayout(this);
        vTrk.setOrientation(LinearLayout.VERTICAL);
        vTrk.setVisibility(View.GONE);

        EditText s = new EditText(this);
        s.setHint("🔍 Search Tracking ID or Order ID...");
        s.setHintTextColor(Color.parseColor("#717688"));
        s.setTextColor(Color.WHITE);
        s.setBackground(box(Color.parseColor("#181920"), 14, Color.parseColor("#00E676"), 1));
        s.setPadding(22, 18, 22, 18);
        s.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int i, int i1, int i2) {}
            public void onTextChanged(CharSequence c, int i, int i1, int i2) { qry(c.toString().trim()); }
            public void afterTextChanged(Editable e) {}
        });
        vTrk.addView(s);

        tCnt = new TextView(this);
        tCnt.setTextColor(Color.parseColor("#00E676"));
        tCnt.setPadding(6, 10, 6, 8);
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
                c.setPadding(20, 16, 20, 16);
                c.setBackground(box(Color.parseColor("#181920"), 14, Color.parseColor("#2A2D3D"), 1));
                String[] it = ords.get(i);

                TextView t1 = new TextView(MainActivity.this);
                t1.setText("📦 Track ID: " + it[0]);
                t1.setTextColor(Color.parseColor("#38BDF8"));
                t1.setTypeface(Typeface.DEFAULT_BOLD);

                TextView t2 = new TextView(MainActivity.this);
                t2.setText("🛒 Order ID: " + it[1] + "  📋 (Tap to Copy)");
                t2.setTextColor(Color.parseColor("#00E676"));
                t2.setPadding(0, 6, 0, 0);

                c.addView(t1);
                c.addView(t2);
                c.setOnClickListener(vw -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("ID", it[1]));
                    Toast.makeText(MainActivity.this, "Copied: " + it[1], Toast.LENGTH_SHORT).show();
                });
                return c;
            }
        };
        lv.setAdapter(adp);
        vTrk.addView(lv, new LinearLayout.LayoutParams(-1, -1));
        body.addView(vTrk);
                // Performance View
        vPrf = new LinearLayout(this);
        vPrf.setOrientation(LinearLayout.VERTICAL);

        LinearLayout fl = new LinearLayout(this);
        b1 = flt("📅 Daily", "daily");
        b2 = flt("📆 Weekly", "weekly");
        b3 = flt("🗓️ Monthly", "monthly");
        LinearLayout.LayoutParams lpF = new LinearLayout.LayoutParams(0, -2, 1f);
        lpF.setMargins(2, 0, 2, 8);
        fl.addView(b1, lpF);
        fl.addView(b2, new LinearLayout.LayoutParams(lpF));
        fl.addView(b3, new LinearLayout.LayoutParams(lpF));
        vPrf.addView(fl);

        // Hub Details Card
        LinearLayout hubBox = new LinearLayout(this);
        hubBox.setOrientation(LinearLayout.VERTICAL);
        hubBox.setBackground(box(Color.parseColor("#181920"), 14, Color.parseColor("#38BDF8"), 1));
        hubBox.setPadding(18, 16, 18, 16);

        TextView hTitle = new TextView(this);
        hTitle.setText("🏢 NAME: MALBAZARHUB_NJP");
        hTitle.setTextColor(Color.parseColor("#38BDF8"));
        hTitle.setTextSize(14f);
        hTitle.setTypeface(Typeface.DEFAULT_BOLD);
        hubBox.addView(hTitle);

        tHubData = new TextView(this);
        tHubData.setTextColor(Color.WHITE);
        tHubData.setTextSize(13f);
        tHubData.setPadding(0, 6, 0, 0);
        hubBox.addView(tHubData);
        vPrf.addView(hubBox, new LinearLayout.LayoutParams(-1, -2));

        // Sort Button
        bSort = new Button(this);
        bSort.setText("↕️ Sort: High to Low");
        bSort.setBackground(box(Color.parseColor("#232634"), 10, 0, 0));
        bSort.setTextColor(Color.WHITE);
        bSort.setOnClickListener(v -> {
            isHighToLow = !isHighToLow;
            bSort.setText(isHighToLow ? "↕️ Sort: High to Low" : "↕️ Sort: Low to High");
            load();
        });
        LinearLayout.LayoutParams sortLp = new LinearLayout.LayoutParams(-1, -2);
        sortLp.setMargins(0, 8, 0, 8);
        vPrf.addView(bSort, sortLp);

        ScrollView sv = new ScrollView(this);
        vCrd = new LinearLayout(this);
        vCrd.setOrientation(LinearLayout.VERTICAL);
        sv.addView(vCrd);
        vPrf.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        body.addView(vPrf);

        bT.setOnClickListener(v -> {
            vTrk.setVisibility(View.VISIBLE);
            vPrf.setVisibility(View.GONE);
            bT.setBackground(box(Color.parseColor("#00E676"), 12, 0, 0));
            bT.setTextColor(Color.BLACK);
            bP.setBackground(box(Color.parseColor("#232634"), 12, 0, 0));
            bP.setTextColor(Color.parseColor("#8E92A4"));
            cnt();
        });
        bP.setOnClickListener(v -> {
            vTrk.setVisibility(View.GONE);
            vPrf.setVisibility(View.VISIBLE);
            bP.setBackground(box(Color.parseColor("#00E676"), 12, 0, 0));
            bP.setTextColor(Color.BLACK);
            bT.setBackground(box(Color.parseColor("#232634"), 12, 0, 0));
            bT.setTextColor(Color.parseColor("#8E92A4"));
            load();
        });

        root.addView(main);
        load();
        cnt();
    }

    void load() {
        try {
            vCrd.removeAllViews();
            String w = "daily".equals(mode) ? " WHERE dt = (SELECT MAX(dt) FROM prf) " : ("weekly".equals(mode) ? " WHERE dt >= date('now','localtime','-7 days') " : " WHERE dt >= date('now','localtime','-30 days') ");
            
            // Hub Totals Calculation
            Cursor hc = db.rawQuery("SELECT SUM(o), SUM(l), SUM(p), SUM(k) FROM prf " + w, null);
            if (hc != null && hc.moveToFirst()) {
                int to = hc.getInt(0), tl = hc.getInt(1), tp = hc.getInt(2), tk = hc.getInt(3);
                int tdnp = to + tp, tdnpc = tl + tk;
                double ofdConv = to > 0 ? ((double) tl / to) * 100.0 : 0.0;
                double ofpConv = tp > 0 ? ((double) tk / tp) * 100.0 : 0.0;
                double dnpConv = tdnp > 0 ? ((double) tdnpc / tdnp) * 100.0 : 0.0;

                tHubData.setText(
                    "OFD/DEL: " + to + "/" + tl + " = " + String.format(Locale.US, "%.1f%%", ofdConv) + "\n" +
                    "OFP/PIKED: " + tp + "/" + tk + " = " + String.format(Locale.US, "%.1f%%", ofpConv) + "\n" +
                    "DNP/DNPC: " + tdnp + "/" + tdnpc + " = " + String.format(Locale.US, "%.1f%%", dnpConv)
                );
            }
            if (hc != null) hc.close();

            // Agent List
            Cursor ac = db.rawQuery("SELECT n, SUM(o), SUM(l), SUM(p), SUM(k) FROM prf " + w + " GROUP BY n", null);
            ArrayList<String[]> list = new ArrayList<>();
            while (ac != null && ac.moveToNext()) {
                String name = ac.getString(0);
                int o = ac.getInt(1), l = ac.getInt(2), p = ac.getInt(3), k = ac.getInt(4);
                int dnp = o + p, dnpc = l + k;
                double r = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;
                list.add(new String[]{name, String.valueOf(o), String.valueOf(l), String.valueOf(p), String.valueOf(k), String.valueOf(dnp), String.valueOf(dnpc), String.format(Locale.US, "%.1f", r), String.valueOf(r)});
            }
            if (ac != null) ac.close();

            Collections.sort(list, (a, b) -> isHighToLow ? Double.compare(Double.parseDouble(b[8]), Double.parseDouble(a[8])) : Double.compare(Double.parseDouble(a[8]), Double.parseDouble(b[8])));

            for (String[] ag : list) {
                int strk = getStreak(ag[0]);
                double rate = Double.parseDouble(ag[8]);
                int badgeColor = (rate >= 92.0) ? Color.parseColor("#00E676") : ((rate >= 85.0) ? Color.parseColor("#FBBF24") : Color.parseColor("#EF4444"));

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(box(Color.parseColor("#181920"), 14, 0, 0));
                card.setPadding(20, 16, 20, 16);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
                clp.setMargins(0, 0, 0, 12);
                card.setLayoutParams(clp);

                TextView n = new TextView(this);
                n.setText("👤 " + ag[0] + "  |  🔥 Active: " + strk + " Days");
                n.setTextColor(badgeColor);
                n.setTypeface(Typeface.DEFAULT_BOLD);
                n.setTextSize(14f);
                card.addView(n);

                LinearLayout r1 = new LinearLayout(this);
                r1.setPadding(0, 8, 0, 4);
                r1.addView(makePill("OFD/DEL", ag[1] + "/" + ag[2], Color.parseColor("#60A5FA")), new LinearLayout.LayoutParams(0, -2, 1f));
                r1.addView(makePill("OFP/PIK", ag[3] + "/" + ag[4], Color.parseColor("#FBBF24")), new LinearLayout.LayoutParams(0, -2, 1f));
                card.addView(r1);

                LinearLayout r2 = new LinearLayout(this);
                r2.setPadding(0, 2, 0, 2);
                r2.addView(makePill("DNP/DNPC", ag[5] + "/" + ag[6], Color.WHITE), new LinearLayout.LayoutParams(0, -2, 1f));
                r2.addView(makePill("CONV", ag[7] + "%", badgeColor), new LinearLayout.LayoutParams(0, -2, 1f));
                card.addView(r2);

                final String agName = ag[0];
                card.setOnClickListener(v -> showDetails(agName));
                vCrd.addView(card);
            }
        } catch (Exception ignored) {}
    }
        int getStreak(String name) {
        int streak = 0;
        Cursor c = db.rawQuery("SELECT dt FROM prf WHERE n = ? AND (o+p) > 0 GROUP BY dt ORDER BY dt DESC", new String[]{name});
        String lastDt = null;
        while (c != null && c.moveToNext()) {
            String dt = c.getString(0);
            if (lastDt == null) {
                streak = 1;
            } else {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    Calendar cal1 = Calendar.getInstance(); cal1.setTime(sdf.parse(lastDt));
                    Calendar cal2 = Calendar.getInstance(); cal2.setTime(sdf.parse(dt));
                    cal2.add(Calendar.DAY_OF_YEAR, 1);
                    if (cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)) {
                        streak++;
                    } else {
                        break;
                    }
                } catch (Exception e) { break; }
            }
            lastDt = dt;
        }
        if (c != null) c.close();
        return Math.max(1, streak);
    }

    void showDetails(String name) {
        Cursor c = db.rawQuery("SELECT dt, o, l, p, k FROM prf WHERE n = ? AND dt >= date('now','localtime','-30 days') ORDER BY dt DESC", new String[]{name});
        LinearLayout pop = new LinearLayout(this);
        pop.setOrientation(LinearLayout.VERTICAL);
        pop.setPadding(20, 20, 20, 20);
        pop.setBackgroundColor(Color.parseColor("#0F1015"));

        TextView h = new TextView(this);
        h.setText("👤 " + name + " (Last 30 Days)");
        h.setTextColor(Color.parseColor("#00E676"));
        h.setTextSize(15f);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, 0, 0, 10);
        pop.addView(h);

        while (c != null && c.moveToNext()) {
            int o = c.getInt(1), l = c.getInt(2), p = c.getInt(3), k = c.getInt(4);
            int dnp = o + p, dnpc = l + k;
            double cr = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;

            TextView item = new TextView(this);
            item.setText("📅 " + c.getString(0) + "  |  Conv: " + String.format(Locale.US, "%.1f%%", cr) + "\nOFD: " + o + " | DEL: " + l + " | OFP: " + p + " | PIK: " + k);
            item.setTextColor(Color.WHITE);
            item.setTextSize(11.5f);
            item.setBackground(box(Color.parseColor("#181920"), 8, Color.parseColor("#2A2D3D"), 1));
            item.setPadding(14, 10, 14, 10);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, 6);
            pop.addView(item, lp);
        }
        if (c != null) c.close();

        ScrollView sv = new ScrollView(this);
        sv.addView(pop);
        new AlertDialog.Builder(this).setView(sv).setPositiveButton("Close", null).show();
    }

    Button flt(String txt, String m) {
        Button b = new Button(this);
        b.setText(txt);
        b.setBackground(box(m.equals(mode) ? Color.parseColor("#00E676") : Color.parseColor("#181920"), 10, 0, 0));
        b.setTextColor(m.equals(mode) ? Color.BLACK : Color.parseColor("#9CA3AF"));
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setOnClickListener(v -> {
            mode = m;
            b1.setBackground(box("daily".equals(m) ? Color.parseColor("#00E676") : Color.parseColor("#181920"), 10, 0, 0));
            b1.setTextColor("daily".equals(m) ? Color.BLACK : Color.parseColor("#9CA3AF"));
            b2.setBackground(box("weekly".equals(m) ? Color.parseColor("#00E676") : Color.parseColor("#181920"), 10, 0, 0));
            b2.setTextColor("weekly".equals(m) ? Color.BLACK : Color.parseColor("#9CA3AF"));
            b3.setBackground(box("monthly".equals(m) ? Color.parseColor("#00E676") : Color.parseColor("#181920"), 10, 0, 0));
            b3.setTextColor("monthly".equals(m) ? Color.BLACK : Color.parseColor("#9CA3AF"));
            load();
        });
        return b;
    }

    View makePill(String l, String v, int c) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setBackground(box(Color.parseColor("#232634"), 8, 0, 0));
        p.setPadding(10, 8, 10, 8);
        TextView t = new TextView(this);
        t.setText(l + ": " + v);
        t.setTextColor(c);
        t.setTextSize(12f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        p.addView(t);
        return p;
    }

    void cnt() {
        try {
            Cursor c = db.rawQuery("SELECT COUNT(*) FROM ord", null);
            tCnt.setText("📦 Active Search Orders: " + (c.moveToFirst() ? c.getInt(0) : 0));
            c.close();
        } catch (Exception ignored) {}
    }

    void qry(String q) {
        try {
            ords.clear();
            if (!q.isEmpty()) {
                Cursor c = db.rawQuery("SELECT DISTINCT t, d FROM ord WHERE t LIKE ? OR t LIKE ? OR d LIKE ? ORDER BY CASE WHEN t LIKE ? THEN 1 WHEN t LIKE ? THEN 2 ELSE 3 END LIMIT 30", 
                    new String[]{"%" + q, "%" + q + "%", "%" + q + "%", q, "%" + q});
                while (c.moveToNext()) ords.add(new String[]{c.getString(0), c.getString(1)});
                c.close();
            }
            adp.notifyDataSetChanged();
        } catch (Exception ignored) {}
    }

    void doSync(boolean isAuto) {
        try {
            if (!isAuto) runOnUiThread(() -> Toast.makeText(this, "Syncing live data...", Toast.LENGTH_SHORT).show());
            String curDt = getDt();
            HttpURLConnection conn = (HttpURLConnection) new URL(CSV).openConnection();
            conn.setConnectTimeout(15000);
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            db.beginTransaction();
            int count = 0;
            try {
                db.delete("prf", "dt = ?", new String[]{curDt});
                db.delete("prf", "dt < date('now', 'localtime', '-30 days')", null);

                String l;
                boolean hd = true;
                while ((l = r.readLine()) != null) {
                    if (hd) { hd = false; continue; }
                    String[] p = l.split(",", -1);
                    if (p.length < 2) continue;
                    String t = p[0].replace("\"", "").trim();
                    String o = p[1].replace("\"", "").trim();
                    String name = p.length > 2 ? p[2].replace("\"", "").trim() : "";

                    if (!t.isEmpty() && !o.isEmpty() && !t.equalsIgnoreCase("TRACKING ID")) {
                        ContentValues cv = new ContentValues();
                        cv.put("t", t);
                        cv.put("d", o);
                        db.insertWithOnConflict("ord", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                        count++;
                    }
                    if (!name.isEmpty() && !name.equalsIgnoreCase("NAME")) {
                        ContentValues cv = new ContentValues();
                        cv.put("n", name);
                        cv.put("o", p.length > 3 ? pInt(p[3]) : 0);
                        cv.put("l", p.length > 4 ? pInt(p[4]) : 0);
                        cv.put("p", p.length > 5 ? pInt(p[5]) : 0);
                        cv.put("k", p.length > 6 ? pInt(p[6]) : 0);
                        cv.put("dt", curDt);
                        db.insert("prf", null, cv);
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            final int fin = count;
            runOnUiThread(() -> {
                if (!isAuto) Toast.makeText(this, "Synced successfully!", Toast.LENGTH_SHORT).show();
                cnt();
                load();
            });
        } catch (Exception e) {
            if (!isAuto) runOnUiThread(() -> Toast.makeText(this, "Sync failed! Check internet.", Toast.LENGTH_SHORT).show());
        }
    }

    String getDt() {
        Calendar c = Calendar.getInstance();
        if (c.get(Calendar.HOUR_OF_DAY) < 9) c.add(Calendar.DAY_OF_YEAR, -1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
    }

    int pInt(String s) {
        try { return Integer.parseInt(s.replace("\"", "").trim()); } catch (Exception e) { return 0; }
    }
}
