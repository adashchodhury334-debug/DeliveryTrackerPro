package com.example.deliveryapp;

public class AgentModel {
    private String name;
    private int ofd, del, ofp, piked;
    private String phone; // Filhaal blank rahega

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
}package com.example.deliveryapp;

public class HubVsHubModel {
    private String hubName;
    private String ofd, del, ofdCon;
    private String ofp, piked, pikCon;
    private String dnp, dnpc, totalCon;

    public HubVsHubModel(String hubName, String ofd, String del, String ofdCon, 
                         String ofp, String piked, String pikCon, 
                         String dnp, String dnpc, String totalCon) {
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
}<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/nav_order_id"
        android:icon="@android:drawable/ic_menu_search"
        android:title="ORDER ID" />
    <item
        android:id="@+id/nav_performance"
        android:icon="@android:drawable/ic_menu_myplaces"
        android:title="PERFORMANCE" />
    <item
        android:id="@+id/nav_hub_vs_hub"
        android:icon="@android:drawable/ic_menu_share"
        android:title="HUB VS HUB" />
</menu>
    <?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#121212">

    <!-- 1. ORDER ID TAB -->
    <LinearLayout
        android:id="@+id/layoutOrderIdTab"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_above="@id/bottomNav"
        android:gravity="center"
        android:orientation="vertical"
        android:visibility="gone">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="ORDER ID SEARCH"
            android:textColor="#FFFFFF"
            android:textSize="18sp"
            android:textStyle="bold" />
    </LinearLayout>

    <!-- 2. PERFORMANCE TAB -->
    <include
        android:id="@+id/layoutPerformanceTab"
        layout="@layout/tab_performance"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_above="@id/bottomNav"
        android:visibility="visible" />

    <!-- 3. HUB VS HUB TAB -->
    <LinearLayout
        android:id="@+id/layoutHubVsHubTab"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_above="@id/bottomNav"
        android:orientation="vertical"
        android:visibility="gone">

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:padding="14dp"
            android:text="⚔️ HUB VS HUB"
            android:textColor="#FFFFFF"
            android:textSize="18sp"
            android:textStyle="bold" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rvHubVsHub"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
    </LinearLayout>

    <!-- 3 BOTTOM ICONS -->
    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottomNav"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_alignParentBottom="true"
        android:background="#1E1E1E"
        app:itemIconTint="#FFFFFF"
        app:itemTextColor="#FFFFFF"
        app:menu="@menu/bottom_nav_menu" />
</RelativeLayout>
    <?xml version="1.0" encoding="utf-8"?>
<androidx.core.widget.NestedScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#121212">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="12dp">

        <!-- HUB HEADER CARD -->
        <androidx.cardview.widget.CardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:cardBackgroundColor="#1F2937"
            app:cardCornerRadius="12dp"
            app:cardElevation="3dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="14dp">

                <TextView
                    android:id="@+id/tvHubHeaderName"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="MALBAZARHUB_NJP"
                    android:textColor="#60A5FA"
                    android:textSize="18sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/tvHubOfdDel"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="6dp"
                    android:text="OFD/DEL = 0%"
                    android:textColor="#E5E7EB"
                    android:textSize="14sp" />

                <TextView
                    android:id="@+id/tvHubOfpPik"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="OFP/PIKED = 0%"
                    android:textColor="#E5E7EB"
                    android:textSize="14sp" />

                <TextView
                    android:id="@+id/tvHubDnpDnpc"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="DNP/DNPC = 0%"
                    android:textColor="#34D399"
                    android:textSize="15sp"
                    android:textStyle="bold" />
            </LinearLayout>
        </androidx.cardview.widget.CardView>

        <!-- TOP CONVERSION & TOP DNPC BADGES -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="10dp"
            android:orientation="horizontal"
            android:weightSum="2">

            <TextView
                android:id="@+id/tvTopConversion"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginEnd="6dp"
                android:layout_weight="1"
                android:background="#374151"
                android:padding="10dp"
                android:text="🔥 TOP CONVERSION\n---"
                android:textColor="#FBBF24"
                android:textSize="12sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/tvTopDnpc"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="6dp"
                android:layout_weight="1"
                android:background="#374151"
                android:padding="10dp"
                android:text="📦 TOP DNPC\n---"
                android:textColor="#60A5FA"
                android:textSize="12sp"
                android:textStyle="bold" />
        </LinearLayout>

        <!-- AGENT PERFORMANCE SECTION HEADER -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:layout_marginBottom="8dp"
            android:text="AGENT PERFORMANCE"
            android:textColor="#9CA3AF"
            android:textSize="14sp"
            android:textStyle="bold" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rvAgentPerformance"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:nestedScrollingEnabled="false" />
    </LinearLayout>
</androidx.core.widget.NestedScrollView>
    <?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="8dp"
    app:cardBackgroundColor="#1E1E1E"
    app:cardCornerRadius="10dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="12dp">

        <TextView
            android:id="@+id/tvAgentName"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="AGENT NAME"
            android:textColor="#FFFFFF"
            android:textSize="16sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tvAgentOfdDel"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:text="OFD/DEL = 0%"
            android:textColor="#D1D5DB"
            android:textSize="13sp" />

        <TextView
            android:id="@+id/tvAgentOfpPik"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:text="OFP/PIKED = 0%"
            android:textColor="#D1D5DB"
            android:textSize="13sp" />

