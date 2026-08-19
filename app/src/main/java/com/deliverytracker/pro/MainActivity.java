package com.deliverytracker.pro;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private View layoutOrderId, layoutPerformance, layoutHubVsHub;
    private RecyclerView rvAgentPerformance, rvHubVsHub;
    private TextView tvHubHeaderName, tvHubOfdDel, tvHubOfpPik, tvHubDnpDnpc;
    private TextView tvTopConversion, tvTopDnpc;

    // Google Sheet CSV Export Link
    private final String CSV_URL = "https://docs.google.com/spreadsheets/d/1SxsB-1srlfIv3AN5H2ZbMJDEyteJ6LIDTV4EI7rbxjw/export?format=csv&gid=0";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Layouts
        layoutOrderId = findViewById(R.id.layoutOrderIdTab);
        layoutPerformance = findViewById(R.id.layoutPerformanceTab);
        layoutHubVsHub = findViewById(R.id.layoutHubVsHubTab);

        // Views
        tvHubHeaderName = findViewById(R.id.tvHubHeaderName);
        tvHubOfdDel = findViewById(R.id.tvHubOfdDel);
        tvHubOfpPik = findViewById(R.id.tvHubOfpPik);
        tvHubDnpDnpc = findViewById(R.id.tvHubDnpDnpc);
        tvTopConversion = findViewById(R.id.tvTopConversion);
        tvTopDnpc = findViewById(R.id.tvTopDnpc);

        // Recycler Views
        rvAgentPerformance = findViewById(R.id.rvAgentPerformance);
        rvAgentPerformance.setLayoutManager(new LinearLayoutManager(this));

        rvHubVsHub = findViewById(R.id.rvHubVsHub);
        rvHubVsHub.setLayoutManager(new LinearLayoutManager(this));

        // Bottom Navigation Tabs
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_order_id) {
                showTab(layoutOrderId);
            } else if (id == R.id.nav_performance) {
                showTab(layoutPerformance);
            } else if (id == R.id.nav_hub_vs_hub) {
                showTab(layoutHubVsHub);
            }
            return true;
        });

        // Load Live Data from Sheet
        fetchSheetData();
    }

    private void showTab(View tabToShow) {
        layoutOrderId.setVisibility(View.GONE);
        layoutPerformance.setVisibility(View.GONE);
        layoutHubVsHub.setVisibility(View.GONE);
        tabToShow.setVisibility(View.VISIBLE);
    }

    private void fetchSheetData() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<String[]> rows = new ArrayList<>();
            try {
                URL url = new URL(CSV_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    rows.add(line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"));
                }
                reader.close();

                new Handler(Looper.getMainLooper()).post(() -> parseAndDisplay(rows));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void parseAndDisplay(List<String[]> rows) {
        List<AgentModel> agentList = new ArrayList<>();
        List<HubVsHubModel> hubVsHubList = new ArrayList<>();

        int totalHubOfd = 0, totalHubDel = 0, totalHubOfp = 0, totalHubPiked = 0;
        String topConvAgent = "---", topDnpcAgent = "---";
        double maxConv = -1;
        int maxDnpc = -1;

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);

            // 1. AGENT PERFORMANCE DATA (Cols B to G)
            if (row.length >= 6) {
                String agentName = clean(row[1]);
                if (!agentName.isEmpty() && !agentName.equalsIgnoreCase("WM Name") && !agentName.equalsIgnoreCase("Total") && !agentName.contains("Total")) {
                    int ofd = parseInt(row[2]);
                    int del = parseInt(row[3]);
                    int ofp = parseInt(row[4]);
                    int pik = parseInt(row[5]);

                    totalHubOfd += ofd;
                    totalHubDel += del;
                    totalHubOfp += ofp;
                    totalHubPiked += pik;

                    AgentModel agent = new AgentModel(agentName, ofd, del, ofp, pik, "");
                    agentList.add(agent);

                    if (agent.getTotalCon() > maxConv) {
                        maxConv = agent.getTotalCon();
                        topConvAgent = agentName + " (" + String.format("%.1f", maxConv) + "%)";
                    }
                    if (agent.getDnpc() > maxDnpc) {
                        maxDnpc = agent.getDnpc();
                        topDnpcAgent = agentName + " (" + maxDnpc + ")";
                    }
                }
            }

            // 2. HUB VS HUB DATA (Cols H to P)
            if (row.length >= 17) {
                String hubName = clean(row[8]);
                if (!hubName.isEmpty() && !hubName.equalsIgnoreCase("HUB NAME")) {
                    String ofd = clean(row[9]);
                    String del = clean(row[10]);
                    String ofdCon = clean(row[11]);
                    String ofp = clean(row[12]);
                    String pik = clean(row[13]);
                    String pikCon = clean(row[14]);
                    String totCon = clean(row[16]);

                    int dnp = parseInt(ofd) + parseInt(ofp);
                    int dnpc = parseInt(del) + parseInt(pik);

                    hubVsHubList.add(new HubVsHubModel(hubName, ofd, del, ofdCon, ofp, pik, pikCon, String.valueOf(dnp), String.valueOf(dnpc), totCon));
                }
            }
        }

        // Hub Header Stats
        double hubOfdCon = (totalHubOfd > 0) ? ((double) totalHubDel / totalHubOfd) * 100 : 0;
        double hubOfpCon = (totalHubOfp > 0) ? ((double) totalHubPiked / totalHubOfp) * 100 : 0;
        int totalDnp = totalHubOfd + totalHubOfp;
        int totalDnpc = totalHubDel + totalHubPiked;
        double hubTotalCon = (totalDnp > 0) ? ((double) totalDnpc / totalDnp) * 100 : 0;

        tvHubOfdDel.setText("OFD/DEL = " + totalHubOfd + "/" + totalHubDel + " = " + String.format("%.1f", hubOfdCon) + "%");
        tvHubOfpPik.setText("OFP/PIKED = " + totalHubOfp + "/" + totalHubPiked + " = " + String.format("%.1f", hubOfpCon) + "%");
        tvHubDnpDnpc.setText("DNP/DNPC = " + totalDnp + "/" + totalDnpc + " = " + String.format("%.1f", hubTotalCon) + "%");

        tvTopConversion.setText("🔥 TOP CONVERSION\n" + topConvAgent);
        tvTopDnpc.setText("📦 TOP DNPC\n" + topDnpcAgent);

        // Bind Adapters
        rvAgentPerformance.setAdapter(new AgentAdapter(agentList));
        rvHubVsHub.setAdapter(new HubVsHubAdapter(hubVsHubList));
    }

    private String clean(String val) {
        if (val == null) return "";
        return val.replace("\"", "").trim();
    }

    private int parseInt(String val) {
        try {
            return Integer.parseInt(clean(val).replace("%", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    // ================= DATA MODELS & ADAPTERS =================

    public static class AgentModel {
        private final String name;
        private final int ofd, del, ofp, piked;
        private final String phone;

        public AgentModel(String name, int ofd, int del, int ofp, int piked, String phone) {
            this.name = name;
            this.ofd = ofd;
            this.del = del;
            this.ofp = ofp;
            this.piked = piked;
            this.phone = phone;
        }

        public String getName() { return name; }
        public String getOfdDelCon() {
            double con = (ofd > 0) ? ((double) del / ofd) * 100 : 0;
            return ofd + "/" + del + " = " + String.format("%.1f", con) + "%";
        }
        public String getOfpPikCon() {
            double con = (ofp > 0) ? ((double) piked / ofp) * 100 : 0;
            return ofp + "/" + piked + " = " + String.format("%.1f", con) + "%";
        }
        public String getDnpDnpcCon() {
            int dnp = ofd + ofp;
            int dnpc = del + piked;
            double con = (dnp > 0) ? ((double) dnpc / dnp) * 100 : 0;
            return dnp + "/" + dnpc + " = " + String.format("%.1f", con) + "%";
        }
        public double getTotalCon() {
            int dnp = ofd + ofp;
            int dnpc = del + piked;
            return (dnp > 0) ? ((double) dnpc / dnp) * 100 : 0;
        }
        public int getDnpc() { return del + piked; }
        public String getPhone() { return phone; }
    }

    public static class HubVsHubModel {
        private final String hubName, ofd, del, ofdCon, ofp, piked, pikCon, dnp, dnpc, totalCon;

        public HubVsHubModel(String hubName, String ofd, String del, String ofdCon, String ofp, String piked, String pikCon, String dnp, String dnpc, String totalCon) {
            this.hubName = hubName;
            this.ofd = ofd;
            this.del = del;
            this.ofdCon = ofdCon;
            this.ofp = ofp;
            this.piked = piked;
            this.pikCon = pikCon;
            this.dnp = dnp;
            this.dnpc = dnpc;
            this.totalCon = totalCon;
        }

        public String getHubName() { return hubName; }
        public String getOfdDelCon() { return ofd + "/" + del + " = " + ofdCon; }
        public String getOfpPikCon() { return ofp + "/" + piked + " = " + pikCon; }
        public String getDnpDnpcCon() { return dnp + "/" + dnpc + " = " + totalCon; }
    }

    public static class AgentAdapter extends RecyclerView.Adapter<AgentAdapter.ViewHolder> {
        private final List<AgentModel> list;
        public AgentAdapter(List<AgentModel> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_agent_performance, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AgentModel item = list.get(position);
            holder.tvName.setText(item.getName());
            holder.tvOfdDel.setText("OFD/DEL = " + item.getOfdDelCon());
            holder.tvOfpPik.setText("OFP/PIKED = " + item.getOfpPikCon());
            holder.tvDnpDnpc.setText("DNP/DNPC = " + item.getDnpDnpcCon());
            holder.tvContact.setText(item.getPhone());
        }

        @Override
        public int getItemCount() { return list.size(); }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvOfdDel, tvOfpPik, tvDnpDnpc, tvContact;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvAgentName);
                tvOfdDel = itemView.findViewById(R.id.tvAgentOfdDel);
                tvOfpPik = itemView.findViewById(R.id.tvAgentOfpPik);
                tvDnpDnpc = itemView.findViewById(R.id.tvAgentDnpDnpc);
                tvContact = itemView.findViewById(R.id.tvAgentContact);
            }
        }
    }

    public static class HubVsHubAdapter extends RecyclerView.Adapter<HubVsHubAdapter.ViewHolder> {
        private final List<HubVsHubModel> list;
        public HubVsHubAdapter(List<HubVsHubModel> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hub_vs_hub, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HubVsHubModel item = list.get(position);
            holder.tvHubName.setText(item.getHubName());
            holder.tvOfdDel.setText("OFD/DEL = " + item.getOfdDelCon());
            holder.tvOfpPik.setText("OFP/PIKED = " + item.getOfpPikCon());
            holder.tvDnpDnpc.setText("DNP/DNPC = " + item.getDnpDnpcCon());
        }

        @Override
        public int getItemCount() { return list.size(); }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvHubName, tvOfdDel, tvOfpPik, tvDnpDnpc;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvHubName = itemView.findViewById(R.id.tvVsHubName);
                tvOfdDel = itemView.findViewById(R.id.tvVsOfdDel);
                tvOfpPik = itemView.findViewById(R.id.tvVsOfpPik);
                tvDnpDnpc = itemView.findViewById(R.id.tvVsDnpDnpc);
            }
        }
    }
                                          }
