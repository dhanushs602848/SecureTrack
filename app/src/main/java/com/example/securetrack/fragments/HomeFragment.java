package com.example.securetrack.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.securetrack.activities.DeviceSecurityActivity;
import com.example.securetrack.adapters.ActivityLogAdapter;
import com.example.securetrack.databinding.FragmentHomeBinding;
import com.example.securetrack.models.Device;
import com.example.securetrack.repositories.DeviceRepository;
import com.example.securetrack.utils.SessionManager;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private SessionManager sessionManager;
    private DeviceRepository deviceRepository;
    private ActivityLogAdapter activityAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionManager = new SessionManager(requireContext());
        deviceRepository = new DeviceRepository(requireContext());

        binding.tvWelcomeName.setText("Welcome, " + sessionManager.getUserName());

        activityAdapter = new ActivityLogAdapter();
        binding.rvRecentActivity.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvRecentActivity.setAdapter(activityAdapter);

        binding.swipeRefresh.setOnRefreshListener(this::loadDashboardData);

        binding.btnTrackLive.setOnClickListener(v -> {
            if (getActivity() instanceof com.example.securetrack.MainActivity) {
                ((com.example.securetrack.MainActivity) getActivity()).navigateToTrackingTab();
            }
        });

        binding.btnSecureDevice.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), DeviceSecurityActivity.class));
        });

        loadDashboardData();
    }

    private void loadDashboardData() {
        binding.swipeRefresh.setRefreshing(true);

        deviceRepository.getUserDevices(new DeviceRepository.ApiCallback<List<Device>>() {
            @Override
            public void onSuccess(List<Device> result) {
                if (getContext() == null) return;
                binding.swipeRefresh.setRefreshing(false);
                int total = result != null ? result.size() : 0;
                binding.tvStatTotalDevices.setText(String.valueOf(total));
                binding.tvStatOnlineDevices.setText(String.valueOf(total));

                if (result != null && !result.isEmpty()) {
                    Device primary = result.get(0);
                    sessionManager.setPrimaryDeviceId(primary.getId());
                    binding.tvPrimaryDeviceName.setText(primary.getDeviceName());
                    binding.tvPrimaryDeviceModel.setText("Model: " + (primary.getModel() != null ? primary.getModel() : "Android"));
                }

                List<String> mockActivity = new ArrayList<>();
                mockActivity.add("Security scan complete — System Healthy");
                mockActivity.add("Location ping sent successfully");
                mockActivity.add("Device registered and active");
                activityAdapter.setActivities(mockActivity);
            }

            @Override
            public void onError(String errorMessage) {
                if (getContext() == null) return;
                binding.swipeRefresh.setRefreshing(false);
                Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
