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
    private SecurityAlertAdapter adapter;
    private SessionManager sessionManager;

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

        binding.btnDeviceSecurityAdmin.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), DeviceSecurityActivity.class));
        });

        binding.btnRemoteLockDialog.setOnClickListener(v -> dispatchRemoteCommand("LOCK_DEVICE"));
        binding.btnTriggerAlarm.setOnClickListener(v -> dispatchRemoteCommand("ALARM_ALERT"));

        loadSecurityAlerts();
    }

    private void dispatchRemoteCommand(String commandType) {
        long deviceId = sessionManager.getPrimaryDeviceId();
        if (deviceId == -1) {
            Toast.makeText(requireContext(), "No primary device registered", Toast.LENGTH_SHORT).show();
            return;
        }

        DeviceCommand cmd = new DeviceCommand(deviceId, commandType, "Manual user trigger from app");
        securityRepository.createCommand(deviceId, cmd, new SecurityRepository.ApiCallback<DeviceCommand>() {
            @Override
            public void onSuccess(DeviceCommand result) {
                Toast.makeText(requireContext(), "Command '" + commandType + "' queued with status: " + result.getStatus(), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(requireContext(), "Error: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadSecurityAlerts() {
        securityRepository.getUserSecurityEvents(new SecurityRepository.ApiCallback<List<SecurityEvent>>() {
            @Override
            public void onSuccess(List<SecurityEvent> result) {
                if (getContext() == null) return;
                adapter.setEvents(result);
            }

            @Override
            public void onError(String errorMessage) {
                if (getContext() == null) return;
                Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSecurityAlerts();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
