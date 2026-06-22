package com.gg_tech_bharat.gdialer;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.telecom.Call;
import android.telecom.VideoProfile;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public class VoicemailActivity extends AppCompatActivity {

    private static final String TAG = "VoicemailActivity";
    
    private TextView tvNumber, tvStatus, tvTranscription;
    private View btnEndVoicemail, btnPickup, viewWaveform;
    private Button btnReply1, btnReply2, btnReply3;
    
    private Call activeCall;
    private MediaRecorder mediaRecorder;
    private AudioManager audioManager;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private String phoneNumber;
    private android.os.ParcelFileDescriptor pfd;
    private Uri currentAudioUri;
    
    private boolean isRecording = false;
    private StringBuilder fullTranscript = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());

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
        
        initViews();
        setupInteraction();

        activeCall = CallManager.sCurrentCall;
        if (activeCall != null) {
            startTranscriptionEngine();
        } else {
            finish();
        }
    }

    private void initViews() {
        tvNumber = findViewById(R.id.tvVoicemailNumber);
        tvStatus = findViewById(R.id.tvVoicemailStatus);
        tvTranscription = findViewById(R.id.tvLiveTranscription);
        btnEndVoicemail = findViewById(R.id.btnEndVoicemail);
        btnPickup = findViewById(R.id.btnPickupCall);
        viewWaveform = findViewById(R.id.viewWaveformIndicator);
        btnReply1 = findViewById(R.id.btnReply1);
        btnReply2 = findViewById(R.id.btnReply2);
        btnReply3 = findViewById(R.id.btnReply3);
        
        phoneNumber = getIntent().getStringExtra("EXTRA_NUMBER");
        if (phoneNumber == null) phoneNumber = "Unknown";
        tvNumber.setText(phoneNumber);
    }

    private void setupInteraction() {
        btnEndVoicemail.setOnClickListener(v -> terminateVoicemail());
        btnPickup.setOnClickListener(v -> pickupCall());
        
        View.OnClickListener replyListener = v -> {
            String msg = ((Button)v).getText().toString();
            sendQuickReplyToCaller(msg);
        };
        btnReply1.setOnClickListener(replyListener);
        btnReply2.setOnClickListener(replyListener);
        btnReply3.setOnClickListener(replyListener);
    }

    private void startTranscriptionEngine() {
        if (activeCall.getState() == Call.STATE_RINGING) {
            activeCall.answer(VideoProfile.STATE_AUDIO_ONLY);
        }
        
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        tvStatus.setText("Screening...");

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                speakGreeting();
            }
        });

        initSpeechRecognizer();
    }

    private void speakGreeting() {
        audioManager.setSpeakerphoneOn(true); // Loopback for greeting
        String message = "Voice mail is on. Please tell your talk here, i will contact you later.";
        
        Bundle params = new Bundle();
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL);
        
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, params, "greeting");
        
        handler.postDelayed(() -> {
            audioManager.setSpeakerphoneOn(false);
            startHardwareRecording();
            startListeningToCaller();
        }, 6000);
    }

    private void sendQuickReplyToCaller(String message) {
        if (tts != null) {
            audioManager.setSpeakerphoneOn(true);
            Bundle params = new Bundle();
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL);
            tts.speak(message, TextToSpeech.QUEUE_ADD, params, "reply");
            
            // Return to privacy mode after reply
            handler.postDelayed(() -> audioManager.setSpeakerphoneOn(false), 3000);
        }
    }

    private void initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0);
                    fullTranscript.append(text).append(". ");
                    tvTranscription.setText(fullTranscript.toString());
                }
                startListeningToCaller(); // Restart loop
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    tvTranscription.setText(fullTranscript.toString() + matches.get(0));
                }
            }
            @Override public void onError(int error) { startListeningToCaller(); }
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void startListeningToCaller() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechRecognizer.startListening(intent);
    }

    private void startHardwareRecording() {
        if (isRecording) return;
        
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, "Voicemail_" + phoneNumber);
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Voicemails");
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
        
        currentAudioUri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        if (currentAudioUri == null) return;

        try {
            pfd = getContentResolver().openFileDescriptor(currentAudioUri, "w");
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(pfd.getFileDescriptor());
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
        } catch (IOException e) {
            Log.e(TAG, "Recording failed", e);
        }
    }

    private void terminateVoicemail() {
        stopAllEngines();
        if (activeCall != null) activeCall.disconnect();
        saveTranscriptionLocally();
        finish();
    }

    private void pickupCall() {
        stopAllEngines();
        audioManager.setMode(AudioManager.MODE_IN_CALL);
        
        Intent intent = new Intent(this, OngoingCallActivity.class);
        intent.putExtra("EXTRA_NUMBER", phoneNumber);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void stopAllEngines() {
        CallManager.isVoicemailScreening = false;
        if (isRecording && mediaRecorder != null) {
            try { mediaRecorder.stop(); } catch (Exception ignored) {}
            mediaRecorder.release();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (tts != null) {
            tts.shutdown();
        }
        audioManager.setMode(AudioManager.MODE_NORMAL);
    }

    private void saveTranscriptionLocally() {
        // Logic to update RecentModel in Database with fullTranscript.toString()
        // Implementation depends on your RecentDao structure
    }

    @Override
    protected void onDestroy() {
        stopAllEngines();
        super.onDestroy();
    }
}
