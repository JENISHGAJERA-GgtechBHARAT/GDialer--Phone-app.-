package com.gg_tech_bharat.gdialer;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.imageview.ShapeableImageView;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RecordingsListActivity extends AppCompatActivity {

    private RecyclerView rvRecordings;
    private TextView tvEmpty;
    private RecordingsAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recordings_list);

        rvRecordings = findViewById(R.id.rvRecordings);
        tvEmpty = findViewById(R.id.tvEmpty);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        rvRecordings.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecordingsAdapter(this);
        rvRecordings.setAdapter(adapter);

        loadRecordings();
    }

    private void loadRecordings() {
        File dir = new File(getFilesDir(), "recordings");
        List<RecordingModel> recordingsList = new ArrayList<>();

        if (dir.exists()) {
            File[] files = dir.listFiles((dir1, name) -> name.startsWith("REC_") && name.endsWith(".m4a"));
            if (files != null) {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                for (File file : files) {
                    try {
                        String name = file.getName();
                        // Format: REC_Number_Timestamp.m4a
                        String[] parts = name.substring(4, name.length() - 4).split("_");
                        String number = "Unknown";
                        long timestamp = file.lastModified();
                        if (parts.length >= 1) {
                            number = parts[0];
                        }
                        if (parts.length >= 2) {
                            try {
                                timestamp = Long.parseLong(parts[1]);
                            } catch (NumberFormatException ignored) {}
                        }

                        // Retrieve duration
                        long durationSecs = 0;
                        try {
                            retriever.setDataSource(file.getAbsolutePath());
                            String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                            if (time != null) {
                                durationSecs = Long.parseLong(time) / 1000;
                            }
                        } catch (Exception e) {
                            durationSecs = 0;
                        }

                        String contactName = Utils.queryContactName(this, number);
                        if (contactName == null) contactName = number;

                        recordingsList.add(new RecordingModel(
                                number,
                                contactName,
                                file.getAbsolutePath(),
                                timestamp,
                                durationSecs
                        ));
                    } catch (Exception ignored) {}
                }
                try {
                    retriever.release();
                } catch (Exception ignored) {}
            }
        }

        // Sort by timestamp descending (newest first)
        Collections.sort(recordingsList, (r1, r2) -> Long.compare(r2.getTimestamp(), r1.getTimestamp()));

        adapter.setRecordings(recordingsList);
        tvEmpty.setVisibility(recordingsList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        if (adapter != null) adapter.release();
        super.onDestroy();
    }

    public static class RecordingModel {
        private final String phoneNumber;
        private final String contactName;
        private final String filePath;
        private final long timestamp;
        private final long duration;

        public RecordingModel(String phoneNumber, String contactName, String filePath, long timestamp, long duration) {
            this.phoneNumber = phoneNumber;
            this.contactName = contactName;
            this.filePath = filePath;
            this.timestamp = timestamp;
            this.duration = duration;
        }

        public String getPhoneNumber() { return phoneNumber; }
        public String getContactName() { return contactName; }
        public String getFilePath() { return filePath; }
        public long getTimestamp() { return timestamp; }
        public long getDuration() { return duration; }
    }

    private class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.ViewHolder> {

        private final Context context;
        private List<RecordingModel> recordings = new ArrayList<>();
        private int expandedPosition = -1;
        private MediaPlayer mediaPlayer;
        private final Handler seekHandler = new Handler(Looper.getMainLooper());
        private ViewHolder currentPlayingHolder;
        private float currentSpeed = 1.0f;
        private boolean isSpeakerOn = true;

        public RecordingsAdapter(Context context) {
            this.context = context;
        }

        public void setRecordings(List<RecordingModel> recordings) {
            this.recordings = recordings;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.item_recording, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RecordingModel recording = recordings.get(position);
            holder.tvName.setText(recording.getContactName());
            holder.tvNumber.setText(recording.getPhoneNumber());
            holder.tvTime.setText(Utils.formatTimestamp(recording.getTimestamp()));
            holder.tvDuration.setText(Utils.formatDuration(recording.getDuration()));

            boolean isExpanded = position == expandedPosition;
            holder.layoutPlayer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> {
                int currentPos = holder.getBindingAdapterPosition();
                if (currentPos == RecyclerView.NO_POSITION) return;

                int prevExpanded = expandedPosition;
                expandedPosition = (expandedPosition == currentPos) ? -1 : currentPos;

                if (currentPlayingHolder != null && (expandedPosition == -1 || prevExpanded == currentPlayingHolder.getBindingAdapterPosition())) {
                    stopPlaying();
                }

                if (prevExpanded != -1) notifyItemChanged(prevExpanded);
                if (expandedPosition != -1) notifyItemChanged(expandedPosition);
            });

            holder.btnPlayPause.setOnClickListener(v -> handlePlayPause(holder, recording));
            holder.btnCallback.setOnClickListener(v -> Utils.makePhoneCall(context, recording.getPhoneNumber()));
            holder.btnDelete.setOnClickListener(v -> deleteRecording(holder.getBindingAdapterPosition(), recording));
            holder.btnShare.setOnClickListener(v -> shareRecording(recording));

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

        private void handlePlayPause(ViewHolder holder, RecordingModel recording) {
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
                startPlaying(holder, recording);
            }
        }

        private void startPlaying(ViewHolder holder, RecordingModel recording) {
            stopPlaying();
            currentPlayingHolder = holder;
            mediaPlayer = new MediaPlayer();
            try {
                mediaPlayer.setDataSource(recording.getFilePath());
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
                Toast.makeText(context, "Error playing recording", Toast.LENGTH_SHORT).show();
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

            holder.btnPlaybackSpeed.setText(String.format(Locale.getDefault(), "%.1fx", currentSpeed));
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

        private void deleteRecording(int position, RecordingModel recording) {
            try {
                File file = new File(recording.getFilePath());
                if (file.exists()) {
                    if (file.delete()) {
                        recordings.remove(position);
                        notifyItemRemoved(position);
                        notifyItemRangeChanged(position, recordings.size());
                        if (recordings.isEmpty()) {
                            tvEmpty.setVisibility(View.VISIBLE);
                        }
                        Toast.makeText(context, "Recording deleted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Exception e) {
                Toast.makeText(context, "Error deleting recording", Toast.LENGTH_SHORT).show();
            }
        }

        private void shareRecording(RecordingModel recording) {
            try {
                File file = new File(recording.getFilePath());
                Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("audio/*");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(Intent.createChooser(intent, "Share Recording"));
            } catch (Exception e) {
                Toast.makeText(context, "Cannot share recording", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public int getItemCount() {
            return recordings.size();
        }

        public void release() {
            stopPlaying();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvNumber, tvTime, tvDuration, btnPlaybackSpeed;
            ShapeableImageView ivAvatar;
            View layoutPlayer;
            ImageButton btnPlayPause, btnCallback, btnShare, btnDelete, btnSpeakerToggle;
            SeekBar seekBar;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvRecordingName);
                tvNumber = itemView.findViewById(R.id.tvRecordingNumber);
                tvTime = itemView.findViewById(R.id.tvRecordingTime);
                tvDuration = itemView.findViewById(R.id.tvRecordingDuration);
                ivAvatar = itemView.findViewById(R.id.ivRecordingAvatar);
                layoutPlayer = itemView.findViewById(R.id.layoutRecordingPlayer);
                btnPlayPause = itemView.findViewById(R.id.btnRecordingPlayPause);
                btnCallback = itemView.findViewById(R.id.btnRecordingCallback);
                btnShare = itemView.findViewById(R.id.btnRecordingShare);
                btnDelete = itemView.findViewById(R.id.btnRecordingDelete);
                btnPlaybackSpeed = itemView.findViewById(R.id.btnPlaybackSpeed);
                btnSpeakerToggle = itemView.findViewById(R.id.btnSpeakerToggle);
                seekBar = itemView.findViewById(R.id.recordingSeekBar);
            }
        }
    }
}
