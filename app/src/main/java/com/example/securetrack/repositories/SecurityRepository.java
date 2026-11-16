package com.example.securetrack.repositories;

import android.content.Context;
import com.example.securetrack.api.ApiClient;
import com.example.securetrack.api.ApiService;
import com.example.securetrack.models.*;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SecurityRepository {

    private final ApiService apiService;

    public SecurityRepository(Context context) {
        this.apiService = ApiClient.getApiService(context);
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    public void getUserSecurityEvents(ApiCallback<List<SecurityEvent>> callback) {
        apiService.getUserSecurityEvents().enqueue(new Callback<ApiResponse<List<SecurityEvent>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<SecurityEvent>>> call, Response<ApiResponse<List<SecurityEvent>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess(response.body().getData());
                } else {
                    callback.onError("Failed to fetch security events");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<SecurityEvent>>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    public void recordSecurityEvent(SecurityEvent event, ApiCallback<SecurityEvent> callback) {
        apiService.recordSecurityEvent(event).enqueue(new Callback<ApiResponse<SecurityEvent>>() {
            @Override
            public void onResponse(Call<ApiResponse<SecurityEvent>> call, Response<ApiResponse<SecurityEvent>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess(response.body().getData());
                } else {
                    callback.onError("Failed to record security event");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<SecurityEvent>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    public void createCommand(Long deviceId, DeviceCommand command, ApiCallback<DeviceCommand> callback) {
        apiService.createCommand(deviceId, command).enqueue(new Callback<ApiResponse<DeviceCommand>>() {
            @Override
            public void onResponse(Call<ApiResponse<DeviceCommand>> call, Response<ApiResponse<DeviceCommand>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess(response.body().getData());
                } else {
                    callback.onError("Failed to dispatch command");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<DeviceCommand>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    public void getDeviceCommands(Long deviceId, ApiCallback<List<DeviceCommand>> callback) {
        apiService.getDeviceCommands(deviceId).enqueue(new Callback<ApiResponse<List<DeviceCommand>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<DeviceCommand>>> call, Response<ApiResponse<List<DeviceCommand>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess(response.body().getData());
                } else {
                    callback.onError("Failed to fetch command history");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<DeviceCommand>>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }
}
