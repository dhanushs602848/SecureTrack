package com.example.securetrack.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.securetrack.MainActivity;
import com.example.securetrack.databinding.ActivityLoginBinding;
import com.example.securetrack.models.AuthResponse;
import com.example.securetrack.repositories.AuthRepository;
import com.example.securetrack.utils.GoogleAuthManager;
import com.example.securetrack.utils.SessionManager;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;

    private ActivityLoginBinding binding;
    private AuthRepository authRepository;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authRepository = new AuthRepository(this);
        sessionManager = new SessionManager(this);

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

        authRepository.login(email, password, new AuthRepository.ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse result) {
                binding.btnLogin.setEnabled(true);
                sessionManager.saveAuthSession(
                        result.getAccessToken(),
                        result.getRefreshToken(),
                        result.getUser().getId(),
                        result.getUser().getEmail(),
                        result.getUser().getFullName()
                );
                Toast.makeText(LoginActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                binding.btnLogin.setEnabled(true);
                Toast.makeText(LoginActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void performGoogleSignIn() {
        binding.btnGoogleSignIn.setEnabled(false);
        GoogleAuthManager.signInWithCredentialManager(this, new GoogleAuthManager.GoogleSignInCallback() {
            @Override
            public void onSuccess(String idToken) {
                sendGoogleTokenToBackend(idToken);
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
                    sendGoogleTokenToBackend(idToken);
                }

                @Override
                public void onError(String errorMessage) {
                    binding.btnGoogleSignIn.setEnabled(true);
                    Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void sendGoogleTokenToBackend(String idToken) {
        authRepository.googleLogin(idToken, new AuthRepository.ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse result) {
                binding.btnGoogleSignIn.setEnabled(true);
                sessionManager.saveAuthSession(
                        result.getAccessToken(),
                        result.getRefreshToken(),
                        result.getUser().getId(),
                        result.getUser().getEmail(),
                        result.getUser().getFullName()
                );
                Toast.makeText(LoginActivity.this, "Google Sign-In Successful", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                binding.btnGoogleSignIn.setEnabled(true);
                Toast.makeText(LoginActivity.this, "Google Authentication Error: " + errorMessage, Toast.LENGTH_LONG).show();
            }
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
                authRepository.forgotPassword(email, new AuthRepository.ApiCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        Toast.makeText(LoginActivity.this, result, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}
