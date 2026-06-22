package com.gg_tech_bharat.gdialer;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VoicemailAdapter extends RecyclerView.Adapter<VoicemailAdapter.VoicemailViewHolder> {

    private final Context context;
    private final List<VoicemailModel> voicemails;
    private MediaPlayer mediaPlayer;
    
    private boolean isSelectionMode = false;
    private final Set<Long> selectedIds = new HashSet<>();
    private OnSelectionModeListener selectionModeListener;

    public interface OnSelectionModeListener {
        void onSelectionModeChanged(boolean isSelectionMode, int selectedCount);
    }

    public VoicemailAdapter(Context context, List<VoicemailModel> voicemails) {
        this.context = context;
        this.voicemails = voicemails;
    }

    public void setOnSelectionModeListener(OnSelectionModeListener listener) {
        this.selectionModeListener = listener;
    }

    public void setSelectionMode(boolean active) {
        this.isSelectionMode = active;
        if (!active) selectedIds.clear();
        notifyDataSetChanged();
        if (selectionModeListener != null) selectionModeListener.onSelectionModeChanged(active, selectedIds.size());
    }

    public void selectAll(boolean select) {
        selectedIds.clear();
        if (select) {
            for (VoicemailModel item : voicemails) selectedIds.add(item.getId());
        }
        notifyDataSetChanged();
        if (selectionModeListener != null) selectionModeListener.onSelectionModeChanged(isSelectionMode, selectedIds.size());
    }

    public Set<Long> getSelectedIds() { return selectedIds; }

    @NonNull
    @Override
    public VoicemailViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VoicemailViewHolder(LayoutInflater.from(context).inflate(R.layout.item_voicemail, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VoicemailViewHolder holder, int position) {
        VoicemailModel item = voicemails.get(position);
        holder.tvName.setText(item.getName());
        holder.tvTime.setText(Utils.formatTimestamp(item.getTimestamp()));

        holder.cbSelect.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
        holder.cbSelect.setChecked(selectedIds.contains(item.getId()));
        holder.btnPlay.setVisibility(isSelectionMode ? View.GONE : View.VISIBLE);

        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(item.getId());
            } else {
                playVoicemail(item);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                setSelectionMode(true);
                toggleSelection(item.getId());
                return true;
            }
            return false;
        });

        holder.btnPlay.setOnClickListener(v -> playVoicemail(item));
    }

    private void toggleSelection(long id) {
        if (selectedIds.contains(id)) selectedIds.remove(id);
        else selectedIds.add(id);
        notifyDataSetChanged();
        if (selectionModeListener != null) selectionModeListener.onSelectionModeChanged(isSelectionMode, selectedIds.size());
    }

    private void playVoicemail(VoicemailModel item) {
        try {
            if (mediaPlayer != null) {
                try { mediaPlayer.stop(); } catch (Exception ignored) {}
                mediaPlayer.release();
                mediaPlayer = null;
            }
            
            mediaPlayer = new MediaPlayer();
            
            // Modern Audio Attributes for guaranteed speaker playback
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            
            mediaPlayer.setAudioAttributes(attributes);
            mediaPlayer.setDataSource(context, item.getUri());
            mediaPlayer.setVolume(1.0f, 1.0f);
            
            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d("VoicemailAdapter", "MediaPlayer Prepared. Starting playback...");
                mp.start();
                Toast.makeText(context, "Playing voicemail...", Toast.LENGTH_SHORT).show();
            });
            
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e("VoicemailAdapter", "Playback Error: " + what + ", " + extra);
                Toast.makeText(context, "Playback failed: Invalid file", Toast.LENGTH_SHORT).show();
                return true;
            });
            
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e("VoicemailAdapter", "Setup failed", e);
            Toast.makeText(context, "Could not play voicemail", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() { return voicemails.size(); }

    public void release() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    static class VoicemailViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvTime;
        ImageButton btnPlay;
        CheckBox cbSelect;
        public VoicemailViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvTime = itemView.findViewById(R.id.tvTime);
            btnPlay = itemView.findViewById(R.id.btnPlay);
            cbSelect = itemView.findViewById(R.id.cbSelectVoicemail);
        }
    }
}
