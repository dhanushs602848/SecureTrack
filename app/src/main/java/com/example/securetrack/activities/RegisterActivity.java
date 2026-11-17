package com.example.securetrack.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.securetrack.MainActivity;
import com.example.securetrack.databinding.ActivityRegisterBinding;
import com.example.securetrack.models.AuthResponse;
import com.example.securetrack.repositories.AuthRepository;
import com.example.securetrack.utils.GoogleAuthManager;
import com.example.securetrack.utils.SessionManager;

public class RegisterActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9002;

    private ActivityRegisterBinding binding;
    private AuthRepository authRepository;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authRepository = new AuthRepository(this);
        sessionManager = new SessionManager(this);

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

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please fill in required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnRegister.setEnabled(false);

        authRepository.register(email, password, name, phone, new AuthRepository.ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse result) {
                binding.btnRegister.setEnabled(true);
                sessionManager.saveAuthSession(
                        result.getAccessToken(),
                        result.getRefreshToken(),
                        result.getUser().getId(),
                        result.getUser().getEmail(),
                        result.getUser().getFullName()
                );
                Toast.makeText(RegisterActivity.this, "Account Created Successfully", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                binding.btnRegister.setEnabled(true);
                Toast.makeText(RegisterActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void performGoogleSignUp() {
        binding.btnGoogleRegister.setEnabled(false);
        GoogleAuthManager.signInWithCredentialManager(this, new GoogleAuthManager.GoogleSignInCallback() {
            @Override
            public void onSuccess(String idToken) {
                sendGoogleTokenToBackend(idToken);
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
                    sendGoogleTokenToBackend(idToken);
                }

                @Override
                public void onError(String errorMessage) {
                    binding.btnGoogleRegister.setEnabled(true);
                    Toast.makeText(RegisterActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void sendGoogleTokenToBackend(String idToken) {
        authRepository.googleLogin(idToken, new AuthRepository.ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse result) {
                binding.btnGoogleRegister.setEnabled(true);
                sessionManager.saveAuthSession(
                        result.getAccessToken(),
                        result.getRefreshToken(),
                        result.getUser().getId(),
                        result.getUser().getEmail(),
                        result.getUser().getFullName()
                );
                Toast.makeText(RegisterActivity.this, "Google Registration Successful", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                binding.btnGoogleRegister.setEnabled(true);
                Toast.makeText(RegisterActivity.this, "Google Registration Error: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }
}
