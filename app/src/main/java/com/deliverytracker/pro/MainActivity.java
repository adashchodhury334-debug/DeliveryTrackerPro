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
        
        String htmlData = "<!DOCTYPE html><html lang='hi'><head><meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
            "<title>Delivery Tracker Pro</title><style>" +
            "* { box-sizing: border-box; font-family: system-ui, -apple-system, sans-serif; margin: 0; padding: 0; }" +
            "body { background-color: #121212; color: #e0e0e0; padding: 16px; min-height: 100vh; }" +
            "header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; padding-top: 10px; }" +
            "h1 { font-size: 20px; color: #00E676; }" +
            ".btn-lock { background: #333; color: #00E676; border: 1px solid #00E676; padding: 6px 12px; border-radius: 6px; font-size: 12px; font-weight: bold; cursor: pointer; }" +
            ".card { background: #1e1e1e; padding: 16px; border-radius: 12px; margin-bottom: 16px; border: 1px solid #333; }" +
            ".card-title { font-size: 15px; font-weight: bold; margin-bottom: 12px; color: #fff; display: flex; justify-content: space-between; }" +
            "textarea { width: 100%; height: 90px; padding: 12px; border-radius: 8px; border: 1px solid #444; background: #2a2a2a; color: #fff; font-size: 13px; outline: none; margin-bottom: 10px; resize: none; }" +
            "input { width: 100%; padding: 14px; border-radius: 8px; border: 1px solid #444; background: #2a2a2a; color: #fff; font-size: 15px; outline: none; margin-bottom: 10px; }" +
            ".btn-add { background: #00E676; color: #000; border: none; font-weight: bold; padding: 12px; width: 100%; border-radius: 8px; cursor: pointer; font-size: 14px; margin-bottom: 8px; }" +
            ".btn-sync { background: #0288d1; color: #fff; border: none; font-weight: bold; padding: 12px; width: 100%; border-radius: 8px; cursor: pointer; font-size: 14px; margin-bottom: 8px; }" +
            ".btn-danger { background: #c62828; color: #fff; border: none; font-weight: bold; padding: 10px; width: 100%; border-radius: 8px; cursor: pointer; font-size: 13px; margin-top: 10px; }" +
            ".order-item { background: #252525; padding: 14px; border-radius: 10px; margin-bottom: 10px; border-left: 5px solid #00E676; display: flex; justify-content: space-between; align-items: center; }" +
            ".order-info { font-size: 14px; line-height: 1.6; word-break: break-all; }" +
            ".track-id { font-size: 13px; color: #aaa; }" +
            ".order-id { font-size: 16px; font-weight: bold; color: #81c784; margin-top: 2px; }" +
            ".action-btns { display: flex; gap: 8px; flex-shrink: 0; margin-left: 10px; }" +
            ".btn-copy { background: #333; color: #fff; border: 1px solid #555; padding: 8px 12px; border-radius: 6px; font-size: 13px; cursor: pointer; }" +
            ".btn-delete { background: #c62828; color: #fff; border: none; padding: 8px 10px; border-radius: 6px; font-size: 13px; cursor: pointer; }" +
            ".no-result { text-align: center; color: #888; padding: 15px 0; font-size: 14px; }" +
            ".status-info { text-align: center; font-size: 13px; color: #00E676; margin-bottom: 8px; font-weight: bold; }" +
            "#admin-panel, #pass-box { display: none; }" +
            ".perf-card { background: #252525; border-radius: 10px; padding: 14px; margin-bottom: 12px; border-left: 5px solid #00E676; }" +
            ".perf-name { font-size: 16px; font-weight: bold; color: #00E676; margin-bottom: 2px; }" +
            ".perf-phone { font-size: 13px; color: #aaa; margin-bottom: 8px; }" +
            ".perf-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; font-size: 13px; }" +
            ".perf-item { background: #1a1a1a; padding: 6px 8px; border-radius: 6px; border: 1px solid #333; }" +
            ".perf-item span { font-weight: bold; }" +
            ".c-ofd { color: #29b6f6; }" +
            ".c-del { color: #66bb6a; }" +
            ".c-ofp { color: #ffa726; }" +
            ".c-ofpc { color: #ab47bc; }" +
            ".c-tot { color: #ffca28; }" +
            ".c-rate { color: #ff4081; font-size: 14px; }" +
            ".welcome-popup { position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); background: rgba(30, 30, 30, 0.95); color: #00E676; padding: 20px 30px; border-radius: 16px; font-weight: bold; font-size: 18px; text-align: center; border: 2px solid #00E676; box-shadow: 0 10px 30px rgba(0,0,0,0.8); opacity: 0; transition: opacity 0.4s ease; pointer-events: none; z-index: 9999; letter-spacing: 1px; }" +
            ".welcome-popup.show { opacity: 1; }" +
            "</style></head><body>" +
            "<div id='welcome-toast' class='welcome-popup'>✨ MANAGED BY ADARSH ✨</div>" +
            "<header>" +
            "<h1>Delivery Tracker Pro</h1>" +
            "<button class='btn-lock' id='lock-btn' onclick='handleAdminClick()'>🔒 Admin Login</button>" +
            "</header>" +
            "<div class='card' id='pass-box'>" +
            "<div class='card-title'>🔐 Enter Admin PIN</div>" +
            "<input type='password' id='pin-input' placeholder='Enter PIN...' />" +
            "<button class='btn-add' onclick='verifyPin()'>Login</button>" +
            "</div>" +
            "<div class='card' id='admin-panel'>" +
            "<div class='card-title'>🔄 Google Sheet Auto Sync (30k+ Capacity)</div>" +
            "<button class='btn-sync' onclick='syncGoogleSheet()'>🔄 Sync From Google Sheet</button>" +
            "<div class='card-title' style='margin-top:15px;'>📋 Manual Import (Copy/Paste)</div>" +
            "<textarea id='bulk-input' placeholder='Google Sheet से टेक्स्ट कॉपी करके पेस्ट करें...'></textarea>" +
            "<button class='btn-add' onclick='bulkImport()'>⚡ Import Manual Orders</button>" +
            "<button class='btn-danger' onclick='clearAllOrders()'>⚠️ Clear All Saved Data</button>" +
            "</div>" +
            "<div class='card'>" +
            "<div class='card-title'>🔍 Enter Tracking ID</div>" +
            "<input type='text' id='search-input' placeholder='Enter Tracking ID (e.g. last 4-5 digits)...' oninput='searchOrders()' style='margin-bottom:0;'>" +
            "</div>" +
            "<div class='card'>" +
            "<div class='card-title'>📦 Order Result</div>" +
            "<div id='status-text' class='status-info'></div>" +
            "<div id='orders-list'></div>" +
            "</div>" +
            "<div class='card'>" +
            "<div class='card-title'>📊 Agent Performance & Conversion %</div>" +
            "<div id='perf-list'><div class='no-result'>सिंक करने पर परफॉरमेंस लोड होगी...</div></div>" +
            "</div>" +
            "<script>" +
            "let isAdmin = false;" +
            "const ADMIN_PIN = '9547927698';" +
            "function showWelcomePopup() {" +
            "let toast = document.getElementById('welcome-toast');" +
            "toast.classList.add('show');" +
            "setTimeout(() => { toast.classList.remove('show'); }, 2000);" +
            "}" +
            "function updateStatus() {" +
            "let total = AndroidNative.getTotalCount();" +
            "document.getElementById('status-text').innerText = '✅ Total Active Orders: ' + total;" +
            "loadPerformance();" +
            "}" +
            "function handleAdminClick() {" +
            "if(isAdmin) {" +
            "isAdmin = false;" +
            "document.getElementById('admin-panel').style.display = 'none';" +
            "document.getElementById('lock-btn').innerText = '🔒 Admin Login';" +
            "searchOrders();" +
            "} else {" +
            "let box = document.getElementById('pass-box');" +
            "box.style.display = box.style.display === 'block' ? 'none' : 'block';" +
            "}" +
            "}" +
            "function verifyPin() {" +
            "let inputPin = document.getElementById('pin-input').value.trim();" +
            "if(inputPin === ADMIN_PIN) {" +
            "isAdmin = true;" +
            "document.getElementById('pin-input').value = '';" +
            "document.getElementById('pass-box').style.display = 'none';" +
            "document.getElementById('admin-panel').style.display = 'block';" +
            "document.getElementById('lock-btn').innerText = '🔓 Logout Admin';" +
            "searchOrders();" +
            "alert('Admin Mode Activated!');" +
            "} else { alert('Wrong PIN!'); }" +
            "}" +
            "function syncGoogleSheet() {" +
            "alert('गूगल शीट से लाइव डाटा सिंक हो रहा है...');" +
            "setTimeout(() => {" +
            "let added = AndroidNative.syncFromSheet();" +
            "if(added >= 0) {" +
            "alert('सफलतापूर्वक ' + added + ' ऑर्डर्स सिंक हो गए!');" +
            "updateStatus();" +
            "searchOrders();" +
            "} else {" +
            "alert('❌ Error! इंटरनेट कनेक्शन चेक करें।');" +
            "}" +
            "}, 100);" +
            "}" +
            "function loadPerformance() {" +
            "let json = AndroidNative.getPerformanceJson();" +
            "let list = document.getElementById('perf-list');" +
            "if(!json || json === '[]') {" +
            "list.innerHTML = '<div class=\"no-result\">कोई परफॉरमेंस डेटा उपलब्ध नहीं है</div>';" +
            "return;" +
            "}" +
            "let data = JSON.parse(json);" +
            "list.innerHTML = '';" +
            "data.forEach(item => {" +
            "let card = `<div class='perf-card'>" +
            "<div class='perf-name'>👤 ${item.name}</div>" +
            "<div class='perf-phone'>📞 ${item.mobile}</div>" +
            "<div class='perf-grid'>" +
            "<div class='perf-item'>OFD: <span class='c-ofd'>${item.ofd}</span></div>" +
            "<div class='perf-item'>Delivered: <span class='c-del'>${item.delivered}</span></div>" +
            "<div class='perf-item'>OFP: <span class='c-ofp'>${item.ofp}</span></div>" +
            "<div class='perf-item'>OFP Done: <span class='c-ofpc'>${item.ofpComp}</span></div>" +
            "<div class='perf-item'>Total: <span class='c-tot'>${item.totalOfdOfp}</span></div>" +
            "<div class='perf-item'>Conv %: <span class='c-rate'>${item.conversionRate}</span></div>" +
            "</div></div>`;" +
            "list.innerHTML += card;" +
            "});" +
            "}" +
            "function bulkImport() {" +
            "let rawText = document.getElementById('bulk-input').value.trim();" +
            "if(!rawText) { alert('पेस्ट बॉक्स खाली है!'); return; }" +
            "let lines = rawText.split(/\\r?\\n/);" +
            "let items = [];" +
            "for(let i = 0; i < lines.length; i++) {" +
            "let line = lines[i].trim();" +
            "if(!line) continue;" +
            "let parts = line.split(/[\\t,]/).map(p => p.trim());" +
            "if(parts.length >= 2 && parts[0] && parts[1]) {" +
            "if(!parts[0].toUpperCase().includes('TRACKING')) {" +
            "items.push({ t: parts[0], o: parts[1] });" +
            "}" +
            "}" +
            "}" +
            "let added = AndroidNative.insertBulk(JSON.stringify(items));" +
            "document.getElementById('bulk-input').value = '';" +
            "alert('सफलतापूर्वक ' + added + ' ऑर्डर्स मैन्युअली सेव हो गए!');" +
            "updateStatus();" +
            "searchOrders();" +
            "}" +
            "function clearAllOrders() {" +
            "AndroidNative.deleteAll();" +
            "updateStatus();" +
            "searchOrders();" +
            "alert('सारा डेटा डिलीट हो गया है!');" +
            "}" +
            "function deleteSingle(id) {" +
            "AndroidNative.deleteOrder(id);" +
            "updateStatus();" +
            "searchOrders();" +
            "}" +
            "function copyToClipboard(text) {" +
            "navigator.clipboard.writeText(text);" +
            "alert('Order ID Copied: ' + text);" +
            "}" +
            "function searchOrders() {" +
            "const list = document.getElementById('orders-list');" +
            "const search = document.getElementById('search-input').value.trim();" +
            "list.innerHTML = '';" +
            "if(search === '') {" +
            "list.innerHTML = '<div class=\"no-result\">ऑर्डर देखने के लिए Tracking ID दर्ज करें</div>';" +
            "return;" +
            "}" +
            "let results = JSON.parse(AndroidNative.searchByTrackingId(search));" +
            "if(results.length === 0) {" +
            "list.innerHTML = '<div class=\"no-result\">❌ No Order ID found for this Tracking ID</div>';" +
            "return;" +
            "}" +
            "results.forEach(item => {" +
            "const div = document.createElement('div'); div.className = 'order-item';" +
            "let delBtn = isAdmin ? `<button class='btn-delete' onclick='deleteSingle(${item.id})'>🗑️</button>` : '';" +
            "div.innerHTML = `<div class='order-info'><div class='track-id'>Tracking ID: ${item.t}</div><div class='order-id'>Order ID: ${item.o}</div></div><div class='action-btns'><button class='btn-copy' onclick='copyToClipboard(\"${item.o}\")'>Copy</button>${delBtn}</div>`;" +
            "list.appendChild(div);" +
            "});" +
            "}" +
            "updateStatus();" +
            "showWelcomePopup();" +
            "</script></body></html>";

        webView.loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null);
        setContentView(webView);
    }

    public class WebAppInterface {
        @JavascriptInterface
        public int syncFromSheet() {
            int count = 0;
            SQLiteDatabase db = null;
            BufferedReader reader = null;
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
                db.delete("agent_performance", null, null);

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

                        if (trackingId.isEmpty() || seenTrackingIds.contains(trackingId)) {
                            continue;
                        }
                        seenTrackingIds.add(trackingId);

                        if (!trackingId.toUpperCase().contains("TRACKING") && !orderId.isEmpty()) {
                            ContentValues cv = new ContentValues();
                            cv.put("tracking_id", trackingId);
                            cv.put("order_id", orderId);
                            db.insert("orders", null, cv);
                            count++;
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
                    
                    int totalAttempts = p.ofd + p.ofp;
                    int totalComplete = p.delivered + p.ofpComp;
                    pCv.put("total_attempts", totalAttempts);
                    pCv.put("total_complete", totalComplete);
                    
                    String rate = "0%";
                    if (totalAttempts > 0) {
                        double r = ((double) totalComplete / totalAttempts) * 100.0;
                        rate = String.format(Locale.US, "%.1f%%", r);
                    }
                    pCv.put("conversion_rate", rate);

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

        private int parseSafeIn
