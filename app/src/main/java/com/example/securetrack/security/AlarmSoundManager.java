package com.example.securetrack.security;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.example.securetrack.R;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages remote loud anti-theft panic security siren playback.
 * Uses an authentic synthesized rising-falling urgent panic siren waveform,
 * strictly enforces a 5-second (5,000 ms) maximum duration,
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
    private MediaPlayer activeMediaPlayer;
    private Ringtone activeRingtone;
    private ToneGenerator activeToneGenerator;
    private final List<AlarmCallback> activeCallbacks = new ArrayList<>();

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
     * Plays the panic security siren for exactly 5 seconds, then automatically stops and releases resources.
     */
    public synchronized void playFiveSecondAlarm(AlarmCallback callback) {
        if (isPlaying) {
            Log.w(TAG, "Panic security siren is already playing, registering callback with active cycle");
            if (callback != null) {
                activeCallbacks.add(callback);
                callback.onAlarmStarted();
            }
            return;
        }

        isPlaying = true;
        activeCallbacks.clear();
        if (callback != null) {
            activeCallbacks.add(callback);
        }
        Log.i(TAG, "Starting 5-second panic security siren");

        try {
            boolean audioStarted = false;

            // 1. Primary: Play synthesized urgent panic security siren asset
            try {
                activeMediaPlayer = MediaPlayer.create(context, R.raw.panic_security_siren);
                if (activeMediaPlayer != null) {
                    activeMediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                    activeMediaPlayer.setLooping(false);
                    activeMediaPlayer.setOnCompletionListener(mp -> Log.i(TAG, "Panic security siren finished cycle"));
                    activeMediaPlayer.start();
                    audioStarted = true;
                    Log.i(TAG, "Playing authentic panic security siren waveform via MediaPlayer");
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not initialize raw panic siren audio, falling back: " + e.getMessage());
            }

            // 2. Secondary fallback: System Alarm Ringtone
            if (!audioStarted) {
                Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmUri == null) {
                    alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
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
                        audioStarted = true;
                    }
                }
            }

            // 3. Tertiary fallback: ToneGenerator on STREAM_ALARM
            if (!audioStarted) {
                activeToneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
                activeToneGenerator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, (int) ALARM_DURATION_MS);
            }

            if (callback != null) {
                callback.onAlarmStarted();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error initiating panic security siren sound", e);
            cleanupAudio();
            isPlaying = false;
            notifyAllCallbacksCompleted("Failed to initiate audio: " + e.getMessage());
            return;
        }

        // Schedule guaranteed automatic stop at exactly 5000ms
        stopRunnable = () -> {
            synchronized (AlarmSoundManager.this) {
                if (isPlaying) {
                    Log.i(TAG, "5 seconds elapsed. Stopping panic security siren automatically.");
                    cleanupAudio();
                    isPlaying = false;
                    notifyAllCallbacksCompleted("Alarm completed automatically after 5 seconds");
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
        Log.i(TAG, "Manual panic siren stop requested");
        if (stopRunnable != null) {
            mainHandler.removeCallbacks(stopRunnable);
            stopRunnable = null;
        }
        cleanupAudio();
        isPlaying = false;
        notifyAllCallbacksCompleted("Alarm stopped manually");
    }

    private void notifyAllCallbacksCompleted(String reason) {
        List<AlarmCallback> callbacksToNotify;
        synchronized (this) {
            callbacksToNotify = new ArrayList<>(activeCallbacks);
            activeCallbacks.clear();
        }
        for (AlarmCallback cb : callbacksToNotify) {
            try {
                cb.onAlarmCompleted(reason);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying alarm callback", e);
            }
        }
    }

    private void cleanupAudio() {
        try {
            if (activeMediaPlayer != null) {
                if (activeMediaPlayer.isPlaying()) {
                    activeMediaPlayer.stop();
                }
                activeMediaPlayer.reset();
                activeMediaPlayer.release();
                activeMediaPlayer = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error stopping media player", e);
        }

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
