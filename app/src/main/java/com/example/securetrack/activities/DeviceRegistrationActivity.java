package com.example.securetrack.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import com.example.securetrack.databinding.ActivityDeviceRegistrationBinding;
import com.example.securetrack.models.Device;
import com.example.securetrack.models.PairingInvitation;
import com.example.securetrack.repositories.DeviceRepository;
import com.example.securetrack.utils.DeviceUtils;
import com.example.securetrack.utils.SessionManager;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import org.json.JSONObject;
import java.util.Map;

public class DeviceRegistrationActivity extends AppCompatActivity {

    private ActivityDeviceRegistrationBinding binding;
    private DeviceRepository deviceRepository;
    private SessionManager sessionManager;
    private CountDownTimer countDownTimer;
    private ListenerRegistration invitationListener;
    private PairingInvitation currentInvitation;

    private final ActivityResultLauncher<ScanOptions> qrScanLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    handleScannedQrPayload(result.getContents());
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeviceRegistrationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        deviceRepository = new DeviceRepository(this);
        sessionManager = new SessionManager(this);

        setupModeSelector();

        binding.btnCompleteRegistration.setOnClickListener(v -> handlePrimaryRegistration());
        binding.btnCreateInvitation.setOnClickListener(v -> generatePairingInvitation());
        binding.btnCopyToken.setOnClickListener(v -> copyPairingTokenToClipboard());
        binding.btnScanQr.setOnClickListener(v -> launchQrScanner());
        binding.btnSubmitClaim.setOnClickListener(v -> submitPairingClaim());

