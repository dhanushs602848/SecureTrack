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
import com.example.securetrack.adapters.SecurityAlertAdapter;
import com.example.securetrack.databinding.FragmentSecurityBinding;
import com.example.securetrack.models.DeviceCommand;
import com.example.securetrack.models.SecurityEvent;
import com.example.securetrack.repositories.SecurityRepository;
import com.example.securetrack.utils.SessionManager;
import java.util.List;

public class SecurityFragment extends Fragment {

    private FragmentSecurityBinding binding;
    private SecurityRepository securityRepository;
    private SessionManager sessionManager;
    private SecurityAlertAdapter adapter;

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

        adapter = new SecurityAlertAdapter();
        binding.rvSecurityAlerts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvSecurityAlerts.setAdapter(adapter);

        binding.swipeRefresh.setOnRefreshListener(this::loadAlerts);

        binding.btnLockDevice.setOnClickListener(v -> dispatchCommand("LOCK_DEVICE"));
        binding.btnTriggerAlarm.setOnClickListener(v -> dispatchCommand("ALARM_ALERT"));
        binding.btnDeviceAdmin.setOnClickListener(v -> startActivity(new Intent(requireContext(), DeviceSecurityActivity.class)));

        loadAlerts();
    }

    private void dispatchCommand(String commandType) {
        String deviceId = sessionManager.getCurrentDeviceId();
        if (deviceId == null) {
            Toast.makeText(requireContext(), "No device registered on this installation", Toast.LENGTH_SHORT).show();
            return;
        }

        DeviceCommand cmd = new DeviceCommand(commandType, "QUEUED", "Manual user trigger from app", "");
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

    private void loadAlerts() {
        binding.swipeRefresh.setRefreshing(true);
        securityRepository.getUserSecurityEvents(new SecurityRepository.ApiCallback<List<SecurityEvent>>() {
            @Override
            public void onSuccess(List<SecurityEvent> result) {
                if (binding != null) {
                    binding.swipeRefresh.setRefreshing(false);
                    adapter.setEvents(result);
                    if (result.isEmpty()) {
                        binding.tvEmptyState.setVisibility(View.VISIBLE);
                        binding.rvSecurityAlerts.setVisibility(View.GONE);
                    } else {
                        binding.tvEmptyState.setVisibility(View.GONE);
                        binding.rvSecurityAlerts.setVisibility(View.VISIBLE);
                    }
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
