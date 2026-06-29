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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

public class IncomingCallActivity extends AppCompatActivity implements SensorEventListener {

    private TextView tvCallerName, tvCallerNumber, tvSpamWarning;
    private TextView tvIncomingVolte, tvIncomingVowifi;
    private ShapeableImageView ivCallerPhoto;
    private View layoutAccept, layoutReject, layoutCallWaitingActions, swipeActionsContainer;
    private View btnEndAndAnswer, btnHoldAndAnswer, btnMergeAndAnswer, btnQuickMessageWaiting;
    private View layoutQuickResponseHandle, btnScreenCallVoicemail, layoutIncomingSecondaryActions;
    private View viewAcceptRing1, viewAcceptRing2, viewRejectRing1, viewRejectRing2;

    private String phoneNumber;
    private String callerName = "Unknown";
    private boolean isSpam = false;
    private boolean needUnlockFirst = false;

    private android.media.ToneGenerator toneGenerator;
    private com.google.android.material.bottomsheet.BottomSheetDialog quickReplyDialog;

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
            runOnUiThread(() -> {
                updateUIForCallWaiting();
                updateCallStatusIndicators();
            });
        }
    };

    private void updateCallStatusIndicators() {
        Call ringingCall = null;
        for (Call c : CallManager.getCalls()) {
            if (c.getState() == Call.STATE_RINGING) {
                ringingCall = c;
                break;
            }
        }

        if (ringingCall != null) {
            Call.Details details = ringingCall.getDetails();
            if (details != null) {
                int props = details.getCallProperties();
                boolean isWifi = (props & Call.Details.PROPERTY_WIFI) != 0;
                boolean isVolte = !isWifi && ((props & Call.Details.PROPERTY_HIGH_DEF_AUDIO) != 0 || 
                                 (details.getCallCapabilities() & Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL) != 0);

                if (tvIncomingVowifi != null) tvIncomingVowifi.setVisibility(isWifi ? View.VISIBLE : View.GONE);
                if (tvIncomingVolte != null) tvIncomingVolte.setVisibility(isVolte ? View.VISIBLE : View.GONE);
            }
        }
    }

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
        startPulseAnimations();
        
        CallManager.registerListener(callManagerListener);
        updateUIForCallWaiting();
        updateCallStatusIndicators();

        if (InCallServiceImpl.sInstance != null) {
            InCallServiceImpl.sInstance.silenceRingingNotification();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.gg_tech_bharat.gdialer.CALL_DISCONNECTED");
        filter.addAction("com.gg_tech_bharat.gdialer.RINGING_CALL_REMOVED");
        try {
            androidx.core.content.ContextCompat.registerReceiver(this, disconnectReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        } catch (Exception ignored) {}

        if (getIntent().getBooleanExtra("TRIGGER_UNLOCK_AND_ANSWER", false)) {
            int videoState = getIntent().getIntExtra("VIDEO_STATE", VideoProfile.STATE_AUDIO_ONLY);
            needUnlockFirst = true;
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    answerCall(videoState);
                }
            }, 300);
        }
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
        tvIncomingVolte = findViewById(R.id.tvIncomingVolte);
        tvIncomingVowifi = findViewById(R.id.tvIncomingVowifi);
        ivCallerPhoto = findViewById(R.id.ivIncomingCallerPhoto);
        layoutAccept = findViewById(R.id.layoutAccept);
        layoutReject = findViewById(R.id.layoutReject);
        layoutQuickResponseHandle = findViewById(R.id.layoutQuickResponseHandle);
        btnScreenCallVoicemail = findViewById(R.id.btnScreenCallVoicemail);
        layoutIncomingSecondaryActions = findViewById(R.id.layoutIncomingSecondaryActions);
        viewAcceptRing1 = findViewById(R.id.viewAcceptRing1);
        viewAcceptRing2 = findViewById(R.id.viewAcceptRing2);
        viewRejectRing1 = findViewById(R.id.viewRejectRing1);
        viewRejectRing2 = findViewById(R.id.viewRejectRing2);
        layoutCallWaitingActions = findViewById(R.id.layoutCallWaitingActions);
        swipeActionsContainer = findViewById(R.id.swipeActionsContainer);
        btnEndAndAnswer = findViewById(R.id.btnEndAndAnswer);
        btnHoldAndAnswer = findViewById(R.id.btnHoldAndAnswer);
        btnMergeAndAnswer = findViewById(R.id.btnMergeAndAnswer);
        btnQuickMessageWaiting = findViewById(R.id.btnQuickMessageWaiting);

        if (layoutAccept != null) {
            layoutAccept.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#34C759")));
        }
        if (layoutReject != null) {
            layoutReject.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF3B30")));
        }
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
                if (btnMergeAndAnswer != null) btnMergeAndAnswer.setVisibility(View.VISIBLE);
            }
            if (swipeActionsContainer != null) swipeActionsContainer.setVisibility(View.GONE);
            if (layoutIncomingSecondaryActions != null) layoutIncomingSecondaryActions.setVisibility(View.GONE);
            startWaitingTone();
        } else {
            if (layoutCallWaitingActions != null) layoutCallWaitingActions.setVisibility(View.GONE);
            if (swipeActionsContainer != null) swipeActionsContainer.setVisibility(View.VISIBLE);
            if (layoutIncomingSecondaryActions != null) layoutIncomingSecondaryActions.setVisibility(View.VISIBLE);
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
                        toneGenerator = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80);
                    }
                    toneGenerator.startTone(ToneGenerator.TONE_SUP_DIAL, 250);
                    toneCount++;
                    waitingToneHandler.postDelayed(this, 3000);
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
        waitingToneHandler.postDelayed(toneRunnable, 1000);
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
        if (btnEndAndAnswer != null) btnEndAndAnswer.setOnClickListener(v -> { Utils.triggerHaptic(v); endOngoingAndAnswer(); });
        if (btnHoldAndAnswer != null) btnHoldAndAnswer.setOnClickListener(v -> { Utils.triggerHaptic(v); holdOngoingAndAnswer(); });
        if (btnMergeAndAnswer != null) btnMergeAndAnswer.setOnClickListener(v -> { Utils.triggerHaptic(v); mergeAndAnswer(); });
        if (btnQuickMessageWaiting != null) btnQuickMessageWaiting.setOnClickListener(v -> { Utils.triggerHaptic(v); showQuickReplySheet(); });
    }

    private void endOngoingAndAnswer() {
        stopWaitingTone();
        List<Call> calls = CallManager.getCalls();
        for (Call c : calls) {
            int state = c.getState();
            if (state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING || state == Call.STATE_DIALING || state == Call.STATE_CONNECTING) {
                try { c.disconnect(); } catch (Exception ignored) {}
            }
        }
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
        waitingToneHandler.postDelayed(() -> answerCall(VideoProfile.STATE_AUDIO_ONLY), 200);
    }

    private void mergeAndAnswer() {
        stopWaitingTone();
        answerCall(VideoProfile.STATE_AUDIO_ONLY);
        CallManager.registerListener(new CallManager.CallStateListener() {
            private boolean isMerged = false;
            @Override public void onStateChanged(int state) { if (state == Call.STATE_ACTIVE) tryMerge(); }
            @Override public void onCallListChanged() { tryMerge(); }
            private void tryMerge() {
                if (isMerged) return;
                List<Call> calls = CallManager.getCalls();
                Call active = null; Call holding = null;
                for (Call c : calls) { if (c.getState() == Call.STATE_ACTIVE) active = c; else if (c.getState() == Call.STATE_HOLDING) holding = c; }
                if (active != null && holding != null) {
                    isMerged = true;
                    try {
                        active.conference(holding);
                        Toast.makeText(IncomingCallActivity.this, "Merging calls...", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e("IncomingCallActivity", "Merge failed", e);
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

        if (intent.getBooleanExtra("TRIGGER_UNLOCK_AND_ANSWER", false)) {
            int videoState = intent.getIntExtra("VIDEO_STATE", VideoProfile.STATE_AUDIO_ONLY);
            needUnlockFirst = true;
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    answerCall(videoState);
                }
            }, 300);
        }
    }

    private void loadCallerDetails() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            ContactDao dao = AppDatabase.getDatabase(this).contactDao();
            String normalized = Utils.normalizePhoneNumber(phoneNumber);
            ContactModel contact = dao.getContactByNormalizedNumber(normalized);
            if (contact == null && normalized.length() >= 10) contact = dao.getContactByLastDigits(normalized.substring(normalized.length() - 10));
            if (contact != null) {
                callerName = contact.getName();
                isSpam = contact.isSpam();
                needUnlockFirst = contact.isNeedUnlock();
                final String photoUri = contact.getPhotoUri();
                runOnUiThread(() -> {
                    if (tvCallerName != null) tvCallerName.setText(callerName);
                    if (isSpam && tvSpamWarning != null) tvSpamWarning.setVisibility(View.VISIBLE);
                    Utils.loadContactPhoto(this, photoUri, ivCallerPhoto);
                });
            } else {
                String systemName = Utils.queryContactName(this, phoneNumber);
                String systemPhoto = Utils.queryContactPhotoUri(this, phoneNumber);
                if (systemName != null) {
                    callerName = systemName;
                    runOnUiThread(() -> {
                        if (tvCallerName != null) tvCallerName.setText(callerName);
                    });
                }
                if (systemPhoto != null) {
                    runOnUiThread(() -> {
                        Utils.loadContactPhoto(this, systemPhoto, ivCallerPhoto);
                    });
                }
            }
        });
    }

    private final java.util.List<android.animation.Animator> pulseAnimators = new java.util.ArrayList<>();

    private void startPulseAnimations() {
        runOnUiThread(this::executeStartPulse);
    }

    private void executeStartPulse() {
        stopPulseAnimations();
        // Absolute synchronization for both buttons (Samsung Style)
        animatePulse(viewAcceptRing1, 0);
        animatePulse(viewAcceptRing2, 1200);
        animatePulse(viewRejectRing1, 0);
        animatePulse(viewRejectRing2, 1200);
    }

    private void stopPulseAnimations() {
        for (android.animation.Animator animator : pulseAnimators) {
            if (animator != null) {
                animator.removeAllListeners();
                animator.end();
                animator.cancel();
            }
        }
        pulseAnimators.clear();
        
        // Reset ring views to hidden state
        View[] rings = {viewAcceptRing1, viewAcceptRing2, viewRejectRing1, viewRejectRing2};
        for (View r : rings) {
            if (r != null) {
                r.animate().cancel();
                r.setScaleX(1.0f);
                r.setScaleY(1.0f);
                r.setAlpha(0f);
            }
        }
    }

    private void animatePulse(View view, int delay) {
        if (view == null) return;
        view.setScaleX(1.0f); view.setScaleY(1.0f); view.setAlpha(0.8f);
        android.animation.ObjectAnimator sx = android.animation.ObjectAnimator.ofFloat(view, View.SCALE_X, 1.0f, 2.5f);
        android.animation.ObjectAnimator sy = android.animation.ObjectAnimator.ofFloat(view, View.SCALE_Y, 1.0f, 2.5f);
        android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofFloat(view, View.ALPHA, 0.8f, 0f);
        
        sx.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        sy.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        a.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        
        sx.setDuration(2400); sy.setDuration(2400); a.setDuration(2400);
        sx.setStartDelay(delay); sy.setStartDelay(delay); a.setStartDelay(delay);
        
        sx.start(); sy.start(); a.start();
        
        pulseAnimators.add(sx);
        pulseAnimators.add(sy);
        pulseAnimators.add(a);
    }

    private void setupSwipeGestures() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float screenWidth = metrics.widthPixels;
        float swipeThreshold = screenWidth * 0.35f;

        if (layoutAccept != null) {
            layoutAccept.setOnTouchListener(new View.OnTouchListener() {
                private float startX;
                @Override public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            startX = event.getRawX();
                            v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).start();
                            stopPulseAnimations();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - startX;
                            if (dx > 0) {
                                v.setTranslationX(dx);
                                float alpha = 1.0f - (dx / (swipeThreshold * 1.5f));
                                v.setAlpha(Math.max(0.3f, alpha));
                                if (dx > swipeThreshold) {
                                    v.setOnTouchListener(null); // Prevent multi-trigger
                                    answerCall(VideoProfile.STATE_AUDIO_ONLY);
                                    return false;
                                }
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            v.animate().translationX(0).scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(250).start();
                            startPulseAnimations();
                            return true;
                    } return false;
                }
            });
        }

        if (layoutReject != null) {
            layoutReject.setOnTouchListener(new View.OnTouchListener() {
                private float startX;
                @Override public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            startX = event.getRawX();
                            v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).start();
                            stopPulseAnimations();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = startX - event.getRawX();
                            if (dx > 0) {
                                v.setTranslationX(-dx);
                                float alpha = 1.0f - (dx / (swipeThreshold * 1.5f));
                                v.setAlpha(Math.max(0.3f, alpha));
                                if (dx > swipeThreshold) {
                                    v.setOnTouchListener(null);
                                    rejectCall();
                                    return false;
                                }
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            v.animate().translationX(0).scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(250).start();
                            startPulseAnimations();
                            return true;
                    } return false;
                }
            });
        }
    }

    @Override public void onSensorChanged(SensorEvent event) {}
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override protected void onResume() { super.onResume(); }
    @Override protected void onPause() { super.onPause(); }

    private void setupQuickReplies() {
        if (layoutQuickResponseHandle != null) {
            layoutQuickResponseHandle.setOnClickListener(v -> { Utils.triggerHaptic(v); showQuickReplySheet(); });
        }
        if (btnScreenCallVoicemail != null) {
            btnScreenCallVoicemail.setOnClickListener(v -> {
                Utils.triggerHaptic(v);
                launchVoicemailScreen();
            });
        }
    }

    private void launchVoicemailScreen() {
        CallManager.isVoicemailScreening = true; // Block OngoingCallActivity immediately
        Intent intent = new Intent(this, VoicemailActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        intent.putExtra("EXTRA_NUMBER", phoneNumber);
        startActivity(intent);
    }

    private void showQuickReplySheet() {
        if (quickReplyDialog != null && quickReplyDialog.isShowing()) return;
        quickReplyDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.SamsungBottomSheetDialog);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_quick_replies, null);
        LinearLayout listContainer = sheetView.findViewById(R.id.layoutQuickRepliesList);
        AppDatabase.getDatabase(this).quickReplyDao().getAllQuickReplies().observe(this, replies -> {
            if (replies == null || listContainer == null) return;
            listContainer.removeAllViews();
            for (QuickReplyModel reply : replies) {
                View itemView = getLayoutInflater().inflate(R.layout.item_quick_reply_sheet, listContainer, false);
                ((TextView) itemView.findViewById(R.id.tvQuickReplyMessage)).setText(reply.getMessage());
                itemView.setOnClickListener(v -> { quickReplyDialog.dismiss(); sendQuickMessageDecline(reply.getMessage()); });
                listContainer.addView(itemView);
            }
            View customItem = getLayoutInflater().inflate(R.layout.item_quick_reply_sheet, listContainer, false);
            TextView tvCustom = customItem.findViewById(R.id.tvQuickReplyMessage);
            tvCustom.setText("Write new message...");
            tvCustom.setTextColor(getResources().getColor(R.color.accent_green));
            customItem.setOnClickListener(v -> { quickReplyDialog.dismiss(); showCustomMessageDialog(); });
            listContainer.addView(customItem);
        });
        quickReplyDialog.setContentView(sheetView);
        quickReplyDialog.show();
    }

    private void showCustomMessageDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_custom_message, null);
        final android.widget.EditText editText = dialogView.findViewById(R.id.etCustomMessage);
        
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this, R.style.SamsungCustomDialog)
                .setTitle("New Message")
                .setView(dialogView)
                .setPositiveButton("Send", (d, which) -> {
                    if (editText != null) {
                        String msg = editText.getText().toString().trim();
                        if (!msg.isEmpty()) {
                            AppDatabase.databaseWriteExecutor.execute(() -> {
                                AppDatabase.getDatabase(IncomingCallActivity.this).quickReplyDao().insert(new QuickReplyModel(msg));
                            });
                            sendQuickMessageDecline(msg);
                        }
                    }
                })
                .setNegativeButton("Cancel", null).create();
        
        dialog.setOnShowListener(d -> {
            // High-reliability theme-compliant color enforcement
            android.widget.Button pos = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            android.widget.Button neg = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);
            if (pos != null) pos.setTextColor(getResources().getColor(R.color.text_primary));
            if (neg != null) neg.setTextColor(getResources().getColor(R.color.text_primary));
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
    }

    private void sendQuickMessageDecline(String message) {
        final Context appContext = getApplicationContext();
        final String targetNumber = phoneNumber;
        
        // 1. Instant Message Send (Direct moderm queue)
        new Thread(() -> {
            try {
                android.telephony.SmsManager smsManager;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    smsManager = appContext.getSystemService(android.telephony.SmsManager.class);
                } else {
                    smsManager = android.telephony.SmsManager.getDefault();
                }
                if (smsManager != null) {
                    smsManager.sendTextMessage(targetNumber, null, message, null, null);
                    Log.d("IncomingCall", "Direct high-speed SMS dispatched");
                }
            } catch (Exception e) {
                Log.e("IncomingCall", "Direct SMS failed", e);
            }
        }).start();
        
        // 2. Instant Feedback and Rejection
        Toast.makeText(appContext, "Message Sent", Toast.LENGTH_SHORT).show();
        rejectCall();
    }

    private void answerCall(int videoState) {
        android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        boolean isLocked = (km != null) && km.isKeyguardLocked();

        if (needUnlockFirst && isLocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            km.requestDismissKeyguard(this, new android.app.KeyguardManager.KeyguardDismissCallback() {
                @Override
                public void onDismissSucceeded() {
                    runOnUiThread(() -> performAnswer(videoState));
                }

                @Override
                public void onDismissCancelled() {
                    runOnUiThread(() -> Toast.makeText(IncomingCallActivity.this, "Unlock required to answer", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onDismissError() {
                    runOnUiThread(() -> Toast.makeText(IncomingCallActivity.this, "Unlock failed", Toast.LENGTH_SHORT).show());
                }
            });
        } else {
            performAnswer(videoState);
        }
    }

    private void performAnswer(int videoState) {
        Call ringingCall = null;
        for (Call c : CallManager.getCalls()) { if (c.getState() == Call.STATE_RINGING) { ringingCall = c; break; } }
        if (ringingCall != null) { try { ringingCall.answer(videoState); finishWithTransition(); } catch (Exception e) { finish(); } }
        else if (CallManager.sCurrentCall != null) { try { CallManager.sCurrentCall.answer(videoState); finishWithTransition(); } catch (Exception e) { finish(); } }
        else finish();
    }

    private void rejectCall() {
        Call ringingCall = null;
        for (Call c : CallManager.getCalls()) { if (c.getState() == Call.STATE_RINGING) { ringingCall = c; break; } }
        if (ringingCall != null) { try { ringingCall.disconnect(); } catch (Exception ignored) {} }
        else if (CallManager.sCurrentCall != null) { try { CallManager.sCurrentCall.disconnect(); } catch (Exception ignored) {} }
        finish(); overridePendingTransition(0, R.anim.premium_fade_out);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override protected void onDestroy() {
        CallManager.unregisterListener(callManagerListener);
        try { unregisterReceiver(disconnectReceiver); } catch (Exception ignored) {}
        stopWaitingTone(); super.onDestroy();
    }
}
