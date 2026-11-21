package com.example.securetrack.utils;

import com.google.firebase.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Pure Java utility for parsing and formatting Firebase Timestamps.
 */
public class TimestampUtils {

    public static Timestamp parseTimestamp(String timestampStr) {
        if (timestampStr != null && !timestampStr.trim().isEmpty()) {
            String trimmed = timestampStr.trim();
            try {
                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = isoFormat.parse(trimmed);
                if (date != null) return new Timestamp(date);
            } catch (Exception ignored) {}

            try {
                long epoch = Long.parseLong(trimmed);
                return new Timestamp(new Date(epoch));
            } catch (Exception ignored) {}

            try {
                SimpleDateFormat displayFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US);
                Date date = displayFormat.parse(trimmed);
                if (date != null) return new Timestamp(date);
            } catch (Exception ignored) {}
        }
        return new Timestamp(new Date());
    }
}
