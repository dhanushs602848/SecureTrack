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
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check location state when the app resumes to identify missed transitions
        LocationSecurityManager.getInstance(this).checkLocationState();
        // Update device heartbeat on app resume
        com.example.securetrack.utils.SessionManager sm = new com.example.securetrack.utils.SessionManager(this);
        if (sm.isLoggedIn()) {
            new com.example.securetrack.repositories.DeviceRepository(this).sendHeartbeat(null);
        }
    }

    public void navigateToTrackingTab() {
        binding.bottomNavigation.setSelectedItemId(R.id.navigation_tracking);
    }
}
