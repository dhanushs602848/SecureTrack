package com.example.securetrack.security;

import android.util.Log;
import com.example.securetrack.utils.DeviceUtils;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Robust, cryptographically verified Firestore pairing recovery service.
 * Independently evaluates every candidate approved invitation against the 10 security criteria:
 * 1. Invitation exists and status == APPROVED.
 * 2. claimedByUid matches authenticated claiming identity.
 * 3. claimedDeviceId matches exact installation ID.
 * 4. ownerUid is valid and distinct from claiming identity (proper direction).
 * 5. Corresponding device document exists under users/{ownerUid}/devices/{installationId}.
 * 6. Target device document is NOT REVOKED.
 * 7. Target device document identity matches installation ID and auth UID.
 * 8. Constant-time comparison of pairing credentials (bleSharedSecret).
 * 9. Detects and resolves duplicate invitations under the same verified owner.
 * 10. Explicitly rejects ambiguous multiple conflicting owners requiring fresh authorized pairing.
 *
 * Never logs secrets or full sensitive identifiers (redacted).
 * Never uses .limit(1) to hide ambiguity.
 */
public class FirestorePairingRecoveryService implements IPairingRecoveryService {
    private static final String TAG = "FirestorePairingRecov";
    private final FirebaseFirestore db;

    public FirestorePairingRecoveryService(FirebaseFirestore db) {
        this.db = db != null ? db : FirebaseFirestore.getInstance();
    }

    @Override
    public void checkRecovery(String authUid, String installationId, RecoveryCallback callback) {
        checkRecovery(authUid, installationId, null, callback);
    }

    @Override
    public void checkRecovery(String authUid, String installationId, String expectedBleSecret, RecoveryCallback callback) {
        if (callback == null) return;

        if (authUid == null || authUid.trim().isEmpty()) {
            callback.onRecoveryRejected("Missing authenticated UID for pairing recovery");
            return;
        }
        if (installationId == null || installationId.trim().isEmpty()) {
            callback.onRecoveryRejected("Missing installation ID for pairing recovery");
            return;
        }

        Log.i(TAG, "Initiating multi-candidate verified pairing recovery for target="
                + DeviceUtils.redactIdentifier(installationId)
                + ", authUid=" + DeviceUtils.redactIdentifier(authUid));

        db.collection("pairing_invitations")
                .whereEqualTo("claimedByUid", authUid)
                .whereEqualTo("claimedDeviceId", installationId)
                .whereEqualTo("status", "APPROVED")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        Log.i(TAG, "No approved invitations found for target " + DeviceUtils.redactIdentifier(installationId));
                        callback.onNoSecondaryInvitation();
                        return;
                    }

                    List<DocumentSnapshot> invDocs = querySnapshot.getDocuments();
                    Log.i(TAG, "Discovered " + invDocs.size() + " candidate approved invitation(s). Evaluating each independently...");

                    // Collect candidate owner UIDs
                    Set<String> candidateOwners = new HashSet<>();
                    for (DocumentSnapshot doc : invDocs) {
                        String ownerUid = doc.getString("ownerUid");
                        if (ownerUid != null && !ownerUid.trim().isEmpty() && !ownerUid.equals(authUid)) {
                            candidateOwners.add(ownerUid);
                        }
                    }

                    if (candidateOwners.isEmpty()) {
                        Log.w(TAG, "All candidate invitations had missing, empty or self-referential ownerUid");
                        callback.onRecoveryRejected("No candidate invitations with valid ownerUid");
                        return;
                    }

                    // Asynchronously fetch device documents for all candidate owners
                    Map<String, Map<String, Object>> devDocsByOwner = new ConcurrentHashMap<>();
                    AtomicInteger pendingFetches = new AtomicInteger(candidateOwners.size());

                    for (String oUid : candidateOwners) {
                        db.collection("users").document(oUid)
                                .collection("devices").document(installationId)
                                .get()
                                .addOnSuccessListener(devDoc -> {
                                    if (devDoc != null && devDoc.exists() && devDoc.getData() != null) {
                                        devDocsByOwner.put(oUid, devDoc.getData());
                                    }
                                    if (pendingFetches.decrementAndGet() == 0) {
                                        processCandidateEvaluations(authUid, installationId, expectedBleSecret, invDocs, devDocsByOwner, callback);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.w(TAG, "Error fetching device document for candidate owner " + DeviceUtils.redactIdentifier(oUid) + ": " + e.getMessage());
                                    if (pendingFetches.decrementAndGet() == 0) {
                                        processCandidateEvaluations(authUid, installationId, expectedBleSecret, invDocs, devDocsByOwner, callback);
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Pairing recovery query failed: " + e.getMessage());
                    callback.onRecoveryError(e);
                });
    }

    private void processCandidateEvaluations(
            String authUid,
            String installationId,
            String expectedBleSecret,
            List<DocumentSnapshot> invDocs,
            Map<String, Map<String, Object>> devDocsByOwner,
            RecoveryCallback callback
    ) {
        List<Map<String, Object>> invMaps = new ArrayList<>();
        for (DocumentSnapshot doc : invDocs) {
            Map<String, Object> data = doc.getData();
            if (data != null) {
                invMaps.add(new HashMap<>(data));
            }
        }

        CommandExecutionManager.PairingRecoveryResult result = CommandExecutionManager.evaluateRecoveryMultiOwner(
                authUid,
                installationId,
                invMaps,
                devDocsByOwner,
                expectedBleSecret
        );

        if (result.success) {
            Log.i(TAG, "Pairing recovery unambiguously verified under owner: " + DeviceUtils.redactIdentifier(result.ownerUid));
            callback.onRecoverySuccess(result.ownerUid, result.deviceName, result.bleSharedSecret);
        } else {
            Log.w(TAG, "Pairing recovery rejected: " + result.rejectionReason);
            callback.onRecoveryRejected(result.rejectionReason);
        }
    }
}
