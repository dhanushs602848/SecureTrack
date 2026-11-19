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
import com.example.securetrack.activities.DeviceRegistrationActivity;
import com.example.securetrack.activities.DeviceSecurityActivity;
import com.example.securetrack.adapters.DeviceAdapter;
import com.example.securetrack.databinding.FragmentDevicesBinding;
import com.example.securetrack.models.Device;
import com.example.securetrack.repositories.DeviceRepository;
import java.util.List;

public class DevicesFragment extends Fragment {

    private FragmentDevicesBinding binding;
    private DeviceRepository deviceRepository;
    private DeviceAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDevicesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        deviceRepository = new DeviceRepository(requireContext());
        adapter = new DeviceAdapter();

        binding.rvDevices.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvDevices.setAdapter(adapter);

        adapter.setOnDeviceClickListener(device -> {
            Intent intent = new Intent(requireContext(), DeviceSecurityActivity.class);
            intent.putExtra("device_id", device.getId());
            intent.putExtra("device_name", device.getDeviceName());
            startActivity(intent);
        });

        binding.fabAddDevice.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), DeviceRegistrationActivity.class));
        });

        loadDevices();
    }

    private void loadDevices() {
        deviceRepository.getUserDevices(new DeviceRepository.ApiCallback<List<Device>>() {
            @Override
            public void onSuccess(List<Device> result) {
                if (getContext() == null) return;
                adapter.setDevices(result);
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
        loadDevices();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
