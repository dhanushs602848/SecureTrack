package com.example.securetrack.location;

import android.location.Location;

/**
 * Abstraction for location acquisition services.
 * Allows deterministic fake injection for automated testing without physical hardware.
 */
public interface ISecureLocationProvider {

    interface LocationCallback {
        void onSuccess(Location location);
        void onFailure(Exception exception);
    }

    void getCurrentLocation(LocationCallback callback);
    void getLastKnownLocation(LocationCallback callback);
}
