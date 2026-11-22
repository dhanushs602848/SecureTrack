package com.example.securetrack.activities;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.securetrack.R;
import com.example.securetrack.api.ApiClient;
import com.example.securetrack.databinding.ActivitySettingsBinding;
import com.example.securetrack.security.SecureAdminReceiver;
import com.example.securetrack.utils.SessionManager;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        binding.btnBack.setOnClickListener(v -> finish());

        // 0. Appearance Theme Customisation (Mandatory 3 Modes)
        setupAppearanceSelection();

        // 1. Emergency Recovery Contacts
        binding.etEmergencyContact.setText(sessionManager.getEmergencyContact());
        binding.etLockMessage.setText(sessionManager.getLockMessage());

        binding.btnSaveRecoveryInfo.setOnClickListener(v -> {
            String contact = binding.etEmergencyContact.getText() != null ? binding.etEmergencyContact.getText().toString().trim() : "";
            String message = binding.etLockMessage.getText() != null ? binding.etLockMessage.getText().toString().trim() : "";
            sessionManager.setEmergencyContact(contact);
            sessionManager.setLockMessage(message);
            Toast.makeText(this, "Emergency recovery details saved", Toast.LENGTH_SHORT).show();
        });

        // 2. Anti-Theft Protection Triggers
        binding.switchSimAlert.setChecked(sessionManager.isSimAlertEnabled());
        binding.switchSimAlert.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setSimAlertEnabled(isChecked);
            Toast.makeText(this, isChecked ? "SIM change detection enabled" : "SIM change detection disabled", Toast.LENGTH_SHORT).show();
        });

        binding.switchLowBatteryAlert.setChecked(sessionManager.isLowBatteryAlertEnabled());
        binding.switchLowBatteryAlert.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setLowBatteryAlertEnabled(isChecked);
            Toast.makeText(this, isChecked ? "Low battery beacon enabled" : "Low battery beacon disabled", Toast.LENGTH_SHORT).show();
        });

        binding.switchBackgroundGps.setChecked(sessionManager.isBackgroundTrackingEnabled());
        binding.switchBackgroundGps.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setBackgroundTrackingEnabled(isChecked);
            Toast.makeText(this, isChecked ? "Background GPS enabled" : "Background GPS disabled", Toast.LENGTH_SHORT).show();
        });

        binding.switchBiometricLock.setChecked(sessionManager.isBiometricLockEnabled());
        binding.switchBiometricLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !com.example.securetrack.security.BiometricAuthHelper.canAuthenticate(this)) {
                binding.switchBiometricLock.setChecked(false);
                Toast.makeText(this, "Biometrics or device lock credentials are not configured on this phone.", Toast.LENGTH_LONG).show();
                return;
            }
            sessionManager.setBiometricLockEnabled(isChecked);
            Toast.makeText(this, isChecked ? "Biometric App Shield enabled" : "Biometric App Shield disabled", Toast.LENGTH_SHORT).show();
        });

        // 3. Device Administrator Privilege
        binding.btnManageAdmin.setOnClickListener(v -> {
            boolean isAdmin = SecureAdminReceiver.isAdminActive(this);
            if (!isAdmin) {
                ComponentName adminComponent = new ComponentName(this, SecureAdminReceiver.class);
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enables SecureTrack to remotely lock device in case of theft.");
                startActivity(intent);
            } else {
                try {
                    Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Device Administrator is active", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 4. Backward Compatibility for Legacy API Endpoint
        if (binding.etServerUrl != null) {
            binding.etServerUrl.setText(sessionManager.getBaseUrl());
        }

        if (binding.btnSaveSettings != null) {
            binding.btnSaveSettings.setOnClickListener(v -> {
                String url = binding.etServerUrl.getText() != null ? binding.etServerUrl.getText().toString().trim() : "";
                if (!TextUtils.isEmpty(url)) {
                    sessionManager.setBaseUrl(url);
                    ApiClient.resetClient();
                    Toast.makeText(this, "Server URL updated successfully", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(this, "URL cannot be empty", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 5. System Diagnostics & Health Check Screen
        if (binding.tvSettingsVersion != null) {
            binding.tvSettingsVersion.setText(com.example.securetrack.BuildConfig.VERSION_NAME + " (Build " + com.example.securetrack.BuildConfig.VERSION_CODE + ")");
        }
        if (binding.btnOpenDiagnostics != null) {
            binding.btnOpenDiagnostics.setOnClickListener(v -> {
                startActivity(new Intent(this, DiagnosticsActivity.class));
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAdminStatus();
        updateDiagnosticsSummary();
    }

    private void updateDiagnosticsSummary() {
        if (binding.tvSettingsFcmStatus != null) {
            String token = sessionManager.getFcmToken();
            if (!TextUtils.isEmpty(token)) {
                binding.tvSettingsFcmStatus.setText("Registered & Active");
                binding.tvSettingsFcmStatus.setTextColor(getColor(R.color.status_success));
            } else {
                binding.tvSettingsFcmStatus.setText("Pending Registration");
                binding.tvSettingsFcmStatus.setTextColor(getColor(R.color.status_warning));
            }
        }
    }

    private void updateAdminStatus() {
        boolean isAdmin = SecureAdminReceiver.isAdminActive(this);
        if (isAdmin) {
            binding.tvAdminStatus.setText("Active & Granted");
            binding.tvAdminStatus.setTextColor(getColor(R.color.status_success));
            binding.btnManageAdmin.setText("Manage in Security Settings");
        } else {
            binding.tvAdminStatus.setText("Not Activated");
            binding.tvAdminStatus.setTextColor(getColor(R.color.status_warning));
            binding.btnManageAdmin.setText("Enable Device Admin");
        }
    }

    private void setupAppearanceSelection() {
        updateAppearanceUI(sessionManager.getThemeMode());

        binding.cardThemeLight.setOnClickListener(v -> {
            sessionManager.setThemeMode(SessionManager.THEME_LIGHT);
            updateAppearanceUI(SessionManager.THEME_LIGHT);
            Toast.makeText(this, "Light mode activated", Toast.LENGTH_SHORT).show();
        });

        binding.cardThemeDark.setOnClickListener(v -> {
            sessionManager.setThemeMode(SessionManager.THEME_DARK);
            updateAppearanceUI(SessionManager.THEME_DARK);
            Toast.makeText(this, "Dark mode activated", Toast.LENGTH_SHORT).show();
        });

        binding.cardThemeSystem.setOnClickListener(v -> {
            sessionManager.setThemeMode(SessionManager.THEME_SYSTEM);
            updateAppearanceUI(SessionManager.THEME_SYSTEM);
            Toast.makeText(this, "Following system theme", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateAppearanceUI(String currentTheme) {
        boolean isLight = SessionManager.THEME_LIGHT.equalsIgnoreCase(currentTheme);
        boolean isDark = SessionManager.THEME_DARK.equalsIgnoreCase(currentTheme);
        boolean isSystem = SessionManager.THEME_SYSTEM.equalsIgnoreCase(currentTheme) || (!isLight && !isDark);

        binding.cardThemeLight.setBackgroundResource(isLight ? R.drawable.bg_theme_card_selected : R.drawable.bg_theme_card_unselected);
        binding.ivCheckLight.setVisibility(isLight ? android.view.View.VISIBLE : android.view.View.INVISIBLE);

        binding.cardThemeDark.setBackgroundResource(isDark ? R.drawable.bg_theme_card_selected : R.drawable.bg_theme_card_unselected);
        binding.ivCheckDark.setVisibility(isDark ? android.view.View.VISIBLE : android.view.View.INVISIBLE);

        binding.cardThemeSystem.setBackgroundResource(isSystem ? R.drawable.bg_theme_card_selected : R.drawable.bg_theme_card_unselected);
        binding.ivCheckSystem.setVisibility(isSystem ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
    }
}
