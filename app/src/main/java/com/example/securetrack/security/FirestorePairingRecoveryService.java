package com.example.securetrack.security;

import android.util.Log;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class FirestorePairingRecoveryService implements IPairingRecoveryService {
    private static final String TAG = "FirestorePairingRecov";
    private final FirebaseFirestore db;

    public FirestorePairingRecoveryService(FirebaseFirestore db) {
        this.db = db != null ? db : FirebaseFirestore.getInstance();
    }

    @Override
    public void checkRecovery(String authUid, String installationId, RecoveryCallback callback) {
        if (callback == null) return;

        db.collection("pairing_invitations")
                .whereEqualTo("claimedByUid", authUid)
                .whereEqualTo("claimedDeviceId", installationId)
                .whereEqualTo("status", "APPROVED")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        callback.onNoSecondaryInvitation();
                        return;
                    }

                    if (querySnapshot.size() > 1) {
                        Log.w(TAG, "Ambiguous multiple matching approved invitations (" + querySnapshot.size() + ")");
                        callback.onRecoveryRejected("Ambiguous multiple matching approved invitations (" + querySnapshot.size() + ")");
                        return;
                    }

                    DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                    String ownerUid = doc.getString("ownerUid");
                    String claimedDevId = doc.getString("claimedDeviceId");
                    String claimedByUid = doc.getString("claimedByUid");

                    if (ownerUid == null || ownerUid.trim().isEmpty()
                            || !installationId.equals(claimedDevId)
                            || !authUid.equals(claimedByUid)) {
                        callback.onRecoveryRejected("Mismatched or missing invitation fields");
                        return;
                    }

                    // Verify target device document exists and is not REVOKED
                    db.collection("users").document(ownerUid)
                            .collection("devices").document(installationId)
                            .get()
                            .addOnSuccessListener(devDoc -> {
                                if (!devDoc.exists()) {
                                    callback.onRecoveryRejected("Device document does not exist under owner " + ownerUid);
                                    return;
                                }

                                String devStatus = devDoc.getString("status");
                                if ("REVOKED".equalsIgnoreCase(devStatus)) {
                                    callback.onRecoveryRejected("Device is REVOKED under owner " + ownerUid);
                                    return;
                                }

                                String devName = devDoc.getString("deviceName");
                                String bleSecret = doc.getString("bleSharedSecret");
                                callback.onRecoverySuccess(ownerUid, devName, bleSecret);
                            })
                            .addOnFailureListener(callback::onRecoveryError);
                })
                .addOnFailureListener(callback::onRecoveryError);
    }
}
