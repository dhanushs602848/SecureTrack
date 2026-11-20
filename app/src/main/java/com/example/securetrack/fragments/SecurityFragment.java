package com.example.securetrack.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.securetrack.R;
import com.example.securetrack.activities.DeviceSecurityActivity;
import com.example.securetrack.activities.LostDeviceActivity;
import com.example.securetrack.adapters.SecurityAlertAdapter;
import com.example.securetrack.ble.BleNearbyManager;
import com.example.securetrack.databinding.FragmentSecurityBinding;
import com.example.securetrack.location.LocationPriorityManager;
import com.example.securetrack.models.DeviceCommand;
import com.example.securetrack.models.ResolvedLocation;
import com.example.securetrack.models.SecurityEvent;
import com.example.securetrack.repositories.SecurityRepository;
import com.example.securetrack.security.AlarmSoundManager;
import com.example.securetrack.security.SecureAdminReceiver;
import com.example.securetrack.utils.PermissionUtils;
import com.example.securetrack.utils.SessionManager;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SecurityFragment extends Fragment {

    private FragmentSecurityBinding binding;
    private SecurityRepository securityRepository;
    private SessionManager sessionManager;
    private SecurityAlertAdapter adapter;
    private LocationPriorityManager locationPriorityManager;
    private BleNearbyManager bleNearbyManager;

    private List<SecurityEvent> allEvents = new ArrayList<>();
    private String currentFilter = "ALL";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSecurityBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        securityRepository = new SecurityRepository(requireContext());
        sessionManager = new SessionManager(requireContext());
        locationPriorityManager = new LocationPriorityManager(requireContext());
        bleNearbyManager = BleNearbyManager.getInstance(requireContext());

        setupHeaderAndDeviceDetails();
        setupAlertsRecyclerView();
        setupProtectionChecklist();
        setupQuickActions();
        setupFilterChips();

        binding.swipeRefresh.setOnRefreshListener(() -> {
            loadAlerts();
            refreshLocationPriority();
            setupProtectionChecklist();
        });

        loadAlerts();
        refreshLocationPriority();
    }

    @Override
    public void onResume() {
        super.onResume();
        setupProtectionChecklist();
    }

    private void setupHeaderAndDeviceDetails() {
        String devName = sessionManager.getDeviceName();
        if (devName != null && !devName.isEmpty()) {
            binding.tvDeviceHeaderName.setText(devName);
            binding.tvDeviceSubtitle.setText("Monitoring " + devName);
        } else {
            binding.tvDeviceHeaderName.setText("Primary Device");
        }

        binding.btnOpenAdmin.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), DeviceSecurityActivity.class);
            intent.putExtra("device_id", sessionManager.getCurrentDeviceId());
            intent.putExtra("device_name", sessionManager.getDeviceName());
            startActivity(intent);
        });

        binding.btnLostMode.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), LostDeviceActivity.class);
            intent.putExtra("device_id", sessionManager.getCurrentDeviceId());
            intent.putExtra("device_name", sessionManager.getDeviceName());
            startActivity(intent);
        });

        binding.btnLocateNow.setOnClickListener(v -> refreshLocationPriority());
    }

    private void refreshLocationPriority() {
        String deviceId = sessionManager.getCurrentDeviceId();
        if (deviceId == null) return;

        binding.tvLocationCoords.setText("Acquiring best available location...");
        binding.btnLocateNow.setEnabled(false);

        locationPriorityManager.resolveTargetLocation(deviceId, new LocationPriorityManager.LocationResolutionCallback() {
            @Override
            public void onLocationResolved(ResolvedLocation location) {
                if (binding == null) return;
                binding.btnLocateNow.setEnabled(true);
                binding.tvLocationCoords.setText(String.format(Locale.US, "%.5f° N, %.5f° E",
                        location.getLatitude(), location.getLongitude()));
                binding.tvLocationAccuracy.setText(String.format(Locale.US, "±%.0fm", location.getAccuracy()));
                binding.tvLocationUpdated.setText("Resolved via: " + location.getSourceDescription());

                // Update Chip badge
                if (location.getPathway() == ResolvedLocation.Pathway.CLOUD_LIVE) {
                    binding.chipLocationSource.setText("CLOUD LIVE");
                    binding.chipLocationSource.setBackgroundResource(R.drawable.bg_chip_live);
                    binding.chipLocationSource.setTextColor(Color.parseColor("#10B981"));
                } else if (location.getPathway() == ResolvedLocation.Pathway.BLE_NEARBY) {
                    binding.chipLocationSource.setText("BLE NEARBY");
                    binding.chipLocationSource.setBackgroundResource(R.drawable.bg_chip_ble);
                    binding.chipLocationSource.setTextColor(Color.parseColor("#8B5CF6"));
                } else {
                    binding.chipLocationSource.setText("CACHED SNAPSHOT");
                    binding.chipLocationSource.setBackgroundResource(R.drawable.bg_chip_stale);
                    binding.chipLocationSource.setTextColor(Color.parseColor("#F59E0B"));
                }
            }

            @Override
            public void onProgress(String statusUpdate) {
                if (binding != null) {
                    binding.tvLocationCoords.setText(statusUpdate);
                }
            }

            @Override
            public void onError(String message) {
                if (binding != null) {
                    binding.btnLocateNow.setEnabled(true);
                    binding.tvLocationCoords.setText("Location unavailable: " + message);
                }
            }
        });
    }

    private void setupQuickActions() {
        // 5s Alarm Action
        binding.btnActionAlarm.setOnClickListener(v -> {
            AlarmSoundManager.getInstance(requireContext()).playFiveSecondAlarm(new AlarmSoundManager.AlarmCallback() {
                @Override
                public void onAlarmStarted() {
                    Toast.makeText(requireContext(), "Playing 5-second loud alarm...", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onAlarmCompleted(String reason) {
                    Toast.makeText(requireContext(), "Alarm stopped: " + reason, Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Remote Lock Action
        binding.btnActionLock.setOnClickListener(v -> {
            dispatchCommand("LOCK_DEVICE");
        });

        // BLE Action
        binding.btnActionBle.setOnClickListener(v -> {
            if (!bleNearbyManager.isBleSupported()) {
                Toast.makeText(requireContext(), "Bluetooth LE is not supported on this device", Toast.LENGTH_SHORT).show();
                return;
            }
            if (bleNearbyManager.isAdvertising()) {
                bleNearbyManager.stopAdvertising();
                binding.tvBleStatus.setText("Beacon Paused");
                Toast.makeText(requireContext(), "BLE Beacon stopped", Toast.LENGTH_SHORT).show();
            } else {
                bleNearbyManager.startAdvertisingAsTarget();
                binding.tvBleStatus.setText("Beacon Broadcasting");
                Toast.makeText(requireContext(), "Broadcasting BLE nearby presence", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupProtectionChecklist() {
        // 1. Location permission
        boolean hasLocation = PermissionUtils.hasFineLocationPermission(requireContext());
        binding.statusChkLocation.setText(hasLocation ? "ACTIVE" : "ACTION REQUIRED");
        binding.statusChkLocation.setTextColor(Color.parseColor(hasLocation ? "#10B981" : "#EF4444"));

        // 2. Background location
        boolean hasBg = PermissionUtils.hasBackgroundLocationPermission(requireContext());
        binding.statusChkBackground.setText(hasBg ? "ACTIVE" : "STANDBY");
        binding.statusChkBackground.setTextColor(Color.parseColor(hasBg ? "#10B981" : "#F59E0B"));

        // 3. Device Admin
        boolean isAdmin = SecureAdminReceiver.isAdminActive(requireContext());
        binding.statusChkAdmin.setText(isAdmin ? "ACTIVE" : "DISABLED");
        binding.statusChkAdmin.setTextColor(Color.parseColor(isAdmin ? "#10B981" : "#EF4444"));

        // 4. BLE Ready
        boolean hasBlePerms = PermissionUtils.hasBluetoothPermissions(requireContext());
        boolean bleSupported = bleNearbyManager.isBleSupported();
        binding.statusChkBle.setText(bleSupported && hasBlePerms ? "READY" : "NEEDS SETUP");
        binding.statusChkBle.setTextColor(Color.parseColor(bleSupported && hasBlePerms ? "#8B5CF6" : "#F59E0B"));
    }

    private void setupAlertsRecyclerView() {
        adapter = new SecurityAlertAdapter();
        binding.rvSecurityAlerts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvSecurityAlerts.setAdapter(adapter);

        // Swipe-to-Dismiss with Undo Snackbar
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                SecurityEvent event = adapter.getEventAt(position);
                if (event == null) return;

                // Remove from adapter visually
                adapter.removeEventAt(position);

                // Update in repository as DISMISSED
                String alertId = event.getId();
                securityRepository.dismissAlert(alertId, new SecurityRepository.ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        // Show Undo Snackbar
                        Snackbar.make(binding.getRoot(), "Alert dismissed", Snackbar.LENGTH_LONG)
                                .setAction("UNDO", undoView -> {
                                    securityRepository.restoreAlert(alertId, new SecurityRepository.ApiCallback<Void>() {
                                        @Override
                                        public void onSuccess(Void res) {
                                            loadAlerts();
                                        }

                                        @Override
                                        public void onError(String err) {
                                            Toast.makeText(requireContext(), "Failed to restore: " + err, Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                })
                                .show();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Toast.makeText(requireContext(), "Dismiss failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                        loadAlerts();
                    }
                });
            }
        };

        ItemTouchHelper touchHelper = new ItemTouchHelper(swipeCallback);
        touchHelper.attachToRecyclerView(binding.rvSecurityAlerts);

        // Clear / Dismiss all resolved alerts button
        binding.btnClearResolved.setOnClickListener(v -> {
            securityRepository.bulkDismissResolvedAlerts(new SecurityRepository.ApiCallback<Integer>() {
                @Override
                public void onSuccess(Integer count) {
                    Toast.makeText(requireContext(), count + " resolved alerts dismissed", Toast.LENGTH_SHORT).show();
                    loadAlerts();
                }

                @Override
                public void onError(String errorMessage) {
                    Toast.makeText(requireContext(), "Failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void setupFilterChips() {
        binding.chipFilterAll.setOnClickListener(v -> setFilter("ALL"));
        binding.chipFilterActive.setOnClickListener(v -> setFilter("ACTIVE"));
        binding.chipFilterResolved.setOnClickListener(v -> setFilter("RESOLVED"));
        binding.chipFilterDismissed.setOnClickListener(v -> setFilter("DISMISSED"));
    }

    private void setFilter(String filter) {
        currentFilter = filter;
        resetFilterChipStyles();

        if ("ACTIVE".equals(filter)) {
            binding.chipFilterActive.setTextColor(Color.parseColor("#00D2FF"));
        } else if ("RESOLVED".equals(filter)) {
            binding.chipFilterResolved.setTextColor(Color.parseColor("#00D2FF"));
        } else if ("DISMISSED".equals(filter)) {
            binding.chipFilterDismissed.setTextColor(Color.parseColor("#00D2FF"));
        } else {
            binding.chipFilterAll.setTextColor(Color.parseColor("#00D2FF"));
        }

        applyFilter();
    }

    private void resetFilterChipStyles() {
        int muted = Color.parseColor("#94A3B8");
        binding.chipFilterAll.setTextColor(muted);
        binding.chipFilterActive.setTextColor(muted);
        binding.chipFilterResolved.setTextColor(muted);
        binding.chipFilterDismissed.setTextColor(muted);
    }

    private void applyFilter() {
        int activeCount = 0;
        int dismissedCount = 0;
        int resolvedCount = 0;
        for (SecurityEvent ev : allEvents) {
            String status = ev.getStatus() != null ? ev.getStatus().toUpperCase() : "ACTIVE";
            if ("ACTIVE".equals(status)) activeCount++;
            else if ("DISMISSED".equals(status)) dismissedCount++;
            else if ("RESOLVED".equals(status)) resolvedCount++;
        }

        binding.chipFilterActive.setText("ACTIVE (" + activeCount + ")");
        binding.chipFilterDismissed.setText("DISMISSED (" + dismissedCount + ")");
        binding.chipFilterResolved.setText("RESOLVED (" + resolvedCount + ")");
        binding.chipFilterAll.setText("ALL (" + (activeCount + resolvedCount) + ")");

        List<SecurityEvent> filtered = new ArrayList<>();
        for (SecurityEvent ev : allEvents) {
            String status = ev.getStatus() != null ? ev.getStatus().toUpperCase() : "ACTIVE";
            if ("ALL".equals(currentFilter)) {
                // By default 'ALL' shows non-dismissed alerts
                if (!"DISMISSED".equals(status)) {
                    filtered.add(ev);
                }
            } else if (currentFilter.equals(status)) {
                filtered.add(ev);
            }
        }

        adapter.setEvents(filtered);
        if (filtered.isEmpty()) {
            binding.tvEmptyState.setVisibility(View.VISIBLE);
            binding.rvSecurityAlerts.setVisibility(View.GONE);
            if ("ACTIVE".equals(currentFilter) || "ALL".equals(currentFilter)) {
                binding.tvEmptyState.setText("All caught up • 0 active security alerts\nTap 'Dismissed' to view alert history");
            } else if ("DISMISSED".equals(currentFilter)) {
                binding.tvEmptyState.setText("No dismissed alerts in history");
            } else {
                binding.tvEmptyState.setText("No alerts matching current filter");
            }
        } else {
            binding.tvEmptyState.setVisibility(View.GONE);
            binding.rvSecurityAlerts.setVisibility(View.VISIBLE);
        }
    }

    private void loadAlerts() {
        binding.swipeRefresh.setRefreshing(true);
        securityRepository.getUserSecurityEvents(new SecurityRepository.ApiCallback<List<SecurityEvent>>() {
            @Override
            public void onSuccess(List<SecurityEvent> result) {
                if (binding != null) {
                    binding.swipeRefresh.setRefreshing(false);
                    allEvents = result != null ? result : new ArrayList<>();
                    applyFilter();
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (binding != null) {
                    binding.swipeRefresh.setRefreshing(false);
                    Toast.makeText(requireContext(), "Error: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void dispatchCommand(String commandType) {
        String deviceId = sessionManager.getCurrentDeviceId();
        if (deviceId == null) {
            Toast.makeText(requireContext(), "No device registered on this installation", Toast.LENGTH_SHORT).show();
            return;
        }

        DeviceCommand cmd = new DeviceCommand(commandType, "QUEUED", "Triggered from Security Center", "");
        securityRepository.createCommand(deviceId, cmd, new SecurityRepository.ApiCallback<DeviceCommand>() {
            @Override
            public void onSuccess(DeviceCommand result) {
                Toast.makeText(requireContext(), "Command " + commandType + " dispatched successfully", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(requireContext(), "Failed to dispatch: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
