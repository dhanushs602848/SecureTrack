package com.example.securetrack.repositories;

import android.content.Context;
import com.example.securetrack.api.ApiClient;
import com.example.securetrack.api.ApiService;
import com.example.securetrack.models.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {

    private final ApiService apiService;

    public AuthRepository(Context context) {
        this.apiService = ApiClient.getApiService(context);
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    public void login(String email, String password, ApiCallback<AuthResponse> callback) {
        apiService.login(new AuthRequest(email, password)).enqueue(new Callback<ApiResponse<AuthResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<AuthResponse>> call, Response<ApiResponse<AuthResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess(response.body().getData());
                } else {
                    callback.onError(response.body() != null ? response.body().getMessage() : "Authentication failed");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<AuthResponse>> call, Throwable t) {
                callback.onError(t.getLocalizedMessage() != null ? t.getLocalizedMessage() : "Network error");
            }
        });
    }

    public void googleLogin(String idToken, ApiCallback<AuthResponse> callback) {
        apiService.googleLogin(new GoogleAuthRequest(idToken)).enqueue(new Callback<ApiResponse<AuthResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<AuthResponse>> call, Response<ApiResponse<AuthResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess(response.body().getData());
                } else {
                    callback.onError(response.body() != null ? response.body().getMessage() : "Google authentication failed");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<AuthResponse>> call, Throwable t) {
                callback.onError(t.getLocalizedMessage() != null ? t.getLocalizedMessage() : "Network error during Google authentication");
            }
        });
    }

    public void register(String email, String password, String fullName, String phoneNumber, ApiCallback<AuthResponse> callback) {
        apiService.register(new RegisterRequest(email, password, fullName, phoneNumber)).enqueue(new Callback<ApiResponse<AuthResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<AuthResponse>> call, Response<ApiResponse<AuthResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess(response.body().getData());
                } else {
                    callback.onError(response.body() != null ? response.body().getMessage() : "Registration failed");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<AuthResponse>> call, Throwable t) {
                callback.onError(t.getLocalizedMessage() != null ? t.getLocalizedMessage() : "Network error");
            }
        });
    }

    public void forgotPassword(String email, ApiCallback<String> callback) {
        apiService.forgotPassword(new PasswordResetRequest(email)).enqueue(new Callback<ApiResponse<String>>() {
            @Override
            public void onResponse(Call<ApiResponse<String>> call, Response<ApiResponse<String>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess(response.body().getMessage());
                } else {
                    callback.onError(response.body() != null ? response.body().getMessage() : "Request failed");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                callback.onError(t.getLocalizedMessage() != null ? t.getLocalizedMessage() : "Network error");
            }
        });
    }
}