        binding.etDeviceName.setText(DeviceUtils.getDeviceName());
        binding.etSecondaryDeviceName.setText(DeviceUtils.getDeviceName());
    }

    private void setupModeSelector() {
        binding.rgSetupMethod.setOnCheckedChangeListener((group, checkedId) -> {
            binding.layoutPrimaryRegistration.setVisibility(View.GONE);
            binding.layoutGenerateInvitation.setVisibility(View.GONE);
            binding.layoutEnrolSecondary.setVisibility(View.GONE);

            if (checkedId == binding.rbThisPhone.getId()) {
                binding.layoutPrimaryRegistration.setVisibility(View.VISIBLE);
            } else if (checkedId == binding.rbGenerateInvitation.getId()) {
                binding.layoutGenerateInvitation.setVisibility(View.VISIBLE);
            } else if (checkedId == binding.rbEnrolDevice.getId()) {
                binding.layoutEnrolSecondary.setVisibility(View.VISIBLE);
            }
        });
    }

    private boolean isValidImei(String imei) {
        if (imei == null || imei.length() != 15 || !imei.matches("\\d+")) {
            return false;
        }
        int sum = 0;
        for (int i = 14; i >= 0; i--) {
            int digit = imei.charAt(i) - '0';
            if ((14 - i) % 2 != 0) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return sum % 10 == 0;
    }

    private void handlePrimaryRegistration() {
        String name = binding.etDeviceName.getText() != null ? binding.etDeviceName.getText().toString().trim() : "";
        if (TextUtils.isEmpty(name)) name = DeviceUtils.getDeviceName();

        String imei1 = binding.etImei1.getText() != null ? binding.etImei1.getText().toString().trim() : "";
        String imei2 = binding.etImei2.getText() != null ? binding.etImei2.getText().toString().trim() : "";

        binding.tilImei1.setError(null);
        binding.tilImei2.setError(null);

        if (!TextUtils.isEmpty(imei1) && !isValidImei(imei1)) {
            binding.tilImei1.setError("Invalid IMEI. Must be 15 digits and pass checksum.");
            binding.etImei1.requestFocus();
            return;
        }
        if (!TextUtils.isEmpty(imei2) && !isValidImei(imei2)) {
            binding.tilImei2.setError("Invalid IMEI. Must be 15 digits and pass checksum.");
            binding.etImei2.requestFocus();
            return;
        }

        if ((!TextUtils.isEmpty(imei1) || !TextUtils.isEmpty(imei2)) && !binding.cbImeiConsent.isChecked()) {
            Toast.makeText(this, "Please check the consent box to store IMEI information.", Toast.LENGTH_LONG).show();
            return;
        }

        binding.btnCompleteRegistration.setEnabled(false);
        Device newDevice = new Device(
                name,
                DeviceUtils.getDeviceModel(),
                DeviceUtils.getAndroidVersion(),
                sessionManager.getInstallationId(),
                DeviceUtils.getBatteryPercentage(this)
        );

        if (!TextUtils.isEmpty(imei1)) newDevice.setImei1(imei1);
        if (!TextUtils.isEmpty(imei2)) newDevice.setImei2(imei2);

        deviceRepository.registerDevice(newDevice, new DeviceRepository.ApiCallback<Device>() {
            @Override
            public void onSuccess(Device result) {
                binding.btnCompleteRegistration.setEnabled(true);
                sessionManager.setCurrentDeviceId(result.getId());
                sessionManager.setDeviceName(result.getDeviceName());
                Toast.makeText(DeviceRegistrationActivity.this, "Device Registered Successfully", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                binding.btnCompleteRegistration.setEnabled(true);
                Toast.makeText(DeviceRegistrationActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void generatePairingInvitation() {
        binding.btnCreateInvitation.setEnabled(false);
        deviceRepository.createPairingInvitation(new DeviceRepository.ApiCallback<PairingInvitation>() {
            @Override
            public void onSuccess(PairingInvitation invitation) {
                binding.btnCreateInvitation.setEnabled(true);
                currentInvitation = invitation;
                displayInvitation(invitation);
            }

            @Override
            public void onError(String errorMessage) {
                binding.btnCreateInvitation.setEnabled(true);
                Toast.makeText(DeviceRegistrationActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void displayInvitation(PairingInvitation invitation) {
        binding.cardInvitationDisplay.setVisibility(View.VISIBLE);
        binding.tvPairingToken.setText(invitation.getRawToken());

        // Render QR Code bitmap
        Bitmap qrBmp = generateQrBitmap(invitation.getQrPayload());
        if (qrBmp != null) {
            binding.ivQrCode.setImageBitmap(qrBmp);
        }

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(10 * 60 * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long mins = millisUntilFinished / 60000;
                long secs = (millisUntilFinished % 60000) / 1000;
                binding.tvCountdown.setText(String.format("Invitation expires in: %02d:%02d", mins, secs));
            }

            @Override
            public void onFinish() {
                binding.tvCountdown.setText("Invitation expired");
                binding.cardInvitationDisplay.setVisibility(View.GONE);
            }
        }.start();

        // Listen for claimant to request approval
        if (invitationListener != null) {
            invitationListener.remove();
        }

        invitationListener = deviceRepository.listenToInvitation(invitation.getTokenHash(), new DeviceRepository.ApiCallback<PairingInvitation>() {
            @Override
            public void onSuccess(PairingInvitation updated) {
                if ("PENDING_APPROVAL".equals(updated.getStatus())) {
                    showApprovalPanel(updated);
                }
            }

            @Override
            public void onError(String errorMessage) {
                // Ignore listener transient errors
            }
        });
    }

    private void showApprovalPanel(PairingInvitation updated) {
        currentInvitation = updated;
        binding.layoutPendingApproval.setVisibility(View.VISIBLE);

        Map<String, Object> dev = updated.getDeviceInfo();
        String info = "Device: " + (dev != null && dev.get("deviceName") != null ? dev.get("deviceName") : "Android")
                + "\nModel: " + (dev != null && dev.get("model") != null ? dev.get("model") : "Generic")
                + "\nAndroid Version: " + (dev != null && dev.get("androidVersion") != null ? dev.get("androidVersion") : "14")
                + "\nBattery: " + (dev != null && dev.get("batteryLevel") != null ? dev.get("batteryLevel") : "100") + "%";

        binding.tvPendingDeviceInfo.setText(info);

        binding.btnApproveDevice.setOnClickListener(v -> {
            binding.btnApproveDevice.setEnabled(false);
            deviceRepository.approveDeviceClaim(updated, new DeviceRepository.ApiCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Toast.makeText(DeviceRegistrationActivity.this, "Device Approved and Enrolled!", Toast.LENGTH_LONG).show();
                    binding.layoutPendingApproval.setVisibility(View.GONE);
                    binding.cardInvitationDisplay.setVisibility(View.GONE);
                }

                @Override
                public void onError(String errorMessage) {
                    binding.btnApproveDevice.setEnabled(true);
                    Toast.makeText(DeviceRegistrationActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        });

        binding.btnRejectDevice.setOnClickListener(v -> {
            binding.btnRejectDevice.setEnabled(false);
            deviceRepository.rejectDeviceClaim(updated.getTokenHash(), new DeviceRepository.ApiCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Toast.makeText(DeviceRegistrationActivity.this, "Device pairing request rejected.", Toast.LENGTH_SHORT).show();
                    binding.layoutPendingApproval.setVisibility(View.GONE);
                }

                @Override
                public void onError(String errorMessage) {
                    binding.btnRejectDevice.setEnabled(true);
                    Toast.makeText(DeviceRegistrationActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void copyPairingTokenToClipboard() {
        if (currentInvitation != null && currentInvitation.getRawToken() != null) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData cd = ClipData.newPlainText("SecureTrack Pairing Token", currentInvitation.getRawToken());
            if (cm != null) {
                cm.setPrimaryClip(cd);
                Toast.makeText(this, "Pairing token copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void launchQrScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan SecureTrack Pairing QR Code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        qrScanLauncher.launch(options);
    }

    private void handleScannedQrPayload(String contents) {
        if (contents == null) return;
        try {
            if (contents.startsWith("{")) {
                JSONObject json = new JSONObject(contents);
                String token = json.optString("token");
                if (!TextUtils.isEmpty(token)) {
                    binding.etManualToken.setText(token);
                    Toast.makeText(this, "QR Code Scanned Successfully!", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        } catch (Exception ignored) {}

        binding.etManualToken.setText(contents.trim());
        Toast.makeText(this, "QR Code Scanned!", Toast.LENGTH_SHORT).show();
    }

    private void submitPairingClaim() {
        String token = binding.etManualToken.getText() != null ? binding.etManualToken.getText().toString().trim() : "";
        if (TextUtils.isEmpty(token) || token.length() < 32) {
            Toast.makeText(this, "Please enter or scan a valid 256-bit pairing token", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = binding.etSecondaryDeviceName.getText() != null ? binding.etSecondaryDeviceName.getText().toString().trim() : "";
        if (TextUtils.isEmpty(name)) name = DeviceUtils.getDeviceName();

        binding.btnSubmitClaim.setEnabled(false);
        binding.layoutClaimProgress.setVisibility(View.VISIBLE);

        deviceRepository.claimPairingInvitation(token, name, new DeviceRepository.ApiCallback<Device>() {
            @Override
            public void onSuccess(Device result) {
                binding.layoutClaimProgress.setVisibility(View.GONE);
                binding.btnSubmitClaim.setEnabled(true);
                Toast.makeText(DeviceRegistrationActivity.this, "Paired Successfully to Owner Account!", Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                binding.layoutClaimProgress.setVisibility(View.GONE);
                binding.btnSubmitClaim.setEnabled(true);
                Toast.makeText(DeviceRegistrationActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private Bitmap generateQrBitmap(String text) {
        if (text == null) return null;
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        if (invitationListener != null) {
            invitationListener.remove();
        }
    }
}
