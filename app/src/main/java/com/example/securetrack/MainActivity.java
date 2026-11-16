package com.example.securetrack;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.securetrack.databinding.ActivityMainBinding;
import com.example.securetrack.fragments.*;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

    public void navigateToTrackingTab() {
        binding.bottomNavigation.setSelectedItemId(R.id.navigation_tracking);
    }
}
