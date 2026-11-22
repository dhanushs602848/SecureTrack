package com.example.securetrack.activities;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.securetrack.BuildConfig;
import com.example.securetrack.R;
import com.example.securetrack.databinding.ActivityDiagnosticsBinding;
import com.example.securetrack.fcm.FcmTokenManager;
import com.example.securetrack.utils.DeviceUtils;
import com.example.securetrack.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

public class DiagnosticsActivity extends AppCompatActivity {

    private ActivityDiagnosticsBinding binding;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDiagnosticsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        binding.btnBack.setOnClickListener(v -> finish());

        populateStaticDiagnostics();
        refreshLiveStatus();

        binding.btnDiagRefreshFcm.setOnClickListener(v -> forceRefreshFcmToken());
        binding.btnDiagRunTest.setOnClickListener(v -> runLiveSelfTest());
    }

    private void populateStaticDiagnostics() {
        binding.tvDiagVersionName.setText(BuildConfig.VERSION_NAME);
        binding.tvDiagVersionCode.setText(String.valueOf(BuildConfig.VERSION_CODE));
        binding.tvDiagPackage.setText(getPackageName());

        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toUpperCase() : "UNKNOWN";
        String model = Build.MODEL != null ? Build.MODEL : "Android Device";
        binding.tvDiagModel.setText(manufacturer + " " + model);

        String os = "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        binding.tvDiagOs.setText(os);

        String installationId = sessionManager.getInstallationId();
        binding.tvDiagInstallationId.setText(installationId);
    }

    private void refreshLiveStatus() {
        // 1. Firebase Auth Status
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String email = user.getEmail();
            String displayAuth = !TextUtils.isEmpty(email) ? "Authenticated (" + email + ")" : "Authenticated (Anonymous)";
            binding.tvDiagFirebaseAuth.setText(displayAuth);
            binding.tvDiagFirebaseAuth.setTextColor(getColor(R.color.status_success));
            binding.tvDiagAuthUid.setText(DeviceUtils.redactIdentifier(user.getUid()));
        } else {
            binding.tvDiagFirebaseAuth.setText("Unauthenticated / Offline");
            binding.tvDiagFirebaseAuth.setTextColor(getColor(R.color.status_warning));
            binding.tvDiagAuthUid.setText("None");
        }

        // 2. FCM Status
        String fcmToken = sessionManager.getFcmToken();
        if (!TextUtils.isEmpty(fcmToken)) {
            binding.tvDiagFcmStatus.setText("Registered & Active");
            binding.tvDiagFcmStatus.setTextColor(getColor(R.color.status_success));
            String redactedToken = fcmToken.length() > 16
                    ? fcmToken.substring(0, 8) + "..." + fcmToken.substring(fcmToken.length() - 8)
                    : fcmToken;
            binding.tvDiagFcmToken.setText(redactedToken);
        } else {
            binding.tvDiagFcmStatus.setText("Pending Registration");
            binding.tvDiagFcmStatus.setTextColor(getColor(R.color.status_warning));
            binding.tvDiagFcmToken.setText("Not registered yet");
        }
    }

    private void forceRefreshFcmToken() {
        binding.btnDiagRefreshFcm.setEnabled(false);
        Toast.makeText(this, "Requesting new FCM registration token...", Toast.LENGTH_SHORT).show();

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            binding.btnDiagRefreshFcm.setEnabled(true);
            if (task.isSuccessful() && task.getResult() != null) {
                String token = task.getResult();
                sessionManager.setFcmToken(token);
                FcmTokenManager.registerOrRefreshFcmToken(this);
                refreshLiveStatus();
                Toast.makeText(this, "FCM token updated successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to retrieve FCM token: " + (task.getException() != null ? task.getException().getMessage() : "Unknown"), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void runLiveSelfTest() {
        binding.btnDiagRunTest.setEnabled(false);
        binding.btnDiagRunTest.setText("Testing Cloud Connection...");

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        // Ping Firestore metadata or check connectivity
        db.collection("system").document("ping").get().addOnCompleteListener(task -> {
            binding.btnDiagRunTest.setEnabled(true);
            binding.btnDiagRunTest.setText("Run Live Connection Self-Test");

            if (task.isSuccessful()) {
                binding.tvDiagFirestoreStatus.setText("Online (Verified)");
                binding.tvDiagFirestoreStatus.setTextColor(getColor(R.color.status_success));
                Toast.makeText(this, "Diagnostics self-test passed: Cloud connected!", Toast.LENGTH_SHORT).show();
            } else {
                // If permission denied or network error, still indicates reachable server or specific error
                Exception ex = task.getException();
                String err = ex != null ? ex.getMessage() : "Unknown";
                if (err != null && err.contains("PERMISSION_DENIED")) {
                    binding.tvDiagFirestoreStatus.setText("Online (Security Rules Active)");
                    binding.tvDiagFirestoreStatus.setTextColor(getColor(R.color.status_success));
                    Toast.makeText(this, "Self-test: Connected to Firestore securely.", Toast.LENGTH_SHORT).show();
                } else {
                    binding.tvDiagFirestoreStatus.setText("Error: " + err);
                    binding.tvDiagFirestoreStatus.setTextColor(getColor(R.color.status_error));
                    Toast.makeText(this, "Connection check: " + err, Toast.LENGTH_LONG).show();
                }
            }
            refreshLiveStatus();
        });
    }
}
