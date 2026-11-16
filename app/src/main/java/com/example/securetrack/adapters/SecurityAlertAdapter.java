package com.example.securetrack.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.securetrack.databinding.ItemSecurityAlertBinding;
import com.example.securetrack.models.SecurityEvent;
import java.util.ArrayList;
import java.util.List;

public class SecurityAlertAdapter extends RecyclerView.Adapter<SecurityAlertAdapter.ViewHolder> {

    private final List<SecurityEvent> events = new ArrayList<>();

    public void setEvents(List<SecurityEvent> newEvents) {
        events.clear();
        if (newEvents != null) {
            events.addAll(newEvents);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSecurityAlertBinding binding = ItemSecurityAlertBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SecurityEvent event = events.get(position);
        holder.binding.tvEventType.setText(event.getEventType());
        holder.binding.tvEventSeverity.setText(event.getSeverity() != null ? event.getSeverity() : "INFO");
        holder.binding.tvEventDesc.setText(event.getDescription() != null ? event.getDescription() : "Security event logged");
        holder.binding.tvEventTime.setText(event.getTimestamp() != null ? event.getTimestamp() : "Recently");
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
