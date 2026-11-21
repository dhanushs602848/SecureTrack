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
import com.example.securetrack.MainActivity;
import com.example.securetrack.activities.DeviceRegistrationActivity;
import com.example.securetrack.activities.DeviceSecurityActivity;
import com.example.securetrack.adapters.DeviceAdapter;
import com.example.securetrack.databinding.FragmentDevicesBinding;
import com.example.securetrack.models.Device;
import com.example.securetrack.repositories.DeviceRepository;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.List;

public class DevicesFragment extends Fragment {

    private FragmentDevicesBinding binding;
    private DeviceRepository deviceRepository;
    private DeviceAdapter adapter;
    private ListenerRegistration devicesListener;

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

        adapter.setOnDeviceClickListener(new DeviceAdapter.OnDeviceClickListener() {
            @Override
            public void onDeviceClick(Device device) {
                openDeviceSecurity(device);
            }

            @Override
            public void onTrackOnMapClick(Device device) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).navigateToTrackingTabWithDevice(device.getId());
                }
            }

            @Override
            public void onSecurityControlsClick(Device device) {
                openDeviceSecurity(device);
            }
        });

        binding.fabAddDevice.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), DeviceRegistrationActivity.class));
        });

        startListeningToDevices();
    }

    private void openDeviceSecurity(Device device) {
        if (device == null || getContext() == null) return;
        Intent intent = new Intent(requireContext(), DeviceSecurityActivity.class);
        intent.putExtra("device_id", device.getId());
        intent.putExtra("device_name", device.getDeviceName());
        startActivity(intent);
    }

    private void startListeningToDevices() {
        stopListeningToDevices();

        devicesListener = deviceRepository.listenToUserDevices(new DeviceRepository.ApiCallback<List<Device>>() {
            @Override
            public void onSuccess(List<Device> result) {
                if (getContext() == null || binding == null) return;
                adapter.setDevices(result);
                if (result == null || result.isEmpty()) {
                    binding.layoutEmptyDevices.setVisibility(View.VISIBLE);
                    binding.rvDevices.setVisibility(View.GONE);
                } else {
                    binding.layoutEmptyDevices.setVisibility(View.GONE);
                    binding.rvDevices.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (getContext() == null || binding == null) return;
                Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void stopListeningToDevices() {
        if (devicesListener != null) {
            devicesListener.remove();
            devicesListener = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startListeningToDevices();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopListeningToDevices();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopListeningToDevices();
        binding = null;
    }
}
