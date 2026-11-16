package com.example.securetrack.repositories;

import android.content.Context;
import com.example.securetrack.api.ApiClient;
import com.example.securetrack.api.ApiService;
import com.example.securetrack.models.*;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DeviceRepository {

    private final ApiService apiService;

    public DeviceRepository(Context context) {
        this.apiService = ApiClient.getApiService(context);
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    public void registerDevice(Device device, ApiCallback<Device> callback) {
        apiService.registerDevice(device).enqueue(new Callback<ApiResponse<Device>>() {
            @Override
            public void onResponse(Call<ApiResponse<Device>> call, Response<ApiResponse<Device>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess(response.body().getData());
                } else {
                    callback.onError("Failed to register device");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Device>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    public void getUserDevices(ApiCallback<List<Device>> callback) {
        apiService.getUserDevices().enqueue(new Callback<ApiResponse<List<Device>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Device>>> call, Response<ApiResponse<List<Device>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess(response.body().getData());
                } else {
                    callback.onError("Failed to fetch devices");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Device>>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    public void createPairingCode(ApiCallback<PairingResponse> callback) {
        apiService.createPairingCode().enqueue(new Callback<ApiResponse<PairingResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<PairingResponse>> call, Response<ApiResponse<PairingResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess(response.body().getData());
                } else {
                    callback.onError("Failed to generate pairing code");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<PairingResponse>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    public void completePairing(PairingRequest request, ApiCallback<Device> callback) {
        apiService.completePairing(request).enqueue(new Callback<ApiResponse<Device>>() {
            @Override
            public void onResponse(Call<ApiResponse<Device>> call, Response<ApiResponse<Device>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess(response.body().getData());
                } else {
                    callback.onError(response.body() != null ? response.body().getMessage() : "Pairing failed");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Device>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }
}
