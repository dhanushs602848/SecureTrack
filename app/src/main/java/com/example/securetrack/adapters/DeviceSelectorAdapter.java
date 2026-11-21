package com.example.securetrack.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.securetrack.R;
import com.example.securetrack.databinding.ItemDeviceSelectorChipBinding;
import com.example.securetrack.models.Device;
import java.util.ArrayList;
import java.util.List;

public class DeviceSelectorAdapter extends RecyclerView.Adapter<DeviceSelectorAdapter.ViewHolder> {

    private final List<Device> devices = new ArrayList<>();
    private String selectedDeviceId;
    private OnDeviceSelectedListener listener;

    public interface OnDeviceSelectedListener {
        void onDeviceSelected(Device device);
    }

    public void setOnDeviceSelectedListener(OnDeviceSelectedListener listener) {
        this.listener = listener;
    }

    public void setDevices(List<Device> newDevices, String currentSelectedId) {
        devices.clear();
        if (newDevices != null) {
            devices.addAll(newDevices);
        }
        this.selectedDeviceId = currentSelectedId;
        notifyDataSetChanged();
    }

    public void setSelectedDeviceId(String deviceId) {
        this.selectedDeviceId = deviceId;
        notifyDataSetChanged();
    }

    public String getSelectedDeviceId() {
        return selectedDeviceId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDeviceSelectorChipBinding binding = ItemDeviceSelectorChipBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Device device = devices.get(position);
        Context context = holder.itemView.getContext();

        String name = device.getDeviceName();
        if (name == null || name.trim().isEmpty() || name.matches("^[0-9a-fA-F-]{32,36}$")) {
            name = (device.getModel() != null && !device.getModel().isEmpty()) ? device.getModel() : "Device " + (position + 1);
        }

        holder.binding.tvChipDeviceName.setText(name);

        boolean isSelected = device.getId() != null && device.getId().equals(selectedDeviceId);
        if (isSelected) {
            holder.binding.cardChip.setStrokeColor(ContextCompat.getColor(context, R.color.electric_cyan));
            holder.binding.cardChip.setStrokeWidth(4);
            holder.binding.cardChip.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_bg));
            holder.binding.tvChipDeviceName.setTextColor(ContextCompat.getColor(context, R.color.electric_cyan));
        } else {
            holder.binding.cardChip.setStrokeColor(ContextCompat.getColor(context, R.color.border_color));
            holder.binding.cardChip.setStrokeWidth(2);
            holder.binding.cardChip.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_bg));
            holder.binding.tvChipDeviceName.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        }

        boolean isOnline = device.isOnline();
        if (isOnline) {
            holder.binding.viewStatusDot.setBackgroundResource(R.drawable.bg_badge_success);
        } else {
            holder.binding.viewStatusDot.setBackgroundResource(R.drawable.bg_chip_stale);
        }

        holder.itemView.setOnClickListener(v -> {
            selectedDeviceId = device.getId();
            notifyDataSetChanged();
            if (listener != null) {
                listener.onDeviceSelected(device);
            }
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemDeviceSelectorChipBinding binding;

        public ViewHolder(ItemDeviceSelectorChipBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
