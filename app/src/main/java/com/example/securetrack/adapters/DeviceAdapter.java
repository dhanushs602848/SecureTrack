package com.example.securetrack.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.securetrack.R;
import com.example.securetrack.databinding.ItemDeviceBinding;
import com.example.securetrack.models.Device;
import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

    private final List<Device> devices = new ArrayList<>();
    private OnDeviceClickListener listener;

    public interface OnDeviceClickListener {
        void onDeviceClick(Device device);
        void onTrackOnMapClick(Device device);
        void onSecurityControlsClick(Device device);
    }

    public void setOnDeviceClickListener(OnDeviceClickListener listener) {
        this.listener = listener;
    }

    public void setDevices(List<Device> newDevices) {
        devices.clear();
        if (newDevices != null) {
            devices.addAll(newDevices);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDeviceBinding binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Device device = devices.get(position);
        String name = device.getDeviceName();
        if (name == null || name.trim().isEmpty() || name.matches("^[0-9a-fA-F-]{32,36}$")) {
            name = (device.getModel() != null && !device.getModel().isEmpty()) ? device.getModel() : "Protected Device";
        }
        holder.binding.tvDeviceName.setText(name);

        String modelText = "Model: " + (device.getModel() != null ? device.getModel() : "Android") +
                (device.getAndroidVersion() != null ? " (Android " + device.getAndroidVersion() + ")" : "");
        holder.binding.tvDeviceModel.setText(modelText);

        Integer battery = device.getBatteryLevel();
        String batteryText = "Battery: " + (battery != null ? battery + "%" : "N/A");
        String accuracyText = "Accuracy: " + device.getFormattedAccuracy();
        String lastSeenText = "Updated " + device.getFormattedLastSeen();

        holder.binding.tvBatteryLastSeen.setText(batteryText + " • " + accuracyText + " • " + lastSeenText);

        boolean isOnline = device.isOnline();
        if (isOnline) {
            holder.binding.tvDeviceStatus.setText(R.string.status_online);
            holder.binding.tvDeviceStatus.setBackgroundResource(R.drawable.bg_badge_success);
            holder.binding.tvDeviceStatus.setTextColor(holder.itemView.getContext().getColor(R.color.status_success));
            holder.binding.tvStaleWarning.setVisibility(View.GONE);
        } else {
            String statusStr = device.getStatus() != null ? device.getStatus() : "OFFLINE";
            holder.binding.tvDeviceStatus.setText(statusStr.toUpperCase());
            holder.binding.tvDeviceStatus.setBackgroundResource(R.drawable.bg_chip_stale);
            holder.binding.tvDeviceStatus.setTextColor(holder.itemView.getContext().getColor(R.color.text_secondary));

            if (device.isLocationStale()) {
                holder.binding.tvStaleWarning.setVisibility(View.VISIBLE);
                holder.binding.tvStaleWarning.setText("Location is stale or device offline. Tap 'Track on Map' to locate remotely.");
            } else {
                holder.binding.tvStaleWarning.setVisibility(View.GONE);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onDeviceClick(device);
        });

        holder.binding.btnTrackOnMap.setOnClickListener(v -> {
            if (listener != null) listener.onTrackOnMapClick(device);
        });

        holder.binding.btnSecurityControls.setOnClickListener(v -> {
            if (listener != null) listener.onSecurityControlsClick(device);
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemDeviceBinding binding;
        public ViewHolder(ItemDeviceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
