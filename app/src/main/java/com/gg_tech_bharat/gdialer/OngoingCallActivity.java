package com.gg_tech_bharat.gdialer;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
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
    private TextView tvKeypadDigits, tvHdIcon, tvWifiIcon;
    private TextView tvKeypadToggle, tvAddCall, tvVideoCallInCall, tvMerge;
    private ShapeableImageView ivCallerPhoto;
    private ImageView ivMute, ivSpeaker, ivHold, btnMore, ivKeypadToggle, ivAddCall, ivVideoCallInCall, ivMerge;
    private View btnMute, btnSpeaker, btnKeypadToggle, btnHold, btnAddCall, btnVideoCallInCall;
    private View btnEndCall, btnMerge, controlGrid;
    private TextView tvMultiCallSummary;
    private View viewPulse1, viewPulse2;
    private View layoutInCallKeypad, layoutAvatarPulsing, btnHideKeypad;
    
    private View cardLocalPreview;
    private TextureView textureRemoteVideo, textureLocalPreview;

    private android.hardware.camera2.CameraDevice cameraDevice;
    private android.hardware.camera2.CameraCaptureSession cameraCaptureSession;
    private String cameraId;
    private boolean mockVideoState = false;

    private String phoneNumber;
    private String callerName = "Unknown";
    private boolean isMuted = false;
    private boolean isHeld = false;

    private SensorManager sensorManager;
    private Sensor proximitySensor;
    private PowerManager.WakeLock wakeLock;

    private final Handler timerHandler = new Handler();
    private long callStartTime = 0;
    private final Runnable statusPollingRunnable = new Runnable() {
        @Override public void run() {
            updateCallStatusIndicators();
            if (CallManager.sCurrentCall != null) {
                int state = CallManager.sCurrentCall.getState();
                if (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_ACTIVE) {
                    timerHandler.postDelayed(this, 500);
                }
            }
        }
    };
    private final Runnable timerRunnable = new Runnable() {
        @Override public void run() {
            updateTimerUI();
            timerHandler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver disconnectReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if ("com.gg_tech_bharat.gdialer.CALL_DISCONNECTED".equals(intent.getAction())) {
                // Stop recording when call disconnected
                try {
                    Intent recordIntent = new Intent(context, RecordingService.class);
                    recordIntent.setAction(RecordingService.ACTION_STOP_RECORDING);
                    context.startService(recordIntent);
                } catch (Exception ignored) {}

                // Vibration handled by InCallServiceImpl to avoid duplicates
                finishAndRemoveTask();
                overridePendingTransition(0, R.anim.premium_fade_out);
            } else if ("com.gg_tech_bharat.gdialer.VIDEO_STATE_CHANGED".equals(intent.getAction())) {
                updateVideoUI();
            }
        }
    };

    private final Call.Callback individualCallCallback = new Call.Callback() {
        @Override public void onStateChanged(Call call, int state) {
            super.onStateChanged(call, state);
            updateMultiCallUI();
        }
        @Override public void onDetailsChanged(Call call, Call.Details details) {
            super.onDetailsChanged(call, details);
            updateVideoUI();
            updateCallStatusIndicators();
        }
    };

    private final CallManager.CallStateListener callListListener = new CallManager.CallStateListener() {
        @Override public void onStateChanged(int state) {
            if (state == Call.STATE_ACTIVE && callStartTime == 0) callStartTime = SystemClock.elapsedRealtime();
            updateTimerUI();
            updateVideoUI();
            updateCallStatusIndicators();
        }
        @Override public void onCallListChanged() { 
            updateMultiCallUI(); 
            for (Call call : CallManager.getCalls()) {
                call.unregisterCallback(individualCallCallback);
                call.registerCallback(individualCallCallback);
            }
            updateCallStatusIndicators();
        }
        @Override public void onAudioStateChanged(CallAudioState audioState) {
            runOnUiThread(() -> updateSpeakerUI(audioState));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        android.content.SharedPreferences prefs = getSharedPreferences("DialerPrefs", MODE_PRIVATE);
        boolean useSystem = prefs.getBoolean("use_system_theme", true);
        if (useSystem) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            boolean darkMode = prefs.getBoolean("dark_mode", true);
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    darkMode ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ongoing_call);

        initViews();
        setupIntentData();
        setupProximitySensor();
        setupVideoSurfaces();
        loadCallerDetails();
        startPulseAnimation();
        timerHandler.post(timerRunnable);
        timerHandler.post(statusPollingRunnable);
        setupPremiumEndCallInteraction();
        setupControlButtons();
        setupInCallKeypad();
        CallManager.registerListener(callListListener);
        updateMultiCallUI();
        updateVideoUI();
        updateSpeakerUI(null); // Initial speaker state
        registerDisconnectReceiver();
        animateEntry();

        if (getIntent().getBooleanExtra("EXTRA_AUTO_MERGE", false)) {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::mergeCalls, 1500);
        }
        
        overridePendingTransition(R.anim.premium_fade_in, R.anim.premium_fade_out);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
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

        if (intent.getBooleanExtra("EXTRA_AUTO_MERGE", false)) {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::mergeCalls, 1500);
        }

        overridePendingTransition(R.anim.premium_fade_in, R.anim.premium_fade_out);
        loadCallerDetails();
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
        tvHdIcon = findViewById(R.id.tvHdIcon);
        tvWifiIcon = findViewById(R.id.tvWifiIcon);
        textureRemoteVideo = findViewById(R.id.textureRemoteVideo);
        textureLocalPreview = findViewById(R.id.textureLocalPreview);
        cardLocalPreview = findViewById(R.id.cardLocalPreview);

        ivKeypadToggle = findViewById(R.id.ivKeypadToggle);
        tvKeypadToggle = findViewById(R.id.tvKeypadToggle);
        ivAddCall = findViewById(R.id.ivAddCall);
        tvAddCall = findViewById(R.id.tvAddCall);
        ivVideoCallInCall = findViewById(R.id.ivVideoCallInCall);
        tvVideoCallInCall = findViewById(R.id.tvVideoCallInCall);
        ivMerge = findViewById(R.id.ivMerge);
        tvMerge = findViewById(R.id.tvMerge);
    }

    private void setupIntentData() {
        Intent intent = getIntent();
        phoneNumber = intent.getStringExtra("EXTRA_NUMBER");
        String passedName = intent.getStringExtra("EXTRA_NAME");
        if (phoneNumber == null) phoneNumber = "Unknown";
        if (tvCallerNumber != null) tvCallerNumber.setText(phoneNumber);
        if (passedName != null && !passedName.equals(phoneNumber)) callerName = passedName;
        else callerName = phoneNumber;
        if (tvCallerName != null) tvCallerName.setText(callerName);
        if (CallManager.sCurrentCall != null && CallManager.sCurrentCall.getState() == Call.STATE_ACTIVE) {
            long connectTime = CallManager.sCurrentCall.getDetails().getConnectTimeMillis();
            if (connectTime > 0) callStartTime = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - connectTime);
        }
    }

    private void setupProximitySensor() {
        // Disabled local proximity sensor management in favor of InCallServiceImpl's global wake lock
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
                @Override public void onSurfaceTextureAvailable(@NonNull SurfaceTexture st, int w, int h) { 
                    setVideoSurfaces(); 
                    if (cameraDevice != null && cameraCaptureSession == null) {
                        createCameraPreviewSession();
                    }
                }
                @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture st, int w, int h) {}
                @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture st) { return true; }
                @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture st) {}
            });
        }
    }

    private void setVideoSurfaces() {
        if (CallManager.sCurrentCall != null) {
            try {
                InCallService.VideoCall vc = CallManager.sCurrentCall.getVideoCall();
                if (vc != null) {
                    if (textureRemoteVideo != null && textureRemoteVideo.isAvailable()) vc.setDisplaySurface(new Surface(textureRemoteVideo.getSurfaceTexture()));
                    if (textureLocalPreview != null && textureLocalPreview.isAvailable()) vc.setPreviewSurface(new Surface(textureLocalPreview.getSurfaceTexture()));
                    String frontCameraId = getFrontCameraId();
                    if (frontCameraId != null) {
                        vc.setCamera(frontCameraId);
                    }
                }
            } catch (Exception e) { Log.e("OngoingCallActivity", "Video error", e); }
        }
    }

    private String getFrontCameraId() {
        android.hardware.camera2.CameraManager manager = (android.hardware.camera2.CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) return null;
        try {
            for (String id : manager.getCameraIdList()) {
                android.hardware.camera2.CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) {
                    return id;
                }
            }
        } catch (Exception e) {
            Log.e("OngoingCallActivity", "Error getting front camera ID", e);
        }
        return null;
    }

    private void updateVideoUI() {
        if (CallManager.sCurrentCall != null) {
            try {
                int state = CallManager.sCurrentCall.getState();
                int videoState = CallManager.sCurrentCall.getDetails().getVideoState();
                boolean isVideo = (VideoProfile.isVideo(videoState) || mockVideoState) && state == Call.STATE_ACTIVE;
                boolean isRealVideo = VideoProfile.isVideo(videoState) && CallManager.sCurrentCall.getVideoCall() != null;

                runOnUiThread(() -> {
                    if (isVideo) {
                        if (textureRemoteVideo != null) textureRemoteVideo.setVisibility(View.VISIBLE);
                        if (cardLocalPreview != null) cardLocalPreview.setVisibility(View.VISIBLE);
                        if (layoutAvatarPulsing != null) layoutAvatarPulsing.setVisibility(View.GONE);
                        if (controlGrid != null) controlGrid.setAlpha(0.8f);
                        
                        if (isRealVideo) {
                            stopLocalCameraPreview();
                            setVideoSurfaces();
                        } else {
                            if (cameraDevice == null) startLocalCameraPreview();
                        }
                    } else {
                        if (textureRemoteVideo != null) textureRemoteVideo.setVisibility(View.GONE);
                        if (cardLocalPreview != null) cardLocalPreview.setVisibility(View.GONE);
                        if (layoutInCallKeypad != null && layoutInCallKeypad.getVisibility() != View.VISIBLE) {
                            if (layoutAvatarPulsing != null) layoutAvatarPulsing.setVisibility(View.VISIBLE);
                        }
                        if (controlGrid != null) controlGrid.setAlpha(1.0f);
                        stopLocalCameraPreview();
                    }
                    if (btnVideoCallInCall != null) {
                        updateButtonStyle(btnVideoCallInCall, ivVideoCallInCall, tvVideoCallInCall, isVideo);
                    }
                });
                if (isVideo) new Handler(android.os.Looper.getMainLooper()).postDelayed(this::setVideoSurfaces, 500);
            } catch (Exception e) { Log.e("OngoingCallActivity", "Video UI error", e); }
        }
    }

    private void animateEntry() {
        View info = findViewById(R.id.layoutTopInfo);
        if (info != null) {
            info.setAlpha(0f); info.setTranslationY(-50f);
            info.animate().alpha(1f).translationY(0).setDuration(500).start();
        }
        View controls = findViewById(R.id.controlGrid);
        if (controls != null) {
            controls.setAlpha(0f); controls.setTranslationY(100f);
            controls.animate().alpha(1f).translationY(0).setDuration(600).setStartDelay(200).start();
        }
    }

    private void updateMultiCallUI() {
        runOnUiThread(() -> {
            List<Call> calls = CallManager.getCalls();
            int participantCount = 0;
            for (Call c : calls) {
                if (c.getDetails() != null && c.getDetails().hasProperty(Call.Details.PROPERTY_CONFERENCE)) {
                    continue;
                }
                String num = getNumberFromCall(c);
                if (num == null || num.equals("Unknown") || num.isEmpty()) {
                    continue;
                }
                participantCount++;
            }

            if (participantCount > 1) {
                if (tvMultiCallSummary != null) {
                    tvMultiCallSummary.setVisibility(View.VISIBLE);
                    tvMultiCallSummary.setText(participantCount + " people in call");
                    tvMultiCallSummary.setOnClickListener(v -> { Utils.triggerHaptic(v); showParticipantsBottomSheet(); });
                }
                if (btnMerge != null) btnMerge.setVisibility(View.VISIBLE);
                if (tvCallerName != null) tvCallerName.setVisibility(View.GONE);
                if (tvCallerNumber != null) tvCallerNumber.setVisibility(View.GONE);
                if (layoutAvatarPulsing != null) { layoutAvatarPulsing.setScaleX(0.7f); layoutAvatarPulsing.setScaleY(0.7f); }
            } else {
                if (tvMultiCallSummary != null) tvMultiCallSummary.setVisibility(View.GONE);
                if (btnMerge != null) btnMerge.setVisibility(View.GONE);
                if (tvCallerName != null) tvCallerName.setVisibility(View.VISIBLE);
                if (tvCallerNumber != null) { tvCallerNumber.setText(phoneNumber); tvCallerNumber.setVisibility(View.VISIBLE); }
                if (layoutAvatarPulsing != null) { layoutAvatarPulsing.setScaleX(1.0f); layoutAvatarPulsing.setScaleY(1.0f); }
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
            if (call.getDetails() != null && call.getDetails().hasProperty(Call.Details.PROPERTY_CONFERENCE)) {
                continue;
            }
            String num = getNumberFromCall(call);
            if (num == null || num.equals("Unknown") || num.isEmpty()) {
                continue;
            }

            View item = getLayoutInflater().inflate(R.layout.multi_call_item, listContainer, false);
            TextView nameTv = item.findViewById(R.id.tvMultiCallName);
            TextView statusTv = item.findViewById(R.id.tvMultiCallStatus);
            View endBtn = item.findViewById(R.id.btnMultiCallEnd);
            
            // Set initial display to number
            nameTv.setText(num); 
            
            // Try to resolve name from cache or system
            AppDatabase.databaseWriteExecutor.execute(() -> {
                ContactModel contact = ContactCache.getContactByNumber(num);
                String resolvedName = (contact != null) ? contact.getName() : Utils.queryContactName(this, num);
                
                if (resolvedName != null) {
                    runOnUiThread(() -> nameTv.setText(resolvedName));
                }
            });

            statusTv.setText(call.getState() == Call.STATE_HOLDING ? "On Hold" : "Active");
            endBtn.setOnClickListener(v -> {
                Utils.triggerHaptic(v);
                call.disconnect();
                listContainer.removeView(item);
                
                // Count remaining valid participants
                int remainingCount = 0;
                for (Call c : CallManager.getCalls()) {
                    if (c.getDetails() != null && c.getDetails().hasProperty(Call.Details.PROPERTY_CONFERENCE)) continue;
                    String numberStr = getNumberFromCall(c);
                    if (numberStr != null && !numberStr.equals("Unknown") && !numberStr.isEmpty()) {
                        remainingCount++;
                    }
                }
                if (remainingCount <= 1) dialog.dismiss();
            });
            listContainer.addView(item);
        }
        dialog.show();
    }

    private String getNumberFromCall(Call call) {
        try {
            if (call == null || call.getDetails() == null) return "Unknown";
            Uri handle = call.getDetails().getHandle();
            if (handle != null) return handle.getSchemeSpecificPart();
            
            // Fallback for conference participants or restricted handles
            if (call.getDetails().getGatewayInfo() != null) {
                return call.getDetails().getGatewayInfo().getOriginalAddress().getSchemeSpecificPart();
            }
        } catch (Exception ignored) {}
        return "Unknown";
    }

    private void updateTimerUI() {
        if (CallManager.sCurrentCall != null) {
            int state = CallManager.sCurrentCall.getState();
            if (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING) {
                if (tvCallTimer != null) tvCallTimer.setText("Calling...");
                return;
            }
        }
        
        if (callStartTime > 0) {
            long durationMs = SystemClock.elapsedRealtime() - callStartTime;
            long seconds = durationMs / 1000;
            if (tvCallTimer != null) tvCallTimer.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60));
        } else {
            if (tvCallTimer != null) tvCallTimer.setText("00:00");
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
                final String uri = contact.getPhotoUri();
                runOnUiThread(() -> {
                    if (tvCallerName != null) tvCallerName.setText(callerName);
                    Utils.loadContactPhoto(this, uri, ivCallerPhoto);
                });
            } else {
                String name = Utils.queryContactName(this, phoneNumber);
                if (name != null) { callerName = name; runOnUiThread(() -> { if (tvCallerName != null) tvCallerName.setText(callerName); }); }
            }
        });
    }

    private void setupPremiumEndCallInteraction() {
        if (btnEndCall == null) return;
        btnEndCall.setOnClickListener(v -> endCallSignal());
        btnEndCall.setOnTouchListener(new View.OnTouchListener() {
            private float startX; private boolean swiping = false;
            @Override public boolean onTouch(View v, MotionEvent event) {
                float threshold = v.getWidth() * 0.5f;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: startX = event.getX(); swiping = false; v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(150).start(); return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = startX - event.getX();
                        if (Math.abs(dx) > 20) { swiping = true; v.setTranslationX(-dx); if (dx > threshold) { v.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(100).start(); endCallSignal(); return false; } }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).translationX(0).setDuration(250).start();
                        if (!swiping && event.getAction() == MotionEvent.ACTION_UP) v.performClick();
                        return true;
                }
                return false;
            }
        });
    }

    private void updateCallStatusIndicators() {
        if (CallManager.sCurrentCall != null) {
            try {
                Call.Details details = CallManager.sCurrentCall.getDetails();
                if (details != null) {
                    int props = details.getCallProperties();
                    
                    // Mutual Exclusive Logic: Show VoWiFi if available, otherwise show VoLTE
                    boolean isWifi = (props & Call.Details.PROPERTY_WIFI) != 0;
                    boolean isVolte = !isWifi && ((props & Call.Details.PROPERTY_HIGH_DEF_AUDIO) != 0);

                    runOnUiThread(() -> {
                        if (tvWifiIcon != null) {
                            tvWifiIcon.setText("VoWiFi");
                            tvWifiIcon.setVisibility(isWifi ? View.VISIBLE : View.GONE);
                        }
                        if (tvHdIcon != null) {
                            tvHdIcon.setText("VoLTE");
                            tvHdIcon.setVisibility(isVolte ? View.VISIBLE : View.GONE);
                        }
                    });
                }
            } catch (Exception e) { Log.e("OngoingCallActivity", "Indicator error", e); }
        }
    }

    private void setupControlButtons() {
        btnMute.setOnClickListener(v -> toggleMute());
        btnSpeaker.setOnClickListener(v -> toggleSpeaker());
        btnHold.setOnClickListener(v -> toggleHold());
        if (btnMore != null) btnMore.setOnClickListener(this::showMoreMenu);
        btnKeypadToggle.setOnClickListener(v -> toggleKeypad());
        if (btnMerge != null) btnMerge.setOnClickListener(v -> { Utils.triggerHaptic(v); mergeCalls(); });
        btnAddCall.setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            startActivityForResult(intent, REQUEST_CODE_ADD_CALL);
        });
        if (btnVideoCallInCall != null) {
            btnVideoCallInCall.setOnClickListener(v -> {
                Utils.triggerHaptic(v);
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1002); return; }
                requestVideoUpgrade();
            });
        }

        // Initialize button styles (fixes lack of contrast in light mode on startup)
        updateButtonStyle(btnMute, ivMute, tvMute, isMuted);
        updateButtonStyle(btnSpeaker, ivSpeaker, tvSpeaker, false);
        updateButtonStyle(btnHold, ivHold, tvHold, isHeld);
        updateButtonStyle(btnKeypadToggle, ivKeypadToggle, tvKeypadToggle, false);
        updateButtonStyle(btnAddCall, ivAddCall, tvAddCall, false);
        updateButtonStyle(btnVideoCallInCall, ivVideoCallInCall, tvVideoCallInCall, false);
        if (btnMerge != null) updateButtonStyle(btnMerge, ivMerge, tvMerge, false);
    }

    private void mergeCalls() {
        try {
            List<Call> calls = CallManager.getCalls();
            if (calls.size() < 2) return;
            Call active = null;
            for (Call c : calls) { if (c.getState() == Call.STATE_ACTIVE) { active = c; break; } }
            if (active != null) {
                for (Call c : calls) {
                    if (c != active && (c.getState() == Call.STATE_ACTIVE || c.getState() == Call.STATE_HOLDING)) {
                        active.conference(c);
                    }
                }
                Toast.makeText(this, "Merging calls...", Toast.LENGTH_SHORT).show();
            } else {
                calls.get(0).conference(calls.get(1));
                Toast.makeText(this, "Merging calls...", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("OngoingCallActivity", "Merge error", e);
            Toast.makeText(this, "Error on merge: try again", Toast.LENGTH_SHORT).show();
        }
    }

    private void showMoreMenu(View v) {
        Utils.triggerHaptic(v); 
        PopupMenu popup = new PopupMenu(this, v);
        
        boolean isRecording = RecordingService.isServiceRunning();
        
        if (isRecording) {
            popup.getMenu().add(Menu.NONE, 1, 1, "Stop Recording");
        } else {
            popup.getMenu().add(Menu.NONE, 1, 1, "Record Call");
        }
        popup.getMenu().add(Menu.NONE, 2, 2, "View Recordings");
        
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == 1) {
                if (isRecording) {
                    Intent intent = new Intent(this, RecordingService.class);
                    intent.setAction(RecordingService.ACTION_STOP_RECORDING);
                    startService(intent);
                    Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
                } else {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1003);
                    } else {
                        startCallRecording();
                    }
                }
                return true;
            } else if (itemId == 2) {
                startActivity(new Intent(this, RecordingsListActivity.class));
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void startCallRecording() {
        Intent intent = new Intent(this, RecordingService.class);
        intent.setAction(RecordingService.ACTION_START_RECORDING);
        intent.putExtra(RecordingService.EXTRA_PHONE_NUMBER, phoneNumber);
        intent.putExtra(RecordingService.EXTRA_CALLER_NAME, callerName);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to start recording service", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleKeypad() {
        Utils.triggerHaptic(btnKeypadToggle); boolean visible = layoutInCallKeypad.getVisibility() == View.VISIBLE;
        if (visible) { layoutInCallKeypad.setVisibility(View.GONE); if (controlGrid != null) controlGrid.setVisibility(View.VISIBLE); if (CallManager.sCurrentCall != null && !VideoProfile.isVideo(CallManager.sCurrentCall.getDetails().getVideoState())) if (layoutAvatarPulsing != null) layoutAvatarPulsing.setVisibility(View.VISIBLE); }
        else { layoutInCallKeypad.setVisibility(View.VISIBLE); if (controlGrid != null) controlGrid.setVisibility(View.GONE); if (layoutAvatarPulsing != null) layoutAvatarPulsing.setVisibility(View.GONE); }
    }

    private void setupInCallKeypad() {
        int[] ids = {R.id.btnKey1, R.id.btnKey2, R.id.btnKey3, R.id.btnKey4, R.id.btnKey5, R.id.btnKey6, R.id.btnKey7, R.id.btnKey8, R.id.btnKey9, R.id.btnKeyStar, R.id.btnKey0, R.id.btnKeyHash};
        String[] chars = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        for (int i = 0; i < ids.length; i++) {
            final String d = chars[i]; View b = findViewById(ids[i]);
            if (b != null) b.setOnClickListener(v -> { Utils.triggerHaptic(v); if (tvKeypadDigits != null) tvKeypadDigits.append(d); if (CallManager.sCurrentCall != null) { CallManager.sCurrentCall.playDtmfTone(d.charAt(0)); CallManager.sCurrentCall.stopDtmfTone(); } });
        }
        if (btnHideKeypad != null) btnHideKeypad.setOnClickListener(v -> toggleKeypad());
    }

    private void requestVideoUpgrade() {
        if (CallManager.sCurrentCall != null) {
            InCallService.VideoCall vc = CallManager.sCurrentCall.getVideoCall();
            if (vc != null) {
                int curr = CallManager.sCurrentCall.getDetails().getVideoState();
                int next = VideoProfile.isVideo(curr) ? VideoProfile.STATE_AUDIO_ONLY : VideoProfile.STATE_BIDIRECTIONAL;
                vc.sendSessionModifyRequest(new VideoProfile(next));
                if (btnVideoCallInCall != null) {
                    updateButtonStyle(btnVideoCallInCall, ivVideoCallInCall, tvVideoCallInCall, !VideoProfile.isVideo(curr));
                }
            } else {
                mockVideoState = !mockVideoState;
                updateVideoUI();
            }
        }
    }

    private void startLocalCameraPreview() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        android.hardware.camera2.CameraManager manager = (android.hardware.camera2.CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) return;
        try {
            for (String id : manager.getCameraIdList()) {
                android.hardware.camera2.CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = id;
                    break;
                }
            }
            if (cameraId == null) {
                if (manager.getCameraIdList().length > 0) cameraId = manager.getCameraIdList()[0];
                else return;
            }
            manager.openCamera(cameraId, new android.hardware.camera2.CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull android.hardware.camera2.CameraDevice camera) {
                    cameraDevice = camera;
                    createCameraPreviewSession();
                }
                @Override
                public void onDisconnected(@NonNull android.hardware.camera2.CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }
                @Override
                public void onError(@NonNull android.hardware.camera2.CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                }
            }, null);
        } catch (Exception e) {
            Log.e("OngoingCallActivity", "Camera open error", e);
        }
    }

    private void createCameraPreviewSession() {
        if (cameraDevice == null || textureLocalPreview == null || !textureLocalPreview.isAvailable()) return;
        try {
            SurfaceTexture texture = textureLocalPreview.getSurfaceTexture();
            if (texture == null) return;
            texture.setDefaultBufferSize(640, 480);
            Surface previewSurface = new Surface(texture);
            final android.hardware.camera2.CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(previewSurface);
            cameraDevice.createCaptureSession(java.util.Arrays.asList(previewSurface), new android.hardware.camera2.CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull android.hardware.camera2.CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    cameraCaptureSession = session;
                    try {
                        builder.set(android.hardware.camera2.CaptureRequest.CONTROL_MODE, android.hardware.camera2.CameraMetadata.CONTROL_MODE_AUTO);
                        cameraCaptureSession.setRepeatingRequest(builder.build(), null, null);
                    } catch (Exception e) {
                        Log.e("OngoingCallActivity", "Capture request error", e);
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull android.hardware.camera2.CameraCaptureSession session) {
                    Log.e("OngoingCallActivity", "Capture session configure failed");
                }
            }, null);
        } catch (Exception e) {
            Log.e("OngoingCallActivity", "Session create error", e);
        }
    }

    private void stopLocalCameraPreview() {
        if (cameraCaptureSession != null) {
            try { cameraCaptureSession.close(); } catch (Exception ignored) {}
            cameraCaptureSession = null;
        }
        if (cameraDevice != null) {
            try { cameraDevice.close(); } catch (Exception ignored) {}
            cameraDevice = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1002) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestVideoUpgrade();
            } else {
                Toast.makeText(this, "Camera permission is required for video calls", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 1003) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCallRecording();
            } else {
                Toast.makeText(this, "Microphone permission is required to record calls", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ADD_CALL && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                    if (c != null && c.moveToFirst()) { String num = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)); if (num != null && !num.isEmpty()) Utils.makePhoneCall(this, num); }
                } catch (Exception e) { Log.e("OngoingCallActivity", "Pick error", e); }
            }
        }
    }

    private void toggleMute() {
        Utils.triggerHaptic(btnMute); isMuted = !isMuted; if (btnMute != null) btnMute.setActivated(isMuted);
        updateButtonStyle(btnMute, ivMute, tvMute, isMuted); if (InCallServiceImpl.sInstance != null) InCallServiceImpl.sInstance.setMuted(isMuted);
    }

    private void toggleSpeaker() {
        Utils.triggerHaptic(btnSpeaker);
        if (InCallServiceImpl.sInstance != null) {
            CallAudioState s = InCallServiceImpl.sInstance.getCallAudioState();
            if (s != null) {
                int r = s.getRoute();
                int next;
                if (r == CallAudioState.ROUTE_SPEAKER) {
                    next = CallAudioState.ROUTE_EARPIECE;
                } else if ((s.getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH) != 0) {
                    // If BT is connected, show routing menu
                    showAudioRoutePopup(btnSpeaker);
                    return;
                } else {
                    next = CallAudioState.ROUTE_SPEAKER;
                }
                InCallServiceImpl.sInstance.setAudioRoute(next);
            }
        }
    }

    private void showAudioRoutePopup(View anchor) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.SamsungBottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_audio_routing, null);
        dialog.setContentView(view);
        view.findViewById(R.id.btnRouteEarpiece).setOnClickListener(v -> { if (InCallServiceImpl.sInstance != null) InCallServiceImpl.sInstance.setAudioRoute(CallAudioState.ROUTE_EARPIECE); dialog.dismiss(); });
        view.findViewById(R.id.btnRouteSpeaker).setOnClickListener(v -> { if (InCallServiceImpl.sInstance != null) InCallServiceImpl.sInstance.setAudioRoute(CallAudioState.ROUTE_SPEAKER); dialog.dismiss(); });
        view.findViewById(R.id.btnRouteBluetooth).setOnClickListener(v -> { if (InCallServiceImpl.sInstance != null) InCallServiceImpl.sInstance.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH); dialog.dismiss(); });
        dialog.show();
    }

    private void updateSpeakerUI(@Nullable CallAudioState s) {
        if (s == null && InCallServiceImpl.sInstance != null) s = InCallServiceImpl.sInstance.getCallAudioState();
        if (s == null) return;
        int r = s.getRoute(); 
        int mask = s.getSupportedRouteMask();
        boolean bluetoothAvailable = (mask & CallAudioState.ROUTE_BLUETOOTH) != 0;
        
        // Multi-state button logic
        boolean active = (r == CallAudioState.ROUTE_SPEAKER || r == CallAudioState.ROUTE_BLUETOOTH);
        if (btnSpeaker != null) btnSpeaker.setActivated(active);
        
        if (ivSpeaker != null) {
            // Show Bluetooth icon if Bluetooth is current route OR if Bluetooth is connected but we are on earpiece
            if (r == CallAudioState.ROUTE_BLUETOOTH || (r == CallAudioState.ROUTE_EARPIECE && bluetoothAvailable)) {
                ivSpeaker.setImageResource(R.drawable.ic_bluetooth);
            } else {
                ivSpeaker.setImageResource(R.drawable.ic_speaker);
            }
        }
        
        if (tvSpeaker != null) {
            if (r == CallAudioState.ROUTE_BLUETOOTH) tvSpeaker.setText("Bluetooth");
            else if (r == CallAudioState.ROUTE_EARPIECE && bluetoothAvailable) tvSpeaker.setText("Bluetooth");
            else tvSpeaker.setText("Speaker");
        }
        
        updateButtonStyle(btnSpeaker, ivSpeaker, tvSpeaker, active);
    }

    private void toggleHold() {
        Utils.triggerHaptic(btnHold); isHeld = !isHeld; if (btnHold != null) btnHold.setActivated(isHeld);
        updateButtonStyle(btnHold, ivHold, tvHold, isHeld); if (tvHold != null) tvHold.setText(isHeld ? "Unhold" : "Hold");
        if (CallManager.sCurrentCall != null) { if (isHeld) CallManager.sCurrentCall.hold(); else CallManager.sCurrentCall.unhold(); }
    }

    private void updateButtonStyle(View btn, ImageView iv, TextView tv, boolean active) {
        if (active) {
            if (btn != null) btn.setActivated(true);
            if (iv != null) iv.setColorFilter(getResources().getColor(R.color.card_bg));
            if (tv != null) tv.setTextColor(getResources().getColor(R.color.card_bg));
        } else {
            if (btn != null) btn.setActivated(false);
            if (iv != null) iv.setColorFilter(getResources().getColor(R.color.text_primary));
            if (tv != null) tv.setTextColor(getResources().getColor(R.color.text_secondary));
        }
    }

    private void startPulseAnimation() { if (viewPulse1 != null) animatePulse(viewPulse1, 0); if (viewPulse2 != null) animatePulse(viewPulse2, 1200); }

    private void animatePulse(View view, int delay) {
        view.setScaleX(0.8f); view.setScaleY(0.8f); view.setAlpha(0.6f);
        ObjectAnimator sx = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.8f, 1.5f); ObjectAnimator sy = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.8f, 1.5f); ObjectAnimator a = ObjectAnimator.ofFloat(view, View.ALPHA, 0.6f, 0f);
        sx.setRepeatCount(ValueAnimator.INFINITE); sy.setRepeatCount(ValueAnimator.INFINITE); a.setRepeatCount(ValueAnimator.INFINITE);
        sx.setDuration(2500); sy.setDuration(2500); a.setDuration(2500); sx.setStartDelay(delay); sy.setStartDelay(delay); a.setStartDelay(delay); sx.start(); sy.start(); a.start();
    }

    private void endCallSignal() { if (CallManager.sCurrentCall != null) { try { CallManager.sCurrentCall.disconnect(); } catch (Exception ignored) {} } finishAndRemoveTask(); }
    @Override public void onSensorChanged(SensorEvent event) {}
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override protected void onResume() { super.onResume(); updateSpeakerUI(null); }
    @Override protected void onPause() { 
        super.onPause(); 
        stopLocalCameraPreview();
    }
    private void registerDisconnectReceiver() { IntentFilter f = new IntentFilter(); f.addAction("com.gg_tech_bharat.gdialer.CALL_DISCONNECTED"); f.addAction("com.gg_tech_bharat.gdialer.VIDEO_STATE_CHANGED"); try { androidx.core.content.ContextCompat.registerReceiver(this, disconnectReceiver, f, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED); } catch (Exception ignored) {} }
    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override protected void onDestroy() {
        // Stop recording and camera preview when activity destroyed just in case
        try {
            Intent recordIntent = new Intent(this, RecordingService.class);
            recordIntent.setAction(RecordingService.ACTION_STOP_RECORDING);
            startService(recordIntent);
        } catch (Exception ignored) {}
        stopLocalCameraPreview();
        timerHandler.removeCallbacks(timerRunnable); 
        CallManager.unregisterListener(callListListener); 
        try { unregisterReceiver(disconnectReceiver); } catch (Exception ignored) {} 
        super.onDestroy(); 
    }
}
