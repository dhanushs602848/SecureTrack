package com.example.securetrack.activities;

import android.os.Bundle;
import android.text.TextUtils;
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
    private boolean isPairingMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeviceRegistrationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        deviceRepository = new DeviceRepository(this);
        sessionManager = new SessionManager(this);

        binding.rbThisPhone.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                isPairingMode = false;
                binding.layoutPairingCode.setVisibility(View.GONE);
                binding.btnCompleteRegistration.setText("Register This Device");
            }
        });

        binding.rbAnotherDevice.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                isPairingMode = true;
                binding.layoutPairingCode.setVisibility(View.VISIBLE);
                binding.btnCompleteRegistration.setText("Submit Pairing Code");
            }
        });

        binding.btnCompleteRegistration.setOnClickListener(v -> handleRegistration());

        binding.btnGeneratePairing.setOnClickListener(v -> generatePairingCode());
    }

    private void handleRegistration() {
        if (isPairingMode) {
            submitPairingCode();
        } else {
            String name = binding.etDeviceName.getText() != null ? binding.etDeviceName.getText().toString().trim() : "";
            if (TextUtils.isEmpty(name)) name = DeviceUtils.getDeviceName();

            binding.btnCompleteRegistration.setEnabled(false);
            Device newDevice = new Device(
                    name,
                    DeviceUtils.getDeviceModel(),
                    DeviceUtils.getAndroidVersion(),
                    sessionManager.getInstallationId(),
                    DeviceUtils.getBatteryPercentage(this)
            );

            deviceRepository.registerDevice(newDevice, new DeviceRepository.ApiCallback<Device>() {
                @Override
                public void onSuccess(Device result) {
                    binding.btnCompleteRegistration.setEnabled(true);
                    sessionManager.setPrimaryDeviceIdStr(result.getId());
                    Toast.makeText(DeviceRegistrationActivity.this, "Device Registered", Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onError(String errorMessage) {
                    binding.btnCompleteRegistration.setEnabled(true);
                    Toast.makeText(DeviceRegistrationActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void generatePairingCode() {
        binding.btnGeneratePairing.setEnabled(false);
        deviceRepository.createPairingCode(new DeviceRepository.ApiCallback<PairingResponse>() {
            @Override
            public void onSuccess(PairingResponse result) {
                binding.btnGeneratePairing.setEnabled(true);
                Toast.makeText(DeviceRegistrationActivity.this, "Code: " + result.getCode() + " (expires in 10 mins)", Toast.LENGTH_LONG).show();
                binding.etPairingCode.setText(result.getCode());
            }

            @Override
            public void onError(String errorMessage) {
                binding.btnGeneratePairing.setEnabled(true);
                Toast.makeText(DeviceRegistrationActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void submitPairingCode() {
        String code = binding.etPairingCode.getText() != null ? binding.etPairingCode.getText().toString().trim() : "";
        if (TextUtils.isEmpty(code)) {
            Toast.makeText(this, "Enter pairing code", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnCompleteRegistration.setEnabled(false);
        PairingRequest request = new PairingRequest(
                code,
                DeviceUtils.getDeviceName(),
                DeviceUtils.getDeviceModel(),
                DeviceUtils.getAndroidVersion(),
                sessionManager.getInstallationId()
        );

        deviceRepository.completePairing(request, new DeviceRepository.ApiCallback<Device>() {
            @Override
            public void onSuccess(Device result) {
                binding.btnCompleteRegistration.setEnabled(true);
                sessionManager.setPrimaryDeviceIdStr(result.getId());
                Toast.makeText(DeviceRegistrationActivity.this, "Pairing Successful", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                binding.btnCompleteRegistration.setEnabled(true);
                Toast.makeText(DeviceRegistrationActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }
}
