package com.example.securetrack.fragments;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.example.securetrack.activities.LocationHistoryActivity;
import com.example.securetrack.databinding.FragmentTrackingBinding;
import com.example.securetrack.location.LocationTrackingService;
import com.example.securetrack.models.LocationRecord;
import com.example.securetrack.repositories.LocationRepository;
import com.example.securetrack.utils.SessionManager;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public class TrackingFragment extends Fragment {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 2;

    /**
     * Legitimate OpenStreetMap tile provider with explicit identifying User-Agent compliance.
     * Does NOT use FLAG_USER_AGENT_NORMALIZED to prevent osmdroid from stripping
     * the identifying contact headers and triggering OSM 403 / x-blocked errors.
     */
    public static final OnlineTileSourceBase OSM_REAL_STREETS = new XYTileSource(
            "OpenStreetMap",
            0, 19, 256, ".png",
            new String[]{
                    "https://a.tile.openstreetmap.org/",
                    "https://b.tile.openstreetmap.org/",
                    "https://c.tile.openstreetmap.org/"
            },
            "© OpenStreetMap contributors",
            new TileSourcePolicy(2,
                    TileSourcePolicy.FLAG_NO_BULK
                            | TileSourcePolicy.FLAG_NO_PREVENTIVE
                            | TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
            )
    );

    private FragmentTrackingBinding binding;
    private MapView map;
    private LocationRepository locationRepository;
    private SessionManager sessionManager;
    private Marker currentLocationMarker;
    private FusedLocationProviderClient fusedLocationClient;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Ensure osmdroid configuration is fully applied in case fragment is created independently
        Context ctx = requireContext().getApplicationContext();
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue("SecureTrack/1.0 (dhanushs602848@gmail.com; Android Tracker)");
        File basePath = new File(ctx.getCacheDir(), "osmdroid");
        File tileCache = new File(basePath, "tiles");
        if (!tileCache.exists()) {
            tileCache.mkdirs();
        }
        Configuration.getInstance().setOsmdroidBasePath(basePath);
        Configuration.getInstance().setOsmdroidTileCache(tileCache);

        binding = FragmentTrackingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        locationRepository = new LocationRepository(requireContext());
        sessionManager = new SessionManager(requireContext());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        map = binding.mapView;

        // Configure map provider and controls
        map.setTileSource(OSM_REAL_STREETS);
        map.setMultiTouchControls(true);
        // Disable aggressive DPI scaling so 256x256 street tiles render crisp and legible without blur
        map.setTilesScaledToDpi(false);
        map.setMinZoomLevel(4.0);
        map.setMaxZoomLevel(19.0);
        map.getController().setZoom(16.5);

        // Clear in-memory tile cache to purge any previous 403 or blocked tiles
        if (map.getTileProvider() != null) {
            map.getTileProvider().clearTileCache();
        }

        binding.switchBackgroundTracking.setChecked(sessionManager.isBackgroundTrackingEnabled());
        binding.switchBackgroundTracking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                checkAndRequestLocationPermissions();
            } else {
                sessionManager.setBackgroundTrackingEnabled(false);
                stopLocationService();
            }
        });

        // "Locate Now" requests fresh GPS location
        binding.btnRefreshLocation.setOnClickListener(v -> requestFreshGpsLocation());
        binding.btnLocationHistory.setOnClickListener(v -> startActivity(new Intent(requireContext(), LocationHistoryActivity.class)));

        if (sessionManager.isBackgroundTrackingEnabled()) {
            checkAndRequestLocationPermissions();
        }

        // Check if location permission is already available to acquire initial fix; otherwise load last known
        if (hasLocationPermission()) {
            requestFreshGpsLocation();
        } else {
            fetchLatestLocationHistory();
        }
    }

    private boolean hasLocationPermission() {
        Context context = getContext();
        if (context == null) return false;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void checkAndRequestLocationPermissions() {
        if (!hasLocationPermission()) {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        } else {
            checkBackgroundLocationPermission();
        }
    }

    private void checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Context context = getContext();
            if (context != null && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
                requestFreshGpsLocation();
                if (binding != null && binding.switchBackgroundTracking.isChecked()) {
                    checkBackgroundLocationPermission();
                }
            } else {
                if (binding != null) binding.switchBackgroundTracking.setChecked(false);
                Context context = getContext();
                if (context != null) {
                    Toast.makeText(context, "Location permission required to locate device", Toast.LENGTH_SHORT).show();
                }
                fetchLatestLocationHistory();
            }
        } else if (requestCode == BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startTracking();
            } else {
                if (binding != null) binding.switchBackgroundTracking.setChecked(false);
                Context context = getContext();
                if (context != null) {
                    Toast.makeText(context, "Background location permission needed for continuous tracking", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void startTracking() {
        Context context = getContext();
        if (context == null) return;
        sessionManager.setBackgroundTrackingEnabled(true);
        Intent serviceIntent = new Intent(context, LocationTrackingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
        Toast.makeText(context, "Background tracking active", Toast.LENGTH_SHORT).show();
    }

    private void stopLocationService() {
        Context context = getContext();
        if (context == null) return;
        Intent serviceIntent = new Intent(context, LocationTrackingService.class);
        context.stopService(serviceIntent);
        Toast.makeText(context, "Background tracking stopped", Toast.LENGTH_SHORT).show();
    }

    /**
     * Captures an actual fresh GPS location fix using FusedLocationProviderClient,
     * updates the map view and marker, updates the Last Updated timestamp,
     * and persists the real location to Cloud Firestore and local Room storage.
     */
    public void requestFreshGpsLocation() {
        if (!hasLocationPermission()) {
            checkAndRequestLocationPermissions();
            return;
        }

        if (binding != null) {
            binding.progressBar.setVisibility(View.VISIBLE);
        }

        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(15000)
                .setMaxUpdateAgeMillis(10000)
                .build();

        CancellationTokenSource cts = new CancellationTokenSource();

        try {
            fusedLocationClient.getCurrentLocation(request, cts.getToken())
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            handleFreshLocationFix(location);
                        } else {
                            // Fallback to last known location if instant current fix is null
                            fusedLocationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                                if (lastLoc != null) {
                                    handleFreshLocationFix(lastLoc);
                                } else {
                                    if (binding != null) binding.progressBar.setVisibility(View.GONE);
                                    Context context = getContext();
                                    if (context != null) {
                                        Toast.makeText(context, "GPS searching... Please ensure GPS is turned on.", Toast.LENGTH_SHORT).show();
                                    }
                                    fetchLatestLocationHistory();
                                }
                            }).addOnFailureListener(e -> {
                                if (binding != null) binding.progressBar.setVisibility(View.GONE);
                                Context context = getContext();
                                if (context != null) {
                                    Toast.makeText(context, "Location error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                                fetchLatestLocationHistory();
                            });
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (binding != null) binding.progressBar.setVisibility(View.GONE);
                        Context context = getContext();
                        if (context != null) {
                            Toast.makeText(context, "Location request failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                        fetchLatestLocationHistory();
                    });
        } catch (SecurityException se) {
            if (binding != null) binding.progressBar.setVisibility(View.GONE);
            Context context = getContext();
            if (context != null) {
                Toast.makeText(context, "Permission error: " + se.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleFreshLocationFix(@NonNull Location location) {
        if (!isAdded() || map == null || binding == null) return;

        // 1. Center map and update marker
        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
        map.getController().setCenter(point);
        map.getController().setZoom(17.0);

        if (currentLocationMarker == null) {
            currentLocationMarker = new Marker(map);
            currentLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            map.getOverlays().add(currentLocationMarker);
        }

        currentLocationMarker.setPosition(point);
        currentLocationMarker.setTitle("Current Location");
        String snippet = String.format(Locale.US, "Lat: %.5f, Lng: %.5f (±%.0fm)",
                location.getLatitude(), location.getLongitude(), location.getAccuracy());
        currentLocationMarker.setSnippet(snippet);
        currentLocationMarker.showInfoWindow();
        map.invalidate();

        // 2. Update Last Updated UI
        SimpleDateFormat displayFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.getDefault());
        String formattedTime = displayFormat.format(new Date(location.getTime() > 0 ? location.getTime() : System.currentTimeMillis()));
        binding.tvLastUpdated.setText("Last Updated: " + formattedTime);

        // 3. Persist real coordinates to Firestore and Room
        String deviceId = sessionManager.getPrimaryDeviceIdStr();
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String isoTimestamp = isoFormat.format(new Date(location.getTime() > 0 ? location.getTime() : System.currentTimeMillis()));

        LocationRecord record = new LocationRecord(
                deviceId,
                UUID.randomUUID().toString(),
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy(),
                location.getAltitude(),
                location.getSpeed(),
                isoTimestamp
        );

        locationRepository.recordLocation(record, new LocationRepository.ApiCallback<LocationRecord>() {
            @Override
            public void onSuccess(LocationRecord result) {
                if (!isAdded() || binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                Context context = getContext();
                if (context != null) {
                    Toast.makeText(context, "Location recorded & saved", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded() || binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                Context context = getContext();
                if (context != null) {
                    Toast.makeText(context, "Location saved locally", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void fetchLatestLocationHistory() {
        String deviceId = sessionManager.getPrimaryDeviceIdStr();
        if (deviceId == null) return;

        if (binding != null) binding.progressBar.setVisibility(View.VISIBLE);

        locationRepository.getLocationHistoryStr(
                deviceId,
                new LocationRepository.ApiCallback<List<LocationRecord>>() {
                    @Override
                    public void onSuccess(List<LocationRecord> result) {
                        if (!isAdded() || binding == null) return;
                        binding.progressBar.setVisibility(View.GONE);
                        if (result != null && !result.isEmpty()) {
                            displayRecordOnMap(result.get(0));
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (!isAdded() || binding == null) return;
                        binding.progressBar.setVisibility(View.GONE);
                    }
                }
        );
    }

    private void displayRecordOnMap(LocationRecord record) {
        if (!isAdded() || map == null || binding == null || record == null) return;

        GeoPoint point = new GeoPoint(record.getLatitude(), record.getLongitude());
        map.getController().setCenter(point);

        if (currentLocationMarker == null) {
            currentLocationMarker = new Marker(map);
            currentLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            map.getOverlays().add(currentLocationMarker);
        }

        currentLocationMarker.setPosition(point);
        currentLocationMarker.setTitle("Last Recorded Location");
        currentLocationMarker.setSnippet(String.format(Locale.US, "Lat: %.5f, Lng: %.5f (±%.0fm)",
                record.getLatitude(), record.getLongitude(), record.getAccuracy() != null ? record.getAccuracy() : 0.0f));
        map.invalidate();

        if (binding != null) {
            binding.tvLastUpdated.setText("Last Updated: " + (record.getTimestamp() != null ? record.getTimestamp() : "Never"));
        }
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
            map = null;
        }
        currentLocationMarker = null;
        binding = null;
    }
}
