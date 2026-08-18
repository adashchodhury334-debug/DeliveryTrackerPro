package com.deliverytracker.pro;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.tabs.TabLayout;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private LinearLayout secTracker, secPerformance, agentsContainer;
    private EditText searchEditText;
    private ListView ordersListView;
    private TextView txtActiveCount, txtHubName, txtHubStats;
    private Button btnAdmin, btnDaily, btnWeekly, btnMonthly;
    
    private DatabaseHelper dbHelper;
    private String currentFilter = "daily";
    private final ArrayList<OrderModel> ordersList = new ArrayList<>();
    private OrdersAdapter ordersAdapter;

    private static final String CSV_URL = "https://docs.google.com/spreadsheets/d/1Dul38iNZ_eNmABVuYVWhrUg9F_xVMvaVvQvLIXlySj4/export?format=csv";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);

        tabLayout = findViewById(R.id.tabLayout);
        secTracker = findViewById(R.id.secTracker);
        secPerformance = findViewById(R.id.secPerformance);
        agentsContainer = findViewById(R.id.agentsContainer);
        searchEditText = findViewById(R.id.searchEditText);
        ordersListView = findViewById(R.id.ordersListView);
        txtActiveCount = findViewById(R.id.txtActiveCount);
        txtHubName = findViewById(R.id.txtHubName);
        txtHubStats = findViewById(R.id.txtHubStats);
        btnAdmin = findViewById(R.id.btnAdmin);
        btnDaily = findViewById(R.id.btnDaily);
        btnWeekly = findViewById(R.id.btnWeekly);
        btnMonthly = findViewById(R.id.btnMonthly);

        ordersAdapter = new OrdersAdapter(this, ordersList);
        ordersListView.setAdapter(ordersAdapter);

        // Tab Switching
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    secTracker.setVisibility(View.VISIBLE);
                    secPerformance.setVisibility(View.GONE);
                } else {
                    secTracker.setVisibility(View.GONE);
                    secPerformance.setVisibility(View.VISIBLE);
                    loadPerformanceData();
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Search Input
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int count2) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                executeLocalSearch(s.toString().trim());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Admin Button
        btnAdmin.setOnClickListener(v -> showAdminDialog());

        // Time Filters
        btnDaily.setOnClickListener(v -> updateFilter("daily"));
        btnWeekly.setOnClickListener(v -> updateFilter("weekly"));
        btnMonthly.setOnClickListener(v -> updateFilter("monthly"));

        refreshTotalCount();
    }

    private void updateFilter(String filter) {
        currentFilter = filter;
        btnDaily.setBackgroundTintList(android.content.res.ColorStateList.valueOf(filter.equals("daily") ? 0xFF238636 : 0xFF21262d));
        btnWeekly.setBackgroundTintList(android.content.res.ColorStateList.valueOf(filter.equals("weekly") ? 0xFF238636 : 0xFF21262d));
        btnMonthly.setBackgroundTintList(android.content.res.ColorStateList.valueOf(filter.equals("monthly") ? 0xFF238636 : 0xFF21262d));
        loadPerformanceData();
    }

    public static String getShiftCycleDate() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        if (hour < 9) {
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
    }

    private void refreshTotalCount() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM orders", null);
        int count = 0;
        if (cursor.moveToFirst()) count = cursor.getInt(0);
        cursor.close();
        txtActiveCount.setText("📦 Order Results (Active: " + count + ")");
    }

    private void executeLocalSearch(String query) {
        ordersList.clear();
        if (query.isEmpty()) {
            ordersAdapter.notifyDataSetChanged();
            return;
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT tracking_id, order_id FROM orders WHERE tracking_id LIKE ? LIMIT 40", new String[]{"%" + query + "%"});
        while (c.moveToNext()) {
            ordersList.add(new OrderModel(c.getString(0), c.getString(1)));
        }
        c.close();
        ordersAdapter.notifyDataSetChanged();
    }

    private void loadPerformanceData() {
        agentsContainer.removeAllViews();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String cond = "";
        if (currentFilter.equalsIgnoreCase("daily")) {
            cond = " WHERE entry_date = (SELECT MAX(entry_date) FROM agent_performance) ";
        } else if (currentFilter.equalsIgnoreCase("weekly")) {
            cond = " WHERE entry_date >= date('now', 'localtime', '-7 days') ";
        } else if (currentFilter.equalsIgnoreCase("monthly")) {
            cond = " WHERE entry_date >= date('now', 'localtime', '-30 days') ";
        }

        Cursor hc = db.rawQuery("SELECT SUM(ofd), SUM(del), SUM(ofp), SUM(piked) FROM agent_performance" + cond, null);
        if (hc.moveToFirst()) {
            int tofd = hc.getInt(0); int tdel = hc.getInt(1);
            int tofp = hc.getInt(2); int tpik = hc.getInt(3);
            int tdnp = tofd + tofp; int tdnpc = tdel + tpik;
            double rate = tdnp > 0 ? ((double) tdnpc / tdnp) * 100.0 : 0.0;

            txtHubStats.setText(String.format(Locale.US, 
                "Total OFD: %d  |  Total DEL: %d\nTotal OFP: %d  |  Total PIKED: %d\nTotal DNP: %d  |  Total DNPC: %d\nConversion: %.1f%%", 
                tofd, tdel, tofp, tpik, tdnp, tdnpc, rate));
        }
        hc.close();

        Cursor ac = db.rawQuery("SELECT name, mobile, SUM(ofd), SUM(del), SUM(ofp), SUM(piked) FROM agent_performance" + cond + "GROUP BY name, mobile", null);
        ArrayList<AgentModel> list = new ArrayList<>();
        while (ac.moveToNext()) {
            String name = ac.getString(0); String mob = ac.getString(1);
            int ofd = ac.getInt(2); int del = ac.getInt(3);
            int ofp = ac.getInt(4); int pik = ac.getInt(5);
            list.add(new AgentModel(name, mob, ofd, del, ofp, pik));
        }
        ac.close();

        Collections.sort(list, (a, b) -> Double.compare(a.getRate(), b.getRate()));

        for (AgentModel agent : list) {
            View view = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_2, null);
            TextView text1 = view.findViewById(android.R.id.text1);
            TextView text2 = view.findViewById(android.R.id.text2);

            text1.setText("👤 " + agent.name + " (" + agent.mobile + ")");
            text1.setTextColor(0xFFFFFFFF);
            text2.setText(String.format(Locale.US, "OFD: %d | DEL: %d | OFP: %d | PIK: %d | DNP: %d | DNPC: %d | Conv: %.1f%%",
                    agent.ofd, agent.del, agent.ofp, agent.piked, agent.dnp, agent.dnpc, agent.getRate()));
            text2.setTextColor(0xFF8b949e);
            
            view.setPadding(0, 10, 0, 10);
            agentsContainer.addView(view);
        }
    }

    private void showAdminDialog() {
        final EditText input = new EditText(this);
        input.setHint("Enter Admin PIN...");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        
        new AlertDialog.Builder(this)
            .setTitle("🔐 Admin Login")
            .setView(input)
            .setPositiveButton("Verify", (dialog, which) -> {
                if(input.getText().toString().trim().equals("9547927698")) {
                    openSyncAction();
                } else {
                    Toast.makeText(MainActivity.this, "Wrong PIN!", Toast.LENGTH_SHORT).show();
                }
            }).show();
    }

    private void openSyncAction() {
        new AlertDialog.Builder(this)
            .setTitle("🔄 Control Panel")
            .setMessage("Sync data live from Google Sheet or wipe local database?")
            .setPositiveButton("🔄 Live Sync Now", (dialog, which) -> new SyncTask().execute())
            .setNegativeButton("⚠️ Wipe All Data", (dialog, which) -> {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                db.delete("orders", null, null);
                db.delete("agent_performance", null, null);
                refreshTotalCount();
                Toast.makeText(MainActivity.this, "Database Cleared!", Toast.LENGTH_SHORT).show();
            }).show();
    }

    @SuppressLint("StaticFieldLeak")
    private class SyncTask extends AsyncTask<Void, Void, Integer> {
        @Override protected void onPreExecute() { Toast.makeText(MainActivity.this, "Connecting to sheet...", Toast.LENGTH_SHORT).show(); }
        @Override
        protected Integer doInBackground(Void... voids) {
            int count = 0;
            String cycleDate = getShiftCycleDate();
            try {
                URL url = new URL(CSV_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                db.beginTransaction();
                
                db.delete("orders", null, null);
                db.delete("agent_performance", "entry_date = ?", new String[]{cycleDate});

                String line; boolean isHeader = true;
                while ((line = r.readLine()) != null) {
                    if (isHeader) { isHeader = false; continue; }
                    String[] p = line.split(",", -1);
                    if (p.length < 2) continue;

                    String oId = p[0].replace("\"", "").trim();
                    String tId = p[1].replace("\"", "").trim();
                    String name = p.length > 2 ? p[2].replace("\"", "").trim() : "";
                    String mob = p.length > 3 ? p[3].replace("\"", "").trim() : "";

                    if (!tId.isEmpty()) {
                        ContentValues cv = new ContentValues();
                        cv.put("tracking_id", tId);
                        cv.put("order_id", oId);
                        db.insert("orders", null, cv);
                        count++;
                    }

                    if (!name.isEmpty()) {
                        ContentValues cv = new ContentValues();
                        cv.put("name", name); cv.put("mobile", mob);
                        cv.put("ofd", p.length > 4 ? parseZero(p[4]) : 0);
                        cv.put("del", p.length > 5 ? parseZero(p[5]) : 0);
                        cv.put("ofp", p.length > 6 ? parseZero(p[6]) : 0);
                        cv.put("piked", p.length > 7 ? parseZero(p[7]) : 0);
                        cv.put("entry_date", cycleDate);
                        db.insert("agent_performance", null, cv);
                    }
                }
                db.setTransactionSuccessful();
                db.endTransaction();
            } catch(Exception e) { e.printStackTrace(); return -1; }
            return count;
        }
        @Override protected void onPostExecute(Integer res) {
            if(res >= 0) {
                Toast.makeText(MainActivity.this, "Synced successfully! Count: " + res, Toast.LENGTH_LONG).show();
                refreshTotalCount();
            } else {
                Toast.makeText(MainActivity.this, "❌ Sync failed! Check Internet Connection.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private int parseZero(String s) { try{ return Integer.parseInt(s.replace("\"", "").trim()); }catch(Exception e){return 0;} }

    private static class OrderModel { 
        String t, o; 
        OrderModel(String t, String o){ this.t=t; this.o=o; } 
    }

    private static class AgentModel {
        String name, mobile; int ofd, del, ofp, piked, dnp, dnpc;
        AgentModel(String n, String m, int o, int d, int op, int p) {
            name=n; mobile=m; ofd=o; del=d; ofp=op; piked=p;
            dnp = ofd + ofp; dnpc = del + piked;
        }
        double getRate() { return dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0; }
    }

    private static class OrdersAdapter extends BaseAdapter {
        Context ctx; ArrayList<OrderModel> items;
        OrdersAdapter(Context c, ArrayList<OrderModel> i){ ctx=c; items=i; }
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override
        public View getView(int i, View v, ViewGroup p) {
            if (v == null) v = LayoutInflater.from(ctx).inflate(android.R.layout.simple_list_item_2, null);
            TextView t1 = v.findViewById(android.R.id.text1);
            TextView t2 = v.findViewById(android.R.id.text2);
            OrderModel o = items.get(i);
            t1.setText("Track ID: " + o.t); t1.setTextColor(0xFF8b949e);
            t2.setText("Order ID: " + o.o + " [Tap to Copy]"); t2.setTextColor(0xFF00E676);
            v.setOnClickListener(view -> {
                ClipboardManager cb = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Order ID", o.o);
                cb.setPrimaryClip(clip);
                Toast.makeText(ctx, "Copied Order ID: " + o.o, Toast.LENGTH_SHORT).show();
            });
            return v;
        }
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context c) { super(c, "TrackerProNative.db", null, 1); }
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE orders (id INTEGER PRIMARY KEY AUTOINCREMENT, tracking_id TEXT, order_id TEXT);");
            db.execSQL("CREATE TABLE agent_performance (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, mobile TEXT, ofd INTEGER, del INTEGER, ofp INTEGER, piked INTEGER, entry_date TEXT);");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int old, int newV) {}
    }
            }
