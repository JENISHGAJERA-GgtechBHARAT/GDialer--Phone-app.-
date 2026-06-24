package com.gg_tech_bharat.gdialer;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecentAdapter extends RecyclerView.Adapter<RecentAdapter.RecentViewHolder> {

    private final Context context;
    private List<RecentModel> recentList = new ArrayList<>();
    private List<RecentModel> filteredList = new ArrayList<>();
    private int expandedPosition = -1;

    private boolean isSelectionMode = false;
    private final Set<Integer> selectedRecentIds = new HashSet<>();
    private OnSelectionModeListener selectionModeListener;

    public interface OnSelectionModeListener {
        void onSelectionModeChanged(boolean isSelectionMode, int selectedCount);
    }

    public RecentAdapter(Context context) {
        this.context = context;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= filteredList.size()) return RecyclerView.NO_ID;
        return filteredList.get(position).getId();
    }

    public void setOnSelectionModeListener(OnSelectionModeListener listener) {
        this.selectionModeListener = listener;
    }

    private final java.util.concurrent.ExecutorService diffExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    public void setRecents(List<RecentModel> newList) {
        final List<RecentModel> oldList = new ArrayList<>(this.filteredList);
        diffExecutor.execute(() -> {
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return oldList.size(); }
                @Override public int getNewListSize() { return newList.size(); }
                @Override public boolean areItemsTheSame(int oldPos, int newPos) { return oldList.get(oldPos).getId() == newList.get(newPos).getId(); }
                @Override public boolean areContentsTheSame(int oldPos, int newPos) { return java.util.Objects.equals(oldList.get(oldPos), newList.get(newPos)); }
            });
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                this.recentList = newList;
                this.filteredList = newList;
                this.expandedPosition = -1;
                if (!isSelectionMode) selectedRecentIds.clear();
                diffResult.dispatchUpdatesTo(this);
            });
        });
    }

    public void filter(String query) {
        diffExecutor.execute(() -> {
            final List<RecentModel> result = new ArrayList<>();
            if (query == null || query.isEmpty()) {
                result.addAll(recentList);
            } else {
                String q = query.toLowerCase().trim();
                for (RecentModel item : recentList) {
                    String name = (item.getName() != null) ? item.getName().toLowerCase() : "";
                    String number = (item.getNumber() != null) ? item.getNumber() : "";
                    if (name.contains(q) || number.contains(q)) result.add(item);
                }
            }

            final List<RecentModel> oldList = new ArrayList<>(this.filteredList);
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return oldList.size(); }
                @Override public int getNewListSize() { return result.size(); }
                @Override public boolean areItemsTheSame(int oldPos, int newPos) { return oldList.get(oldPos).getId() == result.get(newPos).getId(); }
                @Override public boolean areContentsTheSame(int oldPos, int newPos) { return java.util.Objects.equals(oldList.get(oldPos), result.get(newPos)); }
            });
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                this.filteredList = result;
                this.expandedPosition = -1;
                diffResult.dispatchUpdatesTo(this);
            });
        });
    }

    public void setSelectionMode(boolean active) {
        if (this.isSelectionMode != active) {
            this.isSelectionMode = active;
            if (!active) selectedRecentIds.clear();
            notifyDataSetChanged();
            if (selectionModeListener != null) selectionModeListener.onSelectionModeChanged(active, selectedRecentIds.size());
        }
    }

    public boolean isSelectionMode() { return isSelectionMode; }
    public Set<Integer> getSelectedRecentIds() { return selectedRecentIds; }

    public void selectAll(boolean select) {
        selectedRecentIds.clear();
        if (select) for (RecentModel r : filteredList) selectedRecentIds.add(r.getId());
        notifyDataSetChanged();
        if (selectionModeListener != null) selectionModeListener.onSelectionModeChanged(isSelectionMode, selectedRecentIds.size());
    }

    private void toggleSelection(int id, int p) {
        if (selectedRecentIds.contains(id)) selectedRecentIds.remove(id);
        else selectedRecentIds.add(id);
        notifyItemChanged(p);
        if (selectionModeListener != null) selectionModeListener.onSelectionModeChanged(isSelectionMode, selectedRecentIds.size());
    }

    @NonNull @Override
    public RecentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RecentViewHolder(LayoutInflater.from(context).inflate(R.layout.recent_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecentViewHolder holder, int position) {
        RecentModel recent = filteredList.get(position);
        
        String number = recent.getNumber();
        String currentName = recent.getName();
        String initialDisplay = (currentName != null && !currentName.isEmpty()) ? currentName : number;
        
        // Samsung Style: Show count next to name
        if (recent.getCallCount() > 1) {
            initialDisplay += " (" + recent.getCallCount() + ")";
        }
        holder.tvName.setText(initialDisplay);
        
        // High-performance photo load
        String photoUri = null;
        if (!"Conference".equals(number)) {
            ContactModel contact = ContactCache.getContactByNumber(number);
            if (contact != null) {
                photoUri = contact.getPhotoUri();
            }
        }
        Utils.loadContactPhoto(context, photoUri, holder.ivAvatar);

        holder.tvDetails.setText(String.format("%s • %s", Utils.formatTimestamp(recent.getTimestamp()), Utils.formatDuration(recent.getDuration())));

        int callIcon = R.drawable.ic_incoming;
        int callColor = R.color.call_incoming;
        if (recent.getCallType() == 2) {
            callIcon = R.drawable.ic_outgoing;
            callColor = R.color.gray_light; 
        } else if (recent.getCallType() == 3) {
            callIcon = R.drawable.ic_missed;
            callColor = R.color.call_missed; 
        }
        
        holder.ivTypeIcon.setImageResource(callIcon);
        holder.ivTypeIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(context, callColor));

        holder.ivRecordingBadge.setVisibility(View.GONE);
        
        holder.cbSelect.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
        holder.cbSelect.setChecked(selectedRecentIds.contains(recent.getId()));

        boolean isExpanded = !isSelectionMode && (position == expandedPosition);
        holder.layoutActions.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

        // Show phone number instead of metadata when expanded
        if (isExpanded && currentName != null && !currentName.isEmpty()) {
            holder.tvDetails.setText(number);
        } else {
            holder.tvDetails.setText(String.format("%s • %s", Utils.formatTimestamp(recent.getTimestamp()), Utils.formatDuration(recent.getDuration())));
        }

        holder.itemView.setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            if (isSelectionMode) toggleSelection(recent.getId(), holder.getBindingAdapterPosition());
            else {
                int prev = expandedPosition;
                if (isExpanded) { expandedPosition = -1; notifyItemChanged(holder.getBindingAdapterPosition()); }
                else {
                    expandedPosition = holder.getBindingAdapterPosition();
                    if (prev != -1) notifyItemChanged(prev);
                    notifyItemChanged(expandedPosition);
                }
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            Utils.triggerHaptic(v);
            if (!isSelectionMode) { setSelectionMode(true); toggleSelection(recent.getId(), holder.getBindingAdapterPosition()); return true; }
            return false;
        });

        holder.btnExpandCall.setOnClickListener(v -> { Utils.triggerHaptic(v); Utils.makePhoneCall(context, recent.getNumber()); });
        holder.btnExpandMessage.setOnClickListener(v -> { Utils.triggerHaptic(v); Utils.sendSMS(context, recent.getNumber(), ""); });
        holder.btnExpandVideo.setOnClickListener(v -> { 
            Utils.triggerHaptic(v); 
            Utils.makePhoneCall(context, recent.getNumber(), null, android.telecom.VideoProfile.STATE_BIDIRECTIONAL); 
        });
        holder.btnExpandInfo.setOnClickListener(v -> { 
            Utils.triggerHaptic(v); 
            Intent intent = new Intent(context, ContactDetailsActivity.class);
            intent.putExtra("EXTRA_NUMBER", recent.getNumber());
            // Pre-resolve name to avoid "Unknown" flicker if possible
            if (recent.getName() != null && !recent.getName().isEmpty()) {
                intent.putExtra("EXTRA_NAME", recent.getName());
            }
            context.startActivity(intent); 
        });
    }

    @Override public int getItemCount() { return filteredList.size(); }
    public RecentModel getRecentAt(int p) { 
        if (p < 0 || p >= filteredList.size()) return null;
        return filteredList.get(p); 
    }

    static class RecentViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDetails;
        ImageView ivTypeIcon, ivRecordingBadge, ivAvatar;
        CheckBox cbSelect;
        View layoutActions;
        ImageButton btnExpandCall, btnExpandMessage, btnExpandVideo, btnExpandInfo;
        public RecentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvRecentName);
            tvDetails = itemView.findViewById(R.id.tvRecentDetails);
            ivTypeIcon = itemView.findViewById(R.id.ivCallTypeIcon);
            ivRecordingBadge = itemView.findViewById(R.id.ivRecordingBadge);
            ivAvatar = itemView.findViewById(R.id.ivRecentAvatar);
            cbSelect = itemView.findViewById(R.id.cbSelect);
            layoutActions = itemView.findViewById(R.id.layoutRecentExpandableActions);
            btnExpandCall = itemView.findViewById(R.id.btnExpandCall);
            btnExpandMessage = itemView.findViewById(R.id.btnExpandMessage);
            btnExpandVideo = itemView.findViewById(R.id.btnExpandVideo);
            btnExpandInfo = itemView.findViewById(R.id.btnExpandInfo);
        }
    }
}
