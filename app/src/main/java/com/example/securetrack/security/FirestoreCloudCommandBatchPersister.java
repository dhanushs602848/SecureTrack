package com.example.securetrack.security;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import java.util.Map;

public class FirestoreCloudCommandBatchPersister implements ICloudCommandBatchPersister {
    private final FirebaseFirestore db;

    public FirestoreCloudCommandBatchPersister(FirebaseFirestore db) {
        this.db = db != null ? db : FirebaseFirestore.getInstance();
    }

    @Override
    public void commitLocateBatch(String uid, String deviceId, String commandId, String requestId,
                                  Map<String, Object> locationMap, Map<String, Object> deviceUpdates,
                                  Map<String, Object> commandUpdates, Callback callback) {
        WriteBatch batch = db.batch();
        DocumentReference locRef = db.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .collection("locations").document(requestId);
        batch.set(locRef, locationMap, SetOptions.merge());

        DocumentReference devRef = db.collection("users").document(uid)
                .collection("devices").document(deviceId);
        batch.set(devRef, deviceUpdates, SetOptions.merge());

        if (commandId != null && !commandId.isEmpty()) {
            DocumentReference cmdRef = db.collection("users").document(uid)
                    .collection("devices").document(deviceId)
                    .collection("commands").document(commandId);
            batch.set(cmdRef, commandUpdates, SetOptions.merge());
        }

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e);
                });
    }

    @Override
    public void updateCommandStatus(String uid, String deviceId, String commandId,
                                    Map<String, Object> updates, Callback callback) {
        db.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .collection("commands").document(commandId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e);
                });
    }
}
