package com.example.securetrack.api;

import com.example.securetrack.models.*;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {

    @POST("api/auth/register")
    Call<ApiResponse<AuthResponse>> register(@Body RegisterRequest request);

    @POST("api/auth/login")
    Call<ApiResponse<AuthResponse>> login(@Body AuthRequest request);

    @POST("api/auth/google")
    Call<ApiResponse<AuthResponse>> googleLogin(@Body GoogleAuthRequest request);

    @POST("api/auth/forgot-password")
    Call<ApiResponse<String>> forgotPassword(@Body PasswordResetRequest request);

    @POST("api/auth/reset-password")
    Call<ApiResponse<String>> resetPassword(@Body PasswordResetConfirmRequest request);

    @GET("api/users/me")
    Call<ApiResponse<User>> getCurrentUser();

    @POST("api/devices")
    Call<ApiResponse<Device>> registerDevice(@Body Device device);

    @GET("api/devices")
    Call<ApiResponse<List<Device>>> getUserDevices();

    @GET("api/devices/{id}")
    Call<ApiResponse<Device>> getDevice(@Path("id") Long id);

    @PUT("api/devices/{id}")
    Call<ApiResponse<Device>> updateDevice(@Path("id") Long id, @Body Device device);

    @DELETE("api/devices/{id}")
    Call<ApiResponse<String>> deleteDevice(@Path("id") Long id);

    @POST("api/pairing/create")
    Call<ApiResponse<PairingResponse>> createPairingCode();

    @POST("api/pairing/complete")
    Call<ApiResponse<Device>> completePairing(@Body PairingRequest request);

    @POST("api/locations")
    Call<ApiResponse<LocationRecord>> recordLocation(@Body LocationRecord location);

    @GET("api/locations/latest/{deviceId}")
    Call<ApiResponse<LocationRecord>> getLatestLocation(@Path("deviceId") Long deviceId);

    @GET("api/locations/history/{deviceId}")
    Call<ApiResponse<List<LocationRecord>>> getLocationHistory(@Path("deviceId") Long deviceId);

    @POST("api/security-events")
    Call<ApiResponse<SecurityEvent>> recordSecurityEvent(@Body SecurityEvent event);

    @GET("api/security-events")
    Call<ApiResponse<List<SecurityEvent>>> getUserSecurityEvents();

    @GET("api/security-events/{deviceId}")
    Call<ApiResponse<List<SecurityEvent>>> getDeviceSecurityEvents(@Path("deviceId") Long deviceId);

    @POST("api/devices/{id}/commands")
    Call<ApiResponse<DeviceCommand>> createCommand(@Path("id") Long deviceId, @Body DeviceCommand command);

    @GET("api/devices/{id}/commands")
    Call<ApiResponse<List<DeviceCommand>>> getDeviceCommands(@Path("id") Long deviceId);

    @GET("api/devices/{id}/commands/pending")
    Call<ApiResponse<List<DeviceCommand>>> pollPendingCommands(@Path("id") Long deviceId);

    @PUT("api/commands/{commandId}/status")
    Call<ApiResponse<DeviceCommand>> updateCommandStatus(@Path("commandId") Long commandId, @Body CommandStatusUpdateRequest request);
}
