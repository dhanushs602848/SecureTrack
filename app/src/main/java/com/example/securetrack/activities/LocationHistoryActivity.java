package com.example.securetrack.activities;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.securetrack.adapters.LocationHistoryAdapter;
import com.example.securetrack.databinding.ActivityLocationHistoryBinding;
import com.example.securetrack.models.LocationRecord;
import com.example.securetrack.repositories.LocationRepository;
import com.example.securetrack.utils.SessionManager;
import java.util.List;

public class LocationHistoryActivity extends AppCompatActivity {

    private ActivityLocationHistoryBinding binding;
    private LocationRepository locationRepository;
    private SessionManager sessionManager;
    private LocationHistoryAdapter adapter;
    private String targetDeviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLocationHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        locationRepository = new LocationRepository(this);
        sessionManager = new SessionManager(this);

        targetDeviceId = getIntent().getStringExtra("device_id");
        if (targetDeviceId == null || targetDeviceId.isEmpty()) {
            targetDeviceId = sessionManager.getCurrentDeviceId();
            if (targetDeviceId == null) {
                targetDeviceId = sessionManager.getInstallationId();
            }
        }

        String deviceName = getIntent().getStringExtra("device_name");
        if (deviceName != null && !deviceName.trim().isEmpty()) {
            binding.tvTitleLocationHistory.setText("History: " + deviceName);
        }

        adapter = new LocationHistoryAdapter();
        binding.rvHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.rvHistory.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.swipeRefresh.setOnRefreshListener(this::loadHistory);

        loadHistory();
    }

    private void loadHistory() {
        binding.swipeRefresh.setRefreshing(true);
        if (targetDeviceId == null) {
            binding.swipeRefresh.setRefreshing(false);
            Toast.makeText(this, "No device specified", Toast.LENGTH_SHORT).show();
            return;
        }

        locationRepository.getLocationHistoryStr(targetDeviceId, new LocationRepository.ApiCallback<List<LocationRecord>>() {
            @Override
            public void onSuccess(List<LocationRecord> result) {
                binding.swipeRefresh.setRefreshing(false);
                adapter.setLocations(result);
            }

            @Override
            public void onError(String errorMessage) {
                binding.swipeRefresh.setRefreshing(false);
                Toast.makeText(LocationHistoryActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
