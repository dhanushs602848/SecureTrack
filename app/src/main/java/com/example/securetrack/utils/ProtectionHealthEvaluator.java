package com.example.securetrack.utils;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * Evaluates real-time device protection health based on actual hardware
 * states, runtime permissions, and telemetry connectivity.
 */
public class ProtectionHealthEvaluator {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DEGRADED = "DEGRADED";
    public static final String STATUS_NEEDS_ATTENTION = "NEEDS_ATTENTION";
    public static final String STATUS_OFFLINE = "OFFLINE";

    public static class ProtectionReport {
        public final String status;
        public final String reason;
        public final boolean isLocationPermGranted;
        public final boolean isLocationServiceEnabled;
        public final boolean isBluetoothPermGranted;
        public final boolean isBluetoothEnabled;
        public final boolean isConnected;
        public final boolean protectionEnabled;

        public ProtectionReport(
                String status,
                String reason,
                boolean isLocationPermGranted,
                boolean isLocationServiceEnabled,
                boolean isBluetoothPermGranted,
                boolean isBluetoothEnabled,
                boolean isConnected
        ) {
            this.status = status;
            this.reason = reason;
            this.isLocationPermGranted = isLocationPermGranted;
            this.isLocationServiceEnabled = isLocationServiceEnabled;
            this.isBluetoothPermGranted = isBluetoothPermGranted;
            this.isBluetoothEnabled = isBluetoothEnabled;
            this.isConnected = isConnected;
            // Only true when fully active and healthy
            this.protectionEnabled = STATUS_ACTIVE.equals(status);
        }
    }

    @NonNull
    public static ProtectionReport evaluate(@NonNull Context context) {
        // 1. Network Connectivity
        boolean isConnected = false;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            android.net.Network net = cm.getActiveNetwork();
            NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            isConnected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }

        // 2. Location Permission
        boolean hasLocationPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        // 3. Location Service (GPS Switch)
        boolean isLocationEnabled = false;
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLocationEnabled = lm.isLocationEnabled();
            } else {
                isLocationEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            }
        }

        // 4. Bluetooth Permission
        boolean hasBluetoothPerm = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasBluetoothPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }

        // 5. Bluetooth Hardware Enabled
        boolean isBluetoothEnabled = false;
        try {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter != null) {
                isBluetoothEnabled = btAdapter.isEnabled();
            }
        } catch (SecurityException se) {
            // Permission restricted
        }

        // 6. Check if user paused protection in settings
        SessionManager sm = new SessionManager(context);
        boolean isTrackingEnabled = sm.isBackgroundTrackingEnabled();

        // 7. Comprehensive Hierarchical Evaluation
        if (!isTrackingEnabled) {
            return new ProtectionReport(
                    STATUS_DEGRADED,
                    "Protection service paused by user",
                    hasLocationPerm,
                    isLocationEnabled,
                    hasBluetoothPerm,
                    isBluetoothEnabled,
                    isConnected
            );
        }

        if (!hasLocationPerm) {
            return new ProtectionReport(
                    STATUS_NEEDS_ATTENTION,
                    "Location permission denied by user",
                    false,
                    isLocationEnabled,
                    hasBluetoothPerm,
                    isBluetoothEnabled,
                    isConnected
            );
        }

        if (!isLocationEnabled) {
            return new ProtectionReport(
                    STATUS_DEGRADED,
                    "Location services switched off in system settings",
                    true,
                    false,
                    hasBluetoothPerm,
                    isBluetoothEnabled,
                    isConnected
            );
        }

        if (!isConnected) {
            return new ProtectionReport(
                    STATUS_OFFLINE,
                    "Device has no active internet connection",
                    true,
                    true,
                    hasBluetoothPerm,
                    isBluetoothEnabled,
                    false
            );
        }

        if (!hasBluetoothPerm || !isBluetoothEnabled) {
            String btReason = !hasBluetoothPerm ? "Bluetooth permissions denied" : "Bluetooth switched off (BLE nearby peer discovery disabled)";
            return new ProtectionReport(
                    STATUS_DEGRADED,
                    btReason,
                    true,
                    true,
                    hasBluetoothPerm,
                    isBluetoothEnabled,
                    true
            );
        }

        return new ProtectionReport(
                STATUS_ACTIVE,
                "All security & telemetry protection systems operational",
                true,
                true,
                true,
                true,
                true
        );
    }
}
