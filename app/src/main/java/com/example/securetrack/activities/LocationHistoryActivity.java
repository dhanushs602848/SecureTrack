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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLocationHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        locationRepository = new LocationRepository(this);
        sessionManager = new SessionManager(this);

        adapter = new LocationHistoryAdapter();
        binding.rvHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.rvHistory.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.swipeRefresh.setOnRefreshListener(this::loadHistory);

        loadHistory();
    }

    private void loadHistory() {
        binding.swipeRefresh.setRefreshing(true);
        String deviceId = sessionManager.getPrimaryDeviceIdStr();
        if (deviceId == null) {
            binding.swipeRefresh.setRefreshing(false);
            Toast.makeText(this, "No device registered", Toast.LENGTH_SHORT).show();
            return;
        }

        locationRepository.getLocationHistory(deviceId, new LocationRepository.ApiCallback<List<LocationRecord>>() {
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
