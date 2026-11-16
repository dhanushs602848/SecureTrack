package com.example.securetrack.repositories;

import android.content.Context;
import com.example.securetrack.api.ApiClient;
import com.example.securetrack.api.ApiService;
import com.example.securetrack.database.AppDatabase;
import com.example.securetrack.database.LocationDao;
import com.example.securetrack.database.LocationEntity;
import com.example.securetrack.models.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LocationRepository {

    private final ApiService apiService;
    private final LocationDao locationDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public LocationRepository(Context context) {
        this.apiService = ApiClient.getApiService(context);
        this.locationDao = AppDatabase.getInstance(context).locationDao();
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    public void recordLocation(LocationRecord record, ApiCallback<LocationRecord> callback) {
        apiService.recordLocation(record).enqueue(new Callback<ApiResponse<LocationRecord>>() {
            @Override
            public void onResponse(Call<ApiResponse<LocationRecord>> call, Response<ApiResponse<LocationRecord>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess(response.body().getData());
                } else {
                    // Offline Cache saving
                    cacheLocally(record);
                    callback.onError("Server upload failed, cached locally for retry");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<LocationRecord>> call, Throwable t) {
                cacheLocally(record);
                callback.onError("Offline: Location saved locally to retry queue");
            }
        });
    }

    public void getLocationHistory(Long deviceId, ApiCallback<List<LocationRecord>> callback) {
        apiService.getLocationHistory(deviceId).enqueue(new Callback<ApiResponse<List<LocationRecord>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<LocationRecord>>> call, Response<ApiResponse<List<LocationRecord>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess(response.body().getData());
                } else {
                    callback.onError("Failed to fetch location history");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<LocationRecord>>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    private void cacheLocally(LocationRecord record) {
        executor.execute(() -> {
            if (record.getDeviceId() != null) {
                LocationEntity entity = new LocationEntity(
                        record.getDeviceId(),
                        record.getClientUuid(),
                        record.getLatitude(),
                        record.getLongitude(),
                        record.getAccuracy() != null ? record.getAccuracy() : 0.0f,
                        record.getAltitude() != null ? record.getAltitude() : 0.0,
                        record.getSpeed() != null ? record.getSpeed() : 0.0f,
                        record.getTimestamp(),
                        false
                );
                locationDao.insert(entity);
            }
        });
    }
}
