package com.example.securetrack;

import android.content.Context;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.securetrack.databinding.ActivityMainBinding;
import com.example.securetrack.fragments.*;
import com.example.securetrack.security.LocationSecurityManager;
import org.osmdroid.config.Configuration;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize osmdroid configuration before inflating layouts containing MapView
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue("SecureTrack/1.0 (dhanushs602848@gmail.com; Android Tracker)");
        java.io.File basePath = new java.io.File(ctx.getCacheDir(), "osmdroid");
        java.io.File tileCache = new java.io.File(basePath, "tiles");
        if (!tileCache.exists()) {
            tileCache.mkdirs();
        }
        Configuration.getInstance().setOsmdroidBasePath(basePath);
        Configuration.getInstance().setOsmdroidTileCache(tileCache);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.navigation_devices) {
                selectedFragment = new DevicesFragment();
            } else if (itemId == R.id.navigation_tracking) {
                selectedFragment = new TrackingFragment();
            } else if (itemId == R.id.navigation_security) {
                selectedFragment = new SecurityFragment();
            } else if (itemId == R.id.navigation_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_container, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });

        // Default tab: HomeFragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_container, new HomeFragment())
                    .commit();
        }

        // Reactive Active Security Alert Badge on Bottom Navigation
        com.example.securetrack.repositories.SecurityRepository securityRepo = new com.example.securetrack.repositories.SecurityRepository(this);
        securityRepo.getActiveAlertCountLiveData().observe(this, activeCount -> {
            int count = activeCount != null ? activeCount : 0;
            com.google.android.material.badge.BadgeDrawable badge = binding.bottomNavigation.getOrCreateBadge(R.id.navigation_security);
            if (count > 0) {
                badge.setVisible(true);
                badge.setNumber(count);
                badge.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.status_error));
                badge.setBadgeTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.white));
            } else {
                badge.setVisible(false);
                badge.clearNumber();
            }
        });

        handleNavigationIntent(getIntent());

        // Predictable bottom navigation back-stack behavior: return to Home before exiting
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding != null && binding.bottomNavigation.getSelectedItemId() != R.id.navigation_home) {
                    binding.bottomNavigation.setSelectedItemId(R.id.navigation_home);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        checkUserRequestedExit();
        checkBiometricAuthentication();
    }

    private static boolean isSessionAuthenticated = false;

    public static void resetSessionAuthentication() {
        isSessionAuthenticated = false;
    }

    private void checkBiometricAuthentication() {
        com.example.securetrack.utils.SessionManager sm = new com.example.securetrack.utils.SessionManager(this);
        if (sm.isBiometricLockEnabled() && !isSessionAuthenticated && !sm.isPairedSecondaryDevice()) {
            if (com.example.securetrack.security.BiometricAuthHelper.canAuthenticate(this)) {
                binding.getRoot().setVisibility(android.view.View.INVISIBLE);
                com.example.securetrack.security.BiometricAuthHelper.authenticate(
                        this,
                        "SecureTrack Identity Verification",
                        "Scan fingerprint, face, or enter device PIN to access SecureTrack",
                        new com.example.securetrack.security.BiometricAuthHelper.AuthCallback() {
                            @Override
                            public void onAuthSuccess() {
                                isSessionAuthenticated = true;
                                binding.getRoot().setVisibility(android.view.View.VISIBLE);
                            }

                            @Override
                            public void onAuthError(int errorCode, @androidx.annotation.NonNull CharSequence errString) {
                                android.widget.Toast.makeText(MainActivity.this, "Authentication required to access SecureTrack", android.widget.Toast.LENGTH_SHORT).show();
                                finish();
                            }

                            @Override
                            public void onAuthFailed() {
                                // Biometric prompt allows retries or fallback to device credential
                            }
                        }
                );
            }
        } else {
            binding.getRoot().setVisibility(android.view.View.VISIBLE);
        }
    }

    @Override
    protected void onNewIntent(@androidx.annotation.NonNull android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNavigationIntent(intent);
    }

    private void handleNavigationIntent(android.content.Intent intent) {
        if (intent == null) return;
        String navigateTo = intent.getStringExtra("navigate_to");
        if ("security".equalsIgnoreCase(navigateTo)) {
            binding.bottomNavigation.setSelectedItemId(R.id.navigation_security);
        } else if ("tracking".equalsIgnoreCase(navigateTo)) {
            binding.bottomNavigation.setSelectedItemId(R.id.navigation_tracking);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkBiometricAuthentication();
        // Check location state when the app resumes to identify missed transitions
        LocationSecurityManager.getInstance(this).checkLocationState();
        // Update device heartbeat on app resume
        com.example.securetrack.utils.SessionManager sm = new com.example.securetrack.utils.SessionManager(this);
        if (sm.isLoggedIn()) {
            new com.example.securetrack.repositories.DeviceRepository(this).sendHeartbeat(null);
            com.example.securetrack.security.CommandExecutionManager.getInstance(this).startListeningToCommands();
        }
    }

    private String selectedDeviceIdForTracking;

    public String getSelectedDeviceIdForTracking() {
        return selectedDeviceIdForTracking;
    }

    public void navigateToTrackingTabWithDevice(String deviceId) {
        this.selectedDeviceIdForTracking = deviceId;
        binding.bottomNavigation.setSelectedItemId(R.id.navigation_tracking);
    }

    public void navigateToTrackingTab() {
        binding.bottomNavigation.setSelectedItemId(R.id.navigation_tracking);
    }

    private void checkUserRequestedExit() {
        com.example.securetrack.utils.SessionManager sm = new com.example.securetrack.utils.SessionManager(this);
        // Only evaluate if user's saved preference was that background tracking is enabled
        if (!sm.isBackgroundTrackingEnabled()) {
            return;
        }

        // 1. If foreground protection service is still running after Recents swipe-away,
        // preserve its active state and continue protection without prompting.
        if (com.example.securetrack.location.LocationTrackingService.isServiceRunning(this)) {
            return;
        }

        // 2. Service has actually stopped while background tracking was supposed to be active.
        // Check if there was an exit reason or process termination.
        long lastPrompt = sm.getLastUserExitPromptTimestamp();
        boolean hasInterruption = false;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    java.util.List<android.app.ApplicationExitInfo> exitInfos =
                            am.getHistoricalProcessExitReasons(getPackageName(), 0, 1);
                    if (exitInfos != null && !exitInfos.isEmpty()) {
                        android.app.ApplicationExitInfo latestExit = exitInfos.get(0);
                        if (latestExit.getTimestamp() > lastPrompt) {
                            hasInterruption = true;
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("MainActivity", "Could not inspect ApplicationExitInfo: " + e.getMessage());
            }
        } else {
            hasInterruption = true;
        }

        if (hasInterruption) {
            sm.setLastUserExitPromptTimestamp(System.currentTimeMillis());
            sm.setBackgroundTrackingEnabled(false);
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Protection Service Interrupted")
                    .setMessage("SecureTrack protection was stopped because the application process was terminated. Endpoint tracking is currently paused. Would you like to resume protection?")
                    .setPositiveButton("Resume Protection", (dialog, which) -> navigateToTrackingTab())
                    .setNegativeButton("Dismiss", null)
                    .show();
        }
    }
}
