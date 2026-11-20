package com.example.securetrack.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.securetrack.R;
import com.example.securetrack.databinding.ItemSecurityAlertBinding;
import com.example.securetrack.models.SecurityEvent;
import java.util.ArrayList;
import java.util.List;

public class SecurityAlertAdapter extends RecyclerView.Adapter<SecurityAlertAdapter.ViewHolder> {

    public interface OnAlertActionListener {
        void onAlertDismiss(SecurityEvent event, int position);
        void onAlertClick(SecurityEvent event);
    }

    private final List<SecurityEvent> events = new ArrayList<>();
    private OnAlertActionListener actionListener;

    public void setOnAlertActionListener(OnAlertActionListener listener) {
        this.actionListener = listener;
    }

    public void setEvents(List<SecurityEvent> newEvents) {
        events.clear();
        if (newEvents != null) {
            events.addAll(newEvents);
        }
        notifyDataSetChanged();
    }

    public List<SecurityEvent> getEvents() {
        return events;
    }

    public SecurityEvent getEventAt(int position) {
        if (position >= 0 && position < events.size()) {
            return events.get(position);
        }
        return null;
    }

    public void removeEventAt(int position) {
        if (position >= 0 && position < events.size()) {
            events.remove(position);
            notifyItemRemoved(position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSecurityAlertBinding binding = ItemSecurityAlertBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SecurityEvent event = events.get(position);
        holder.binding.tvEventType.setText(event.getEventType() != null ? event.getEventType() : "SECURITY_ALERT");

        // Severity
        String severity = event.getSeverity() != null ? event.getSeverity().toUpperCase() : "INFO";
        holder.binding.tvEventSeverity.setText(severity);
        if ("CRITICAL".equals(severity) || "HIGH".equals(severity)) {
            holder.binding.tvEventSeverity.setTextColor(Color.parseColor("#EF4444"));
        } else if ("WARNING".equals(severity) || "MEDIUM".equals(severity)) {
            holder.binding.tvEventSeverity.setTextColor(Color.parseColor("#F59E0B"));
        } else {
            holder.binding.tvEventSeverity.setTextColor(Color.parseColor("#00D2FF"));
        }

        // Status
        String status = event.getStatus() != null ? event.getStatus().toUpperCase() : "ACTIVE";
        holder.binding.tvEventStatus.setText(status);
        if ("DISMISSED".equals(status)) {
            holder.binding.tvEventStatus.setTextColor(Color.parseColor("#94A3B8"));
        } else if ("RESOLVED".equals(status)) {
            holder.binding.tvEventStatus.setTextColor(Color.parseColor("#10B981"));
        } else {
            holder.binding.tvEventStatus.setTextColor(Color.parseColor("#00D2FF"));
        }

        // Description
        holder.binding.tvEventDesc.setText(event.getDescription() != null ? event.getDescription() : "Security event logged");

        // Device Tag
        String devName = event.getDeviceName();
        if (devName != null && !devName.trim().isEmpty()) {
            holder.binding.tvDeviceTag.setText(devName);
            holder.binding.tvDeviceTag.setVisibility(View.VISIBLE);
        } else {
            holder.binding.tvDeviceTag.setVisibility(View.GONE);
        }

        // Timestamp
        holder.binding.tvEventTime.setText(event.getTimestamp() != null ? event.getTimestamp() : "Recently");

        // Click listener
        holder.itemView.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onAlertClick(event);
            }
        });
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemSecurityAlertBinding binding;
        public ViewHolder(ItemSecurityAlertBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
