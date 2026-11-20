package com.example.securetrack.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.securetrack.R;
import com.example.securetrack.databinding.ItemCommandBinding;
import com.example.securetrack.models.DeviceCommand;
import java.util.ArrayList;
import java.util.List;

public class CommandAdapter extends RecyclerView.Adapter<CommandAdapter.ViewHolder> {

    private final List<DeviceCommand> commands = new ArrayList<>();

    public void setCommands(List<DeviceCommand> newCommands) {
        commands.clear();
        if (newCommands != null) {
            commands.addAll(newCommands);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCommandBinding binding = ItemCommandBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DeviceCommand cmd = commands.get(position);
        String type = cmd.getCommandType() != null ? cmd.getCommandType() : "COMMAND";
        String transport = cmd.getTransport();
        holder.binding.tvCmdType.setText(type + " • " + transport);

        String status = cmd.getStatus() != null ? cmd.getStatus().toUpperCase() : "QUEUED";
        holder.binding.tvCmdStatus.setText(status);

        if ("EXECUTED".equals(status) || "DELIVERED".equals(status) || "SUCCESS".equals(status)) {
            holder.binding.tvCmdStatus.setBackgroundResource(R.drawable.bg_badge_success);
            holder.binding.tvCmdStatus.setTextColor(Color.parseColor("#2E7456"));
        } else if ("FAILED".equals(status) || "ERROR".equals(status)) {
            holder.binding.tvCmdStatus.setBackgroundResource(R.drawable.bg_badge_error);
            holder.binding.tvCmdStatus.setTextColor(Color.parseColor("#BA1A1A"));
        } else {
            holder.binding.tvCmdStatus.setBackgroundResource(R.drawable.bg_badge_info);
            holder.binding.tvCmdStatus.setTextColor(Color.parseColor("#255DA8"));
        }

        String resultText = cmd.getResult();
        if (resultText == null || resultText.trim().isEmpty()) {
            resultText = cmd.getPayload();
        }
        if (resultText == null || resultText.trim().isEmpty()) {
            resultText = "Awaiting execution";
        }
        holder.binding.tvCmdResult.setText(resultText);

        if (cmd.getCreatedAt() != null && !cmd.getCreatedAt().isEmpty()) {
            holder.binding.tvCmdTime.setText(cmd.getCreatedAt());
            holder.binding.tvCmdTime.setVisibility(View.VISIBLE);
        } else {
            holder.binding.tvCmdTime.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return commands.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemCommandBinding binding;
        public ViewHolder(ItemCommandBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
