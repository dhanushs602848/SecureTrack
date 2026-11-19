package com.example.securetrack.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.securetrack.MainActivity;
import com.example.securetrack.databinding.ActivityRegisterBinding;
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

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";
    private static final int RC_GOOGLE_SIGN_IN = 9002;

    private ActivityRegisterBinding binding;
    private SessionManager sessionManager;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnRegister.setOnClickListener(v -> performRegister());
        binding.btnGoogleRegister.setOnClickListener(v -> performGoogleSignUp());

        binding.tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void performRegister() {
        String name = binding.etFullName.getText() != null ? binding.etFullName.getText().toString().trim() : "";
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String phone = binding.etPhone.getText() != null ? binding.etPhone.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";
        String confirmPassword = binding.etConfirmPassword.getText() != null ? binding.etConfirmPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, getString(com.example.securetrack.R.string.password_too_short), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, getString(com.example.securetrack.R.string.passwords_do_not_match), Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnRegister.setEnabled(false);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().getUser() != null) {
                        FirebaseUser firebaseUser = task.getResult().getUser();
                        saveUserProfileToFirestoreAndNavigate(firebaseUser, name, phone);
                    } else {
                        binding.btnRegister.setEnabled(true);
                        Exception exp = task.getException();
                        String errorMsg;
                        if (exp instanceof com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                            errorMsg = "An account with this email already exists. Please sign in.";
                        } else if (exp instanceof com.google.firebase.auth.FirebaseAuthWeakPasswordException) {
                            errorMsg = "Password is too weak. Please use at least 6 characters.";
                        } else if (exp instanceof com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                            errorMsg = "Invalid email address format.";
                        } else {
                            errorMsg = exp != null ? exp.getLocalizedMessage() : "Registration failed";
                        }
                        Toast.makeText(RegisterActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void performGoogleSignUp() {
        binding.btnGoogleRegister.setEnabled(false);
        GoogleAuthManager.signInWithCredentialManager(this, new GoogleAuthManager.GoogleSignInCallback() {
            @Override
            public void onSuccess(String idToken) {
                firebaseAuthWithGoogle(idToken);
            }

            @Override
            public void onError(String errorMessage) {
                try {
                    Intent signInIntent = GoogleAuthManager.getGoogleSignInClient(RegisterActivity.this).getSignInIntent();
                    startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
                } catch (Exception e) {
                    binding.btnGoogleRegister.setEnabled(true);
                    Toast.makeText(RegisterActivity.this, errorMessage, Toast.LENGTH_LONG).show();
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
                    binding.btnGoogleRegister.setEnabled(true);
                    Toast.makeText(RegisterActivity.this, errorMessage, Toast.LENGTH_LONG).show();
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
                        binding.btnGoogleRegister.setEnabled(true);
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "Google registration failed";
                        Toast.makeText(RegisterActivity.this, "Google Registration Error: " + errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserProfileToFirestoreAndNavigate(FirebaseUser firebaseUser, String name, String phone) {
        String uid = firebaseUser.getUid();
        String email = firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "";

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", uid);
        userMap.put("email", email);
        userMap.put("fullName", name);
        userMap.put("phoneNumber", phone);
        userMap.put("createdAt", FieldValue.serverTimestamp());

        db.collection("users").document(uid)
                .set(userMap, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    binding.btnRegister.setEnabled(true);
                    sessionManager.saveFirebaseAuthSession(uid, email, name);
                    Toast.makeText(RegisterActivity.this, "Account Created Successfully", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    binding.btnRegister.setEnabled(true);
                    Log.e(TAG, "Failed to save profile to Firestore", e);
                    Toast.makeText(RegisterActivity.this, "Failed to save user profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
                    binding.btnGoogleRegister.setEnabled(true);
                    sessionManager.saveFirebaseAuthSession(uid, email, fullName);
                    Toast.makeText(RegisterActivity.this, "Google Registration Successful", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    binding.btnGoogleRegister.setEnabled(true);
                    Log.e(TAG, "Failed to save Google profile to Firestore", e);
                    Toast.makeText(RegisterActivity.this, "Failed to save Google user profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
