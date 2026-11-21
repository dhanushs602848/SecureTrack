package com.example.securetrack.activities;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.securetrack.adapters.CommandAdapter;
import com.example.securetrack.databinding.ActivityDeviceSecurityBinding;
import com.example.securetrack.models.DeviceCommand;
import com.example.securetrack.repositories.DeviceRepository;
import com.example.securetrack.repositories.SecurityRepository;
import com.example.securetrack.security.SecureAdminReceiver;
import com.example.securetrack.utils.SessionManager;
import java.util.ArrayList;
import java.util.List;

public class DeviceSecurityActivity extends AppCompatActivity {

    private ActivityDeviceSecurityBinding binding;
    private SecurityRepository securityRepository;
    private DeviceRepository deviceRepository;
    private SessionManager sessionManager;
    private CommandAdapter adapter;
    private com.google.firebase.firestore.ListenerRegistration commandsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeviceSecurityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        securityRepository = new SecurityRepository(this);
        deviceRepository = new DeviceRepository(this);
        sessionManager = new SessionManager(this);

        setupTargetDeviceHeader();

        adapter = new CommandAdapter();
        binding.rvCommands.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCommands.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnLocateDevice.setOnClickListener(v -> dispatchCommand("LOCATE_NOW"));
        binding.btnLockDevice.setOnClickListener(v -> dispatchCommand("LOCK_DEVICE"));
        binding.btnAlarm.setOnClickListener(v -> {
            com.example.securetrack.security.AlarmSoundManager alarmManager = com.example.securetrack.security.AlarmSoundManager.getInstance(this);
            if (alarmManager.isPlaying()) {
                alarmManager.stopAlarm();
                binding.btnAlarm.setText(com.example.securetrack.R.string.btn_alarm);
                android.widget.Toast.makeText(this, "Panic siren stopped manually", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            String targetId = getTargetDeviceId();
            if (targetId != null && targetId.equals(sessionManager.getCurrentDeviceId())) {
                binding.btnAlarm.setText("Stop Siren");
                alarmManager.playFiveSecondAlarm(new com.example.securetrack.security.AlarmSoundManager.AlarmCallback() {
                    @Override
                    public void onAlarmStarted() {
                        android.widget.Toast.makeText(DeviceSecurityActivity.this, "Panic security siren active (5s)", android.widget.Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAlarmCompleted(String reason) {
                        runOnUiThread(() -> binding.btnAlarm.setText(com.example.securetrack.R.string.btn_alarm));
                    }
                });
            }
            dispatchCommand("ALARM_ALERT");
        });
        binding.btnWipeDevice.setOnClickListener(v -> confirmWipeDevice());
        binding.btnRevokeDevice.setOnClickListener(v -> confirmRevocation());

        setupDeviceAdminSwitch();
        subscribeToCommands();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.switchDeviceAdmin.setChecked(SecureAdminReceiver.isAdminActive(this));
        subscribeToCommands();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCommandsListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCommandsListener();
    }

    private void stopCommandsListener() {
        if (commandsListener != null) {
            commandsListener.remove();
            commandsListener = null;
        }
    }

    private void setupTargetDeviceHeader() {
        String deviceName = getIntent().getStringExtra("device_name");
        if (deviceName == null || deviceName.trim().isEmpty() || isUuid(deviceName.trim())) {
            String targetDevId = getTargetDeviceId();
            if (targetDevId != null && targetDevId.equals(sessionManager.getCurrentDeviceId())) {
                deviceName = sessionManager.getDeviceName();
            }
        }

        if (deviceName != null && !deviceName.trim().isEmpty() && !isUuid(deviceName.trim())) {
            binding.tvTargetDeviceName.setText(getString(com.example.securetrack.R.string.target_device_format, deviceName.trim()));
            binding.tvTargetDeviceName.setVisibility(View.VISIBLE);
        } else {
            binding.tvTargetDeviceName.setVisibility(View.GONE);
        }
    }

    private boolean isUuid(String text) {
        if (text == null) return false;
        return text.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }

    private void setupDeviceAdminSwitch() {
        binding.switchDeviceAdmin.setChecked(SecureAdminReceiver.isAdminActive(this));
        binding.switchDeviceAdmin.setOnClickListener(v -> {
            if (binding.switchDeviceAdmin.isChecked()) {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                        new ComponentName(this, SecureAdminReceiver.class));
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Enables authorized remote lock and anti-theft security capabilities.");
                startActivity(intent);
            } else {
                DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
                ComponentName adminComponent = new ComponentName(this, SecureAdminReceiver.class);
                if (dpm != null) {
                    dpm.removeActiveAdmin(adminComponent);
                    binding.switchDeviceAdmin.setChecked(false);
                    Toast.makeText(this, "Device Admin Disabled", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private String getTargetDeviceId() {
        String devId = getIntent().getStringExtra("device_id");
        if (devId != null && !devId.isEmpty()) {
            return devId;
        }
        return sessionManager.getCurrentDeviceId();
    }

    private void dispatchCommand(String commandType) {
        String deviceId = getTargetDeviceId();
        if (deviceId == null) {
            Toast.makeText(this, "No device selected", Toast.LENGTH_SHORT).show();
            return;
        }

        String toastMsg = "LOCATE_NOW".equalsIgnoreCase(commandType)
                ? "Locate request dispatched • Waiting for target..."
                : "Command Sent";
        Toast.makeText(DeviceSecurityActivity.this, toastMsg, Toast.LENGTH_SHORT).show();

        DeviceCommand cmd = new DeviceCommand(commandType, "QUEUED", "Manual trigger from security dashboard", "");
        securityRepository.createCommand(deviceId, cmd, new SecurityRepository.ApiCallback<DeviceCommand>() {
            @Override
            public void onSuccess(DeviceCommand result) {
                // Real-time listener will automatically update the list
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(DeviceSecurityActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void subscribeToCommands() {
        stopCommandsListener();
        String deviceId = getTargetDeviceId();
        if (deviceId == null) return;

        commandsListener = securityRepository.listenToDeviceCommands(deviceId, new SecurityRepository.ApiCallback<List<DeviceCommand>>() {
            @Override
            public void onSuccess(List<DeviceCommand> result) {
                if (result == null || result.isEmpty()) {
                    binding.tvEmptyCommands.setVisibility(View.VISIBLE);
                    binding.tvCommandsCount.setText("0 commands");
                    adapter.setCommands(new ArrayList<>());
                } else {
                    binding.tvEmptyCommands.setVisibility(View.GONE);
                    String countText = result.size() == 1 ? "1 command" : result.size() + " commands";
                    binding.tvCommandsCount.setText(countText);
                    adapter.setCommands(result);
                }
            }

            @Override
            public void onError(String errorMessage) {
                securityRepository.getDeviceCommands(deviceId, new SecurityRepository.ApiCallback<List<DeviceCommand>>() {
                    @Override
                    public void onSuccess(List<DeviceCommand> list) {
                        if (list == null || list.isEmpty()) {
                            binding.tvEmptyCommands.setVisibility(View.VISIBLE);
                            binding.tvCommandsCount.setText("0 commands");
                            adapter.setCommands(new ArrayList<>());
                        } else {
                            binding.tvEmptyCommands.setVisibility(View.GONE);
                            binding.tvCommandsCount.setText(list.size() + " commands");
                            adapter.setCommands(list);
                        }
                    }

                    @Override
                    public void onError(String err) {
                        binding.tvEmptyCommands.setVisibility(View.VISIBLE);
                        binding.tvCommandsCount.setText("0 commands");
                    }
                });
            }
        });
    }

    private void confirmWipeDevice() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Remote Wipe Feature Disabled")
                .setMessage("Factory wipe operations are strictly disabled in this build to protect against accidental data loss.")
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton("OK", null)
                .show();
    }

    private void confirmRevocation() {
        String deviceId = getTargetDeviceId();
        if (deviceId == null || deviceId.isEmpty()) {
            Toast.makeText(this, "No device selected", Toast.LENGTH_SHORT).show();
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Revoke Device Access")
                .setMessage("Are you sure you want to revoke this device? It will immediately lose authorization to report telemetry, sync location, and execute commands.")
                .setPositiveButton("Revoke", (dialog, which) -> {
                    deviceRepository.revokePairedDevice(deviceId, new DeviceRepository.ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Toast.makeText(DeviceSecurityActivity.this, "Device access revoked successfully", Toast.LENGTH_LONG).show();
                            finish();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Toast.makeText(DeviceSecurityActivity.this, "Failed to revoke device: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
