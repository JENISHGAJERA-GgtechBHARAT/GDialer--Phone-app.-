package com.gg_tech_bharat.gdialer;

import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.IOException;

public class GreetingActivity extends AppCompatActivity {

    private MaterialButton btnRecord, btnPlay, btnSave, btnReset;
    private TextView tvStatus;
    private View viewWaveform;
    private MediaRecorder recorder;
    private MediaPlayer player;
    private boolean isRecording = false;
    private String tempPath;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_greeting);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        prefs = getSharedPreferences("voicemail_prefs", MODE_PRIVATE);
        tempPath = getFilesDir().getAbsolutePath() + "/temp_greeting.m4a";

        tvStatus = findViewById(R.id.tvGreetingStatus);
        viewWaveform = findViewById(R.id.viewWaveform);
        btnRecord = findViewById(R.id.btnRecordGreeting);
        btnPlay = findViewById(R.id.btnPlayGreeting);
        btnSave = findViewById(R.id.btnSaveGreeting);
        btnReset = findViewById(R.id.btnResetGreeting);

        btnRecord.setOnClickListener(v -> {
            if (isRecording) stopRecording();
            else startRecording();
        });

        btnPlay.setOnClickListener(v -> playPreview());
        btnSave.setOnClickListener(v -> saveGreeting());
        btnReset.setOnClickListener(v -> resetGreeting());

        checkExistingGreeting();
    }

    private void startRecording() {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setOutputFile(tempPath);

        try {
            recorder.prepare();
            recorder.start();
            isRecording = true;
            btnRecord.setIconResource(R.drawable.ic_phone_end);
            tvStatus.setText("Recording...");
            viewWaveform.setVisibility(View.VISIBLE);
            updateWaveform();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }
        isRecording = false;
        btnRecord.setIconResource(R.drawable.ic_record);
        tvStatus.setText("Recording finished");
        viewWaveform.setVisibility(View.INVISIBLE);
        btnPlay.setVisibility(View.VISIBLE);
        btnSave.setVisibility(View.VISIBLE);
    }

    private void updateWaveform() {
        if (isRecording && recorder != null) {
            float amplitude = recorder.getMaxAmplitude();
            float scale = 1.0f + (amplitude / 32767.0f) * 5.0f;
            viewWaveform.animate().scaleY(scale).setDuration(100).start();
            new Handler().postDelayed(this::updateWaveform, 100);
        }
    }

    private void playPreview() {
        if (player != null) player.release();
        player = new MediaPlayer();
        try {
            player.setDataSource(tempPath);
            player.prepare();
            player.start();
            tvStatus.setText("Playing preview...");
            player.setOnCompletionListener(mp -> tvStatus.setText("Preview finished"));
        } catch (IOException e) {
            Toast.makeText(this, "Error playing preview", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveGreeting() {
        File temp = new File(tempPath);
        File permanent = new File(getFilesDir(), "custom_greeting.m4a");
        if (temp.renameTo(permanent)) {
            prefs.edit().putString("greeting_path", permanent.getAbsolutePath()).apply();
            Toast.makeText(this, "Greeting saved", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Failed to save greeting", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetGreeting() {
        prefs.edit().remove("greeting_path").apply();
        File permanent = new File(getFilesDir(), "custom_greeting.m4a");
        if (permanent.exists()) permanent.delete();
        btnPlay.setVisibility(View.GONE);
        btnSave.setVisibility(View.GONE);
        tvStatus.setText("Default greeting set");
        Toast.makeText(this, "Reset to default greeting", Toast.LENGTH_SHORT).show();
    }

    private void checkExistingGreeting() {
        String path = prefs.getString("greeting_path", null);
        if (path != null) {
            File f = new File(path);
            if (f.exists()) {
                tvStatus.setText("Custom greeting is active");
                btnPlay.setVisibility(View.VISIBLE);
                tempPath = path; // Allow playing existing
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (recorder != null) recorder.release();
        if (player != null) player.release();
        super.onDestroy();
    }
}
