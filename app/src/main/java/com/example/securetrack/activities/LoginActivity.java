package com.example.securetrack.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.securetrack.MainActivity;
import com.example.securetrack.databinding.ActivityLoginBinding;
import com.example.securetrack.utils.GoogleAuthManager;
import com.example.securetrack.utils.SessionManager;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int RC_GOOGLE_SIGN_IN = 9001;

    private ActivityLoginBinding binding;
    private SessionManager sessionManager;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnLogin.setOnClickListener(v -> performLogin());
        binding.btnGoogleSignIn.setOnClickListener(v -> performGoogleSignIn());

        binding.tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });

        binding.tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
    }

    private void performLogin() {
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnLogin.setEnabled(false);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().getUser() != null) {
                        FirebaseUser firebaseUser = task.getResult().getUser();
                        fetchUserProfileAndNavigate(firebaseUser);
                    } else {
                        binding.btnLogin.setEnabled(true);
                        String errorMsg = task.getException() != null ? task.getException().getLocalizedMessage() : "Authentication failed";
                        Toast.makeText(LoginActivity.this, "Login Error: " + errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void performGoogleSignIn() {
        binding.btnGoogleSignIn.setEnabled(false);
        GoogleAuthManager.signInWithCredentialManager(this, new GoogleAuthManager.GoogleSignInCallback() {
            @Override
            public void onSuccess(String idToken) {
                firebaseAuthWithGoogle(idToken);
            }

            @Override
            public void onError(String errorMessage) {
                try {
                    Intent signInIntent = GoogleAuthManager.getGoogleSignInClient(LoginActivity.this).getSignInIntent();
                    startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
                } catch (Exception e) {
                    binding.btnGoogleSignIn.setEnabled(true);
                    Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            GoogleAuthManager.handleSignInResult(data, new GoogleAuthManager.GoogleSignInCallback() {
                @Override
                public void onSuccess(String idToken) {
                    firebaseAuthWithGoogle(idToken);
                }

                @Override
                public void onError(String errorMessage) {
                    binding.btnGoogleSignIn.setEnabled(true);
                    Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().getUser() != null) {
                        FirebaseUser firebaseUser = task.getResult().getUser();
                        saveGoogleUserToFirestoreAndNavigate(firebaseUser);
                    } else {
                        binding.btnGoogleSignIn.setEnabled(true);
                        String errorMsg = task.getException() != null ? task.getException().getLocalizedMessage() : "Google authentication failed";
                        Toast.makeText(LoginActivity.this, "Google Sign-In Error: " + errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void fetchUserProfileAndNavigate(FirebaseUser firebaseUser) {
        String uid = firebaseUser.getUid();
        String email = firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "";

        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    binding.btnLogin.setEnabled(true);
                    String fullName = documentSnapshot.getString("fullName");
                    if (TextUtils.isEmpty(fullName)) {
                        fullName = firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : email;
                    }
                    sessionManager.saveFirebaseAuthSession(uid, email, fullName);
                    Toast.makeText(LoginActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    binding.btnLogin.setEnabled(true);
                    Log.w(TAG, "Failed to fetch user profile from Firestore: " + e.getMessage());
                    sessionManager.saveFirebaseAuthSession(uid, email, firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : email);
                    Toast.makeText(LoginActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                });
    }

    private void saveGoogleUserToFirestoreAndNavigate(FirebaseUser firebaseUser) {
        String uid = firebaseUser.getUid();
        String email = firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "";
        String fullName = firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : email;

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", uid);
        userMap.put("email", email);
        userMap.put("fullName", fullName);
        userMap.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("users").document(uid)
                .set(userMap, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    binding.btnGoogleSignIn.setEnabled(true);
                    sessionManager.saveFirebaseAuthSession(uid, email, fullName);
                    Toast.makeText(LoginActivity.this, "Google Sign-In Successful", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    binding.btnGoogleSignIn.setEnabled(true);
                    Log.w(TAG, "Failed to save Google user to Firestore: " + e.getMessage());
                    sessionManager.saveFirebaseAuthSession(uid, email, fullName);
                    Toast.makeText(LoginActivity.this, "Google Sign-In Successful", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                });
    }

    private void showForgotPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Reset Password");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Registered Email");
        builder.setView(input);

        builder.setPositiveButton("Send Link", (dialog, which) -> {
            String email = input.getText().toString().trim();
            if (!TextUtils.isEmpty(email)) {
                mAuth.sendPasswordResetEmail(email)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(LoginActivity.this, "Password reset email sent to " + email, Toast.LENGTH_LONG).show();
                            } else {
                                String errorMsg = task.getException() != null ? task.getException().getLocalizedMessage() : "Failed to send reset email";
                                Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                            }
                        });
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}
