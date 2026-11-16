package com.example.securetrack.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.securetrack.databinding.ActivityDeviceRegistrationBinding;
import com.example.securetrack.models.Device;
import com.example.securetrack.models.PairingRequest;
import com.example.securetrack.models.PairingResponse;
import com.example.securetrack.repositories.DeviceRepository;
import com.example.securetrack.utils.DeviceUtils;
import com.example.securetrack.utils.SessionManager;

public class DeviceRegistrationActivity extends AppCompatActivity {

    private ActivityDeviceRegistrationBinding binding;
    private DeviceRepository deviceRepository;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeviceRegistrationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        deviceRepository = new DeviceRepository(this);
        sessionManager = new SessionManager(this);

        binding.etDeviceName.setText(DeviceUtils.getDeviceModel());

        binding.rgSetupMethod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == binding.rbAnotherDevice.getId()) {
                binding.layoutPairingCode.setVisibility(View.VISIBLE);
            } else {
                binding.layoutPairingCode.setVisibility(View.GONE);
            }
        });

        binding.btnGeneratePairing.setOnClickListener(v -> generatePairingCode());
        binding.btnCompleteRegistration.setOnClickListener(v -> completeRegistration());
    }

    private void generatePairingCode() {
        deviceRepository.createPairingCode(new DeviceRepository.ApiCallback<PairingResponse>() {
            @Override
            public void onSuccess(PairingResponse result) {
                binding.etPairingCode.setText(result.getCode());
                Toast.makeText(DeviceRegistrationActivity.this, "Pairing code valid for 10 mins: " + result.getCode(), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(DeviceRegistrationActivity.this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void completeRegistration() {
        String name = binding.etDeviceName.getText() != null ? binding.etDeviceName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            Toast.makeText(this, "Device name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        if (binding.rbAnotherDevice.isChecked()) {
            String code = binding.etPairingCode.getText() != null ? binding.etPairingCode.getText().toString().trim() : "";
            if (code.isEmpty()) {
                Toast.makeText(this, "Enter 6-digit pairing code", Toast.LENGTH_SHORT).show();
                return;
            }

            PairingRequest request = new PairingRequest(
                    code,
                    sessionManager.getInstallationId(),
                    name,
                    DeviceUtils.getDeviceModel(),
                    DeviceUtils.getAndroidVersion()
            );

            deviceRepository.completePairing(request, new DeviceRepository.ApiCallback<Device>() {
                @Override
                public void onSuccess(Device result) {
                    sessionManager.setPrimaryDeviceId(result.getId());
                    Toast.makeText(DeviceRegistrationActivity.this, "Device Paired Successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onError(String errorMessage) {
                    Toast.makeText(DeviceRegistrationActivity.this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Device device = new Device(
                    name,
                    DeviceUtils.getDeviceModel(),
                    DeviceUtils.getAndroidVersion(),
                    sessionManager.getInstallationId(),
                    DeviceUtils.getBatteryLevel(this)
            );

            deviceRepository.registerDevice(device, new DeviceRepository.ApiCallback<Device>() {
                @Override
                public void onSuccess(Device result) {
                    sessionManager.setPrimaryDeviceId(result.getId());
                    Toast.makeText(DeviceRegistrationActivity.this, "Device Registered Successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onError(String errorMessage) {
                    Toast.makeText(DeviceRegistrationActivity.this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
