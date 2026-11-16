package com.example.securetrack.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
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
        holder.binding.tvCmdType.setText(cmd.getCommandType());
        holder.binding.tvCmdStatus.setText(cmd.getStatus() != null ? cmd.getStatus() : "QUEUED");
        holder.binding.tvCmdResult.setText(cmd.getResult() != null ? cmd.getResult() : "Awaiting execution");
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
