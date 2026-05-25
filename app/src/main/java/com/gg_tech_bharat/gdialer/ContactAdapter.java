package com.gg_tech_bharat.gdialer;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> {

    private final Context context;
    private List<ContactModel> contactList = new ArrayList<>();
    private List<ContactModel> filteredList = new ArrayList<>();
    private int expandedPosition = -1;

    private boolean isSelectionMode = false;
    private final Set<Integer> selectedContactIds = new HashSet<>();
    private OnSelectionModeListener selectionModeListener;

    public interface OnSelectionModeListener {
        void onSelectionModeChanged(boolean isSelectionMode, int selectedCount);
    }

    public ContactAdapter(Context context) {
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

    public void setContacts(List<ContactModel> newList) {
        final List<ContactModel> oldList = new ArrayList<>(this.filteredList);
        diffExecutor.execute(() -> {
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return oldList.size(); }
                @Override public int getNewListSize() { return newList.size(); }
                @Override public boolean areItemsTheSame(int oldPos, int newPos) { return oldList.get(oldPos).getId() == newList.get(newPos).getId(); }
                @Override public boolean areContentsTheSame(int oldPos, int newPos) { return java.util.Objects.equals(oldList.get(oldPos), newList.get(newPos)); }
            });
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                this.contactList = newList;
                this.filteredList = newList;
                this.expandedPosition = -1;
                if (!isSelectionMode) selectedContactIds.clear();
                diffResult.dispatchUpdatesTo(this);
            });
        });
    }

    public void filter(String query) {
        diffExecutor.execute(() -> {
            final List<ContactModel> result = new ArrayList<>();
            if (query == null || query.isEmpty()) {
                result.addAll(contactList);
            } else {
                String q = query.toLowerCase().replaceAll("[^a-z0-9]", "");
                for (ContactModel item : contactList) {
                    String normName = item.getNormalizedName() != null ? item.getNormalizedName() : "";
                    String normNum = item.getNormalizedNumber() != null ? item.getNormalizedNumber() : "";
                    if (normName.contains(q) || normNum.contains(q)) result.add(item);
                }
            }
            final List<ContactModel> oldList = new ArrayList<>(this.filteredList);
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
            if (!active) selectedContactIds.clear();
            notifyDataSetChanged();
            if (selectionModeListener != null) selectionModeListener.onSelectionModeChanged(active, selectedContactIds.size());
        }
    }

    public boolean isSelectionMode() { return isSelectionMode; }
    public Set<Integer> getSelectedContactIds() { return selectedContactIds; }

    public void selectAll(boolean select) {
        selectedContactIds.clear();
        if (select) for (ContactModel contact : filteredList) selectedContactIds.add(contact.getId());
        notifyDataSetChanged();
        if (selectionModeListener != null) selectionModeListener.onSelectionModeChanged(isSelectionMode, selectedContactIds.size());
    }

    private void toggleSelection(int id, int position) {
        if (selectedContactIds.contains(id)) selectedContactIds.remove(id);
        else selectedContactIds.add(id);
        notifyItemChanged(position);
        if (selectionModeListener != null) selectionModeListener.onSelectionModeChanged(isSelectionMode, selectedContactIds.size());
    }

    @NonNull @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ContactViewHolder(LayoutInflater.from(context).inflate(R.layout.contact_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        ContactModel contact = filteredList.get(position);
        holder.tvName.setText(contact.getName());
        holder.tvNumber.setText(contact.getNumber());

        com.bumptech.glide.Glide.with(context)
                .load(contact.getPhotoUri())
                .placeholder(R.drawable.ic_contacts)
                .error(R.drawable.ic_contacts)
                .override(100, 100)
                .circleCrop()
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .into(holder.ivAvatar);

        holder.cbSelect.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
        holder.cbSelect.setChecked(selectedContactIds.contains(contact.getId()));

        boolean isExpanded = !isSelectionMode && (position == expandedPosition);
        holder.layoutActions.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            if (isSelectionMode) toggleSelection(contact.getId(), holder.getBindingAdapterPosition());
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
            if (!isSelectionMode) {
                setSelectionMode(true);
                toggleSelection(contact.getId(), holder.getBindingAdapterPosition());
                return true;
            }
            return false;
        });

        holder.btnExpandCall.setOnClickListener(v -> { Utils.triggerHaptic(v); Utils.makePhoneCall(context, contact.getNumber()); });
        holder.btnExpandMessage.setOnClickListener(v -> { Utils.triggerHaptic(v); Utils.sendSMS(context, contact.getNumber(), ""); });
        holder.btnExpandInfo.setOnClickListener(v -> { Utils.triggerHaptic(v); context.startActivity(new Intent(context, ContactDetailsActivity.class).putExtra("EXTRA_NUMBER", contact.getNumber())); });
    }

    @Override public int getItemCount() { return filteredList.size(); }
    public ContactModel getContactAt(int position) { 
        if (position < 0 || position >= filteredList.size()) return null;
        return filteredList.get(position); 
    }

    public static class ContactViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvNumber;
        ShapeableImageView ivAvatar;
        CheckBox cbSelect;
        View layoutActions;
        ImageButton btnExpandCall, btnExpandMessage, btnExpandInfo;
        public ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvContactName);
            tvNumber = itemView.findViewById(R.id.tvContactNumber);
            ivAvatar = itemView.findViewById(R.id.ivContactAvatar);
            cbSelect = itemView.findViewById(R.id.cbSelect);
            layoutActions = itemView.findViewById(R.id.layoutContactExpandableActions);
            btnExpandCall = itemView.findViewById(R.id.btnExpandCall);
            btnExpandMessage = itemView.findViewById(R.id.btnExpandMessage);
            btnExpandInfo = itemView.findViewById(R.id.btnExpandInfo);
        }
    }
}
