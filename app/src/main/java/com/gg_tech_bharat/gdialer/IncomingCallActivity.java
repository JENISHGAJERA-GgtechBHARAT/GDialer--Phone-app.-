package com.gg_tech_bharat.gdialer;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
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
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.imageview.ShapeableImageView;

public class IncomingCallActivity extends AppCompatActivity implements SensorEventListener {

    private TextView tvCallerName, tvCallerNumber, tvSpamWarning;
    private ShapeableImageView ivCallerPhoto;
    private View layoutAccept, layoutReject;
    private LinearLayout layoutQuickReplies;

    private String phoneNumber;
    private String callerName = "Unknown";
    private boolean isSpam = false;

    private final BroadcastReceiver disconnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.gg_tech_bharat.gdialer.CALL_DISCONNECTED".equals(intent.getAction())) {
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        }
        super.onCreate(savedInstanceState);
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);

        setContentView(R.layout.activity_incoming_call);

        tvCallerName = findViewById(R.id.tvIncomingCallerName);
        tvCallerNumber = findViewById(R.id.tvIncomingCallerNumber);
        tvSpamWarning = findViewById(R.id.tvSpamWarning);
        ivCallerPhoto = findViewById(R.id.ivIncomingCallerPhoto);
        layoutAccept = findViewById(R.id.layoutAccept);
        layoutReject = findViewById(R.id.layoutReject);
        layoutQuickReplies = findViewById(R.id.layoutQuickReplies);

        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }
        
        phoneNumber = intent.getStringExtra("EXTRA_NUMBER");
        String passedName = intent.getStringExtra("EXTRA_NAME");

        if (phoneNumber == null) phoneNumber = "Unknown";
        if (tvCallerNumber != null) tvCallerNumber.setText(phoneNumber);
        
        // Show passed name or number immediately
        if (passedName != null && !passedName.equals(phoneNumber)) {
            callerName = passedName;
        } else {
            callerName = phoneNumber;
        }
        if (tvCallerName != null) tvCallerName.setText(callerName);

        loadCallerDetails();
        setupSwipeGestures();
        setupQuickReplies();

        IntentFilter filter = new IntentFilter("com.gg_tech_bharat.gdialer.CALL_DISCONNECTED");
        try {
            androidx.core.content.ContextCompat.registerReceiver(this, disconnectReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        phoneNumber = intent.getStringExtra("EXTRA_NUMBER");
        if (tvCallerNumber != null) tvCallerNumber.setText(phoneNumber);
        loadCallerDetails();
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
                // FALLBACK: Query system ContactsContract
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


    @Override
    public void onSensorChanged(SensorEvent event) {
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override protected void onResume() {
        super.onResume();
    }

    @Override protected void onPause() {
        super.onPause();
    }

    private void setupQuickReplies() {
        if (layoutQuickReplies == null) return;
        String[] replies = {"Busy.", "Can't talk.", "Meeting."};
        for (String msg : replies) {
            Button btn = new Button(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
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
    }

    private void answerCall(int videoState) {
        if (CallManager.sCurrentCall != null) {
            try {
                CallManager.sCurrentCall.answer(videoState);
                finish();
                overridePendingTransition(0, 0); // Zero delay
            } catch (Exception e) { finish(); }
        } else { finish(); }
    }

    private void rejectCall() {
        if (CallManager.sCurrentCall != null) {
            try { CallManager.sCurrentCall.disconnect(); } catch (Exception ignored) {}
        }
        finish();
    }

    private void sendQuickMessageDecline(String message) {
        if (CallManager.sCurrentCall != null) {
            try { CallManager.sCurrentCall.disconnect(); } catch (Exception ignored) {}
            Utils.sendSMS(this, phoneNumber, message);
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(disconnectReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
