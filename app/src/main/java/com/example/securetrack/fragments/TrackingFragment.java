package com.example.securetrack.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.example.securetrack.activities.LocationHistoryActivity;
import com.example.securetrack.databinding.FragmentTrackingBinding;
import com.example.securetrack.location.LocationTrackingService;
import com.example.securetrack.models.LocationRecord;
import com.example.securetrack.repositories.LocationRepository;
import com.example.securetrack.utils.SessionManager;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import java.util.List;

public class TrackingFragment extends Fragment {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 2;

    private FragmentTrackingBinding binding;
    private MapView map;
    private LocationRepository locationRepository;
    private SessionManager sessionManager;
    private Marker currentLocationMarker;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());
        binding = FragmentTrackingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        locationRepository = new LocationRepository(requireContext());
        sessionManager = new SessionManager(requireContext());

        map = binding.mapView;
        map.setMultiTouchControls(true);
        map.getController().setZoom(15.0);

        binding.switchBackgroundTracking.setChecked(sessionManager.isBackgroundTrackingEnabled());
        binding.switchBackgroundTracking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                checkAndRequestLocationPermissions();
            } else {
                sessionManager.setBackgroundTrackingEnabled(false);
                stopLocationService();
            }
        });

        binding.btnRefreshLocation.setOnClickListener(v -> fetchLatestLocation());
        binding.btnLocationHistory.setOnClickListener(v -> startActivity(new Intent(requireContext(), LocationHistoryActivity.class)));

        if (sessionManager.isBackgroundTrackingEnabled()) {
            checkAndRequestLocationPermissions();
        }

        fetchLatestLocation();
    }

    private void checkAndRequestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        } else {
            checkBackgroundLocationPermission();
        }
    }

    private void checkBackgroundLocationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE
                );
            } else {
                startTracking();
            }
        } else {
            startTracking();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkBackgroundLocationPermission();
            } else {
                binding.switchBackgroundTracking.setChecked(false);
                Toast.makeText(requireContext(), "Location permission required", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startTracking();
            } else {
                binding.switchBackgroundTracking.setChecked(false);
                Toast.makeText(requireContext(), "Background location permission is needed for continuous tracking", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startTracking() {
        sessionManager.setBackgroundTrackingEnabled(true);
        Intent serviceIntent = new Intent(requireContext(), LocationTrackingService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent);
        } else {
            requireContext().startService(serviceIntent);
        }
        Toast.makeText(requireContext(), "Background tracking started", Toast.LENGTH_SHORT).show();
    }

    private void stopLocationService() {
        Intent serviceIntent = new Intent(requireContext(), LocationTrackingService.class);
        requireContext().stopService(serviceIntent);
        Toast.makeText(requireContext(), "Background tracking stopped", Toast.LENGTH_SHORT).show();
    }

    private void fetchLatestLocation() {
        String deviceId = sessionManager.getPrimaryDeviceIdStr();
        if (deviceId == null) {
            Toast.makeText(requireContext(), "No primary device registered", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        locationRepository.getLocationHistoryStr(
                deviceId,
                new LocationRepository.ApiCallback<List<LocationRecord>>() {
                    @Override
                    public void onSuccess(List<LocationRecord> result) {
                        if (binding != null) binding.progressBar.setVisibility(View.GONE);
                        if (result != null && !result.isEmpty()) {
                            updateMap(result.get(0));
                        } else {
                            Toast.makeText(requireContext(), "No location data found", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (binding != null) binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void updateMap(LocationRecord record) {
        if (map == null) return;

        GeoPoint point = new GeoPoint(record.getLatitude(), record.getLongitude());
        map.getController().setCenter(point);

        if (currentLocationMarker == null) {
            currentLocationMarker = new Marker(map);
            currentLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            map.getOverlays().add(currentLocationMarker);
        }

        currentLocationMarker.setPosition(point);
        currentLocationMarker.setTitle("Last Location");
        currentLocationMarker.setSnippet("Accuracy: " + record.getAccuracy() + "m");
        map.invalidate();

        binding.tvLastUpdated.setText("Last Updated: " + record.getTimestamp());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) {
            map.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) {
            map.onPause();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (map != null) {
            map.onDetach();
        }
        binding = null;
    }
}
