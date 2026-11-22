package com.example.securetrack;

import android.app.Application;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

public class SecureTrackApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        com.example.securetrack.utils.SessionManager.applySavedTheme(this);
        FirebaseApp.initializeApp(this);

        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        if (BuildConfig.DEBUG) {
            firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
            );
        } else {
            firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
            );
        }

        com.example.securetrack.sync.DurableSyncManager.initializePeriodicSync(this);
        com.example.securetrack.security.CommandExecutionManager.getInstance(this).startListeningToCommands();
        com.example.securetrack.fcm.FcmTokenManager.registerOrRefreshFcmToken(this);
    }
}
