package com.example.securetrack.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.activity.result.ActivityResultLauncher;
import androidx.credentials.CredentialManager;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import com.example.securetrack.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import java.util.concurrent.Executors;

public class GoogleAuthManager {

    private static final String TAG = "GoogleAuthManager";

    public interface GoogleSignInCallback {
        void onSuccess(String idToken);
        void onError(String errorMessage);
    }

    public static String getWebClientId(Context context) {
        try {
            return context.getString(R.string.default_web_client_id);
        } catch (Exception e) {
            return "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com";
        }
    }

    public static void signInWithCredentialManager(Activity activity, GoogleSignInCallback callback) {
        String webClientId = getWebClientId(activity);
        if (webClientId.contains("YOUR_WEB_CLIENT_ID")) {
            callback.onError("Google Cloud Web Client ID not configured. Please configure R.string.default_web_client_id or GOOGLE_CLIENT_ID.");
            return;
        }

        CredentialManager credentialManager = CredentialManager.create(activity);

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                activity,
                request,
                new android.os.CancellationSignal(),
                Executors.newSingleThreadExecutor(),
                new androidx.credentials.CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        try {
                            if (result.getCredential() instanceof GoogleIdTokenCredential) {
                                GoogleIdTokenCredential googleIdTokenCredential = (GoogleIdTokenCredential) result.getCredential();
                                String idToken = googleIdTokenCredential.getIdToken();
                                activity.runOnUiThread(() -> callback.onSuccess(idToken));
                            } else {
                                activity.runOnUiThread(() -> callback.onError("Unsupported credential type"));
                            }
                        } catch (Exception e) {
                            activity.runOnUiThread(() -> callback.onError(e.getLocalizedMessage()));
                        }
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        Log.e(TAG, "Credential Manager error: " + e.getMessage());
                        activity.runOnUiThread(() -> callback.onError("Credential Manager: " + e.getLocalizedMessage()));
                    }
                }
        );
    }

    public static GoogleSignInClient getGoogleSignInClient(Activity activity) {
        String webClientId = getWebClientId(activity);
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();
        return GoogleSignIn.getClient(activity, gso);
    }

    public static void handleSignInResult(Intent data, GoogleSignInCallback callback) {
        try {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account != null && account.getIdToken() != null) {
                callback.onSuccess(account.getIdToken());
            } else {
                callback.onError("Failed to obtain Google ID Token");
            }
        } catch (ApiException e) {
            callback.onError("Google Sign-In failed code (" + e.getStatusCode() + "): " + e.getLocalizedMessage());
        }
    }
}
