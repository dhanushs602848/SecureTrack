package com.example.securetrack.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.example.securetrack.activities.LocationHistoryActivity;
import com.example.securetrack.databinding.FragmentTrackingBinding;
import com.example.securetrack.location.LocationTrackingService;
import com.example.securetrack.models.LocationRecord;
import com.example.securetrack.repositories.LocationRepository;
import com.example.securetrack.utils.PermissionUtils;
import com.example.securetrack.utils.SessionManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public class TrackingFragment extends Fragment {

    private FragmentTrackingBinding binding;
    private SessionManager sessionManager;
    private LocationRepository locationRepository;
    private FusedLocationProviderClient fusedLocationClient;
    private Marker currentMarker;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                Boolean fineGranted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarseGranted = permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                if (Boolean.TRUE.equals(fineGranted) || Boolean.TRUE.equals(coarseGranted)) {
                    getCurrentLocation();
                } else {
                    Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> bgLocationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    sessionManager.setBackgroundTrackingEnabled(true);
                    startBackgroundTrackingService();
                } else {
                    binding.switchBackgroundTracking.setChecked(false);
                    sessionManager.setBackgroundTrackingEnabled(false);
                    Toast.makeText(requireContext(), "Background location permission denied", Toast.LENGTH_SHORT).show();
                }
            });

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

        sessionManager = new SessionManager(requireContext());
        locationRepository = new LocationRepository(requireContext());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());

        binding.mapView.setMultiTouchControls(true);
        binding.mapView.getController().setZoom(15.0);

        binding.switchBackgroundTracking.setChecked(sessionManager.isBackgroundTrackingEnabled());

        binding.switchBackgroundTracking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !PermissionUtils.hasBackgroundLocationPermission(requireContext())) {
                    bgLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                } else {
                    sessionManager.setBackgroundTrackingEnabled(true);
                    startBackgroundTrackingService();
                }
            } else {
                sessionManager.setBackgroundTrackingEnabled(false);
                stopBackgroundTrackingService();
            }
        });

        binding.btnRefreshLocation.setOnClickListener(v -> requestForegroundLocation());
        binding.btnViewHistory.setOnClickListener(v -> startActivity(new Intent(requireContext(), LocationHistoryActivity.class)));

        requestForegroundLocation();
    }

    private void requestForegroundLocation() {
        if (!PermissionUtils.hasForegroundLocationPermission(requireContext())) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        } else {
            getCurrentLocation();
        }
    }

    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMaxUpdates(1)
                .build();

        fusedLocationClient.requestLocationUpdates(request, new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null && locationResult.getLastLocation() != null) {
                    updateMapAndUpload(locationResult.getLastLocation());
                }
            }
        }, Looper.getMainLooper());
    }

    private void updateMapAndUpload(Location location) {
        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());

        if (currentMarker == null) {
            currentMarker = new Marker(binding.mapView);
            currentMarker.setTitle("Current Location");
            binding.mapView.getOverlays().add(currentMarker);
        }
        currentMarker.setPosition(point);
        binding.mapView.getController().setCenter(point);
        binding.mapView.invalidate();

        binding.tvLatitude.setText(String.format(Locale.US, "Lat: %.6f", location.getLatitude()));
        binding.tvLongitude.setText(String.format(Locale.US, "Lng: %.6f", location.getLongitude()));
        binding.tvAccuracy.setText(String.format(Locale.US, "Accuracy: %.1fm", location.getAccuracy()));

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss UTC", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        binding.tvLastUpdated.setText("Updated: " + sdf.format(new Date(location.getTime())));

        long deviceId = sessionManager.getPrimaryDeviceId();
        if (deviceId == -1) return;

        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        LocationRecord record = new LocationRecord(
                deviceId,
                UUID.randomUUID().toString(),
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy(),
                location.getAltitude(),
                location.getSpeed(),
                isoFormat.format(new Date(location.getTime()))
        );

        locationRepository.recordLocation(record, new LocationRepository.ApiCallback<LocationRecord>() {
            @Override
            public void onSuccess(LocationRecord result) {}

            @Override
            public void onError(String errorMessage) {}
        });
    }

    private void startBackgroundTrackingService() {
        Intent serviceIntent = new Intent(requireContext(), LocationTrackingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent);
        } else {
            requireContext().startService(serviceIntent);
        }
        Toast.makeText(requireContext(), "Continuous Location Service Started", Toast.LENGTH_SHORT).show();
    }

    private void stopBackgroundTrackingService() {
        Intent serviceIntent = new Intent(requireContext(), LocationTrackingService.class);
        requireContext().stopService(serviceIntent);
        Toast.makeText(requireContext(), "Continuous Location Service Stopped", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        binding.mapView.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
