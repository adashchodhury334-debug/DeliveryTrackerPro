package com.deliverytracker.pro;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
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

public class MainActivity extends Activity {
    
    private WebView webView;
    private DatabaseHelper dbHelper;
    private static final String GOOGLE_SHEET_CSV_URL = "https://docs.google.com/spreadsheets/d/1Dul38iNZ_eNmABVuYVWhrUg9F_xVMvaVvQvLIXlySj4/export?format=csv";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        dbHelper = new DatabaseHelper(this);
        webView = new WebView(this);
        
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidNative");
        
        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);
    }

    public class WebAppInterface {
        @JavascriptInterface
        public int syncFromSheet() {
            int count = 0;
            SQLiteDatabase db = null;
            BufferedReader reader = null;
            String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            
            try {
                URL url = new URL(GOOGLE_SHEET_CSV_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(15000);
                
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                db = dbHelper.getWritableDatabase();
                db.beginTransaction();

                db.delete("orders", null, null);
                db.delete("agent_performance", "entry_date = ?", new String[]{todayDate});

                HashSet<String> seenTrackingIds = new HashSet<>();
                LinkedHashMap<String, PerformanceData> agentMap = new LinkedHashMap<>();

                String line;
                boolean isHeader = true;

                while ((line = reader.readLine()) != null) {
                    if (isHeader) {
                        isHeader = false;
                        continue;
                    }

                    String[] parts = line.split(",", -1);
                    if (parts.length >= 2) {
                        String trackingId = parts[0].replace("\"", "").trim();
                        String orderId = parts[1].replace("\"", "").trim();

                        if (!trackingId.isEmpty() && !seenTrackingIds.contains(trackingId)) {
                            seenTrackingIds.add(trackingId);
                            if (!trackingId.toUpperCase().contains("TRACKING") && !orderId.isEmpty()) {
                                ContentValues cv = new ContentValues();
                                cv.put("tracking_id", trackingId);
                                cv.put("order_id", orderId);
                                db.insert("orders", null, cv);
                                count++;
                            }
                        }

                        String name = (parts.length > 2) ? parts[2].replace("\"", "").trim() : "";
                        String mobile = (parts.length > 3) ? parts[3].replace("\"", "").trim() : "";

                        if (!name.isEmpty()) {
                            int ofd = (parts.length > 4 && !parts[4].trim().isEmpty()) ? parseSafeInt(parts[4]) : 0;
                            int del = (parts.length > 5 && !parts[5].trim().isEmpty()) ? parseSafeInt(parts[5]) : 0;
                            int ofp = (parts.length > 6 && !parts[6].trim().isEmpty()) ? parseSafeInt(parts[6]) : 0;
                            int ofpComp = (parts.length > 7 && !parts[7].trim().isEmpty()) ? parseSafeInt(parts[7]) : 0;

                            String key = name + "_" + mobile;
                            PerformanceData pData = agentMap.get(key);
                            if (pData == null) {
                                pData = new PerformanceData(name, mobile);
                                agentMap.put(key, pData);
                            }
                            pData.ofd += ofd;
                            pData.delivered += del;
                            pData.ofp += ofp;
                            pData.ofpComp += ofpComp;
                        }
                    }
                }

                for (PerformanceData p : agentMap.values()) {
                    ContentValues pCv = new ContentValues();
                    pCv.put("name", p.name);
                    pCv.put("mobile", p.mobile);
                    pCv.put("ofd", p.ofd);
                    pCv.put("delivered", p.delivered);
                    pCv.put("ofp", p.ofp);
                    pCv.put("ofp_comp", p.ofpComp);
                    pCv.put("total_attempts", (p.ofd + p.ofp));
                    pCv.put("total_complete", (p.delivered + p.ofpComp));
                    pCv.put("entry_date", todayDate);

                    db.insert("agent_performance", null, pCv);
                }

                db.setTransactionSuccessful();
            } catch (Exception e) {
                e.printStackTrace();
                return -1;
            } finally {
                if (db != null) {
                    db.endTransaction();
                }
                if (reader != null) {
                    try { reader.close(); } catch (Exception ignored) {}
                }
            }
            return count;
        }

        private int parseSafeInt(String str) {
            try {
                return Integer.parseInt(str.replace("\"", "").trim());
            } catch (Exception e) {
                return 0;
            }
        }

        @JavascriptInterface
        public String getPerformanceJson(String filterMode) {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            JSONArray arr = new JSONArray();
            String dateCondition = "";

            if ("daily".equalsIgnoreCase(filterMode)) {
                dateCondition = " WHERE entry_date = date('now', 'localtime') ";
            } else if ("weekly".equalsIgnoreCase(filterMode)) {
                dateCondition = " WHERE entry_date >= date('now', 'localtime', '-7 days') ";
            } else if ("monthly".equalsIgnoreCase(filterMode)) {
                dateCondition = " WHERE entry_date >= date('now', 'localtime', '-30 days') ";
            }

            String query = "SELECT name, mobile, " +
                    "SUM(ofd) as sum_ofd, " +
                    "SUM(delivered) as sum_del, " +
                    "SUM(ofp) as sum_ofp, " +
                    "SUM(ofp_comp) as sum_ofp_comp, " +
                    "SUM(total_attempts) as sum_attempts, " +
                    "SUM(total_complete) as sum_complete " +
                    "FROM agent_performance " + dateCondition +
                    "GROUP BY name, mobile " +
                    "ORDER BY sum_complete DESC";

            Cursor cursor = db.rawQuery(query, null);
            try {
                if (cursor.moveToFirst()) {
                    do {
                        JSONObject obj = new JSONObject();
                        obj.put("name", cursor.getString(0));
                        obj.put("mobile", cursor.getString(1));
                        int ofd = cursor.getInt(2);
                        int del = cursor.getInt(3);
                        int ofp = cursor.getInt(4);
                        int ofpComp = cursor.getInt(5);
                        int totalAttempts = cursor.getInt(6);
                        int totalComplete = cursor.getInt(7);

                        obj.put("ofd", ofd);
                        obj.put("delivered", del);
                        obj.put("ofp", ofp);
                        obj.put("ofpComp", ofpComp);
                        obj.put("totalOfdOfp", totalAttempts);
                        obj.put("totalComplete", totalComplete);

                        double rateNum = (totalAttempts > 0) ? ((double) totalComplete / totalAttempts) * 100.0 : 0.0;
                        obj.put("conversionRate", String.format(Locale.US, "%.1f%%", rateNum));
                        obj.put("conversionNum", rateNum);

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

        @JavascriptInterface
        public void deleteOrder(int id) {
            try {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                db.delete("orders", "id = ?", new String[]{String.valueOf(id)});
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public void deleteAll() {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.delete("orders", null, null);
            db.delete("agent_performance", null, null);
        }

        @JavascriptInterface
        public int getTotalCount() {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM orders", null);
            int count = 0;
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
            return count;
        }
    }

    private static class PerformanceData {
        String name;
        String mobile;
        int ofd = 0;
        int delivered = 0;
        int ofp = 0;
        int ofpComp = 0;

        PerformanceData(String name, String mobile) {
            this.name = name;
            this.mobile = mobile;
        }
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "DeliveryTrackerPro.db";
        private static final int DATABASE_VERSION = 2;

        public DatabaseHelper(Activity context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE orders (id INTEGER PRIMARY KEY AUTOINCREMENT, tracking_id TEXT, order_id TEXT);");
            db.execSQL("CREATE INDEX idx_tracking_id ON orders(tracking_id);");
            db.execSQL("CREATE TABLE agent_performance (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, mobile TEXT, ofd INTEGER, delivered INTEGER, ofp INTEGER, ofp_comp INTEGER, total_attempts INTEGER, total_complete INTEGER, entry_date TEXT);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS orders");
            db.execSQL("DROP TABLE IF EXISTS agent_performance");
            onCreate(db);
        }
    }
}
