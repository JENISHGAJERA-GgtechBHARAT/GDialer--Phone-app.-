package com.gg_tech_bharat.gdialer;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class VoicemailLocalAdapter extends RecyclerView.Adapter<VoicemailLocalAdapter.ViewHolder> {

    private final Context context;
    private List<VoicemailEntity> voicemails = new ArrayList<>();
    private int expandedPosition = -1;
    private MediaPlayer mediaPlayer;
    private final Handler seekHandler = new Handler(Looper.getMainLooper());
    private ViewHolder currentPlayingHolder;
    private float currentSpeed = 1.0f;
    private boolean isSpeakerOn = true;

    public VoicemailLocalAdapter(Context context) {
        this.context = context;
    }

    public void setVoicemails(List<VoicemailEntity> voicemails) {
        this.voicemails = voicemails;
        notifyDataSetChanged();
    }

    public VoicemailEntity getVoicemailAt(int position) {
        if (position >= 0 && position < voicemails.size()) {
            return voicemails.get(position);
        }
        return null;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.item_voicemail_local, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VoicemailEntity voicemail = voicemails.get(position);
        holder.tvName.setText(voicemail.getContactName());
        holder.tvNumber.setText(voicemail.getPhoneNumber());
        holder.tvTime.setText(Utils.formatTimestamp(voicemail.getTimestamp()));
        holder.tvDuration.setText(Utils.formatDuration(voicemail.getDuration()));
        
        holder.unreadIndicator.setVisibility(voicemail.isRead() ? View.GONE : View.VISIBLE);
        holder.ivStar.setImageResource(voicemail.isStarred() ? R.drawable.ic_star : R.drawable.ic_star_border);

        boolean isExpanded = position == expandedPosition;
        holder.layoutPlayer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        
        holder.itemView.setOnClickListener(v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return;

            int prevExpanded = expandedPosition;
            expandedPosition = (expandedPosition == currentPos) ? -1 : currentPos;
            
            if (prevExpanded != -1) notifyItemChanged(prevExpanded);
            if (expandedPosition != -1) notifyItemChanged(expandedPosition);
            
            if (!voicemail.isRead()) {
                voicemail.setRead(true);
                AppDatabase.databaseWriteExecutor.execute(() -> 
                    AppDatabase.getDatabase(context).voicemailDao().update(voicemail)
                );
            }
        });

        holder.btnPlayPause.setOnClickListener(v -> handlePlayPause(holder, voicemail));
        holder.btnCallback.setOnClickListener(v -> Utils.makePhoneCall(context, voicemail.getPhoneNumber()));
        holder.btnDelete.setOnClickListener(v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                deleteVoicemail(voicemail);
            }
        });
        holder.btnShare.setOnClickListener(v -> shareVoicemail(voicemail));
        
        holder.ivStar.setOnClickListener(v -> {
            voicemail.setStarred(!voicemail.isStarred());
            AppDatabase.databaseWriteExecutor.execute(() -> 
                AppDatabase.getDatabase(context).voicemailDao().update(voicemail)
            );
            notifyItemChanged(holder.getBindingAdapterPosition());
        });

        holder.btnPlaybackSpeed.setOnClickListener(v -> cycleSpeed(holder));

        holder.btnSpeakerToggle.setOnClickListener(v -> toggleSpeaker(holder));

        if (isExpanded && mediaPlayer != null && currentPlayingHolder == holder) {
            updateSeekBar(holder);
        } else {
            holder.seekBar.setProgress(0);
            holder.btnPlayPause.setImageResource(R.drawable.ic_play);
        }

        holder.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null && currentPlayingHolder == holder) {
                    mediaPlayer.seekTo(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void handlePlayPause(ViewHolder holder, VoicemailEntity voicemail) {
        if (mediaPlayer != null && currentPlayingHolder == holder) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                holder.btnPlayPause.setImageResource(R.drawable.ic_play);
            } else {
                mediaPlayer.start();
                holder.btnPlayPause.setImageResource(R.drawable.ic_pause);
                updateSeekBar(holder);
            }
        } else {
            startPlaying(holder, voicemail);
        }
    }

    private void startPlaying(ViewHolder holder, VoicemailEntity voicemail) {
        stopPlaying();
        currentPlayingHolder = holder;
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(voicemail.getFilePath());
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            
            mediaPlayer.prepare();
            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(currentSpeed));
            mediaPlayer.start();
            
            holder.btnPlayPause.setImageResource(R.drawable.ic_pause);
            holder.seekBar.setMax(mediaPlayer.getDuration());
            updateSeekBar(holder);

            mediaPlayer.setOnCompletionListener(mp -> {
                holder.btnPlayPause.setImageResource(R.drawable.ic_play);
                holder.seekBar.setProgress(0);
                stopPlaying();
            });
        } catch (Exception e) {
            Toast.makeText(context, "Error playing voicemail", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPlaying() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (currentPlayingHolder != null) {
            currentPlayingHolder.btnPlayPause.setImageResource(R.drawable.ic_play);
            currentPlayingHolder = null;
        }
        seekHandler.removeCallbacksAndMessages(null);
    }

    private void updateSeekBar(ViewHolder holder) {
        if (mediaPlayer != null && holder == currentPlayingHolder && mediaPlayer.isPlaying()) {
            holder.seekBar.setProgress(mediaPlayer.getCurrentPosition());
            seekHandler.postDelayed(() -> updateSeekBar(holder), 100);
        }
    }

    private void cycleSpeed(ViewHolder holder) {
        if (currentSpeed == 1.0f) currentSpeed = 1.5f;
        else if (currentSpeed == 1.5f) currentSpeed = 2.0f;
        else if (currentSpeed == 2.0f) currentSpeed = 0.5f;
        else currentSpeed = 1.0f;

        holder.btnPlaybackSpeed.setText(String.format(java.util.Locale.getDefault(), "%.1fx", currentSpeed));
        if (mediaPlayer != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(currentSpeed));
            } catch (Exception ignored) {}
        }
    }

    private void toggleSpeaker(ViewHolder holder) {
        isSpeakerOn = !isSpeakerOn;
        holder.btnSpeakerToggle.setImageResource(isSpeakerOn ? R.drawable.ic_speaker : R.drawable.ic_mic);
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.setSpeakerphoneOn(isSpeakerOn);
        }
    }

    private void deleteVoicemail(VoicemailEntity voicemail) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase.getDatabase(context).voicemailDao().delete(voicemail);
            File file = new File(voicemail.getFilePath());
            if (file.exists()) file.delete();
        });
    }

    private void shareVoicemail(VoicemailEntity voicemail) {
        try {
            File file = new File(voicemail.getFilePath());
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("audio/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent, "Share Voicemail"));
        } catch (Exception e) {
            Toast.makeText(context, "Cannot share voicemail", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return voicemails.size();
    }

    public void release() {
        stopPlaying();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvNumber, tvTime, tvDuration, btnPlaybackSpeed;
        ShapeableImageView ivAvatar;
        ImageView ivStar;
        View unreadIndicator, layoutPlayer;
        ImageButton btnPlayPause, btnCallback, btnShare, btnDelete, btnSpeakerToggle;
        SeekBar seekBar;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvVoicemailName);
            tvNumber = itemView.findViewById(R.id.tvVoicemailNumber);
            tvTime = itemView.findViewById(R.id.tvVoicemailTime);
            tvDuration = itemView.findViewById(R.id.tvVoicemailDuration);
            ivAvatar = itemView.findViewById(R.id.ivVoicemailAvatar);
            ivStar = itemView.findViewById(R.id.ivVoicemailStar);
            unreadIndicator = itemView.findViewById(R.id.unreadIndicator);
            layoutPlayer = itemView.findViewById(R.id.layoutVoicemailPlayer);
            btnPlayPause = itemView.findViewById(R.id.btnVoicemailPlayPause);
            btnCallback = itemView.findViewById(R.id.btnVoicemailCallback);
            btnShare = itemView.findViewById(R.id.btnVoicemailShare);
            btnDelete = itemView.findViewById(R.id.btnVoicemailDelete);
            btnPlaybackSpeed = itemView.findViewById(R.id.btnPlaybackSpeed);
            btnSpeakerToggle = itemView.findViewById(R.id.btnSpeakerToggle);
            seekBar = itemView.findViewById(R.id.voicemailSeekBar);
        }
    }
}
