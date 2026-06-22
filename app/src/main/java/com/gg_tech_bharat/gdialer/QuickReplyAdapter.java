package com.gg_tech_bharat.gdialer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class QuickReplyAdapter extends RecyclerView.Adapter<QuickReplyAdapter.ViewHolder> {

    private List<QuickReplyModel> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onEdit(QuickReplyModel item);
        void onDelete(QuickReplyModel item);
    }

    public QuickReplyAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<QuickReplyModel> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.quick_reply_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        QuickReplyModel item = items.get(position);
        holder.tvMessage.setText(item.getMessage());
        holder.btnEdit.setOnClickListener(v -> listener.onEdit(item));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(item));
    }

    @Override public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage;
        ImageButton btnEdit, btnDelete;
        ViewHolder(View v) {
            super(v);
            tvMessage = v.findViewById(R.id.tvQuickReplyMessage);
            btnEdit = v.findViewById(R.id.btnEditQuickReply);
            btnDelete = v.findViewById(R.id.btnDeleteQuickReply);
        }
    }
}
