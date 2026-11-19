package com.example.securetrack.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.securetrack.api.ApiClient;
import com.example.securetrack.databinding.ActivitySettingsBinding;
import com.example.securetrack.utils.SessionManager;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        binding.btnBack.setOnClickListener(v -> finish());

        if (binding.etServerUrl != null) {
            binding.etServerUrl.setText(sessionManager.getBaseUrl());
        }

        if (binding.btnSaveSettings != null) {
            binding.btnSaveSettings.setOnClickListener(v -> {
                String url = binding.etServerUrl.getText() != null ? binding.etServerUrl.getText().toString().trim() : "";
                if (!TextUtils.isEmpty(url)) {
                    sessionManager.setBaseUrl(url);
                    ApiClient.resetClient();
                    Toast.makeText(this, "Server URL updated successfully", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(this, "URL cannot be empty", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
