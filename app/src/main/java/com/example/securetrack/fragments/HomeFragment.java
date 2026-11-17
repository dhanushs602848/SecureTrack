package com.example.securetrack.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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
                Device primary = null;
                for (Device d : devices) {
                    if ("ONLINE".equalsIgnoreCase(d.getStatus())) {
                        onlineCount++;
                    }
                    if (primary == null || sessionManager.getPrimaryDeviceIdStr() != null && sessionManager.getPrimaryDeviceIdStr().equals(d.getId())) {
                        primary = d;
                    }
                }
                binding.tvOnlineDevices.setText(String.valueOf(onlineCount));

                if (primary != null) {
                    binding.tvPrimaryDeviceName.setText(primary.getDeviceName());
                    binding.tvProtectionStatus.setText(primary.isProtectionEnabled() ? "Active & Protected" : "Protection Paused");
                    sessionManager.setPrimaryDeviceIdStr(primary.getId());
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
                    binding.tvAlertsCount.setText(String.valueOf(events.size()));
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
