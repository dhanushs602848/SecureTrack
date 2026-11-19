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

    private boolean isValidImei(String imei) {
        if (imei == null || imei.length() != 15 || !imei.matches("\\d+")) {
            return false;
        }
        int sum = 0;
        for (int i = 14; i >= 0; i--) {
            int digit = imei.charAt(i) - '0';
            if ((14 - i) % 2 != 0) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return sum % 10 == 0;
    }

    private void handleRegistration() {
        if (isPairingMode) {
            submitPairingCode();
        } else {
            String name = binding.etDeviceName.getText() != null ? binding.etDeviceName.getText().toString().trim() : "";
            if (TextUtils.isEmpty(name)) name = DeviceUtils.getDeviceName();

            String imei1 = binding.etImei1.getText() != null ? binding.etImei1.getText().toString().trim() : "";
            String imei2 = binding.etImei2.getText() != null ? binding.etImei2.getText().toString().trim() : "";

            binding.tilImei1.setError(null);
            binding.tilImei2.setError(null);

            if (!TextUtils.isEmpty(imei1) && !isValidImei(imei1)) {
                binding.tilImei1.setError("Invalid IMEI. Must be 15 digits and pass checksum.");
                binding.etImei1.requestFocus();
                return;
            }
            if (!TextUtils.isEmpty(imei2) && !isValidImei(imei2)) {
                binding.tilImei2.setError("Invalid IMEI. Must be 15 digits and pass checksum.");
                binding.etImei2.requestFocus();
                return;
            }

            if ((!TextUtils.isEmpty(imei1) || !TextUtils.isEmpty(imei2)) && !binding.cbImeiConsent.isChecked()) {
                Toast.makeText(this, "Please check the consent box to store IMEI information.", Toast.LENGTH_LONG).show();
                return;
            }

            binding.btnCompleteRegistration.setEnabled(false);
            Device newDevice = new Device(
                    name,
                    DeviceUtils.getDeviceModel(),
                    DeviceUtils.getAndroidVersion(),
                    sessionManager.getInstallationId(),
                    DeviceUtils.getBatteryPercentage(this)
            );
            
            if (!TextUtils.isEmpty(imei1)) newDevice.setImei1(imei1);
            if (!TextUtils.isEmpty(imei2)) newDevice.setImei2(imei2);

            deviceRepository.registerDevice(newDevice, new DeviceRepository.ApiCallback<Device>() {
                @Override
                public void onSuccess(Device result) {
                    binding.btnCompleteRegistration.setEnabled(true);
                    sessionManager.setCurrentDeviceId(result.getId());
                    sessionManager.setDeviceName(result.getDeviceName());
                    Toast.makeText(DeviceRegistrationActivity.this, "Device Registered Successfully", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(DeviceRegistrationActivity.this, "Generated Code: " + result.getCode() + " (expires in 10 mins)", Toast.LENGTH_LONG).show();
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
        if (TextUtils.isEmpty(code) || code.length() != 8) {
            Toast.makeText(this, "Please enter a valid 8-digit pairing code", Toast.LENGTH_SHORT).show();
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
                sessionManager.setCurrentDeviceId(result.getId());
                sessionManager.setDeviceName(result.getDeviceName());
                Toast.makeText(DeviceRegistrationActivity.this, "Paired Successfully to Owner Account", Toast.LENGTH_SHORT).show();
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
