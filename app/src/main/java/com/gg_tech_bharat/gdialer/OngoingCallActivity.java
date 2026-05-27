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
        @Override public void run() {
            updateTimerUI();
            timerHandler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver disconnectReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if ("com.gg_tech_bharat.gdialer.CALL_DISCONNECTED".equals(intent.getAction())) {
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
        }
    };

    private final CallManager.CallStateListener callListListener = new CallManager.CallStateListener() {
        @Override public void onStateChanged(int state) {
            if (state == Call.STATE_ACTIVE && callStartTime == 0) callStartTime = SystemClock.elapsedRealtime();
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
        setupPremiumEndCallInteraction();
        setupControlButtons();
        setupInCallKeypad();
        CallManager.registerListener(callListListener);
        updateMultiCallUI();
        updateVideoUI();
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
        if (passedName != null && !passedName.equals(phoneNumber)) callerName = passedName;
        else callerName = phoneNumber;
        if (tvCallerName != null) tvCallerName.setText(callerName);
        if (CallManager.sCurrentCall != null && CallManager.sCurrentCall.getState() == Call.STATE_ACTIVE) {
            long connectTime = CallManager.sCurrentCall.getDetails().getConnectTimeMillis();
            if (connectTime > 0) callStartTime = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - connectTime);
        }
    }

    private void setupProximitySensor() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && proximitySensor != null) {
                try { wakeLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, getLocalClassName()); }
                catch (Exception e) { Log.e("OngoingCallActivity", "Wakelock error", e); }
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
                InCallService.VideoCall vc = CallManager.sCurrentCall.getVideoCall();
                if (vc != null) {
                    if (textureRemoteVideo != null && textureRemoteVideo.isAvailable()) vc.setDisplaySurface(new Surface(textureRemoteVideo.getSurfaceTexture()));
                    if (textureLocalPreview != null && textureLocalPreview.isAvailable()) vc.setPreviewSurface(new Surface(textureLocalPreview.getSurfaceTexture()));
                }
            } catch (Exception e) { Log.e("OngoingCallActivity", "Video error", e); }
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
            if (calls.size() > 1) {
                if (tvMultiCallSummary != null) {
                    tvMultiCallSummary.setVisibility(View.VISIBLE);
                    tvMultiCallSummary.setText(calls.size() + " people in call");
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
            View item = getLayoutInflater().inflate(R.layout.multi_call_item, listContainer, false);
            TextView nameTv = item.findViewById(R.id.tvMultiCallName);
            TextView statusTv = item.findViewById(R.id.tvMultiCallStatus);
            View endBtn = item.findViewById(R.id.btnMultiCallEnd);
            String num = getNumberFromCall(call);
            nameTv.setText(num); 
            AppDatabase.databaseWriteExecutor.execute(() -> {
                String name = Utils.queryContactName(this, num);
                if (name != null) runOnUiThread(() -> nameTv.setText(name));
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
            if (call != null && call.getDetails() != null && call.getDetails().getHandle() != null) return call.getDetails().getHandle().getSchemeSpecificPart();
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
        Utils.triggerHaptic(v); PopupMenu popup = new PopupMenu(this, v);
        popup.getMenu().add(Menu.NONE, 1, Menu.NONE, isRecording ? "Stop recording" : "Record");
        popup.setOnMenuItemClickListener(item -> { if (item.getItemId() == 1) { toggleRecording(); return true; } return false; });
        popup.show();
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
                if (btnVideoCallInCall != null) btnVideoCallInCall.setActivated(!VideoProfile.isVideo(curr));
                updateButtonStyle(btnVideoCallInCall, null, null, !VideoProfile.isVideo(curr));
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
                boolean bt = (s.getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH) != 0;
                if (bt) showAudioRoutePopup(btnSpeaker);
                else { int curr = s.getRoute(); int next = (curr == CallAudioState.ROUTE_SPEAKER) ? CallAudioState.ROUTE_EARPIECE : CallAudioState.ROUTE_SPEAKER; InCallServiceImpl.sInstance.setAudioRoute(next); updateSpeakerUI(next == CallAudioState.ROUTE_SPEAKER); }
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
        int r = s.getRoute(); boolean active = (r == CallAudioState.ROUTE_SPEAKER);
        if (btnSpeaker != null) btnSpeaker.setActivated(active);
        if (ivSpeaker != null) {
            if (r == CallAudioState.ROUTE_SPEAKER) ivSpeaker.setImageResource(R.drawable.ic_speaker);
            else if (r == CallAudioState.ROUTE_BLUETOOTH) ivSpeaker.setImageResource(R.drawable.ic_bluetooth);
            else ivSpeaker.setImageResource(R.drawable.ic_phone);
        }
        if (tvSpeaker != null) {
            if (r == CallAudioState.ROUTE_SPEAKER) tvSpeaker.setText("Speaker");
            else if (r == CallAudioState.ROUTE_BLUETOOTH) tvSpeaker.setText("Bluetooth");
            else if (r == CallAudioState.ROUTE_EARPIECE) tvSpeaker.setText("Earpiece");
            else if (r == CallAudioState.ROUTE_WIRED_HEADSET) tvSpeaker.setText("Headset");
            else tvSpeaker.setText("Audio");
        }
        updateButtonStyle(btnSpeaker, ivSpeaker, tvSpeaker, active);
    }

    private void updateSpeakerUI(boolean active) { updateSpeakerUI(null); }

    private void toggleHold() {
        Utils.triggerHaptic(btnHold); isHeld = !isHeld; if (btnHold != null) btnHold.setActivated(isHeld);
        updateButtonStyle(btnHold, ivHold, tvHold, isHeld); if (tvHold != null) tvHold.setText(isHeld ? "Unhold" : "Hold");
        if (CallManager.sCurrentCall != null) { if (isHeld) CallManager.sCurrentCall.hold(); else CallManager.sCurrentCall.unhold(); }
    }

    private void toggleRecording() {
        Intent intent = getPackageManager().getLaunchIntentForPackage("com.nothing.recorder");
        if (intent != null) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent); Toast.makeText(this, "Opening Nothing Recorder...", Toast.LENGTH_SHORT).show(); }
        else { isRecording = !isRecording; if (isRecording) { RecordingService.start(this, callerName); Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show(); } else { RecordingService.stop(this); Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show(); } }
    }

    private void updateButtonStyle(View btn, ImageView iv, TextView tv, boolean active) {
        if (active) { if (btn != null) btn.setActivated(true); if (iv != null) iv.setColorFilter(getResources().getColor(R.color.white)); if (tv != null) tv.setTextColor(getResources().getColor(R.color.white)); }
        else { if (btn != null) btn.setActivated(false); if (iv != null) iv.setColorFilter(getResources().getColor(R.color.white)); if (tv != null) tv.setTextColor(getResources().getColor(R.color.gray_light)); }
    }

    private void startPulseAnimation() { if (viewPulse1 != null) animatePulse(viewPulse1, 0); if (viewPulse2 != null) animatePulse(viewPulse2, 1200); }

    private void animatePulse(View view, int delay) {
        view.setScaleX(0.8f); view.setScaleY(0.8f); view.setAlpha(0.6f);
        ObjectAnimator sx = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.8f, 1.5f); ObjectAnimator sy = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.8f, 1.5f); ObjectAnimator a = ObjectAnimator.ofFloat(view, View.ALPHA, 0.6f, 0f);
        sx.setRepeatCount(ValueAnimator.INFINITE); sy.setRepeatCount(ValueAnimator.INFINITE); a.setRepeatCount(ValueAnimator.INFINITE);
        sx.setDuration(2500); sy.setDuration(2500); a.setDuration(2500); sx.setStartDelay(delay); sy.setStartDelay(delay); a.setStartDelay(delay); sx.start(); sy.start(); a.start();
    }

    private void endCallSignal() { if (CallManager.sCurrentCall != null) { try { CallManager.sCurrentCall.disconnect(); } catch (Exception ignored) {} } finishAndRemoveTask(); }
    @Override public void onSensorChanged(SensorEvent event) { if (proximitySensor == null) return; if (event.values[0] < proximitySensor.getMaximumRange()) { if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(10 * 60 * 1000L); } else { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override protected void onResume() { super.onResume(); if (proximitySensor != null) sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL); }
    @Override protected void onPause() { super.onPause(); if (sensorManager != null) sensorManager.unregisterListener(this); if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); }
    private void registerDisconnectReceiver() { IntentFilter f = new IntentFilter(); f.addAction("com.gg_tech_bharat.gdialer.CALL_DISCONNECTED"); f.addAction("com.gg_tech_bharat.gdialer.VIDEO_STATE_CHANGED"); try { androidx.core.content.ContextCompat.registerReceiver(this, disconnectReceiver, f, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED); } catch (Exception ignored) {} }
    @Override protected void onDestroy() { timerHandler.removeCallbacks(timerRunnable); CallManager.unregisterListener(callListListener); try { unregisterReceiver(disconnectReceiver); } catch (Exception ignored) {} super.onDestroy(); }
}
