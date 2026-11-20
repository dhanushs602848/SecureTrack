package com.example.securetrack.security;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Manages remote loud anti-theft alarm playback.
 * Strictly enforces a 5-second (5,000 ms) maximum duration,
 * automatic resource release, manual stop, and duplicate-prevention.
 */
public class AlarmSoundManager {

    private static final String TAG = "AlarmSoundManager";
    public static final long ALARM_DURATION_MS = 5000L; // Exactly 5 seconds

    private static volatile AlarmSoundManager instance;

    private final Context context;
    private final Handler mainHandler;
    private boolean isPlaying = false;
    private Runnable stopRunnable;
    private Ringtone activeRingtone;
    private ToneGenerator activeToneGenerator;

    public interface AlarmCallback {
        void onAlarmStarted();
        void onAlarmCompleted(String reason);
    }

    private AlarmSoundManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static AlarmSoundManager getInstance(Context context) {
        if (instance == null) {
            synchronized (AlarmSoundManager.class) {
                if (instance == null) {
                    instance = new AlarmSoundManager(context);
                }
            }
        }
        return instance;
    }

    public synchronized boolean isPlaying() {
        return isPlaying;
    }

    /**
     * Plays the alarm for exactly 5 seconds, then automatically stops and releases resources.
     */
    public synchronized void playFiveSecondAlarm(AlarmCallback callback) {
        if (isPlaying) {
            Log.w(TAG, "Alarm is already playing, ignoring duplicate request");
            if (callback != null) {
                callback.onAlarmStarted();
            }
            return;
        }

        isPlaying = true;
        Log.i(TAG, "Starting 5-second anti-theft alarm");

        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            if (alarmUri != null) {
                activeRingtone = RingtoneManager.getRingtone(context, alarmUri);
                if (activeRingtone != null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        activeRingtone.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build());
                    }
                    activeRingtone.play();
                }
            }

            // Fallback to high-frequency ToneGenerator if ringtone unavailable
            if (activeRingtone == null || !activeRingtone.isPlaying()) {
                activeToneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
                activeToneGenerator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, (int) ALARM_DURATION_MS);
            }

            if (callback != null) {
                callback.onAlarmStarted();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error initiating alarm sound", e);
            cleanupAudio();
            isPlaying = false;
            if (callback != null) {
                callback.onAlarmCompleted("Failed to initiate audio: " + e.getMessage());
            }
            return;
        }

        // Schedule guaranteed automatic stop at exactly 5000ms
        stopRunnable = () -> {
            synchronized (AlarmSoundManager.this) {
                if (isPlaying) {
                    Log.i(TAG, "5 seconds elapsed. Stopping alarm automatically.");
                    cleanupAudio();
                    isPlaying = false;
                    if (callback != null) {
                        callback.onAlarmCompleted("Alarm completed automatically after 5 seconds");
                    }
                }
            }
        };
        mainHandler.postDelayed(stopRunnable, ALARM_DURATION_MS);
    }

    /**
     * Immediate manual stop.
     */
    public synchronized void stopAlarm() {
        if (!isPlaying) return;
        Log.i(TAG, "Manual alarm stop requested");
        if (stopRunnable != null) {
            mainHandler.removeCallbacks(stopRunnable);
            stopRunnable = null;
        }
        cleanupAudio();
        isPlaying = false;
    }

    private void cleanupAudio() {
        try {
            if (activeRingtone != null) {
                if (activeRingtone.isPlaying()) {
                    activeRingtone.stop();
                }
                activeRingtone = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error stopping ringtone", e);
        }

        try {
            if (activeToneGenerator != null) {
                activeToneGenerator.stopTone();
                activeToneGenerator.release();
                activeToneGenerator = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error releasing tone generator", e);
        }
    }
}
