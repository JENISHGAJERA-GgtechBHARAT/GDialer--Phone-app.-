package com.gg_tech_bharat.gdialer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.VideoProfile;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

public class IncomingCallActivity extends AppCompatActivity implements SensorEventListener {

    private TextView tvCallerName, tvCallerNumber, tvSpamWarning;
    private ShapeableImageView ivCallerPhoto;
    private View layoutAccept, layoutReject, layoutCallWaitingActions, swipeActionsContainer;
    private View btnEndAndAnswer, btnHoldAndAnswer, btnMergeAndAnswer, btnQuickMessageWaiting;
    private LinearLayout layoutQuickReplies;
    private android.widget.HorizontalScrollView scrollQuickReplies;

    private String phoneNumber;
    private String callerName = "Unknown";
    private boolean isSpam = false;

    private android.media.ToneGenerator toneGenerator;

    private final BroadcastReceiver disconnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.gg_tech_bharat.gdialer.CALL_DISCONNECTED".equals(action) || 
                "com.gg_tech_bharat.gdialer.RINGING_CALL_REMOVED".equals(action)) {
                stopWaitingTone();
                finishWithTransition();
            }
        }
    };

    private final CallManager.CallStateListener callManagerListener = new CallManager.CallStateListener() {
        @Override public void onStateChanged(int state) {}
        @Override public void onCallListChanged() {
            runOnUiThread(() -> updateUIForCallWaiting());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incoming_call);

        initViews();
        setupIntentData();
        loadCallerDetails();
        setupSwipeGestures();
        setupQuickReplies();
        setupCallWaitingButtons();
        
        CallManager.registerListener(callManagerListener);
        updateUIForCallWaiting();

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.gg_tech_bharat.gdialer.CALL_DISCONNECTED");
        filter.addAction("com.gg_tech_bharat.gdialer.RINGING_CALL_REMOVED");
        try {
            androidx.core.content.ContextCompat.registerReceiver(this, disconnectReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        } catch (Exception ignored) {}
    }

    private void finishWithTransition() {
        stopWaitingTone();
        finish();
        overridePendingTransition(0, R.anim.premium_fade_out);
    }

    private void initViews() {
        tvCallerName = findViewById(R.id.tvIncomingCallerName);
        tvCallerNumber = findViewById(R.id.tvIncomingCallerNumber);
        tvSpamWarning = findViewById(R.id.tvSpamWarning);
        ivCallerPhoto = findViewById(R.id.ivIncomingCallerPhoto);
        layoutAccept = findViewById(R.id.layoutAccept);
        layoutReject = findViewById(R.id.layoutReject);
        layoutQuickReplies = findViewById(R.id.layoutQuickReplies);
        scrollQuickReplies = findViewById(R.id.scrollQuickReplies);
        layoutCallWaitingActions = findViewById(R.id.layoutCallWaitingActions);
        swipeActionsContainer = findViewById(R.id.swipeActionsContainer);
        btnEndAndAnswer = findViewById(R.id.btnEndAndAnswer);
        btnHoldAndAnswer = findViewById(R.id.btnHoldAndAnswer);
        btnMergeAndAnswer = findViewById(R.id.btnMergeAndAnswer);
        btnQuickMessageWaiting = findViewById(R.id.btnQuickMessageWaiting);
    }

    private void setupIntentData() {
        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }
        phoneNumber = intent.getStringExtra("EXTRA_NUMBER");
        String passedName = intent.getStringExtra("EXTRA_NAME");
        if (phoneNumber == null) phoneNumber = "Unknown";
        if (tvCallerNumber != null) tvCallerNumber.setText(phoneNumber);
        if (passedName != null && !passedName.equals(phoneNumber)) callerName = passedName;
        else callerName = phoneNumber;
        if (tvCallerName != null) tvCallerName.setText(callerName);
    }

    private void updateUIForCallWaiting() {
        List<Call> calls = CallManager.getCalls();
        boolean hasRinging = false;
        boolean hasActiveOrHolding = false;
        
        for (Call c : calls) {
            int s = c.getState();
            if (s == Call.STATE_RINGING) hasRinging = true;
            else if (s == Call.STATE_ACTIVE || s == Call.STATE_HOLDING) hasActiveOrHolding = true;
        }

        if (!hasRinging && hasActiveOrHolding) {
            stopWaitingTone();
            finish();
            return;
        }

        if (hasRinging && hasActiveOrHolding) {
            if (layoutCallWaitingActions != null) {
                layoutCallWaitingActions.setVisibility(View.VISIBLE);
                // Ensure Merge button is visible for multi-call situations
                if (btnMergeAndAnswer != null) btnMergeAndAnswer.setVisibility(View.VISIBLE);
            }
            if (swipeActionsContainer != null) swipeActionsContainer.setVisibility(View.GONE);
            startWaitingTone();
        } else {
            if (layoutCallWaitingActions != null) layoutCallWaitingActions.setVisibility(View.GONE);
            if (swipeActionsContainer != null) swipeActionsContainer.setVisibility(View.VISIBLE);
            stopWaitingTone();
        }
    }

    private android.os.Handler waitingToneHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private int toneCount = 0;
    private final Runnable toneRunnable = new Runnable() {
        @Override
        public void run() {
            if (toneCount < 3) {
                try {
                    if (toneGenerator == null) {
                        // Use VOICE_CALL stream for tone
                        toneGenerator = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80);
                    }
                    // Professional beep for call waiting
                    toneGenerator.startTone(ToneGenerator.TONE_SUP_DIAL, 250);
                    toneCount++;
                    waitingToneHandler.postDelayed(this, 3000); // 3-second interval
                } catch (Exception e) { 
                    Log.e("IncomingCallActivity", "Tone error", e);
                    stopWaitingTone(); 
                }
            } else {
                stopWaitingTone();
            }
        }
    };

    private void startWaitingTone() {
        toneCount = 0;
        waitingToneHandler.removeCallbacks(toneRunnable);
        waitingToneHandler.postDelayed(toneRunnable, 1000); // Start after 1s delay
    }

    private void stopWaitingTone() {
        waitingToneHandler.removeCallbacks(toneRunnable);
        if (toneGenerator != null) {
            try {
                toneGenerator.stopTone();
                toneGenerator.release();
            } catch (Exception ignored) {}
            toneGenerator = null;
        }
    }

    private void setupCallWaitingButtons() {
        if (btnEndAndAnswer != null) {
            btnEndAndAnswer.setOnClickListener(v -> {
                Utils.triggerHaptic(v);
                endOngoingAndAnswer();
            });
        }
        if (btnHoldAndAnswer != null) {
            btnHoldAndAnswer.setOnClickListener(v -> {
                Utils.triggerHaptic(v);
                holdOngoingAndAnswer();
            });
        }
        if (btnMergeAndAnswer != null) {
            btnMergeAndAnswer.setOnClickListener(v -> {
                Utils.triggerHaptic(v);
                mergeAndAnswer();
            });
        }
        if (btnQuickMessageWaiting != null) {
            btnQuickMessageWaiting.setOnClickListener(v -> {
                Utils.triggerHaptic(v);
                if (scrollQuickReplies != null) {
                    int vis = scrollQuickReplies.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE;
                    scrollQuickReplies.setVisibility(vis);
                }
            });
        }
    }

    private void endOngoingAndAnswer() {
        stopWaitingTone();
        List<Call> calls = CallManager.getCalls();
        for (Call c : calls) {
            // Disconnect any call that isn't the current ringing one
            int state = c.getState();
            if (state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING || state == Call.STATE_DIALING || state == Call.STATE_CONNECTING) {
                try { c.disconnect(); } catch (Exception ignored) {}
            }
        }
        // Buffer to ensure disconnection starts before answering
        waitingToneHandler.postDelayed(() -> answerCall(VideoProfile.STATE_AUDIO_ONLY), 400);
    }

    private void holdOngoingAndAnswer() {
        stopWaitingTone();
        List<Call> calls = CallManager.getCalls();
        for (Call c : calls) {
            if (c.getState() == Call.STATE_ACTIVE) {
                try { c.hold(); } catch (Exception ignored) {}
            }
        }
        // Small delay to let hold request propagate
        waitingToneHandler.postDelayed(() -> answerCall(VideoProfile.STATE_AUDIO_ONLY), 200);
    }

    private void mergeAndAnswer() {
        stopWaitingTone();
        // 1. Answer the incoming call
        answerCall(VideoProfile.STATE_AUDIO_ONLY);
        
        // 2. Monitor call states to merge once ready
        CallManager.registerListener(new CallManager.CallStateListener() {
            private boolean isMerged = false;
            @Override public void onStateChanged(int state) {
                if (state == Call.STATE_ACTIVE) tryMerge();
            }
            @Override public void onCallListChanged() {
                tryMerge();
            }

            private void tryMerge() {
                if (isMerged) return;
                List<Call> calls = CallManager.getCalls();
                Call active = null;
                Call holding = null;
                for (Call c : calls) {
                    if (c.getState() == Call.STATE_ACTIVE) active = c;
                    else if (c.getState() == Call.STATE_HOLDING) holding = c;
                }
                
                if (active != null && holding != null) {
                    isMerged = true;
                    try {
                        active.conference(holding);
                        Toast.makeText(IncomingCallActivity.this, "Merging calls...", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e("IncomingCallActivity", "Merge failed", e);
                        Toast.makeText(IncomingCallActivity.this, "Error on merge: try manual merge", Toast.LENGTH_LONG).show();
                    }
                    CallManager.unregisterListener(this);
                }
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        phoneNumber = intent.getStringExtra("EXTRA_NUMBER");
        if (tvCallerNumber != null) tvCallerNumber.setText(phoneNumber);
        loadCallerDetails();
        updateUIForCallWaiting();
    }

    private void loadCallerDetails() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            ContactDao dao = AppDatabase.getDatabase(this).contactDao();
            String normalized = Utils.normalizePhoneNumber(phoneNumber);
            ContactModel contact = dao.getContactByNormalizedNumber(normalized);
            if (contact == null && normalized.length() >= 10) {
                contact = dao.getContactByLastDigits(normalized.substring(normalized.length() - 10));
            }

            if (contact != null) {
                callerName = contact.getName();
                isSpam = contact.isSpam();
                final String photoUri = contact.getPhotoUri();
                
                runOnUiThread(() -> {
                    if (tvCallerName != null) tvCallerName.setText(callerName);
                    if (isSpam && tvSpamWarning != null) tvSpamWarning.setVisibility(View.VISIBLE);
                    Utils.loadContactPhoto(this, photoUri, ivCallerPhoto);
                });
            } else {
                String systemName = Utils.queryContactName(this, phoneNumber);
                if (systemName != null) {
                    callerName = systemName;
                    runOnUiThread(() -> {
                        if (tvCallerName != null) tvCallerName.setText(callerName);
                    });
                }
            }
        });
    }

    private void setupSwipeGestures() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float screenWidth = metrics.widthPixels;
        float swipeThreshold = screenWidth * 0.4f;

        if (layoutAccept != null) {
            layoutAccept.setOnClickListener(v -> answerCall(VideoProfile.STATE_AUDIO_ONLY));
            layoutAccept.setOnTouchListener(new View.OnTouchListener() {
                private float startX;
                private boolean isSwiping = false;
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            startX = event.getRawX();
                            isSwiping = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - startX;
                            if (dx > 20) {
                                isSwiping = true;
                                v.setTranslationX(dx);
                                if (dx > swipeThreshold) { 
                                    answerCall(VideoProfile.STATE_AUDIO_ONLY); 
                                    return false; 
                                }
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (!isSwiping && event.getAction() == MotionEvent.ACTION_UP) v.performClick();
                            v.animate().translationX(0).setDuration(200).start();
                            return true;
                    }
                    return false;
                }
            });
        }

        if (layoutReject != null) {
            layoutReject.setOnClickListener(v -> rejectCall());
            layoutReject.setOnTouchListener(new View.OnTouchListener() {
                private float startX;
                private boolean isSwiping = false;
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            startX = event.getRawX();
                            isSwiping = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = startX - event.getRawX();
                            if (dx > 20) {
                                isSwiping = true;
                                v.setTranslationX(-dx);
                                if (dx > swipeThreshold) { rejectCall(); return false; }
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (!isSwiping && event.getAction() == MotionEvent.ACTION_UP) v.performClick();
                            v.animate().translationX(0).setDuration(200).start();
                            return true;
                    }
                    return false;
                }
            });
        }
    }


    @Override public void onSensorChanged(SensorEvent event) {}
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override protected void onResume() { super.onResume(); }
    @Override protected void onPause() { super.onPause(); }

    private void setupQuickReplies() {
        if (layoutQuickReplies == null) return;
        layoutQuickReplies.removeAllViews();
        String[] replies = {"I will call you later.", "Can't talk.", "Meeting."};
        for (String msg : replies) { addQuickReplyButton(msg); }
        Button customBtn = new Button(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(12, 0, 12, 0);
        customBtn.setLayoutParams(params);
        customBtn.setText("Write new...");
        customBtn.setTextSize(12f);
        customBtn.setTransformationMethod(null);
        customBtn.setBackgroundResource(R.drawable.rounded_card);
        customBtn.setBackgroundTintList(androidx.core.content.ContextCompat.getColorStateList(this, R.color.accent_green));
        customBtn.setTextColor(getResources().getColor(R.color.black));
        customBtn.setOnClickListener(v -> { Utils.triggerHaptic(v); showCustomMessageDialog(); });
        layoutQuickReplies.addView(customBtn);
    }

    private void addQuickReplyButton(String msg) {
        Button btn = new Button(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(12, 0, 12, 0);
        btn.setLayoutParams(params);
        btn.setText(msg);
        btn.setTextSize(12f);
        btn.setTransformationMethod(null);
        btn.setBackgroundResource(R.drawable.rounded_card);
        btn.setBackgroundTintList(androidx.core.content.ContextCompat.getColorStateList(this, R.color.card_bg));
        btn.setTextColor(getResources().getColor(R.color.text_primary));
        btn.setOnClickListener(v -> { Utils.triggerHaptic(v); sendQuickMessageDecline(msg); });
        layoutQuickReplies.addView(btn);
    }

    private void showCustomMessageDialog() {
        android.widget.EditText editText = new android.widget.EditText(this);
        editText.setHint("Type your message here...");
        editText.setTextColor(getResources().getColor(R.color.white));
        editText.setHintTextColor(getResources().getColor(R.color.text_secondary));
        editText.setPadding(64, 64, 64, 64);
        new androidx.appcompat.app.AlertDialog.Builder(this, R.style.SamsungBottomSheetDialog)
                .setTitle("Customized Message")
                .setView(editText)
                .setPositiveButton("Send", (dialog, which) -> {
                    String customMsg = editText.getText().toString().trim();
                    if (!customMsg.isEmpty()) { sendQuickMessageDecline(customMsg); }
                })
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    private void answerCall(int videoState) {
        Call ringingCall = null;
        for (Call c : CallManager.getCalls()) {
            if (c.getState() == Call.STATE_RINGING) {
                ringingCall = c;
                break;
            }
        }
        if (ringingCall != null) {
            try {
                ringingCall.answer(videoState);
                // Let InCallServiceImpl handle the launch of OngoingCallActivity via onStateChanged
                finishWithTransition();
            } catch (Exception e) { finish(); }
        } else if (CallManager.sCurrentCall != null) {
            try {
                CallManager.sCurrentCall.answer(videoState);
                finishWithTransition();
            } catch (Exception e) { finish(); }
        } else { finish(); }
    }

    private void rejectCall() {
        Call ringingCall = null;
        for (Call c : CallManager.getCalls()) {
            if (c.getState() == Call.STATE_RINGING) {
                ringingCall = c;
                break;
            }
        }
        if (ringingCall != null) {
            try { ringingCall.disconnect(); } catch (Exception ignored) {}
        } else if (CallManager.sCurrentCall != null) {
            try { CallManager.sCurrentCall.disconnect(); } catch (Exception ignored) {}
        }
        finish();
        overridePendingTransition(0, R.anim.premium_fade_out);
    }

    private void sendQuickMessageDecline(String message) {
        rejectCall();
        Utils.sendSMS(this, phoneNumber, message);
    }

    @Override
    protected void onDestroy() {
        CallManager.unregisterListener(callManagerListener);
        try { unregisterReceiver(disconnectReceiver); } catch (Exception ignored) {}
        stopWaitingTone();
        super.onDestroy();
    }
}
