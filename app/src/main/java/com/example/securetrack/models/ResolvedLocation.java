package com.example.securetrack.models;

public class ResolvedLocation {

    public enum Pathway {
        CLOUD_LIVE,
        BLE_NEARBY,
        LAST_KNOWN,
        FIND_HUB_READY,
        UNAVAILABLE
    }

    private final Pathway pathway;
    private final double latitude;
    private final double longitude;
    private final float accuracy;
    private final long timestamp;
    private final String timestampFormatted;
    private final String sourceDescription;
    private final boolean isLive;
    private final int batteryPercent; // -1 if not available
    private final String statusLabel; // LIVE, BLE NEARBY, LAST SEEN (STALE)

    public ResolvedLocation(Pathway pathway, double latitude, double longitude, float accuracy,
                            long timestamp, String timestampFormatted, String sourceDescription,
                            boolean isLive, int batteryPercent, String statusLabel) {
        this.pathway = pathway;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.timestamp = timestamp;
        this.timestampFormatted = timestampFormatted;
        this.sourceDescription = sourceDescription;
        this.isLive = isLive;
        this.batteryPercent = batteryPercent;
        this.statusLabel = statusLabel;
    }

    public Pathway getPathway() { return pathway; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public float getAccuracy() { return accuracy; }
    public long getTimestamp() { return timestamp; }
    public String getTimestampFormatted() { return timestampFormatted; }
    public String getSourceDescription() { return sourceDescription; }
    public boolean isLive() { return isLive; }
    public int getBatteryPercent() { return batteryPercent; }
    public String getStatusLabel() { return statusLabel; }
}
