package com.gg_tech_bharat.gdialer;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.telecom.VideoProfile;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.imageview.ShapeableImageView;
import java.util.List;

public class OngoingCallActivity extends AppCompatActivity implements SensorEventListener {

    private static final int REQUEST_CODE_ADD_CALL = 5001;

    private TextView tvCallerName, tvCallerNumber, tvCallTimer, tvMute, tvSpeaker, tvHold;
    private TextView tvKeypadDigits;
    private ShapeableImageView ivCallerPhoto;
    private ImageView ivMute, ivSpeaker, ivHold, btnMore;
    private View btnMute, btnSpeaker, btnKeypadToggle, btnHold, btnAddCall, btnVideoCallInCall;
    private View btnEndCall, btnMerge, controlGrid;
    private TextView tvMultiCallSummary;
    private View viewPulse1, viewPulse2;
    private View layoutInCallKeypad, layoutAvatarPulsing, btnHideKeypad;
    
    private View cardLocalPreview;
    private TextureView textureRemoteVideo, textureLocalPreview;

    private String phoneNumber;
    private String callerName = "Unknown";
    private boolean isMuted = false;
    private boolean isHeld = false;
    private boolean isRecording = false;

    private SensorManager sensorManager;
    private Sensor proximitySensor;
    private PowerManager.WakeLock wakeLock;

