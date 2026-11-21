package com.example.securetrack.security;

import java.util.Map;

public interface ICloudCommandBatchPersister {
    interface Callback {
        void onSuccess();
        void onFailure(Exception exception);
    }

    void commitLocateBatch(
            String uid,
            String deviceId,
            String commandId,
            String requestId,
            Map<String, Object> locationMap,
            Map<String, Object> deviceUpdates,
            Map<String, Object> commandUpdates,
            Callback callback
    );

    void updateCommandStatus(
            String uid,
            String deviceId,
            String commandId,
            Map<String, Object> updates,
            Callback callback
    );
}
