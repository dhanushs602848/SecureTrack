package com.example.securetrack.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.securetrack.database.AppDatabase;
import com.example.securetrack.database.DeviceEntity;
import com.example.securetrack.databinding.ActivityLostDeviceBinding;
import com.example.securetrack.location.LocationPriorityManager;
import com.example.securetrack.models.DeviceCommand;
import com.example.securetrack.models.ResolvedLocation;
import com.example.securetrack.repositories.SecurityRepository;
import com.example.securetrack.security.AlarmSoundManager;
import com.example.securetrack.utils.SessionManager;
import java.util.Locale;

public class LostDeviceActivity extends AppCompatActivity {

    private ActivityLostDeviceBinding binding;
    private SessionManager sessionManager;
    private SecurityRepository securityRepository;
    private LocationPriorityManager locationPriorityManager;
    private String targetDeviceId;
    private boolean isLostMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLostDeviceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);
        securityRepository = new SecurityRepository(this);
        locationPriorityManager = new LocationPriorityManager(this);

        targetDeviceId = getIntent().getStringExtra("device_id");
        if (targetDeviceId == null || targetDeviceId.isEmpty()) {
            targetDeviceId = sessionManager.getCurrentDeviceId();
        }

        String targetDeviceName = getIntent().getStringExtra("device_name");
        if (targetDeviceName == null || targetDeviceName.isEmpty()) {
            targetDeviceName = sessionManager.getDeviceName();
        }

        binding.tvDeviceName.setText("Target: " + (targetDeviceName != null ? targetDeviceName : "This Device"));
        binding.btnBack.setOnClickListener(v -> finish());

        // Find Hub Button: Links to official Android Find My Device
        binding.btnOpenFindHub.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://android.com/find"));
            startActivity(browserIntent);
        });

        // 5s Alarm Button
        binding.btnTrigger5sAlarm.setOnClickListener(v -> {
            boolean isLocal = targetDeviceId != null && targetDeviceId.equals(sessionManager.getCurrentDeviceId());
            if (isLocal) {
                AlarmSoundManager.getInstance(this).playFiveSecondAlarm(new AlarmSoundManager.AlarmCallback() {
                    @Override
                    public void onAlarmStarted() {
                        Toast.makeText(LostDeviceActivity.this, "5-Second loud alarm playing...", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAlarmCompleted(String reason) {
                        Toast.makeText(LostDeviceActivity.this, "Alarm ended: " + reason, Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                dispatchRemoteCommand("ALARM_ALERT");
            }
        });

        // Lock Device Emergency
        binding.btnLockDeviceEmergency.setOnClickListener(v -> dispatchRemoteCommand("LOCK_DEVICE"));

        // Locate Now Button
        binding.btnRetryLocate.setOnClickListener(v -> refreshLocation());

        // Toggle Lost Mode
        binding.btnToggleLostMode.setOnClickListener(v -> toggleLostMode());

        loadDeviceState();
        refreshLocation();
    }

    private void loadDeviceState() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            DeviceEntity entity = AppDatabase.getInstance(this).deviceDao().getDeviceById(targetDeviceId);
            if (entity != null) {
                runOnUiThread(() -> {
                    isLostMode = entity.isLostMode();
                    updateLostModeUi();
                    if (entity.getBatteryLevel() >= 0) {
                        binding.tvBatteryLevel.setText(entity.getBatteryLevel() + "%");
                    }
                    if (entity.getLastSeen() != null && !entity.getLastSeen().isEmpty()) {
                        binding.tvLastSeen.setText(entity.getLastSeen());
                    }
                });
            }
        });
    }

    private void updateLostModeUi() {
        if (isLostMode) {
            binding.chipLostStatus.setText("LOST MODE");
            binding.chipLostStatus.setTextColor(getColor(com.example.securetrack.R.color.coral_error));
            binding.btnToggleLostMode.setText("Device Recovered (Deactivate Lost Mode)");
        } else {
            binding.chipLostStatus.setText("NORMAL");
            binding.chipLostStatus.setTextColor(getColor(com.example.securetrack.R.color.mint_green));
            binding.btnToggleLostMode.setText("Mark Device as Lost");
        }
    }

    private void toggleLostMode() {
        isLostMode = !isLostMode;
        updateLostModeUi();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase.getInstance(this).deviceDao().setLostMode(targetDeviceId, isLostMode);
        });

        Toast.makeText(this, isLostMode ? "Lost Mode Activated" : "Device marked as recovered", Toast.LENGTH_SHORT).show();
    }

    private void refreshLocation() {
        binding.btnRetryLocate.setEnabled(false);
        binding.tvLocationSource.setText("Resolving multi-tier location...");

        locationPriorityManager.resolveTargetLocation(targetDeviceId, new LocationPriorityManager.LocationResolutionCallback() {
            @Override
            public void onLocationResolved(ResolvedLocation location) {
                runOnUiThread(() -> {
                    binding.btnRetryLocate.setEnabled(true);
                    binding.tvLocationSource.setText("Priority: " + location.getPathway().name());
                    binding.tvAccuracy.setText(String.format(Locale.US, "Accuracy ±%.1fm", location.getAccuracy()));
                    binding.tvCoordinates.setText(String.format(Locale.US, "%.5f° N, %.5f° E", location.getLatitude(), location.getLongitude()));
                });
            }

            @Override
            public void onProgress(String statusUpdate) {
                runOnUiThread(() -> binding.tvLocationSource.setText(statusUpdate));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    binding.btnRetryLocate.setEnabled(true);
                    binding.tvLocationSource.setText("Resolution: " + message);
                });
            }
        });
    }

    private void dispatchRemoteCommand(String commandType) {
        if (targetDeviceId == null) {
            Toast.makeText(this, "Device not identified", Toast.LENGTH_SHORT).show();
            return;
        }

        DeviceCommand cmd = new DeviceCommand(commandType, "QUEUED", "Lost Mode emergency trigger", "");
        securityRepository.createCommand(targetDeviceId, cmd, new SecurityRepository.ApiCallback<DeviceCommand>() {
            @Override
            public void onSuccess(DeviceCommand result) {
                Toast.makeText(LostDeviceActivity.this, "Command " + commandType + " dispatched", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(LostDeviceActivity.this, "Failed: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }
}
