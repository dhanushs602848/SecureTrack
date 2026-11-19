package com.example.securetrack.security;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.util.Log;

public class LocationStateReceiver extends BroadcastReceiver {
    private static final String TAG = "LocationStateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);
        if (LocationManager.MODE_CHANGED_ACTION.equals(action) ||
            LocationManager.PROVIDERS_CHANGED_ACTION.equals(action)) {

            LocationSecurityManager.getInstance(context).checkLocationState();
        }
    }
}
