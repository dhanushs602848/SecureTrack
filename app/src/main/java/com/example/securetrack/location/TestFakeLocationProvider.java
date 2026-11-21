package com.example.securetrack.location;

import android.location.Location;

public class TestFakeLocationProvider implements ISecureLocationProvider {

    private Location mockLocation;
    private Exception mockError;

    public TestFakeLocationProvider() {}

    public TestFakeLocationProvider(double latitude, double longitude, float accuracy) {
        setMockLocation(latitude, longitude, accuracy);
    }

    public void setMockLocation(double latitude, double longitude, float accuracy) {
        Location loc = new Location("TestFakeProvider");
        loc.setLatitude(latitude);
        loc.setLongitude(longitude);
        loc.setAccuracy(accuracy);
        loc.setTime(System.currentTimeMillis());
        this.mockLocation = loc;
        this.mockError = null;
    }

    public void setMockError(Exception error) {
        this.mockError = error;
        this.mockLocation = null;
    }

    @Override
    public void getCurrentLocation(LocationCallback callback) {
        if (mockError != null) {
            callback.onFailure(mockError);
        } else if (mockLocation != null) {
            callback.onSuccess(mockLocation);
        } else {
            callback.onFailure(new Exception("Mock location not configured"));
        }
    }

    @Override
    public void getLastKnownLocation(LocationCallback callback) {
        getCurrentLocation(callback);
    }
}
