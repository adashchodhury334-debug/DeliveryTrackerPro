package com.deliverytracker.pro;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    
    private WebView webView;
    private DatabaseHelper dbHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String GOOGLE_SHEET_CSV_URL = "https://docs.google.com/spreadsheets/d/1Dul38iNZ_eNmABVuYVWhrUg9F_xVMvaVvQvLIXlySj4/export?format=csv";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        dbHelper = new DatabaseHelper(this);
        
        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        webView.setClickable(true);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidNative");
        
        rootLayout.addView(webView);
        setContentView(rootLayout);

        webView.loadUrl("file:///android_asset/index.html");
    }

    public class WebAppInterface {

        @JavascriptInterface
        public void startSync() {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    final int count = executeSheetSync();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            webView.evaluateJavascript("onSyncFinished(" + count + ");", null);
                        }
                    });
                }
            });
        }

        private int executeSheetSync() {
            int count = 0;
            String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            SQLiteDatabase db = null;
            BufferedReader reader = null;
            try {
                URL url = new URL(GOOGLE_SHEET_CSV_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                db = dbHelper.getWritableDatabase();
                db.beginTransaction();
                
                db.delete("orders", null, null);

                HashSet<String> seenTrackingIds = new HashSet<>();
                LinkedHashMap<String, PerformanceData> agentMap = new LinkedHashMap<>();
                HashSet<String> datesInSheet = new HashSet<>();

                String line;
                boolean isHeader = true;
                while ((line = reader.readLine()) != null) {
                    if (isHeader) { isHeader = false; continue; }
                    String[] parts = line.split(",", -1);
                    if (parts.length < 3) continue;

                    String rawDate = parts[0].replace("\"", "").trim();
                    String tId = parts[1].replace("\"", "").trim();
                    String oId = parts[2].replace("\"", "").trim();
                    
                    if (rawDate.isEmpty() || rawDate.toUpperCase().contains("DATE")) {
                        rawDate = todayDate;
                    }
                    datesInSheet.add(rawDate);

                    if (!tId.isEmpty() && !seenTrackingIds.contains(tId) && !tId.toUpperCase().contains("TRACKING")) {
                        seenTrackingIds.add(tId);
                        if (!oId.isEmpty()) {
                            ContentValues cv = new ContentValues();
                            cv.put("tracking_id", tId);
                            cv.put("order_id", oId);
                            db.insert("orders", null, cv);
                            count++;
                        }
                    }

                    String name = parts.length > 3 ? parts[3].replace("\"", "").trim() : "";
                    String mobile = parts.length > 4 ? parts[4].replace("\"", "").trim() : "";

                    if (!name.isEmpty()) {
                        int ofd = parts.length > 5 ? parseSafeInt(parts[5]) : 0;
                        int del = parts.length > 6 ? parseSafeInt(parts[6]) : 0;
                        int ofp = parts.length > 7 ? parseSafeInt(parts[7]) : 0;
                        int piked = parts.length > 8 ? parseSafeInt(parts[8]) : 0;

                        String key = rawDate + "_" + name + "_" + mobile;
                        PerformanceData p = agentMap.get(key);
                        if (p == null) {
                            p = new PerformanceData(name, mobile, rawDate);
                            agentMap.put(key, p);
                        }
                        p.ofd += ofd;
                        p.del += del;
                        p.ofp += ofp;
                        p.piked += piked;
                    }
                }

                for (String d : datesInSheet) {
                    db.delete("agent_performance", "entry_date = ?", new String[]{d});
                }

                for (PerformanceData p : agentMap.values()) {
                    ContentValues cv = new ContentValues();
                    cv.put("name", p.name);
                    cv.put("mobile", p.mobile);
                    cv.put("ofd", p.ofd);
                    cv.put("del", p.del);
                    cv.put("ofp", p.ofp);
                    cv.put("piked", p.piked);
                    
                    int dnpTotal = p.ofd + p.ofp;
                    int dnpcTotal = p.del + p.piked;
                    cv.put("dnp", dnpTotal);
                    cv.put("dnpc", dnpcTotal);
                    cv.put("total_attempts", dnpTotal);
                    cv.put("total_complete", dnpcTotal);
                    cv.put("entry_date", p.date);
                    db.insert("agent_performance", null, cv);
                }

                db.setTransactionSuccessful();
                String syncTime = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(new Date());
                getSharedPreferences("SyncPrefs", MODE_PRIVATE).edit().putString("last_sync", syncTime).apply();
            } catch (Exception e) {
                e.printStackTrace();
                return -1;
            } finally {
                if (db != null) db.endTransaction();
                if (reader != null) { try { reader.close(); } catch (Exception ignored) {} }
            }
            return count;
        }

        private int parseSafeInt(String str) {
            try { return Integer.parseInt(str.replace("\"", "").trim()); } catch (Exception e) { return 0; }
        }

        @JavascriptInterface
        public String getLastSyncTime() {
            return getSharedPreferences("SyncPrefs", MODE_PRIVATE).getString("last_sync", "Never");
        }

        @JavascriptInterface
        public String getPerformanceJson(String filterMode) {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            JSONArray arr = new JSONArray();
            String cond = "";
            if ("daily".equalsIgnoreCase(filterMode)) {
                cond = " WHERE entry_date = (SELECT MAX(entry_date) FROM agent_performance) ";
            } else if ("weekly".equalsIgnoreCase(filterMode)) {
                cond = " WHERE entry_date >= date('now', 'localtime', '-7 days') ";
            } else if ("monthly".equalsIgnoreCase(filterMode)) {
                cond = " WHERE entry_date >= date('now', 'localtime', '-30 days') ";
            }

            // Low Conversion % ऊपर और High Conversion % नीचे
            String query = "SELECT name, mobile, SUM(ofd), SUM(del), SUM(ofp), SUM(piked), SUM(dnp), SUM(dnpc) " +
                    "FROM agent_performance " + cond +
                    "GROUP BY name, mobile " +
                    "ORDER BY ((SUM(dnpc) * 1.0) / CASE WHEN SUM(dnp) = 0 THEN 1 ELSE SUM(dnp) END) ASC";

            Cursor c = db.rawQuery(query, null);
            try {
                if (c.moveToFirst()) {
                    do {
                        JSONObject o = new JSONObject();
                        o.put("name", c.getString(0));
                        o.put("mobile", c.getString(1));
                        int ofd = c.getInt(2);
                        int del = c.getInt(3);
                        int ofp = c.getInt(4);
                        int piked = c.getInt(5);
                        int dnp = c.getInt(6);
                        int dnpc = c.getInt(7);

                        o.put("ofd", ofd);
                        o.put("del", del);
                        o.put("ofp", ofp);
                        o.put("piked", piked);
                        o.put("dnp", dnp);
                        o.put("dnpc", dnpc);

                        double r = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;
                        o.put("conversionRate", String.format(Locale.US, "%.1f%%", r));
                        o.put("conversionNum", r);
                        arr.put(o);
                    } while (c.moveToNext());
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                c.close();
            }
            return arr.toString();
        }

        @JavascriptInterface
        public String getAgentHistory(String name, String mobile) {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            JSONArray arr = new JSONArray();
            String query = "SELECT entry_date, ofd, del, ofp, piked, dnp, dnpc " +
                    "FROM agent_performance WHERE name = ? AND mobile = ? " +
                    "ORDER BY entry_date DESC LIMIT 30";
            Cursor c = db.rawQuery(query, new String[]{name, mobile});
            try {
                if (c.moveToFirst()) {
                    do {
                        JSONObject o = new JSONObject();
                        o.put("date", c.getString(0));
                        o.put("ofd", c.getInt(1));
                        o.put("del", c.getInt(2));
                        o.put("ofp", c.getInt(3));
                        o.put("piked", c.getInt(4));
                        int dnp = c.getInt(5);
                        int dnpc = c.getInt(6);
                        o.put("dnp", dnp);
                        o.put("dnpc", dnpc);
                        double r = dnp > 0 ? ((double) dnpc / dnp) * 100.0 : 0.0;
                        o.put("conv", String.format(Locale.US, "%.1f%%", r));
                        arr.put(o);
                    } while (c.moveToNext());
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                c.close();
            }
            return arr.toString();
        }

        @JavascriptInterface
        public int insertBulk(String jsonStr) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            int count = 0;
            try {
                db.beginTransaction();
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    ContentValues cv = new ContentValues();
                    cv.put("tracking_id", obj.getString("t"));
                    cv.put("order_id", obj.getString("o"));
                    db.insert("orders", null, cv);
                    count++;
                }
                db.setTransactionSuccessful();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                db.endTransaction();
            }
            return count;
        }

        @JavascriptInterface
        public String searchByTrackingId(String trackingQuery) {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            JSONArray arr = new JSONArray();
            Cursor cursor = db.rawQuery("SELECT id, tracking_id, order_id FROM orders WHERE tracking_id LIKE ? LIMIT 30", 
                    new String[]{"%" + trackingQuery + "%"});
            try {
                if (cursor.moveToFirst()) {
                    do {
                        JSONObject obj = new JSONObject();
                        obj.put("id", cursor.getInt(0));
                        obj.put("t", cursor.getString(1));
                        obj.put("o", cursor.getString(2));
                        arr.put(obj);
                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                cursor.close();
            }
            return arr.toString();
        }

        @JavascriptInterface public void deleteOrder(int id) {
            try { dbHelper.getWritableDatabase().delete("orders", "id = ?", new String[]{String.valueOf(id)}); } catch (Exception ignored) {}
        }
        @JavascriptInterface public void deleteAll() {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.delete("orders", null, null);
            db.delete("agent_performance", null, null);
        }
        @JavascriptInterface public int getTotalCount() {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM orders", null);
            int count = 0;
            if (cursor.moveToFirst()) count = cursor.getInt(0);
            cursor.close();
            return count;
        }
    }

    private static class PerformanceData {
        String name, mobile, date;
        int ofd = 0, del = 0, ofp = 0, piked = 0;
        PerformanceData(String n, String m, String d) { name = n; mobile = m; date = d; }
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "DeliveryTrackerPro.db";
        private static final int DATABASE_VERSION = 11;

        public DatabaseHelper(Activity context) { super(context, DATABASE_NAME, null, DATABASE_VERSION); }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE orders (id INTEGER PRIMARY KEY AUTOINCREMENT, tracking_id TEXT, order_id TEXT);");
            db.execSQL("CREATE INDEX idx_tracking_id ON orders(tracking_id);");
            db.execSQL("CREATE TABLE agent_performance (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, mobile TEXT, ofd INTEGER, del INTEGER, ofp INTEGER, piked INTEGER, dnp INTEGER, dnpc INTEGER, total_attempts INTEGER, total_complete INTEGER, entry_date TEXT);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS orders");
            db.execSQL("DROP TABLE IF EXISTS agent_performance");
            onCreate(db);
        }
    }
}
