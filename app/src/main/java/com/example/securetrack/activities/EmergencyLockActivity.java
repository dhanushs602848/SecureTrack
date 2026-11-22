package com.example.securetrack.activities;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.securetrack.databinding.ActivityEmergencyLockBinding;
import com.example.securetrack.models.SecurityEvent;
import com.example.securetrack.repositories.SecurityRepository;
import com.example.securetrack.security.BiometricAuthHelper;
import com.example.securetrack.utils.SessionManager;

/**
 * High-priority Emergency Lock Screen Overlay for SecureTrack.
 * Displays over Android's lock screen when a remote LOCK_DEVICE command is executed,
 * providing the owner's emergency contact, custom recovery message, and biometric unlock.
 */
public class EmergencyLockActivity extends AppCompatActivity {

    private ActivityEmergencyLockBinding binding;
    private SessionManager sessionManager;
    private String emergencyContactNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        configureLockScreenWindow();

        binding = ActivityEmergencyLockBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        setupLockDetails();
        setupActionListeners();
        setupBackPressInterceptor();
    }

    private void configureLockScreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) {
                km.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }
    }

    private void setupLockDetails() {
        String lockMessage = getIntent().getStringExtra("lock_message");
        if (TextUtils.isEmpty(lockMessage)) {
            lockMessage = sessionManager.getLockMessage();
        }
        if (TextUtils.isEmpty(lockMessage)) {
            lockMessage = "This device has been reported lost or stolen. Please contact the owner immediately to return it.";
        }
        binding.tvOwnerMessage.setText(lockMessage);

        emergencyContactNumber = getIntent().getStringExtra("emergency_contact");
        if (TextUtils.isEmpty(emergencyContactNumber)) {
            emergencyContactNumber = sessionManager.getEmergencyContact();
        }
        if (TextUtils.isEmpty(emergencyContactNumber)) {
            emergencyContactNumber = sessionManager.getUserEmail();
        }

        if (!TextUtils.isEmpty(emergencyContactNumber)) {
            binding.tvEmergencyContact.setText(emergencyContactNumber);
        } else {
            binding.tvEmergencyContact.setText("Owner Contact Not Configured");
        }
    }

    private void setupActionListeners() {
        binding.btnCallOwner.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(emergencyContactNumber)) {
                try {
                    Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + emergencyContactNumber.trim()));
                    dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(dialIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "Unable to initiate call: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "No emergency phone number specified by owner.", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnOwnerUnlock.setOnClickListener(v -> attemptOwnerUnlock());
    }

    private void attemptOwnerUnlock() {
        BiometricAuthHelper.authenticate(
                this,
                "Owner Verification",
                "Scan fingerprint or enter device PIN/Password to unlock",
                new BiometricAuthHelper.AuthCallback() {
                    @Override
                    public void onAuthSuccess() {
                        logUnlockEvent();
                        Toast.makeText(EmergencyLockActivity.this, "Device unlocked successfully", Toast.LENGTH_SHORT).show();
                        finish();
                    }

                    @Override
                    public void onAuthError(int errorCode, @NonNull CharSequence errString) {
                        Toast.makeText(EmergencyLockActivity.this, "Unlock failed: " + errString, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthFailed() {
                        // User can retry
                    }
                }
        );
    }

    private void setupBackPressInterceptor() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Prevent bypass - prompt for unlock
                attemptOwnerUnlock();
            }
        });
    }

    private void logUnlockEvent() {
        String deviceId = sessionManager.getPrimaryDeviceIdStr();
        if (deviceId == null) {
            deviceId = sessionManager.getCurrentDeviceId();
        }
        if (deviceId != null) {
            SecurityRepository repo = new SecurityRepository(getApplicationContext());
            SecurityEvent event = new SecurityEvent(deviceId, "EMERGENCY_LOCK_DISMISSED", "INFO", "Emergency lock screen unlocked by owner verification");
            repo.recordSecurityEvent(event, null);
        }
    }
}
