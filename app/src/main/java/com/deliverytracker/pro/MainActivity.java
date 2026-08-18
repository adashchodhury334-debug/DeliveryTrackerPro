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
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

    private DatabaseHelper dbHelper;
    private ArrayList<OrderModel> ordersList;
    private OrdersAdapter ordersAdapter;

    private LinearLayout secTracker;
    private ScrollView scrollPerf;
    private LinearLayout secPerformance;
    private LinearLayout agentsContainer;
    private Button btnTabTracker;
    private Button btnTabPerf;
    private TextView txtActiveCount;
    private TextView txtHubStats;
    private EditText searchInput;
    private Button btnDaily;
    private Button btnWeekly;
    private Button btnMonthly;
    private String currentFilter = "daily";

    private static final String CSV_URL = "https://docs.google.com/spreadsheets/d/1Dul38iNZ_eNmABVuYVWhrUg9F_xVMvaVvQvLIXlySj4/export?format=csv";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            ordersList = new ArrayList<OrderModel>();
            dbHelper = new DatabaseHelper(this);
            buildDynamicUI();
            refreshTotalCount();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Init Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void buildDynamicUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0d1117"));

        // Header Top Bar
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(Color.parseColor("#161b22"));
        header.setPadding(24, 20, 24, 20);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("⚡ Delivery Tracker Pro");
        title.setTextColor(Color.parseColor("#00E676"));
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        header.addView(title, lpTitle);

        Button btnAdmin = new Button(this);
        btnAdmin.setText("🔒 Admin");
        btnAdmin.setTextColor(Color.parseColor("#00E676"));
        btnAdmin.setBackgroundColor(Color.parseColor("#21262d"));
        btnAdmin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAdminDialog();
            }
        });
        header.addView(btnAdmin);
        root.addView(header);

        // Tab Navigation
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackgroundColor(Color.parseColor("#161b22"));
        tabs.setPadding(12, 10, 12, 10);

        btnTabTracker = new Button(this);
        btnTabTracker.setText("🔍 Tracker");
        btnTabTracker.setTextColor(Color.BLACK);
        btnTabTracker.setBackgroundColor(Color.parseColor("#00E676"));
        btnTabTracker.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lpTab1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lpTab1.setMargins(6, 0, 6, 0);

        btnTabPerf = new Button(this);
        btnTabPerf.setText("📊 Performance");
        btnTabPerf.setTextColor(Color.parseColor("#8b949e"));
        btnTabPerf.setBackgroundColor(Color.parseColor("#21262d"));
        btnTabPerf.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lpTab2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lpTab2.setMargins(6, 0, 6, 0);

        tabs.addView(btnTabTracker, lpTab1);
        tabs.addView(btnTabPerf, lpTab2);
        root.addView(tabs);

        // Body Frame
        FrameLayout container = new FrameLayout(this);
        container.setPadding(20, 20, 20, 20);
        root.addView(container, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 1. Tracker Screen
        secTracker = new LinearLayout(this);
        secTracker.setOrientation(LinearLayout.VERTICAL);
        secTracker.setVisibility(View.VISIBLE);

        searchInput = new EditText(this);
        searchInput.setHint("Enter Tracking ID (FMPC...)...");
        searchInput.setHintTextColor(Color.parseColor("#8b949e"));
        searchInput.setTextColor(Color.WHITE);
        searchInput.setBackgroundColor(Color.parseColor("#161b22"));
        searchInput.setPadding(24, 24, 24, 24);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                executeSearch(s.toString().trim());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        secTracker.addView(searchInput);

        txtActiveCount = new TextView(this);
        txtActiveCount.setText("📦 Orders List (Active: 0)");
        txtActiveCount.setTextColor(Color.parseColor("#8b949e"));
        txtActiveCount.setPadding(0, 20, 0, 16);
        txtActiveCount.setTypeface(Typeface.DEFAULT_BOLD);
        secTracker.addView(txtActiveCount);

        ListView listView = new ListView(this);
        ordersAdapter = new OrdersAdapter(this, ordersList);
        listView.setAdapter(ordersAdapter);
        listView.setDividerHeight(1);
        secTracker.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        container.addView(secTracker);

        // 2. Performance Screen
        scrollPerf = new ScrollView(this);
        secPerformance = new LinearLayout(this);
        secPerformance.setOrientation(LinearLayout.VERTICAL);
        scrollPerf.addView(secPerformance);
        scrollPerf.setVisibility(View.GONE);
        container.addView(scrollPerf);

        // Date Filter Buttons
        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setPadding(0, 0, 0, 16);

        btnDaily = new Button(this);
        btnDaily.setText("📅 Daily");
        btnDaily.setTextColor(Color.WHITE);
        btnDaily.setBackgroundColor(Color.parseColor("#238636"));
        btnDaily.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setFilter("daily"); }
        });

        btnWeekly = new Button(this);
        btnWeekly.setText("📆 Weekly");
        btnWeekly.setTextColor(Color.parseColor("#8b949e"));
        btnWeekly.setBackgroundColor(Color.parseColor("#21262d"));
        btnWeekly.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setFilter("weekly"); }
        });

        btnMonthly = new Button(this);
        btnMonthly.setText("🗓️ Monthly");
        btnMonthly.setTextColor(Color.parseColor("#8b949e"));
        btnMonthly.setBackgroundColor(Color.parseColor("#21262d"));
        btnMonthly.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setFilter("monthly"); }
        });

        LinearLayout.LayoutParams lpF1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lpF1.setMargins(4, 0, 4, 0);
        LinearLayout.LayoutParams lpF2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lpF2.setMargins(4, 0, 4, 0);
        LinearLayout.LayoutParams lpF3 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lpF3.setMargins(4, 0, 4, 0);

        filters.addView(btnDaily, lpF1);
        filters.addView(btnWeekly, lpF2);
        filters.addView(btnMonthly, lpF3);
        secPerformance.addView(filters);

        // Hub Performance Box
        LinearLayout hubBox = new LinearLayout(this);
        hubBox.setOrientation(LinearLayout.VERTICAL);
        hubBox.setBackgroundColor(Color.parseColor("#1c2331"));
        hubBox.setPadding(24, 24, 24, 24);

        TextView hubTitle = new TextView(this);
        hubTitle.setText("🏢 MALBAZARHUB_NJP  |  🎯 Target: 92.0%");
        hubTitle.setTextColor(Color.parseColor("#00E676"));
        hubTitle.setTypeface(Typeface.DEFAULT_BOLD);
        hubTitle.setTextSize(15f);
        hubBox.addView(hubTitle);

        txtHubStats = new TextView(this);
        txtHubStats.setTextColor(Color.WHITE);
        txtHubStats.setTextSize(13f);
        txtHubStats.setPadding(0, 12, 0, 0);
        hubBox.addView(txtHubStats);
        secPerformance.addView(hubBox);

        TextView agentTitle = new TextView(this);
        agentTitle.setText("👥 Delivery Agents Report (Low to High)");
        agentTitle.setTextColor(Color.parseColor("#8b949e"));
        agentTitle.setPadding(0, 24, 0, 12);
        agentTitle.setTypeface(Typeface.DEFAULT_BOLD);
        secPerformance.addView(agentTitle);

        agentsContainer = new LinearLayout(this);
        agentsContainer.setOrientation(LinearLayout.VERTICAL);
        secPerformance.addView(agentsContainer);

        // Tab Switching Handlers
        btnTabTracker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                secTracker.setVisibility(View.VISIBLE);
                scrollPerf.setVisibility(View.GONE);
                btnTabTracker.setBackgroundColor(Color.parseColor("#00E676"));
                btnTabTracker.setTextColor(Color.BLACK);
                btnTabPerf.setBackgroundColor(Color.parseColor("#21262d"));
                btnTabPerf.setTextColor(Color.parseColor("#8b949e"));
            }
        });

        btnTabPerf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                secTracker.setVisibility(View.GONE);
                scrollPerf.setVisibility(View.VISIBLE);
                btnTabPerf.setBackgroundColor(Color.parseColor("#00E676"));
                btnTabPerf.setTextColor(Color.BLACK);
                btnTabTracker.setBackgroundColor(Color.parseColor("#21262d"));
                btnTabTracker.setTextColor(Color.parseColor("#8b949e"));
                loadPerformance();
            }
        });

        setContentView(root);
    }

    private void setFilter(String mode) {
        currentFilter = mode;
        btnDaily.setBackgroundColor(mode.equals("daily") ? Color.parseColor("#238636") : Color.parseColor("#21262d"));
        btnDaily.setTextColor(mode.equals("daily") ? Color.WHITE : Color.parseColor("#8b949e"));
        btnWeekly.setBackgroundColor(mode.equals("weekly") ? Color.parseColor("#238636") : Color.parseColor("#21262d"));
        btnWeekly.setTextColor(mode.equals("weekly") ? Color.WHITE : Color.parseColor("#8b949e"));
        btnMonthly.setBackgroundColor(mode.equals("monthly") ? Color.parseColor("#238636") : Color.parseColor("#21262d"));
        btnMonthly.setTextColor(mode.equals("monthly") ? Color.WHITE : Color.parseColor("#8b949e"));
        loadPerformance();
    }

    public static String getShiftCycleDate() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        if (hour < 9) cal.add(Calendar.DAY_OF_YEAR, -1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
    }

    private void refreshTotalCount() {
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor c = db.rawQuery("SELECT COUNT(*) FROM orders", null);
            int count = 0;
            if (c.moveToFirst()) count = c.getInt(0);
            c.close();
            txtActiveCount.setText("📦 Orders List (Active: " + count + ")");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void executeSearch(String q) {
        ordersList.clear();
        if (q.isEmpty()) {
            ordersAdapter.notifyDataSetChanged();
            return;
        }
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor c = db.rawQuery("SELECT tracking_id, order_id FROM orders WHERE tracking_id LIKE ? LIMIT 40", new String[]{"%" + q + "%"});
            while (c.moveToNext()) {
                ordersList.add(new OrderModel(c.getString(0), c.getString(1)));
            }
            c.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        ordersAdapter.notifyDataSetChanged();
    }

    private void loadPerformance() {
        agentsContainer.removeAllViews();
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            String cond = "";
            if ("daily".equalsIgnoreCase(currentFilter)) {
                cond = " WHERE entry_date = (SELECT MAX(entry_date) FROM agent_performance) ";
            } else if ("weekly".equalsIgnoreCase(currentFilter)) {
                cond = " WHERE entry_date >= date('now', 'localtime', '-7 days') ";
            } else if ("monthly".equalsIgnoreCase(currentFilter)) {
                cond = " WHERE entry_date >= date('now', 'localtime', '-30 days') ";
            }

            Cursor hc = db.rawQuery("SELECT SUM(ofd), SUM(del), SUM(ofp), SUM(piked) FROM agent_performance" + cond, null);
            if (hc.moveToFirst()) {
                int tofd = hc.getInt(0);
                int tdel = hc.getInt(1);
                int tofp = hc.getInt(2);
                int tpik = hc.getInt(3);
                int tdnp = tofd + tofp;
                int tdnpc = tdel + tpik;
                double r = tdnp > 0 ? ((double) tdnpc / tdnp) * 100.0 : 0.0;
                txtHubStats.setText("OFD: " + tofd + " | DEL: " + tdel + " | OFP: " + tofp + " | PIKED: " + tpik + "\nDNP: " + tdnp + " | DNPC: " + tdnpc + " | Actual Conv: " + String.format(Locale.US, "%.1f%%", r));
            } else {
                txtHubStats.setText("No data synced yet. Tap Admin -> Live Sync.");
            }
            hc.close();

            Cursor ac = db.rawQuery("SELECT name, mobile, SUM(ofd), SUM(del), SUM(ofp), SUM(piked) FROM agent_performance " + cond + " GROUP BY name, mobile", null);
            ArrayList<AgentModel> list = new ArrayList<AgentModel>();
            while (ac.moveToNext()) {
                list.add(new AgentModel(ac.getString(0), ac.getString(1), ac.getInt(2), ac.getInt(3), ac.getInt(4), ac.getInt(5)));
            }
            ac.close();

            Collections.sort(list, new Comparator<AgentModel>() {
                @Override
                public int compare(AgentModel a, AgentModel b) {
                    return Double.compare(a.getRate(), b.getRate());
                }
            });

            for (AgentModel agent : list) {
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundColor(Color.parseColor("#161b22"));
                card.setPadding(20, 16, 20, 16);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, 12);
                card.setLayoutParams(lp);

                TextView name = new TextView(this);
                name.setText("👤 " + agent.name + " (" + agent.mobile + ")");
                name.setTextColor(Color.parseColor("#00E676"));
                name.setTypeface(Typeface.DEFAULT_BOLD);
                name.setTextSize(14f);
                card.addView(name);

                TextView stats = new TextView(this);
                stats.setText(String.format(Locale.US, "OFD: %d | DEL: %d | OFP: %d | PIK: %d\nDNP: %d | DNPC: %d | Conv: %.1f%%", 
                        agent.ofd, agent.del, agent.ofp, agent.piked, agent.dnp, agent.dnpc, agent.getRate()));
                stats.setTextColor(Color.WHITE);
                stats.setTextSize(12f);
                stats.setPadding(0, 6, 0, 0);
                card.addView(stats);

                agentsContainer.addView(card);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAdminDialog() {
        final EditText input = new EditText(this);
        input.setHint("Enter Admin PIN...");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔐 Admin Login");
        builder.setView(input);
        builder.setPositiveButton("Verify", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if ("9547927698".equals(input.getText().toString().trim())) {
                    openSyncOptions();
                } else {
                    Toast.makeText(MainActivity.this, "Wrong PIN!", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.show();
    }

    private void openSyncOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚡ Google Sheet Sync");
        builder.setMessage("Sync Google Sheet data live?");
        builder.setPositiveButton("Sync Now", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                new SyncTask().execute();
            }
        });
        builder.setNegativeButton("Clear All Data", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    SQLiteDatabase db = dbHelper.getWritableDatabase();
                    db.delete("orders", null, null);
                    db.delete("agent_performance", null, null);
                    refreshTotalCount();
                    executeSearch("");
                    Toast.makeText(MainActivity.this, "All Data Cleared!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        builder.show();
    }

    private class SyncTask extends AsyncTask<Void, Void, Integer> {
        @Override
        protected void onPreExecute() {
            Toast.makeText(MainActivity.this, "Syncing Google Sheet...", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected Integer doInBackground(Void... voids) {
            int count = 0;
            String cycleDate = getShiftC
