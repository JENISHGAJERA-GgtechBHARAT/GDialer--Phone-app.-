package com.gg_tech_bharat.gdialer;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.FavoriteViewHolder> {

    private final Context context;
    private List<ContactModel> favoriteList = new ArrayList<>();

    public FavoriteAdapter(Context context) {
        this.context = context;
    }

    private final java.util.concurrent.ExecutorService diffExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    public void setFavorites(List<ContactModel> newList) {
        final List<ContactModel> oldList = new ArrayList<>(this.favoriteList);

        diffExecutor.execute(() -> {
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return oldList.size();
                }

                @Override
                public int getNewListSize() {
                    return newList.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return oldList.get(oldItemPosition).getId() == newList.get(newItemPosition).getId();
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return java.util.Objects.equals(oldList.get(oldItemPosition), newList.get(newItemPosition));
                }
            });

            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                this.favoriteList = newList;
                diffResult.dispatchUpdatesTo(this);
            });
        });
    }

    @NonNull
    @Override
    public FavoriteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.favorite_item, parent, false);
        return new FavoriteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FavoriteViewHolder holder, int position) {
        ContactModel contact = favoriteList.get(position);
        holder.tvName.setText(contact.getName());
        holder.tvNumber.setText(contact.getNumber());

        Utils.loadContactPhoto(context, contact.getPhotoUri(), holder.ivAvatar);

        holder.itemView.setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            Intent intent = new Intent(context, ContactDetailsActivity.class);
            intent.putExtra("EXTRA_NUMBER", contact.getNumber());
            context.startActivity(intent);
        });

        holder.ivCallBadge.setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            Utils.makePhoneCall(context, contact.getNumber());
        });
    }

    @Override
    public int getItemCount() {
        return favoriteList.size();
    }

    static class FavoriteViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvNumber;
        ShapeableImageView ivAvatar;
        ImageView ivCallBadge;

        public FavoriteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvFavName);
            tvNumber = itemView.findViewById(R.id.tvFavNumber);
            ivAvatar = itemView.findViewById(R.id.ivFavAvatar);
            ivCallBadge = itemView.findViewById(R.id.ivFavCallBadge);
        }
    }
}
