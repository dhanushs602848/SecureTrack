package com.example.securetrack.security;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import java.util.concurrent.Executor;

/**
 * Biometric & Device Credential Authentication Shield for SecureTrack.
 * Protects Owner monitoring, remote command dispatches, and administrative controls.
 */
public class BiometricAuthHelper {

    public interface AuthCallback {
        void onAuthSuccess();
        void onAuthError(int errorCode, @NonNull CharSequence errString);
        void onAuthFailed();
    }

    /**
     * Checks if the device has biometric hardware and/or secure lock credentials (PIN/Pattern/Password).
     */
    public static boolean canAuthenticate(@NonNull Context context) {
        BiometricManager biometricManager = BiometricManager.from(context);
        int authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        int canAuth = biometricManager.canAuthenticate(authenticators);
        return canAuth == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /**
     * Prompts the user to authenticate using Biometrics (Fingerprint/Face) or Device Credential (PIN/Pattern/Password).
     */
    public static void authenticate(
            @NonNull FragmentActivity activity,
            @NonNull String title,
            @NonNull String subtitle,
            @NonNull AuthCallback callback
    ) {
        if (!canAuthenticate(activity)) {
            // If device has no screen lock or biometrics setup, treat as bypassable or success
            callback.onAuthSuccess();
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(activity);

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        callback.onAuthError(errorCode, errString);
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        callback.onAuthSuccess();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        callback.onAuthFailed();
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }
}