    private final Handler timerHandler = new Handler();
    private long callStartTime = 0;
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimerUI();
            timerHandler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver disconnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.gg_tech_bharat.gdialer.CALL_DISCONNECTED".equals(intent.getAction())) {
                Utils.vibrateDevice(getApplicationContext(), 100); // End call vibration
                finishAndRemoveTask();
            } else if ("com.gg_tech_bharat.gdialer.VIDEO_STATE_CHANGED".equals(intent.getAction())) {
                updateVideoUI();
            }
        }
    };

    private final Call.Callback individualCallCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            super.onStateChanged(call, state);
            updateMultiCallUI();
        }
        @Override
        public void onDetailsChanged(Call call, Call.Details details) {
            super.onDetailsChanged(call, details);
            updateVideoUI();
        }
    };

    private final CallManager.CallStateListener callListListener = new CallManager.CallStateListener() {
        @Override public void onStateChanged(int state) {
            if (state == Call.STATE_ACTIVE && callStartTime == 0) {
                callStartTime = SystemClock.elapsedRealtime();
            }
            // Re-assert lockscreen priority on any state change (Hold/Resume/etc)
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !isFinishing()) {
                    KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                    if (km != null) km.requestDismissKeyguard(OngoingCallActivity.this, null);
                }
            });
            updateTimerUI();
            updateVideoUI();
        }
        @Override public void onCallListChanged() { 
            updateMultiCallUI(); 
            for (Call call : CallManager.getCalls()) {
                call.unregisterCallback(individualCallCallback);
                call.registerCallback(individualCallCallback);
            }
        }
        @Override public void onAudioStateChanged(CallAudioState audioState) {
            runOnUiThread(() -> updateSpeakerUI(audioState));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // PRE-ONCREATE: High-priority window flags for lockscreen bypass
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            // requestDismissKeyguard removed to prevent lingering fingerprint scanner UI
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ongoing_call);

        initViews();
        setupIntentData();
        setupProximitySensor();
        setupVideoSurfaces();

        loadCallerDetails();
        startPulseAnimation();
        timerHandler.post(timerRunnable);

        setupPremiumEndCallInteraction();
        setupControlButtons();
        setupInCallKeypad();
        
        CallManager.registerListener(callListListener);
        updateMultiCallUI();
        updateVideoUI();
        
        registerDisconnectReceiver();
        animateEntry();
        
        // Premium Snappy Transition
        overridePendingTransition(R.anim.premium_fade_in, R.anim.premium_fade_out);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        
        // Re-assert lockscreen bypass on new calls (Add Call/Incoming 2nd)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        
        phoneNumber = intent.getStringExtra("EXTRA_NUMBER");
        String passedName = intent.getStringExtra("EXTRA_NAME");
        if (tvCallerNumber != null) tvCallerNumber.setText(phoneNumber);
        if (passedName != null) {
            callerName = passedName;
            if (tvCallerName != null) tvCallerName.setText(callerName);
        }
        
        overridePendingTransition(R.anim.premium_fade_in, R.anim.premium_fade_out);
        loadCallerDetails();
    }

    private void setupWindowFlags() {
        // Handled in onCreate and onNewIntent for maximum consistency
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
    }

    private void initViews() {
        tvCallerName = findViewById(R.id.tvCallerName);
        tvCallerNumber = findViewById(R.id.tvCallerNumber);
        tvCallTimer = findViewById(R.id.tvCallTimer);
        ivCallerPhoto = findViewById(R.id.ivCallerPhoto);
        viewPulse1 = findViewById(R.id.viewPulse1);
        viewPulse2 = findViewById(R.id.viewPulse2);
        
        ivMute = findViewById(R.id.ivMute);
        ivSpeaker = findViewById(R.id.ivSpeaker);
        ivHold = findViewById(R.id.ivHold);
        btnMore = findViewById(R.id.btnMore);

        tvMute = findViewById(R.id.tvMute);
        tvSpeaker = findViewById(R.id.tvSpeaker);
        tvHold = findViewById(R.id.tvHold);

        btnMute = findViewById(R.id.btnMute);
        btnSpeaker = findViewById(R.id.btnSpeaker);
        btnKeypadToggle = findViewById(R.id.btnKeypadToggle);
        btnHold = findViewById(R.id.btnHold);
        btnAddCall = findViewById(R.id.btnAddCall);
        btnVideoCallInCall = findViewById(R.id.btnVideoCallInCall);
        btnEndCall = findViewById(R.id.btnEndCall);
        btnMerge = findViewById(R.id.btnMerge);
        tvMultiCallSummary = findViewById(R.id.tvMultiCallSummary);

        controlGrid = findViewById(R.id.controlGrid);
        layoutInCallKeypad = findViewById(R.id.layoutInCallKeypad);
        layoutAvatarPulsing = findViewById(R.id.layoutAvatarPulsing);
        tvKeypadDigits = findViewById(R.id.tvKeypadDigits);
        btnHideKeypad = findViewById(R.id.btnHideKeypad);

        textureRemoteVideo = findViewById(R.id.textureRemoteVideo);
        textureLocalPreview = findViewById(R.id.textureLocalPreview);
        cardLocalPreview = findViewById(R.id.cardLocalPreview);
    }

    private void setupIntentData() {
        Intent intent = getIntent();
        phoneNumber = intent.getStringExtra("EXTRA_NUMBER");
        String passedName = intent.getStringExtra("EXTRA_NAME");

        if (phoneNumber == null) phoneNumber = "Unknown";
        if (tvCallerNumber != null) tvCallerNumber.setText(phoneNumber);
        
        if (passedName != null && !passedName.equals(phoneNumber)) {
            callerName = passedName;
        } else {
            callerName = phoneNumber;
        }
        if (tvCallerName != null) tvCallerName.setText(callerName);
        
        if (CallManager.sCurrentCall != null && CallManager.sCurrentCall.getState() == Call.STATE_ACTIVE) {
            long connectTime = CallManager.sCurrentCall.getDetails().getConnectTimeMillis();
            if (connectTime > 0) {
                callStartTime = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - connectTime);
            }
        }
    }

    private void setupProximitySensor() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null && proximitySensor != null) {
                try {
                    wakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, getLocalClassName());
                } catch (Exception e) {
                    Log.e("OngoingCallActivity", "Wakelock creation failed", e);
                }
            }
        }
    }

    private void setupVideoSurfaces() {
        if (textureRemoteVideo != null) {
            textureRemoteVideo.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(@NonNull SurfaceTexture st, int w, int h) { setVideoSurfaces(); }
                @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture st, int w, int h) {}
                @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture st) { return true; }
                @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture st) {}
            });
        }

        if (textureLocalPreview != null) {
            textureLocalPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(@NonNull SurfaceTexture st, int w, int h) { setVideoSurfaces(); }
                @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture st, int w, int h) {}
                @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture st) { return true; }
                @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture st) {}
            });
        }
    }

    private void setVideoSurfaces() {
        if (CallManager.sCurrentCall != null) {
            try {
                InCallService.VideoCall videoCall = CallManager.sCurrentCall.getVideoCall();
                if (videoCall != null) {
                    if (textureRemoteVideo != null && textureRemoteVideo.isAvailable()) {
                        videoCall.setDisplaySurface(new Surface(textureRemoteVideo.getSurfaceTexture()));
                    }
                    if (textureLocalPreview != null && textureLocalPreview.isAvailable()) {
                        videoCall.setPreviewSurface(new Surface(textureLocalPreview.getSurfaceTexture()));
                    }
                }
            } catch (Exception e) {
                Log.e("OngoingCallActivity", "Error setting video surfaces", e);
            }
        }
    }

    private void updateVideoUI() {
        if (CallManager.sCurrentCall != null) {
            try {
                int state = CallManager.sCurrentCall.getState();
                int videoState = CallManager.sCurrentCall.getDetails().getVideoState();
                boolean isVideo = VideoProfile.isVideo(videoState) && state == Call.STATE_ACTIVE;
                
                runOnUiThread(() -> {
                    if (isVideo) {
                        if (textureRemoteVideo != null) textureRemoteVideo.setVisibility(View.VISIBLE);
                        if (cardLocalPreview != null) cardLocalPreview.setVisibility(View.VISIBLE);
                        if (layoutAvatarPulsing != null) layoutAvatarPulsing.setVisibility(View.GONE);
                        if (controlGrid != null) controlGrid.setAlpha(0.8f);
                    } else {
                        if (textureRemoteVideo != null) textureRemoteVideo.setVisibility(View.GONE);
                        if (cardLocalPreview != null) cardLocalPreview.setVisibility(View.GONE);
                        if (layoutInCallKeypad != null && layoutInCallKeypad.getVisibility() != View.VISIBLE) {
                            if (layoutAvatarPulsing != null) layoutAvatarPulsing.setVisibility(View.VISIBLE);
                        }
                        if (controlGrid != null) controlGrid.setAlpha(1.0f);
                    }
                    if (btnVideoCallInCall != null) btnVideoCallInCall.setActivated(isVideo);
                });
                
                if (isVideo) {
                    new Handler(android.os.Looper.getMainLooper()).postDelayed(this::setVideoSurfaces, 500);
                }
            } catch (Exception e) {
                Log.e("OngoingCallActivity", "Error updating video UI", e);
            }
        }
    }

    private void animateEntry() {
        View info = findViewById(R.id.layoutTopInfo);
        if (info != null) {
            info.setAlpha(0f);
            info.setTranslationY(-50f);
            info.animate().alpha(1f).translationY(0).setDuration(500).start();
        }

        View controls = findViewById(R.id.controlGrid);
        if (controls != null) {
            controls.setAlpha(0f);
            controls.setTranslationY(100f);
            controls.animate().alpha(1f).translationY(0).setDuration(600).setStartDelay(200).start();
        }
    }

    private void updateMultiCallUI() {
        runOnUiThread(() -> {
            List<Call> calls = CallManager.getCalls();
            if (calls.size() > 1) {
                if (tvMultiCallSummary != null) {
                    tvMultiCallSummary.setVisibility(View.VISIBLE);
                    tvMultiCallSummary.setText(calls.size() + " people in call");
                    tvMultiCallSummary.setOnClickListener(v -> {
                        Utils.triggerHaptic(v);
                        showParticipantsBottomSheet();
                    });
                }
                
                if (btnMerge != null) btnMerge.setVisibility(View.VISIBLE);
                if (tvCallerName != null) tvCallerName.setVisibility(View.GONE);
                if (tvCallerNumber != null) tvCallerNumber.setVisibility(View.GONE);
                if (layoutAvatarPulsing != null) {
                    layoutAvatarPulsing.setScaleX(0.7f);
                    layoutAvatarPulsing.setScaleY(0.7f);
                }
            } else {
                if (tvMultiCallSummary != null) tvMultiCallSummary.setVisibility(View.GONE);
                if (btnMerge != null) btnMerge.setVisibility(View.GONE);
                if (tvCallerName != null) tvCallerName.setVisibility(View.VISIBLE);
                if (tvCallerNumber != null) tvCallerNumber.setVisibility(View.VISIBLE);
                if (layoutAvatarPulsing != null) {
                    layoutAvatarPulsing.setScaleX(1.0f);
                    layoutAvatarPulsing.setScaleY(1.0f);
                }
            }
        });
    }

    private void showParticipantsBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.SamsungBottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_participants, null);
        LinearLayout listContainer = view.findViewById(R.id.layoutParticipantsList);
        dialog.setContentView(view);
        
        List<Call> calls = CallManager.getCalls();
        for (Call call : calls) {
            View item = getLayoutInflater().inflate(R.layout.multi_call_item, listContainer, false);
            TextView nameTv = item.findViewById(R.id.tvMultiCallName);
            TextView statusTv = item.findViewById(R.id.tvMultiCallStatus);
            View endBtn = item.findViewById(R.id.btnMultiCallEnd);

            String number = getNumberFromCall(call);
            nameTv.setText(number); 
            
            AppDatabase.databaseWriteExecutor.execute(() -> {
                String foundName = Utils.queryContactName(this, number);
                if (foundName != null) runOnUiThread(() -> nameTv.setText(foundName));
            });

            statusTv.setText(call.getState() == Call.STATE_HOLDING ? "On Hold" : "Active");

            endBtn.setOnClickListener(v -> {
                Utils.triggerHaptic(v);
                call.disconnect();
                listContainer.removeView(item);
                if (CallManager.getCalls().size() <= 1) dialog.dismiss();
            });
            
            listContainer.addView(item);
        }
        
        dialog.show();
    }

    private String getNumberFromCall(Call call) {
        try {
            if (call != null && call.getDetails() != null && call.getDetails().getHandle() != null) {
                return call.getDetails().getHandle().getSchemeSpecificPart();
            }
        } catch (Exception ignored) {}
        return "Unknown";
    }

    private void updateTimerUI() {
        if (callStartTime > 0) {
            long durationMs = SystemClock.elapsedRealtime() - callStartTime;
            long seconds = durationMs / 1000;
            if (tvCallTimer != null) tvCallTimer.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60));
        }
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
                final String photoUri = contact.getPhotoUri();
                runOnUiThread(() -> {
                    if (tvCallerName != null) tvCallerName.setText(callerName);
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

    private void setupPremiumEndCallInteraction() {
        if (btnEndCall == null) return;
        btnEndCall.setOnClickListener(v -> endCallSignal());
        btnEndCall.setOnTouchListener(new View.OnTouchListener() {
            private float startX;
            private boolean isSwiping = false;
            @Override public boolean onTouch(View v, MotionEvent event) {
                float threshold = v.getWidth() * 0.5f;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        isSwiping = false;
                        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(150).start();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = startX - event.getX();
                        if (Math.abs(dx) > 20) {
                            isSwiping = true;
                            v.setTranslationX(-dx);
                            if (dx > threshold) { 
                                v.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(100).start();
                                endCallSignal(); 
                                return false; 
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).translationX(0).setDuration(250).start();
                        if (!isSwiping && event.getAction() == MotionEvent.ACTION_UP) {
                            v.performClick();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void setupControlButtons() {
        btnMute.setOnClickListener(v -> toggleMute());
        btnSpeaker.setOnClickListener(v -> toggleSpeaker());
        btnHold.setOnClickListener(v -> toggleHold());
        if (btnMore != null) btnMore.setOnClickListener(this::showMoreMenu);
        btnKeypadToggle.setOnClickListener(v -> toggleKeypad());
        if (btnMerge != null) {
            btnMerge.setOnClickListener(v -> {
                Utils.triggerHaptic(v);
                mergeCalls();
            });
        }
        btnAddCall.setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            startActivityForResult(intent, REQUEST_CODE_ADD_CALL);
        });
        if (btnVideoCallInCall != null) {
            btnVideoCallInCall.setOnClickListener(v -> {
                Utils.triggerHaptic(v);
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1002);
                    return;
                }
                requestVideoUpgrade();
            });
        }
    }

    private void mergeCalls() {
        List<Call> calls = CallManager.getCalls();
        if (calls.size() < 2) return;

        Call activeCall = null;
        for (Call call : calls) {
            if (call.getState() == Call.STATE_ACTIVE) {
                activeCall = call;
                break;
            }
        }

        if (activeCall != null) {
            for (Call call : calls) {
                if (call != activeCall) {
                    activeCall.conference(call);
                }
            }
            Toast.makeText(this, "Merging calls...", Toast.LENGTH_SHORT).show();
        } else {
            calls.get(0).conference(calls.get(1));
            Toast.makeText(this, "Merging calls...", Toast.LENGTH_SHORT).show();
        }
    }

    private void showMoreMenu(View v) {
        Utils.triggerHaptic(v);
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenu().add(Menu.NONE, 1, Menu.NONE, isRecording ? "Stop recording" : "Record");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                toggleRecording();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void toggleKeypad() {
        Utils.triggerHaptic(btnKeypadToggle);
        boolean isKeypadVisible = layoutInCallKeypad.getVisibility() == View.VISIBLE;
        if (isKeypadVisible) {
            layoutInCallKeypad.setVisibility(View.GONE);
            if (controlGrid != null) controlGrid.setVisibility(View.VISIBLE);
            if (CallManager.sCurrentCall != null && !VideoProfile.isVideo(CallManager.sCurrentCall.getDetails().getVideoState())) {
                if (layoutAvatarPulsing != null) layoutAvatarPulsing.setVisibility(View.VISIBLE);
            }
        } else {
            layoutInCallKeypad.setVisibility(View.VISIBLE);
            if (controlGrid != null) controlGrid.setVisibility(View.GONE);
            if (layoutAvatarPulsing != null) layoutAvatarPulsing.setVisibility(View.GONE);
        }
    }

    private void setupInCallKeypad() {
        int[] keyIds = {R.id.btnKey1, R.id.btnKey2, R.id.btnKey3, R.id.btnKey4, R.id.btnKey5, R.id.btnKey6, 
                        R.id.btnKey7, R.id.btnKey8, R.id.btnKey9, R.id.btnKeyStar, R.id.btnKey0, R.id.btnKeyHash};
        String[] chars = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        
        for (int i = 0; i < keyIds.length; i++) {
            final String digit = chars[i];
            View btn = findViewById(keyIds[i]);
            if (btn != null) {
                btn.setOnClickListener(v -> {
                    Utils.triggerHaptic(v);
                    if (tvKeypadDigits != null) tvKeypadDigits.append(digit);
                    if (CallManager.sCurrentCall != null) {
                        CallManager.sCurrentCall.playDtmfTone(digit.charAt(0));
                        CallManager.sCurrentCall.stopDtmfTone();
                    }
                });
            }
        }
        if (btnHideKeypad != null) btnHideKeypad.setOnClickListener(v -> toggleKeypad());
    }

    private void requestVideoUpgrade() {
        if (CallManager.sCurrentCall != null) {
            InCallService.VideoCall videoCall = CallManager.sCurrentCall.getVideoCall();
            if (videoCall != null) {
                int currentState = CallManager.sCurrentCall.getDetails().getVideoState();
                int newState = VideoProfile.isVideo(currentState) ? VideoProfile.STATE_AUDIO_ONLY : VideoProfile.STATE_BIDIRECTIONAL;
                videoCall.sendSessionModifyRequest(new VideoProfile(newState));
                if (btnVideoCallInCall != null) btnVideoCallInCall.setActivated(!VideoProfile.isVideo(currentState));
                updateButtonStyle(btnVideoCallInCall, null, null, !VideoProfile.isVideo(currentState));
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ADD_CALL && resultCode == Activity.RESULT_OK && data != null) {
            Uri contactUri = data.getData();
            if (contactUri != null) {
                try (Cursor cursor = getContentResolver().query(contactUri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                        String number = cursor.getString(numberIndex);
                        if (number != null && !number.isEmpty()) {
                            Utils.makePhoneCall(this, number);
                        }
                    }
                } catch (Exception e) {
                    Log.e("OngoingCallActivity", "Error picking contact", e);
                }
            }
        }
    }

    private void toggleMute() {
        Utils.triggerHaptic(btnMute);
        isMuted = !isMuted;
        if (btnMute != null) btnMute.setActivated(isMuted);
        updateButtonStyle(btnMute, ivMute, tvMute, isMuted);
        if (InCallServiceImpl.sInstance != null) InCallServiceImpl.sInstance.setMuted(isMuted);
    }

    private void toggleSpeaker() {
        Utils.triggerHaptic(btnSpeaker);
        if (InCallServiceImpl.sInstance != null) {
            CallAudioState state = InCallServiceImpl.sInstance.getCallAudioState();
            if (state != null) {
                boolean bluetooth = (state.getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH) != 0;
                if (bluetooth) showAudioRoutePopup(btnSpeaker);
                else {
                    int currentRoute = state.getRoute();
                    int newRoute = (currentRoute == CallAudioState.ROUTE_SPEAKER) ? CallAudioState.ROUTE_EARPIECE : CallAudioState.ROUTE_SPEAKER;
                    InCallServiceImpl.sInstance.setAudioRoute(newRoute);
                    updateSpeakerUI(newRoute == CallAudioState.ROUTE_SPEAKER);
                }
            }
        }
    }

    private void showAudioRoutePopup(View anchor) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.SamsungBottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_audio_routing, null);
        dialog.setContentView(view);

        view.findViewById(R.id.btnRouteEarpiece).setOnClickListener(v -> {
            if (InCallServiceImpl.sInstance != null) {
                InCallServiceImpl.sInstance.setAudioRoute(CallAudioState.ROUTE_EARPIECE);
                updateSpeakerUI(false);
            }
            dialog.dismiss();
        });

        view.findViewById(R.id.btnRouteSpeaker).setOnClickListener(v -> {
            if (InCallServiceImpl.sInstance != null) {
                InCallServiceImpl.sInstance.setAudioRoute(CallAudioState.ROUTE_SPEAKER);
                updateSpeakerUI(true);
            }
            dialog.dismiss();
        });

        view.findViewById(R.id.btnRouteBluetooth).setOnClickListener(v -> {
            if (InCallServiceImpl.sInstance != null) {
                InCallServiceImpl.sInstance.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
                updateSpeakerUI(false);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateSpeakerUI(CallAudioState audioState) {
        CallAudioState currentAudioState = audioState;
        if (currentAudioState == null && InCallServiceImpl.sInstance != null) {
            currentAudioState = InCallServiceImpl.sInstance.getCallAudioState();
        }
        
        if (currentAudioState == null) return;
        
        int route = currentAudioState.getRoute();
        boolean isSpeakerActive = (route == CallAudioState.ROUTE_SPEAKER);

        if (btnSpeaker != null) btnSpeaker.setActivated(isSpeakerActive);
        
        if (ivSpeaker != null) {
            if (route == CallAudioState.ROUTE_SPEAKER) {
                ivSpeaker.setImageResource(R.drawable.ic_speaker);
            } else if (route == CallAudioState.ROUTE_BLUETOOTH) {
                ivSpeaker.setImageResource(R.drawable.ic_bluetooth);
            } else {
                // For Earpiece and Wired Headset, show the phone icon
                ivSpeaker.setImageResource(R.drawable.ic_phone);
            }
        }

        if (tvSpeaker != null) {
            if (route == CallAudioState.ROUTE_SPEAKER) {
                tvSpeaker.setText("Speaker");
            } else if (route == CallAudioState.ROUTE_BLUETOOTH) {
                tvSpeaker.setText("Bluetooth");
            } else if (route == CallAudioState.ROUTE_EARPIECE) {
                tvSpeaker.setText("Earpiece");
            } else if (route == CallAudioState.ROUTE_WIRED_HEADSET) {
                tvSpeaker.setText("Headset");
            } else {
                tvSpeaker.setText("Audio");
            }
        }

        updateButtonStyle(btnSpeaker, ivSpeaker, tvSpeaker, isSpeakerActive);
    }

    private void updateSpeakerUI(boolean active) {
        updateSpeakerUI(null);
    }

    private void toggleHold() {
        Utils.triggerHaptic(btnHold);
        isHeld = !isHeld;
        if (btnHold != null) btnHold.setActivated(isHeld);
        updateButtonStyle(btnHold, ivHold, tvHold, isHeld);
        if (tvHold != null) tvHold.setText(isHeld ? "Unhold" : "Hold");
        
        if (CallManager.sCurrentCall != null) {
            if (isHeld) CallManager.sCurrentCall.hold(); else CallManager.sCurrentCall.unhold();
        }

        // Force lockscreen bypass refresh after holding/unholding
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        }
    }

    private void toggleRecording() {
        Intent intent = getPackageManager().getLaunchIntentForPackage("com.nothing.recorder");
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Toast.makeText(this, "Opening Nothing Recorder...", Toast.LENGTH_SHORT).show();
        } else {
            isRecording = !isRecording;
            if (isRecording) {
                RecordingService.start(this, callerName);
                Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
            } else {
                RecordingService.stop(this);
                Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateButtonStyle(View btn, ImageView iv, TextView tv, boolean active) {
        if (active) {
            if (btn != null) btn.setActivated(true);
            if (iv != null) iv.setColorFilter(getResources().getColor(R.color.white));
            if (tv != null) tv.setTextColor(getResources().getColor(R.color.white));
        } else {
            if (btn != null) btn.setActivated(false);
            if (iv != null) iv.setColorFilter(getResources().getColor(R.color.white));
            if (tv != null) tv.setTextColor(getResources().getColor(R.color.gray_light));
        }
    }

    private void startPulseAnimation() {
        if (viewPulse1 != null) animatePulse(viewPulse1, 0);
        if (viewPulse2 != null) animatePulse(viewPulse2, 1200);
    }

    private void animatePulse(View view, int delay) {
        view.setScaleX(0.8f); view.setScaleY(0.8f); view.setAlpha(0.6f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.8f, 1.5f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.8f, 1.5f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0.6f, 0f);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        alpha.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setDuration(2500); scaleY.setDuration(2500); alpha.setDuration(2500);
        scaleX.setStartDelay(delay); scaleY.setStartDelay(delay); alpha.setStartDelay(delay);
        scaleX.start(); scaleY.start(); alpha.start();
    }

    private void endCallSignal() {
        Utils.vibrateDevice(getApplicationContext(), 100); // End call vibration
        if (CallManager.sCurrentCall != null) {
            try { CallManager.sCurrentCall.disconnect(); } catch (Exception ignored) {}
        }
        finishAndRemoveTask();
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (proximitySensor == null) return;
        if (event.values[0] < proximitySensor.getMaximumRange()) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(10 * 60 * 1000L);
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override protected void onResume() {
        super.onResume();
        if (proximitySensor != null) sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (proximitySensor != null) sensorManager.unregisterListener(this);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    private void registerDisconnectReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.gg_tech_bharat.gdialer.CALL_DISCONNECTED");
        filter.addAction("com.gg_tech_bharat.gdialer.VIDEO_STATE_CHANGED");
        try { androidx.core.content.ContextCompat.registerReceiver(this, disconnectReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED); } catch (Exception ignored) {}
    }

    @Override protected void onDestroy() {
        timerHandler.removeCallbacks(timerRunnable);
        CallManager.unregisterListener();
        try { unregisterReceiver(disconnectReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
