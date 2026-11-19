package com.example.securetrack.activities;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.securetrack.adapters.CommandAdapter;
import com.example.securetrack.databinding.ActivityDeviceSecurityBinding;
import com.example.securetrack.models.DeviceCommand;
import com.example.securetrack.repositories.SecurityRepository;
import com.example.securetrack.utils.SessionManager;
import java.util.ArrayList;
import java.util.List;

public class DeviceSecurityActivity extends AppCompatActivity {

    private ActivityDeviceSecurityBinding binding;
    private SecurityRepository securityRepository;
    private SessionManager sessionManager;
    private CommandAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeviceSecurityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        securityRepository = new SecurityRepository(this);
        sessionManager = new SessionManager(this);

        adapter = new CommandAdapter();
        binding.rvCommands.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCommands.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnLockDevice.setOnClickListener(v -> dispatchCommand("LOCK_DEVICE"));
        binding.btnAlarm.setOnClickListener(v -> dispatchCommand("ALARM_ALERT"));
        binding.btnWipeDevice.setOnClickListener(v -> dispatchCommand("WIPE_DEVICE"));

        loadCommandHistory();
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

        DeviceCommand cmd = new DeviceCommand(commandType, "QUEUED", "Manual trigger from security dashboard", "");
        securityRepository.createCommand(deviceId, cmd, new SecurityRepository.ApiCallback<DeviceCommand>() {
            @Override
            public void onSuccess(DeviceCommand result) {
                Toast.makeText(DeviceSecurityActivity.this, "Command Sent", Toast.LENGTH_SHORT).show();
                loadCommandHistory();
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(DeviceSecurityActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadCommandHistory() {
        String deviceId = getTargetDeviceId();
        if (deviceId == null) return;

        securityRepository.getDeviceCommands(deviceId, new SecurityRepository.ApiCallback<List<DeviceCommand>>() {
            @Override
            public void onSuccess(List<DeviceCommand> result) {
                adapter.setCommands(result);
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(DeviceSecurityActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
