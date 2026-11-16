package com.example.securetrack.activities;

import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.securetrack.adapters.CommandAdapter;
import com.example.securetrack.databinding.ActivityDeviceSecurityBinding;
import com.example.securetrack.models.DeviceCommand;
import com.example.securetrack.repositories.SecurityRepository;
import com.example.securetrack.security.SecureAdminReceiver;
import com.example.securetrack.utils.SessionManager;
import java.util.List;

public class DeviceSecurityActivity extends AppCompatActivity {

    private ActivityDeviceSecurityBinding binding;
    private SecurityRepository securityRepository;
    private CommandAdapter adapter;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeviceSecurityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        securityRepository = new SecurityRepository(this);
        sessionManager = new SessionManager(this);

        adapter = new CommandAdapter();
        binding.rvCommandHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCommandHistory.setAdapter(adapter);

        updateAdminSwitchState();

        binding.switchDeviceAdmin.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!SecureAdminReceiver.isAdminActive(this)) {
                    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, SecureAdminReceiver.getComponentName(this));
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "SecureTrack needs Device Admin privileges to allow authorized remote locking.");
                    startActivity(intent);
                }
            } else {
                Toast.makeText(this, "Deactivate Device Admin in Android Settings -> Security -> Device Admin Apps", Toast.LENGTH_LONG).show();
            }
        });

        loadCommandHistory();
    }

    private void updateAdminSwitchState() {
        boolean active = SecureAdminReceiver.isAdminActive(this);
        binding.switchDeviceAdmin.setChecked(active);
    }

    private void loadCommandHistory() {
        long deviceId = sessionManager.getPrimaryDeviceId();
        if (deviceId == -1) return;

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

    @Override
    protected void onResume() {
        super.onResume();
        updateAdminSwitchState();
        loadCommandHistory();
    }
}
