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
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import java.util.ArrayList;
import java.util.List;

public class LocationHistoryActivity extends AppCompatActivity {

    private ActivityLocationHistoryBinding binding;
    private LocationRepository locationRepository;
    private LocationHistoryAdapter adapter;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().setUserAgentValue(getPackageName());

        binding = ActivityLocationHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        locationRepository = new LocationRepository(this);
        sessionManager = new SessionManager(this);

        adapter = new LocationHistoryAdapter();
        binding.rvLocationHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.rvLocationHistory.setAdapter(adapter);

        binding.historyMapView.setMultiTouchControls(true);

        loadHistory();
    }

    private void loadHistory() {
        long deviceId = sessionManager.getPrimaryDeviceId();
        if (deviceId == -1) {
            Toast.makeText(this, "No primary device selected", Toast.LENGTH_SHORT).show();
            return;
        }

        locationRepository.getLocationHistory(deviceId, new LocationRepository.ApiCallback<List<LocationRecord>>() {
            @Override
            public void onSuccess(List<LocationRecord> result) {
                adapter.setLocations(result);
                displayRouteOnMap(result);
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(LocationHistoryActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayRouteOnMap(List<LocationRecord> result) {
        if (result == null || result.isEmpty()) return;

        binding.historyMapView.getOverlays().clear();

        List<GeoPoint> points = new ArrayList<>();
        for (LocationRecord loc : result) {
            GeoPoint point = new GeoPoint(loc.getLatitude(), loc.getLongitude());
            points.add(point);

            Marker marker = new Marker(binding.historyMapView);
            marker.setPosition(point);
            marker.setTitle("Time: " + loc.getTimestamp());
            binding.historyMapView.getOverlays().add(marker);
        }

        if (!points.isEmpty()) {
            Polyline polyline = new Polyline();
            polyline.setPoints(points);
            binding.historyMapView.getOverlays().add(polyline);

            binding.historyMapView.getController().setZoom(15.0);
            binding.historyMapView.getController().setCenter(points.get(0));
        }

        binding.historyMapView.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.historyMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        binding.historyMapView.onPause();
    }
}
