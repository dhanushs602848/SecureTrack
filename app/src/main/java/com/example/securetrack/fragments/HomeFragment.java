package com.example.securetrack.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.securetrack.R;
import com.example.securetrack.databinding.FragmentHomeBinding;
import com.example.securetrack.models.Device;
import com.example.securetrack.models.SecurityEvent;
import com.example.securetrack.repositories.DeviceRepository;
import com.example.securetrack.repositories.SecurityRepository;
import com.example.securetrack.utils.SessionManager;
import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private DeviceRepository deviceRepository;
    private SecurityRepository securityRepository;
    private SessionManager sessionManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        deviceRepository = new DeviceRepository(requireContext());
        securityRepository = new SecurityRepository(requireContext());
        sessionManager = new SessionManager(requireContext());

        binding.tvUserName.setText("Hello, " + sessionManager.getUserName());

        // Observe active alert count reactively from Room source of truth
        securityRepository.getActiveAlertCountLiveData().observe(getViewLifecycleOwner(), count -> {
            if (binding != null) {
                int activeAlerts = count != null ? count : 0;
                binding.tvAlertsCount.setText(String.valueOf(activeAlerts));
                if (activeAlerts == 0) {
                    binding.tvAlertsCount.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.status_success));
                } else {
                    binding.tvAlertsCount.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.status_error));
                }
            }
        });

        binding.swipeRefresh.setOnRefreshListener(this::loadDashboardData);

        loadDashboardData();
    }

    private void loadDashboardData() {
        binding.swipeRefresh.setRefreshing(true);

        deviceRepository.getUserDevices(new DeviceRepository.ApiCallback<List<Device>>() {
            @Override
            public void onSuccess(List<Device> devices) {
                if (binding == null) return;
                binding.tvTotalDevices.setText(String.valueOf(devices.size()));

                long onlineCount = 0;
                Device thisDevice = null;
                String currentDevId = sessionManager.getCurrentDeviceId();
                String installId = sessionManager.getInstallationId();

                for (Device d : devices) {
                    if ("ONLINE".equalsIgnoreCase(d.getStatus())) {
                        onlineCount++;
                    }
                    if ((currentDevId != null && currentDevId.equals(d.getId())) ||
                        (installId != null && installId.equals(d.getInstallationId()))) {
                        thisDevice = d;
                    }
                }
                binding.tvOnlineDevices.setText(String.valueOf(onlineCount));

                if (thisDevice != null) {
                    binding.tvPrimaryDeviceName.setText(thisDevice.getDeviceName() + " (This Device)");
                    com.example.securetrack.utils.ProtectionHealthEvaluator.ProtectionReport report =
                            com.example.securetrack.utils.ProtectionHealthEvaluator.evaluate(requireContext());
                    if (com.example.securetrack.utils.ProtectionHealthEvaluator.STATUS_ACTIVE.equals(report.status)) {
                        binding.tvProtectionStatus.setText("Active & Protected");
                        binding.tvProtectionStatus.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.securetrack.R.color.status_success));
                    } else if (com.example.securetrack.utils.ProtectionHealthEvaluator.STATUS_DEGRADED.equals(report.status)) {
                        binding.tvProtectionStatus.setText("Degraded: " + report.reason);
                        binding.tvProtectionStatus.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.securetrack.R.color.status_warning));
                    } else if (com.example.securetrack.utils.ProtectionHealthEvaluator.STATUS_NEEDS_ATTENTION.equals(report.status)) {
                        binding.tvProtectionStatus.setText("Action Needed: " + report.reason);
                        binding.tvProtectionStatus.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.securetrack.R.color.status_error));
                    } else {
                        binding.tvProtectionStatus.setText("Offline (No Connection)");
                        binding.tvProtectionStatus.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.securetrack.R.color.text_secondary));
                    }
                    sessionManager.setCurrentDeviceId(thisDevice.getId());
                    sessionManager.setDeviceName(thisDevice.getDeviceName());
                } else if (!devices.isEmpty()) {
                    binding.tvPrimaryDeviceName.setText(sessionManager.getDeviceName() + " (Unregistered)");
                    binding.tvProtectionStatus.setText("Tap to Register");
                } else {
                    binding.tvPrimaryDeviceName.setText("No Device Found");
                    binding.tvProtectionStatus.setText("N/A");
                }

                loadSecurityEvents();
            }

            @Override
            public void onError(String errorMessage) {
                if (binding != null) {
                    binding.swipeRefresh.setRefreshing(false);
                    binding.tvPrimaryDeviceName.setText(sessionManager.getDeviceName());
                    binding.tvProtectionStatus.setText("Tap to Refresh");
                    Toast.makeText(requireContext(), "Failed to load devices: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void loadSecurityEvents() {
        securityRepository.getUserSecurityEvents(new SecurityRepository.ApiCallback<List<SecurityEvent>>() {
            @Override
            public void onSuccess(List<SecurityEvent> events) {
                if (binding != null) {
                    binding.swipeRefresh.setRefreshing(false);
                    int activeCount = 0;
                    if (events != null) {
                        for (SecurityEvent ev : events) {
                            if ("ACTIVE".equalsIgnoreCase(ev.getStatus())) {
                                activeCount++;
                            }
                        }
                    }
                    binding.tvAlertsCount.setText(String.valueOf(activeCount));
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (binding != null) {
                    binding.swipeRefresh.setRefreshing(false);
                    Toast.makeText(requireContext(), "Failed to load alerts: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
