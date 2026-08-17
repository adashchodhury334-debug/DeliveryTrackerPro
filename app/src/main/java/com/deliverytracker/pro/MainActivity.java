package com.deliverytracker.pro;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
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
        
        // Prevent keyboard from breaking layout/touch
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        
        dbHelper = new DatabaseHelper(this);
        webView = new WebView(this);
        
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidNative");
        
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    public static String getShiftCycleDate() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        // Before 9:00:59 AM count as Previous Day
        if (hour < 9 || (hour == 9 && minute == 0)) {
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
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
                            webView.evaluateJavascript("if(window.onSyncFinished) window.onSyncFinished(" + count + ");", null);
                        }
                    });
                }
            });
        }

        private int executeSheetSync() {
            int count = 0;
            String cycleDate = getShiftCycleDate();
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
                        rawDate = cycleDate;
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
            } catch (Exception e) {
                e.printStackTrace();
                return -1;
            } finally {
                if (db != null) {
                    try { db.endTransaction(); } catch (Exception ignored) {}
                }
                if (reader != null) { 
                    try { reader.close(); } catch (Exception ignored) {} 
                }
            }
            return count;
        }

        private int parseSafeInt(String str) {
            try { return Integer.parseInt(str.replace("\"", "").trim()); } catch (Exception e) { return 0; }
        }

        @JavascriptInterface
        public String getPerformanceJson(String filterMode) {
            JSONObject response = new JSONObject();
            JSONArray arr = new JSONArray();
            Cursor c = null;
            Cursor hubCursor = null;
            try {
                SQLiteDatabase db = dbHelper.getReadableDatabase();
                String cond = "";
                if ("daily".equalsIgnoreCase(filterMode)) {
                    cond = " WHERE entry_date = (SELECT MAX(entry_date) FROM agent_performance) ";
                } else if ("weekly".equalsIgnoreCase(filterMode)) {
                    cond = " WHERE entry_date >= date('now', 'localtime', '-7 days') ";
                } else if ("monthly".equalsIgnoreCase(filterMode)) {
                    cond = " WHERE entry_date >= date('now', 'localtime', '-30 days') ";
                }

                String hubQuery = "SELECT SUM(ofd), SUM(del), SUM(ofp), SUM(piked), SUM(dnp), SUM(dnpc) FROM agent_performance " + cond;
                hubCursor = db.rawQuery(hubQuery, null);
                JSONObject hubObj = new JSONObject();
                hubObj.put("hubName", "MALBAZARHUB_NJP");
                hubObj.put("target", "92.0%");

                if (hubCursor != null && hubCursor.moveToFirst()) {
                    int hOfd = hubCursor.getInt(0);
                    int hDel = hubCursor.getInt(1);
                    int hOfp = hubCursor.getInt(2);
                    int hPik = hubCursor.getInt(3);
                    int hDnp = hubCursor.getInt(4);
                    int hDnpc = hubCursor.getInt(5);

                    hubObj.put("ofd", hOfd);
                    hubObj.put("del", hDel);
                    hubObj.put("ofp", hOfp);
                    hubObj.put("piked", hPik);
                    hubObj.put("dnp", hDnp);
                    hubObj.put("dnpc", hDnpc);
                    double hRate = hDnp > 0 ? ((double) hDnpc / hDnp) * 100.0 : 0.0;
                    hubObj.put("conversionRate", String.format(Locale.US, "%.1f%%", hRate));
                    hubObj.put("conversionNum", hRate);
                } else {
                    hubObj.put("ofd", 0);
                    hubObj.put("del", 0);
                    hubObj.put("ofp", 0);
                    hubObj.put("piked", 0);
                    hubObj.put("dnp", 0);
                    hubObj.put("dnpc", 0);
                    hubObj.put("conversionRate", "0.0%");
                    hubObj.put("conversionNum", 0.0);
                }
                response.put("hub", hubObj);

                String query = "SELECT name, mobile, SUM(ofd), SUM(del), SUM(ofp), SUM(piked), SUM(dnp), SUM(dnpc) " +
                        "FROM agent_performance " + cond +
                        "GROUP BY name, mobile " +
                        "ORDER BY ((SUM(dnpc) * 1.0) / CASE WHEN SUM(dnp) = 0 THEN 1 ELSE SUM(dnp) END) ASC";

                c = db.rawQuery(query, null);
                if (c != null && c.moveToFirst()) {
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
                response.put("agents", arr);

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (hubCursor != null) { try { hubCursor.close(); } catch (Exception ignored) {} }
                if (c != null) { try { c.close(); } catch (Exception ignored) {} }
            }
            return response.toString();
        }

        @JavascriptInterface
        public String getAgentHistory(String name, String mobile) {
            JSONArray arr = new JSONArray();
            Cursor c = null;
            try {
                SQLiteDatabase db = dbHelper.getReadableDatabase();
                String query = "SELECT entry_date, ofd, del, ofp, piked, dnp, dnpc " +
                        "FROM agent_performance WHERE name = ? AND mobile = ? " +
                        "ORDER BY entry_date DESC LIMIT 30";
                c = db.rawQuery(query, new String[]{name, mobile});
                if (c != null && c.moveToFirst()) {
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
                if (c != null) { try { c.close(); } catch (Exception ignored) {} }
            }
            return arr.toString();
        }

        @JavascriptInterface
        public int insertBulk(String jsonStr) {
            SQLiteDatabase db = null;
            int count = 0;
            try {
                db = dbHelper.getWritableDatabase();
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
                if (db != null) { try { db.endTransaction(); } catch (Exception ignored) {} }
            }
            return count;
        }

        @JavascriptInterface
        public String searchByTrackingId(String trackingQuery) {
            JSONArray arr = new JSONArray();
            Cursor cursor = null;
            try {
                SQLiteDatabase db = dbHelper.getReadableDatabase();
                cursor = db.rawQuery("SELECT id, tracking_id, order_id FROM orders WHERE tracking_id LIKE ? LIMIT 30", 
                        new String[]{"%" + trackingQuery + "%"});
                if (cursor != null && cursor.moveToFirst()) {
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
                if (cursor != null) { try { cursor.close(); } catch (Exception ignored) {} }
            }
            return arr.toString();
        }

        @JavascriptInterface 
        public void deleteOrder(int id) {
            try { dbHelper.getWritableDatabase().delete("orders", "id = ?", new String[]{String.valueOf(id)}); } catch (Exception ignored) {}
        }

        @JavascriptInterface 
        public void deleteAll() {
            try {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                db.delete("orders", null, null);
                db.delete("agent_performance", null, null);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface 
        public int getTotalCount() {
            Cursor cursor = null;
            int count = 0;
            try {
                SQLiteDatabase db = dbHelper.getReadableDatabase();
                cursor = db.rawQuery("SELECT COUNT(*) FROM orders", null);
                if (cursor != null && cursor.moveToFirst()) count = cursor.getInt(0);
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) { try { cursor.close(); } catch (Exception ignored) {} }
            }
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
        private static final int DATABASE_VERSION = 15;

        public DatabaseHelper(Activity context) { super(context, DATABASE_NAME, null, DATABASE_VERSION); }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS orders (id INTEGER PRIMARY KEY AUTOINCREMENT, tracking_id TEXT, order_id TEXT);");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_tracking_id ON orders(tracking_id);");
            db.execSQL("CREATE TABLE IF NOT EXISTS agent_performance (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, mobile TEXT, ofd INTEGER, del INTEGER, ofp INTEGER, piked INTEGER, dnp INTEGER, dnpc INTEGER, total_attempts INTEGER, total_complete INTEGER, entry_date TEXT);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS orders");
            db.execSQL("DROP TABLE IF EXISTS agent_performance");
            onCreate(db);
        }
    }
}
