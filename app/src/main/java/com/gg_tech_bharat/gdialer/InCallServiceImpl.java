package com.gg_tech_bharat.gdialer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.SystemClock;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.telecom.VideoProfile;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;

import java.util.List;
import java.util.Objects;

public class InCallServiceImpl extends InCallService {

    public static InCallServiceImpl sInstance;
    private static final String CHANNEL_ID_HIGH = "incoming_calls_v19_silent";
    private static final String CHANNEL_ID_DEFAULT = "ongoing_calls_default_v19_silent";
    private static final String ACTION_END_CALL = "com.gg_tech_bharat.gdialer.ACTION_END_CALL";
    private static final String ACTION_ANSWER_CALL = "com.gg_tech_bharat.gdialer.ACTION_ANSWER_CALL";
    private static final String ACTION_MUTE = "com.gg_tech_bharat.gdialer.ACTION_MUTE";
    private static final String ACTION_SPEAKER = "com.gg_tech_bharat.gdialer.ACTION_SPEAKER";
    private static final int NOTIFICATION_ID = 101;

    private String currentRingingCallId = null;

    private final BroadcastReceiver actionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            Log.d("InCallServiceImpl", "Broadcast action received: " + action);
            
            try {
                switch (action) {
                    case ACTION_END_CALL:
                        Call ringing = CallManager.getRingingCall();
                        if (ringing != null) ringing.disconnect();
                        else if (CallManager.sCurrentCall != null) CallManager.sCurrentCall.disconnect();
                        break;
                    case ACTION_ANSWER_CALL:
                        int videoState = intent.getIntExtra("VIDEO_STATE", VideoProfile.STATE_AUDIO_ONLY);
                        Call ringingAnswer = CallManager.getRingingCall();
                        if (ringingAnswer != null) {
                            ringingAnswer.answer(videoState);
                        } else if (CallManager.sCurrentCall != null && CallManager.sCurrentCall.getState() == Call.STATE_RINGING) {
                            CallManager.sCurrentCall.answer(videoState);
                        }
                        break;
                    case ACTION_MUTE:
                        CallAudioState state = getCallAudioState();
                        if (state != null) setMuted(!state.isMuted());
                        break;
                    case ACTION_SPEAKER:
                        CallAudioState speakerState = getCallAudioState();
                        if (speakerState != null) {
                            int newRoute = (speakerState.getRoute() == CallAudioState.ROUTE_SPEAKER) ? CallAudioState.ROUTE_EARPIECE : CallAudioState.ROUTE_SPEAKER;
                            setAudioRoute(newRoute);
                        }
                        break;
                }
            } catch (Exception e) {
                Log.e("InCallServiceImpl", "Error handling broadcast action", e);
            }
        }
    };

    private final java.util.Set<String> endVibratedCalls = new java.util.HashSet<>();

    private final Call.Callback callCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            super.onStateChanged(call, state);
            CallManager.updateState(state);
            
            String callId = (call.getDetails() != null) ? String.valueOf(call.getDetails().getCreationTimeMillis()) : call.toString();

            if (state == Call.STATE_ACTIVE) {
                // Remove pickup vibration to avoid "vibrate twice" issue
                handleCallState(call, state); // MUST CALL THIS TO LAUNCH ONGOING SCREEN
            } else if (state == Call.STATE_DISCONNECTED) {
                if (!endVibratedCalls.contains(callId)) {
                    // Short vibrate on end
                    try { Utils.vibrateDevice(getApplicationContext(), 100); } catch (Exception ignored) {} 
                    endVibratedCalls.add(callId);
                }
                saveCallToLocalLog(call);
                cleanupCall(call);
            } else {
                handleCallState(call, state);
            }
        }

        @Override
        public void onDetailsChanged(Call call, Call.Details details) {
            super.onDetailsChanged(call, details);
            if (call.getState() == Call.STATE_ACTIVE || call.getState() == Call.STATE_DIALING || call.getState() == Call.STATE_CONNECTING) {
                showActiveCallNotification(getNumberFromCall(call));
            }
        }

        @Override
        public void onVideoCallChanged(Call call, InCallService.VideoCall videoCall) {
            super.onVideoCallChanged(call, videoCall);
            if (videoCall != null) {
                videoCall.registerCallback(new InCallService.VideoCall.Callback() {
                    @Override public void onSessionModifyRequestReceived(VideoProfile videoProfile) {
                        int videoState = videoProfile.getVideoState();
                        if (VideoProfile.isVideo(videoState)) {
                            videoCall.sendSessionModifyResponse(new VideoProfile(videoState));
                        }
                    }
                    @Override public void onSessionModifyResponseReceived(int status, VideoProfile requestedProfile, VideoProfile responseProfile) {
                        Intent intent = new Intent("com.gg_tech_bharat.gdialer.VIDEO_STATE_CHANGED");
                        intent.setPackage(getPackageName());
                        sendBroadcast(intent);
                    }
                    @Override public void onCallSessionEvent(int event) {}
                    @Override public void onPeerDimensionsChanged(int width, int height) {}
                    @Override public void onVideoQualityChanged(int videoQuality) {}
                    @Override public void onCallDataUsageChanged(long dataUsage) {}
                    @Override public void onCameraCapabilitiesChanged(VideoProfile.CameraCapabilities cameraCapabilities) {}
                });
            }
        }
    };

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        super.onCallAudioStateChanged(audioState);
        CallManager.updateAudioState(audioState);
        Call current = CallManager.sCurrentCall;
        if (current != null && current.getState() != Call.STATE_DISCONNECTED && current.getState() != Call.STATE_RINGING) {
            showActiveCallNotification(getNumberFromCall(current));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        createNotificationChannels();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_END_CALL);
        filter.addAction(ACTION_ANSWER_CALL);
        filter.addAction(ACTION_MUTE);
        filter.addAction(ACTION_SPEAKER);
        try {
            androidx.core.content.ContextCompat.registerReceiver(this, actionReceiver, 
                filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        } catch (Exception ignored) {}
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        if (call == null) return;
        CallManager.addCall(call);
        call.registerCallback(callCallback);
        handleCallState(call, call.getState());
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        if (call != null) {
            String callId = (call.getDetails() != null) ? String.valueOf(call.getDetails().getCreationTimeMillis()) : call.toString();
            endVibratedCalls.remove(callId);
            
            if (call.getState() == Call.STATE_RINGING || CallManager.getRingingCall() == null) {
                Intent intent = new Intent("com.gg_tech_bharat.gdialer.RINGING_CALL_REMOVED");
                intent.setPackage(getPackageName());
                sendBroadcast(intent);
            }
        }
        cleanupCall(call);
    }

    private void cleanupCall(Call call) {
        if (call != null) {
            call.unregisterCallback(callCallback);
            CallManager.removeCall(call);
        }
        if (CallManager.getCalls().isEmpty()) {
            onAllCallsEnded();
        }
    }

    private void onAllCallsEnded() {
        setAudioRoute(CallAudioState.ROUTE_EARPIECE);
        setMuted(false);
        stopForeground(STOP_FOREGROUND_REMOVE);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
        Intent intent = new Intent("com.gg_tech_bharat.gdialer.CALL_DISCONNECTED");
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        try { unregisterReceiver(actionReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    private void handleCallState(Call call, int state) {
        try {
            String number = getNumberFromCall(call);
            Log.d("InCallServiceImpl", "State Transition: " + state + " for " + number);

            if (state == Call.STATE_RINGING) {
                if (Objects.equals(number, currentRingingCallId)) return;
                currentRingingCallId = number;
                
                android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                boolean isLocked = km != null && km.isKeyguardLocked();
                boolean isHome = isDeviceAtHome();
                boolean isForeground = isAppInForeground();

                String name = getContactName(number);
                
                // FORCED ACTIVITY LAUNCH: For Lock screen, Home, GDialer, or Call Waiting
                // This ensures the Samsung-style "Hold & Answer" buttons are always accessible
                if (isLocked || isHome || isForeground || CallManager.getCalls().size() > 1) {
                    startCallActivity(IncomingCallActivity.class, number, name);
                    
                    // Show high-priority notification to ensure lockscreen visibility and ringer/popup authority
                    showRingingNotification(number, call.getDetails().getVideoState(), CHANNEL_ID_HIGH);
                } else {
                    // ANY other app is running (multi-tasking) -> Popup (Heads-up) only
                    showRingingNotification(number, call.getDetails().getVideoState(), CHANNEL_ID_HIGH);
                }
            } else if (state == Call.STATE_ACTIVE || state == Call.STATE_DIALING || state == Call.STATE_CONNECTING) {
                currentRingingCallId = null;
                String name = getContactName(number);
                
                android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                boolean isLocked = km != null && km.isKeyguardLocked();
                boolean isGame = isGameRunning();
                boolean isLandscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;

                // Check if voicemail screening is active to prevent launching OngoingCallActivity
                if (CallManager.isVoicemailScreening) {
                    Log.d("InCallServiceImpl", "Voicemail screening active, suppressing OngoingCallActivity");
                    showActiveCallNotification(number);
                    return;
                }

                // FIX: Show Ongoing Call screen everywhere EXCEPT in games or landscape (unless locked)
                boolean showFullScreen = true;
                if ((isGame || isLandscape) && !isLocked && CallManager.getCalls().size() <= 1) {
                    showFullScreen = false;
                }

                if (showFullScreen) {
                    startCallActivity(OngoingCallActivity.class, number, name);
                }

                showActiveCallNotification(number);
            }
        } catch (Exception e) { Log.e("InCallServiceImpl", "State processing error", e); }
    }

    private boolean isGameRunning() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return false;
            
            // Standard check for top task
            List<android.app.ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks != null && !tasks.isEmpty() && tasks.get(0).topActivity != null) {
                String pkg = tasks.get(0).topActivity.getPackageName();
                if (pkg.equals(getPackageName())) return false;

                android.content.pm.ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, 0);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    return info.category == android.content.pm.ApplicationInfo.CATEGORY_GAME;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private String getContactName(String number) {
        // 1. Check optimized memory cache first (Ultra-Fast)
        ContactModel contact = ContactCache.getContactByNumber(number);
        if (contact != null) return contact.getName();
        
        // 2. Fallback to system contacts (Slow)
        return Utils.queryContactName(this, number);
    }

    private void showRingingNotification(String number, int videoState, String channelId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String name = Utils.queryContactName(this, number);
            final String finalName = (name != null) ? name : number;
            
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                try {
                    Intent intent = new Intent(this, IncomingCallActivity.class)
                            .putExtra("EXTRA_NUMBER", number)
                            .putExtra("EXTRA_NAME", finalName);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
                    PendingIntent fullScreenPi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    
                    Intent answerIntent = new Intent(ACTION_ANSWER_CALL);
                    answerIntent.setPackage(getPackageName());
                    answerIntent.putExtra("VIDEO_STATE", videoState);
                    PendingIntent answerPi = PendingIntent.getBroadcast(this, 2, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    
                    Intent declineIntent = new Intent(ACTION_END_CALL);
                    declineIntent.setPackage(getPackageName());
                    PendingIntent declinePi = PendingIntent.getBroadcast(this, 3, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId);

                    builder.setSmallIcon(R.drawable.ic_phone)
                            .setContentTitle("Incoming Call")
                            .setContentText(finalName)
                            .setFullScreenIntent(fullScreenPi, true)
                            .setOngoing(true)
                            .setCategory(NotificationCompat.CATEGORY_CALL)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                            .setAutoCancel(false)
                            .setPriority(NotificationCompat.PRIORITY_MAX);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        androidx.core.app.Person caller = new androidx.core.app.Person.Builder().setName(finalName).setImportant(true).build();
                        builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, declinePi, answerPi));
                    } else {
                        builder.addAction(R.drawable.ic_phone, "Answer", answerPi);
                        builder.addAction(R.drawable.ic_phone_end, "Decline", declinePi);
                    }

                    startForegroundCompat(builder.build());
                } catch (Exception e) { Log.e("InCallServiceImpl", "Ringing notif crash", e); }
            });
        });
    }

    private void showActiveCallNotification(String number) {
        // High-speed name resolution from cache
        String name = getContactName(number);
        final String finalName = (name != null) ? name : number;

        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                if (CallManager.sCurrentCall == null || CallManager.sCurrentCall.getState() == Call.STATE_DISCONNECTED) return;

                Intent intent = new Intent(this, OngoingCallActivity.class).putExtra("EXTRA_NUMBER", number).putExtra("EXTRA_NAME", finalName);
                PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                
                Intent endIntent = new Intent(ACTION_END_CALL);
                endIntent.setPackage(getPackageName());
                PendingIntent endPi = PendingIntent.getBroadcast(this, 1, endIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                Intent muteIntent = new Intent(ACTION_MUTE);
                muteIntent.setPackage(getPackageName());
                PendingIntent mutePi = PendingIntent.getBroadcast(this, 4, muteIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                Intent speakerIntent = new Intent(ACTION_SPEAKER);
                speakerIntent.setPackage(getPackageName());
                PendingIntent speakerPi = PendingIntent.getBroadcast(this, 5, speakerIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                // CUSTOM REMOTE VIEWS
                RemoteViews rv = new RemoteViews(getPackageName(), R.layout.notification_ongoing_call);
                rv.setTextViewText(R.id.tvNotifOngoingName, finalName);
                rv.setOnClickPendingIntent(R.id.btnNotifEnd, endPi);
                rv.setOnClickPendingIntent(R.id.btnNotifMute, mutePi);
                rv.setOnClickPendingIntent(R.id.btnNotifSpeaker, speakerPi);

                // HD and WiFi Indicator update for Notification
                if (CallManager.sCurrentCall != null) {
                    Call.Details details = CallManager.sCurrentCall.getDetails();
                    if (details != null) {
                        int props = details.getCallProperties();
                        boolean isWifi = (props & Call.Details.PROPERTY_WIFI) != 0;
                        boolean isHd = !isWifi && (props & Call.Details.PROPERTY_HIGH_DEF_AUDIO) != 0;
                        
                        rv.setTextViewText(R.id.tvNotifHd, "VoLTE");
                        rv.setViewVisibility(R.id.tvNotifHd, isHd ? View.VISIBLE : View.GONE);
                        rv.setTextViewText(R.id.tvNotifWifi, "VoWiFi");
                        rv.setViewVisibility(R.id.tvNotifWifi, isWifi ? View.VISIBLE : View.GONE);
                    }
                }

                CallAudioState audioState = getCallAudioState();
                String speakerLabelText;
                int speakerIcon;
                String muteLabelText;
                int muteIcon = R.drawable.ic_mic;
                
                if (audioState != null && audioState.getRoute() == CallAudioState.ROUTE_SPEAKER) {
                    rv.setInt(R.id.btnNotifSpeaker, "setBackgroundResource", R.drawable.blue_circle);
                    speakerLabelText = "Speaker On";
                    speakerIcon = R.drawable.ic_speaker;
                } else if (audioState != null && audioState.getRoute() == CallAudioState.ROUTE_BLUETOOTH) {
                    speakerIcon = R.drawable.ic_bluetooth;
                    rv.setImageViewResource(R.id.btnNotifSpeaker, R.drawable.ic_bluetooth);
                    rv.setInt(R.id.btnNotifSpeaker, "setBackgroundResource", R.drawable.blue_circle);
                    speakerLabelText = "Bluetooth";
                } else {
                    speakerIcon = R.drawable.ic_speaker;
                    rv.setImageViewResource(R.id.btnNotifSpeaker, R.drawable.ic_speaker);
                    rv.setInt(R.id.btnNotifSpeaker, "setBackgroundResource", 0);
                    speakerLabelText = "Speaker";
                }

                if (audioState != null && audioState.isMuted()) {
                    rv.setInt(R.id.btnNotifMute, "setBackgroundResource", R.drawable.gray_circle);
                    muteLabelText = "Unmute";
                } else {
                    rv.setInt(R.id.btnNotifMute, "setBackgroundResource", 0);
                    muteLabelText = "Mute";
                }
                
                long connectTime = (CallManager.sCurrentCall != null) ? CallManager.sCurrentCall.getDetails().getConnectTimeMillis() : 0;
                if (connectTime > 0) {
                    long base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - connectTime);
                    rv.setChronometer(R.id.chronometerNotif, base, null, true);
                }

                NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_DEFAULT);

                builder.setSmallIcon(R.drawable.ic_phone)
                        .setCustomContentView(rv)
                        .setCustomBigContentView(rv)
                        .setContentIntent(pi)
                        .setOngoing(true)
                        .setCategory(NotificationCompat.CATEGORY_CALL)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setOnlyAlertOnce(true);

                // Add Speaker and Mute actions explicitly for tray visibility
                builder.addAction(speakerIcon, speakerLabelText, speakerPi);
                builder.addAction(muteIcon, muteLabelText, mutePi);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    androidx.core.app.Person caller = new androidx.core.app.Person.Builder().setName(finalName).setImportant(true).build();
                    builder.setStyle(NotificationCompat.CallStyle.forOngoingCall(caller, endPi));
                } else {
                    builder.addAction(R.drawable.ic_phone_end, "End", endPi);
                }

                startForegroundCompat(builder.build());
            } catch (Exception e) { Log.e("InCallServiceImpl", "Active notif crash", e); }
        });
    }

    private void startForegroundCompat(Notification n) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
        } catch (Exception e) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, n);
        }
    }

    private void startCallActivity(Class<?> activityClass, String number, String name) {
        try {
            Intent intent = new Intent(this, activityClass);
            intent.putExtra("EXTRA_NUMBER", number);
            if (name != null) intent.putExtra("EXTRA_NAME", name);
            
            // Standard flagship flags
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK 
                          | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT 
                          | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            
            Log.d("InCallServiceImpl", "Launching: " + activityClass.getSimpleName());
            startActivity(intent);
        } catch (Exception e) {
            Log.e("InCallServiceImpl", "Failed to launch activity", e);
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel highChannel = new NotificationChannel(CHANNEL_ID_HIGH, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH);
                highChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                highChannel.enableVibration(false); // Silent & No Vibration
                highChannel.setSound(null, null);
                nm.createNotificationChannel(highChannel);

                // Use IMPORTANCE_DEFAULT for buttons to stay functional
                NotificationChannel defaultChannel = new NotificationChannel(CHANNEL_ID_DEFAULT, "Active Calls", NotificationManager.IMPORTANCE_DEFAULT);
                defaultChannel.enableVibration(false); // No Vibration
                defaultChannel.setSound(null, null);
                nm.createNotificationChannel(defaultChannel);
            }
        }
    }

    private boolean isAppInForeground() {
        android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        List<android.app.ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes != null) {
            for (android.app.ActivityManager.RunningAppProcessInfo p : processes) {
                if (p.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    for (String pkg : p.pkgList) { if (pkg.equals(getPackageName())) return true; }
                }
            }
        }
        return false;
    }

    private boolean isDeviceAtHome() {
        android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        List<android.app.ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (tasks != null && !tasks.isEmpty()) {
            android.content.ComponentName top = tasks.get(0).topActivity;
            if (top == null) return false;
            Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo res = getPackageManager().resolveActivity(homeIntent, 0);
            return res != null && res.activityInfo != null && top.getPackageName().equals(res.activityInfo.packageName);
        }
        return false;
    }

    private String getNumberFromCall(Call call) {
        try {
            if (call != null && call.getDetails() != null && call.getDetails().getHandle() != null) {
                return call.getDetails().getHandle().getSchemeSpecificPart();
            }
        } catch (Exception ignored) {}
        return "Unknown";
    }

    private void saveCallToLocalLog(Call call) {
        if (call == null || call.getDetails() == null) return;
        String number = getNumberFromCall(call);
        long startTime = call.getDetails().getCreationTimeMillis();
        long connectTime = call.getDetails().getConnectTimeMillis();
        long duration = (connectTime > 0) ? (System.currentTimeMillis() - connectTime) / 1000 : 0;
        int callType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int direction = call.getDetails().getCallDirection();
            callType = (direction == Call.Details.DIRECTION_OUTGOING) ? 2 : (connectTime <= 0 ? 3 : 1);
        } else {
            callType = (connectTime <= 0) ? 3 : 1;
        }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String name = Utils.queryContactName(this, number);
            RecentModel recent = new RecentModel(number, name != null ? name : number, startTime, duration, callType, false, "");
            AppDatabase.getDatabase(this).recentDao().insert(recent);
        });
    }
}
