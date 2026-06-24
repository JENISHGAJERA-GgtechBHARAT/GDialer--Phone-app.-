package com.gg_tech_bharat.gdialer;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
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
    private android.speech.tts.TextToSpeech tts;

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
        
        prepareVoicemailGreetingAndStart();
    }

    private void prepareVoicemailGreetingAndStart() {
        final File greetingFile = new File(getFilesDir(), "voicemail_greeting.wav");
        if (greetingFile.exists()) {
            playGreetingAndRecord(greetingFile);
        } else {
            tvStatus.setText("Preparing greeting...");
            tts = new android.speech.tts.TextToSpeech(this, status -> {
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    tts.setLanguage(java.util.Locale.US);
                    String text = "This call is picked up for voicemail. Please tell your message. I will contact you.";
                    
                    int result;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        result = tts.synthesizeToFile(text, null, greetingFile, "voicemail_uid");
                    } else {
                        java.util.HashMap<String, String> params = new java.util.HashMap<>();
                        params.put(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "voicemail_uid");
                        result = tts.synthesizeToFile(text, params, greetingFile.getAbsolutePath());
                    }
                    
                    if (result == android.speech.tts.TextToSpeech.SUCCESS) {
                        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                            @Override public void onStart(String utteranceId) {}
                            @Override public void onDone(String utteranceId) {
                                runOnUiThread(() -> playGreetingAndRecord(greetingFile));
                            }
                            @Override public void onError(String utteranceId) {
                                runOnUiThread(() -> playBeepAndRecord());
                            }
                        });
                    } else {
                        runOnUiThread(this::playBeepAndRecord);
                    }
                } else {
                    runOnUiThread(this::playBeepAndRecord);
                }
            });
        }
    }

    private void playGreetingAndRecord(File file) {
        tvStatus.setText("Playing greeting...");
        player = new MediaPlayer();
        try {
            player.setDataSource(file.getAbsolutePath());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                player.setAudioAttributes(new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
            } else {
                player.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
            }
            player.prepare();
            player.setOnCompletionListener(mp -> playBeepAndRecord());
            player.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to play greeting file", e);
            playBeepAndRecord();
        }
    }

    private void playBeepAndRecord() {
        tvStatus.setText("Ready to record...");
        new Thread(() -> {
            try {
                android.media.ToneGenerator toneGen = new android.media.ToneGenerator(AudioManager.STREAM_VOICE_CALL, 100);
                // Play double alert beep
                toneGen.startTone(android.media.ToneGenerator.TONE_SUP_PIP, 400);
                Thread.sleep(800);
                toneGen.stopTone();
                
                // Play classic beep
                toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 800);
                Thread.sleep(1000);
                toneGen.stopTone();
                toneGen.release();
            } catch (Exception e) {
                Log.e(TAG, "Beep failed", e);
            }
            runOnUiThread(this::startRecording);
        }).start();
    }

    private void startRecording() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Record audio permission not granted");
            finishVoicemail();
            return;
        }

        if (player != null) {
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }

        String fileName = "VM_" + phoneNumber + "_" + System.currentTimeMillis() + ".m4a";
        File dir = new File(getFilesDir(), "voicemails");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, fileName);
        currentFilePath = file.getAbsolutePath();

        if (tryStartRecorder(file)) {
            isRecording = true;
            startTime = System.currentTimeMillis();
            tvStatus.setText("Recording message...");
            updateWaveform();
        } else {
            Log.e(TAG, "Failed to start voicemail recording");
            finishVoicemail();
        }
    }

    private boolean tryStartRecorder(File file) {
        int[] sources = {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.MIC
        };
        
        int[] formats = {
            MediaRecorder.OutputFormat.MPEG_4,
            MediaRecorder.OutputFormat.THREE_GPP,
            MediaRecorder.OutputFormat.AMR_NB
        };
        
        int[] encoders = {
            MediaRecorder.AudioEncoder.AAC,
            MediaRecorder.AudioEncoder.AMR_NB,
            MediaRecorder.AudioEncoder.DEFAULT
        };

        for (int source : sources) {
            for (int format : formats) {
                for (int encoder : encoders) {
                    try {
                        recorder = new MediaRecorder();
                        recorder.setAudioSource(source);
                        recorder.setOutputFormat(format);
                        recorder.setAudioEncoder(encoder);
                        recorder.setOutputFile(file.getAbsolutePath());
                        recorder.prepare();
                        recorder.start();
                        
                        Log.d(TAG, "Voicemail started successfully: source=" + source + ", format=" + format + ", encoder=" + encoder);
                        return true;
                    } catch (Throwable t) {
                        Log.w(TAG, "Voicemail config failed: source=" + source + ", format=" + format + ", encoder=" + encoder + ". Error: " + t.getMessage());
                        try {
                            recorder.release();
                        } catch (Exception ignored) {}
                        recorder = null;
                    }
                }
            }
        }
        return false;
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
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {}
            tts = null;
        }
        super.onDestroy();
    }
}
