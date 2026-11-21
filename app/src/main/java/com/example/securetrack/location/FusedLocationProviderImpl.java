package com.example.securetrack.location;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import com.example.securetrack.utils.PermissionUtils;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

public class FusedLocationProviderImpl implements ISecureLocationProvider {

    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;

    public FusedLocationProviderImpl(Context context) {
        this.context = context.getApplicationContext();
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(this.context);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void getCurrentLocation(LocationCallback callback) {
        if (!PermissionUtils.hasForegroundLocationPermission(context)) {
            callback.onFailure(new SecurityException("Location permission denied"));
            return;
        }

        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(15000)
                .setMaxUpdateAgeMillis(10000)
                .build();

        CancellationTokenSource cts = new CancellationTokenSource();

        try {
            fusedLocationClient.getCurrentLocation(request, cts.getToken())
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            callback.onSuccess(location);
                        } else {
                            getLastKnownLocation(callback);
                        }
                    })
                    .addOnFailureListener(callback::onFailure);
        } catch (SecurityException se) {
            callback.onFailure(se);
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void getLastKnownLocation(LocationCallback callback) {
        if (!PermissionUtils.hasForegroundLocationPermission(context)) {
            callback.onFailure(new SecurityException("Location permission denied"));
            return;
        }

        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            callback.onSuccess(location);
                        } else {
                            callback.onFailure(new Exception("GPS/Network location provider returned null"));
                        }
                    })
                    .addOnFailureListener(callback::onFailure);
        } catch (SecurityException se) {
            callback.onFailure(se);
        }
    }
}
