package com.gg_tech_bharat.gdialer;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.VideoProfile;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;

public class VoicemailActivity extends AppCompatActivity {

    private static final String TAG = "VoicemailActivity";
    
    private TextView tvNumber, tvStatus;
    private View btnEnd, viewWaveform;
    
    private Call activeCall;
    private MediaRecorder recorder;
    private MediaPlayer player;
    private AudioManager audioManager;
    private String phoneNumber;
    private boolean isRecording = false;
    private String currentFilePath;
    private long startTime;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON 
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED 
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);
                
        setContentView(R.layout.activity_voicemail);
        
        CallManager.isVoicemailScreening = true;
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        
        tvNumber = findViewById(R.id.tvVoicemailNumber);
        tvStatus = findViewById(R.id.tvVoicemailStatus);
        btnEnd = findViewById(R.id.btnEndVoicemail);
        viewWaveform = findViewById(R.id.viewWaveformIndicator);
        
        // Disable pickup button for this local implementation
        View btnPickup = findViewById(R.id.btnPickupCall);
        if (btnPickup != null) btnPickup.setVisibility(View.GONE);

        phoneNumber = getIntent().getStringExtra("EXTRA_NUMBER");
        if (phoneNumber == null) phoneNumber = "Unknown";
        tvNumber.setText(phoneNumber);

        activeCall = CallManager.sCurrentCall;
        if (activeCall != null) {
            startVoicemailProcess();
        } else {
            finish();
        }

        btnEnd.setOnClickListener(v -> finishVoicemail());
    }

    private void startVoicemailProcess() {
        if (activeCall.getState() == Call.STATE_RINGING) {
            activeCall.answer(VideoProfile.STATE_AUDIO_ONLY);
        }
        
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(true);
        
        tvStatus.setText("Playing greeting...");
        
        SharedPreferences prefs = getSharedPreferences("voicemail_prefs", MODE_PRIVATE);
        String greetingPath = prefs.getString("greeting_path", null);
        
        player = new MediaPlayer();
        try {
            if (greetingPath != null && new File(greetingPath).exists()) {
                player.setDataSource(greetingPath);
            } else {
                // Fallback to a default beep or greeting if asset exists
                // For now, we'll just wait a bit and start recording
                startRecording();
                return;
            }
            
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            
            player.prepare();
            player.setOnCompletionListener(mp -> startRecording());
            player.start();
        } catch (IOException e) {
            startRecording();
        }
    }

    private void startRecording() {
        if (player != null) {
            player.release();
            player = null;
        }

        String fileName = "VM_" + phoneNumber + "_" + System.currentTimeMillis() + ".m4a";
        File dir = new File(getFilesDir(), "voicemails");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, fileName);
        currentFilePath = file.getAbsolutePath();

        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setOutputFile(currentFilePath);

        try {
            recorder.prepare();
            recorder.start();
            isRecording = true;
            startTime = System.currentTimeMillis();
            tvStatus.setText("Recording message...");
            updateWaveform();
        } catch (IOException e) {
            Log.e(TAG, "Failed to start recording", e);
            finishVoicemail();
        }
    }

    private void updateWaveform() {
        if (isRecording && recorder != null) {
            float amplitude = recorder.getMaxAmplitude();
            float scale = 1.0f + (amplitude / 32767.0f) * 6.0f;
            viewWaveform.animate().scaleY(scale).setDuration(100).start();
            new Handler(Looper.getMainLooper()).postDelayed(this::updateWaveform, 100);
        }
    }

    private void finishVoicemail() {
        if (isRecording && recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
            isRecording = false;
            
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            saveVoicemailToDb(duration);
        }
        
        if (player != null) {
            player.release();
            player = null;
        }

        if (activeCall != null) {
            activeCall.disconnect();
        }

        CallManager.isVoicemailScreening = false;
        audioManager.setMode(AudioManager.MODE_NORMAL);
        finish();
    }

    private void saveVoicemailToDb(long duration) {
        if (duration < 1) {
            new File(currentFilePath).delete();
            return;
        }

        String contactName = Utils.queryContactName(this, phoneNumber);
        if (contactName == null) contactName = phoneNumber;

        VoicemailEntity entity = new VoicemailEntity(phoneNumber, contactName, currentFilePath, System.currentTimeMillis(), duration);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase.getDatabase(VoicemailActivity.this).voicemailDao().insert(entity);
        });
    }

    @Override
    protected void onDestroy() {
        if (isRecording) finishVoicemail();
        super.onDestroy();
    }
}
