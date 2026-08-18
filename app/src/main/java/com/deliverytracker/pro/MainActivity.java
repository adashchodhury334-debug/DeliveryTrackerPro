package com.deliverytracker.pro;

import android.app.Activity;
import android.app.AlertDialog;
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

public class MainActivity extends Activity {

    private TabLayout tabLayout;
    private LinearLayout secTracker;
    private View secPerformance;
    private LinearLayout agentsContainer;
    private EditText searchEditText;
    private ListView ordersListView;
    private TextView txtActiveCount, txtHubStats;
    private Button btnAdmin, btnDaily, btnWeekly, btnMonthly;
    
    private DatabaseHelper dbHelper;
    private String currentFilter = "daily";
    private ArrayList<OrderModel> ordersList = new ArrayList<>();
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
        txtHubStats = findViewById(R.id.txtHubStats);
        btnAdmin = findViewById(R.id.btnAdmin);
        btnDaily = findViewById(R.id.btnDaily);
        btnWeekly = findViewById(R.id.btnWeekly);
        btnMonthly = findViewById(R.id.btnMonthly);

        ordersAdapter = new OrdersAdapter(this, ordersList);
        ordersListView.setAdapter(ordersAdapter);

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

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int count2) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                executeLocalSearch(s.toString().trim());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnAdmin.setOnClickListener(v -> showAdminDialog());
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
        int minute = cal.get(Calendar.MINUTE);
        if (hour < 9 || (hour == 9 && minute == 0)) {
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
        Cursor c = db.