        <TextView
            android:id="@+id/tvAgentDnpDnpc"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:text="DNP/DNPC = 0%"
            android:textColor="#34D399"
            android:textSize="13sp"
            android:textStyle="bold" />

        <!-- CONTACT NUMBER (FILHAAL BLANK) -->
        <TextView
            android:id="@+id/tvAgentContact"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:text=""
            android:textColor="#9CA3AF"
            android:textSize="12sp" />
    </LinearLayout>
</androidx.cardview.widget.CardView>
    <?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="10dp"
    android:layout_marginVertical="6dp"
    app:cardBackgroundColor="#1E1E1E"
    app:cardCornerRadius="10dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="12dp">

        <TextView
            android:id="@+id/tvVsHubName"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="HUB NAME"
            android:textColor="#60A5FA"
            android:textSize="16sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tvVsOfdDel"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:text="OFD/DEL = 0%"
            android:textColor="#D1D5DB"
            android:textSize="13sp" />

        <TextView
            android:id="@+id/tvVsOfpPik"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:text="OFP/PIKED = 0%"
            android:textColor="#D1D5DB"
            android:textSize="13sp" />

        <TextView
            android:id="@+id/tvVsDnpDnpc"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:text="DNP/DNPC = 0%"
            android:textColor="#34D399"
            android:textSize="13sp"
            android:textStyle="bold" />
    </LinearLayout>
</androidx.cardview.widget.CardView>
    package com.example.deliveryapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class AgentAdapter extends RecyclerView.Adapter<AgentAdapter.ViewHolder> {
    private List<AgentModel> list;

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
        holder.tvContact.setText(item.getPhone()); // Blank rahega
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
}package com.example.deliveryapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class HubVsHubAdapter extends RecyclerView.Adapter<HubVsHubAdapter.ViewHolder> {
    private List<HubVsHubModel> list;

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
    }package com.example.deliveryapp;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private View layoutOrderId, layoutPerformance, layoutHubVsHub;
    private RecyclerView rvAgentPerformance, rvHubVsHub;
    private TextView tvHubHeaderName, tvHubOfdDel, tvHubOfpPik, tvHubDnpDnpc;
    private TextView tvTopConversion, tvTopDnpc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        layoutOrderId = findViewById(R.id.layoutOrderIdTab);
        layoutPerformance = findViewById(R.id.layoutPerformanceTab);
        layoutHubVsHub = findViewById(R.id.layoutHubVsHubTab);

        rvAgentPerformance = findViewById(R.id.rvAgentPerformance);
        rvAgentPerformance.setLayoutManager(new LinearLayoutManager(this));

        rvHubVsHub = findViewById(R.id.rvHubVsHub);
        rvHubVsHub.setLayoutManager(new LinearLayoutManager(this));

        tvHubHeaderName = findViewById(R.id.tvHubHeaderName);
        tvHubOfdDel = findViewById(R.id.tvHubOfdDel);
        tvHubOfpPik = findViewById(R.id.tvHubOfpPik);
        tvHubDnpDnpc = findViewById(R.id.tvHubDnpDnpc);
        tvTopConversion = findViewById(R.id.tvTopConversion);
        tvTopDnpc = findViewById(R.id.tvTopDnpc);

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

        // Sheet data parsing ko call karein
        // loadSheetData();
    }

    private void showTab(View tabToShow) {
        layoutOrderId.setVisibility(View.GONE);
        layoutPerformance.setVisibility(View.GONE);
        layoutHubVsHub.setVisibility(View.GONE);
        tabToShow.setVisibility(View.VISIBLE);
    }

    public void parseCsvData(List<String[]> rows) {
        List<AgentModel> agentList = new ArrayList<>();
        List<HubVsHubModel> hubVsHubList = new ArrayList<>();

        int totalHubOfd = 0, totalHubDel = 0, totalHubOfp = 0, totalHubPiked = 0;
        String topConvAgent = "---", topDnpcAgent = "---";
        double maxConv = -1;
        int maxDnpc = -1;

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);

            // AGENT PERFORMANCE DATA (Cols B to F)
            if (row.length >= 7 && !row[1].trim().isEmpty() && !row[1].equalsIgnoreCase("Total")) {
                String agentName = row[1].trim();
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

            // HUB VS HUB DATA (Cols H to P)
            if (row.length >= 17 && !row[8].trim().isEmpty() && !row[8].equalsIgnoreCase("HUB NAME")) {
                String hubName = row[8].trim();
                String ofd = row[9].trim();
                String del = row[10].trim();
                String ofdCon = row[11].trim();
                String ofp = row[12].trim();
                String pik = row[13].trim();
                String pikCon = row[14].trim();
                String totCon = row[16].trim();

                int dnp = parseInt(ofd) + parseInt(ofp);
                int dnpc = parseInt(del) + parseInt(pik);

                hubVsHubList.add(new HubVsHubModel(hubName, ofd, del, ofdCon, ofp, pik, pikCon, String.valueOf(dnp), String.valueOf(dnpc), totCon));
            }
        }

        // Set Hub Header Stats
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

        // Adapters Attach
        rvAgentPerformance.setAdapter(new AgentAdapter(agentList));
        rvHubVsHub.setAdapter(new HubVsHubAdapter(hubVsHubList));
    }

    private int parseInt(String val) {
        try {
            return Integer.parseInt(val.trim().replace("%", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}

}



