package com.example.securetrack.security;

public interface IPairingRecoveryService {
    interface RecoveryCallback {
        void onRecoverySuccess(String ownerUid, String deviceName, String bleSecret);
        void onRecoveryRejected(String reason);
        void onRecoveryError(Exception exception);
        void onNoSecondaryInvitation();
    }

    void checkRecovery(String authUid, String installationId, RecoveryCallback callback);
}
