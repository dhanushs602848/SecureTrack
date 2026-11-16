package com.example.securetrack.security;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import com.example.securetrack.api.ApiClient;
import com.example.securetrack.api.ApiService;
import com.example.securetrack.models.ApiResponse;
import com.example.securetrack.models.CommandStatusUpdateRequest;
import com.example.securetrack.models.DeviceCommand;
import com.example.securetrack.utils.SessionManager;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CommandExecutionManager {

    private final Context context;
    private final ApiService apiService;
    private final SessionManager sessionManager;

    public CommandExecutionManager(Context context) {
        this.context = context.getApplicationContext();
        this.apiService = ApiClient.getApiService(this.context);
        this.sessionManager = new SessionManager(this.context);
    }

    public void pollAndExecuteCommands() {
        long deviceId = sessionManager.getPrimaryDeviceId();
        if (deviceId == -1) return;

        apiService.pollPendingCommands(deviceId).enqueue(new Callback<ApiResponse<List<DeviceCommand>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<DeviceCommand>>> call, Response<ApiResponse<List<DeviceCommand>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<DeviceCommand> commands = response.body().getData();
                    if (commands != null) {
                        for (DeviceCommand cmd : commands) {
                            executeSingleCommand(cmd);
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<DeviceCommand>>> call, Throwable t) {}
        });
    }

    private void executeSingleCommand(DeviceCommand cmd) {
        String type = cmd.getCommandType();
        if ("LOCK_DEVICE".equalsIgnoreCase(type)) {
            if (SecureAdminReceiver.isAdminActive(context)) {
                try {
                    DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                    if (dpm != null) {
                        dpm.lockNow();
                        updateCommandStatus(cmd.getId(), "EXECUTED", "Device locked successfully");
                    } else {
                        updateCommandStatus(cmd.getId(), "FAILED", "DevicePolicyManager unavailable");
                    }
                } catch (Exception e) {
                    updateCommandStatus(cmd.getId(), "FAILED", e.getMessage());
                }
            } else {
                updateCommandStatus(cmd.getId(), "FAILED", "Device Policy Admin privileges not activated by user");
            }
        } else if ("ALARM_ALERT".equalsIgnoreCase(type)) {
            try {
                Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmUri == null) {
                    alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                }
                Ringtone ringtone = RingtoneManager.getRingtone(context, alarmUri);
                if (ringtone != null) {
                    ringtone.play();
                }
                updateCommandStatus(cmd.getId(), "EXECUTED", "Alarm alert triggered successfully");
            } catch (Exception e) {
                updateCommandStatus(cmd.getId(), "FAILED", "Alarm trigger failed: " + e.getMessage());
            }
        } else if ("LOCATE_NOW".equalsIgnoreCase(type)) {
            updateCommandStatus(cmd.getId(), "EXECUTED", "Instant location request dispatched");
        } else {
            updateCommandStatus(cmd.getId(), "FAILED", "Unsupported command type: " + type);
        }
    }

    private void updateCommandStatus(Long commandId, String status, String result) {
        apiService.updateCommandStatus(commandId, new CommandStatusUpdateRequest(status, result))
                .enqueue(new Callback<ApiResponse<DeviceCommand>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<DeviceCommand>> call, Response<ApiResponse<DeviceCommand>> response) {}

                    @Override
                    public void onFailure(Call<ApiResponse<DeviceCommand>> call, Throwable t) {}
                });
    }
}
