package com.example.securetrack.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.securetrack.databinding.ItemLocationHistoryBinding;
import com.example.securetrack.models.LocationRecord;
import java.util.ArrayList;
import java.util.List;

public class LocationHistoryAdapter extends RecyclerView.Adapter<LocationHistoryAdapter.ViewHolder> {

    private final List<LocationRecord> locations = new ArrayList<>();

    public void setLocations(List<LocationRecord> newLocations) {
        locations.clear();
        if (newLocations != null) {
            locations.addAll(newLocations);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLocationHistoryBinding binding = ItemLocationHistoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocationRecord loc = locations.get(position);
        holder.binding.tvHistoryCoords.setText("Lat: " + loc.getLatitude() + ", Lng: " + loc.getLongitude());
        holder.binding.tvHistoryTime.setText("Accuracy: " + loc.getAccuracy() + "m | " + loc.getTimestamp());
    }

    @Override
    public int getItemCount() {
        return locations.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemLocationHistoryBinding binding;
        public ViewHolder(ItemLocationHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
