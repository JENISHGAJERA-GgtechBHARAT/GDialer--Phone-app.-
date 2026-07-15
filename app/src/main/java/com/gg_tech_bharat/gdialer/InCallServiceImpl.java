package com.gg_tech_bharat.gdialer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
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
import androidx.core.content.ContextCompat;
import android.Manifest;

import java.util.List;
import java.util.Objects;

public class InCallServiceImpl extends InCallService {

    public static InCallServiceImpl sInstance;
    private static final String CHANNEL_ID_HIGH = "incoming_calls_v20";
    private static final String CHANNEL_ID_DEFAULT = "ongoing_calls_v20";
    private static final String ACTION_END_CALL = "com.gg_tech_bharat.gdialer.ACTION_END_CALL";
    private static final String ACTION_ANSWER_CALL = "com.gg_tech_bharat.gdialer.ACTION_ANSWER_CALL";
    private static final String ACTION_MUTE = "com.gg_tech_bharat.gdialer.ACTION_MUTE";
    private static final String ACTION_SPEAKER = "com.gg_tech_bharat.gdialer.ACTION_SPEAKER";
    private static final int NOTIFICATION_ID = 101;

    private String currentRingingCallId = null;
    private android.os.PowerManager.WakeLock proximityWakeLock;
    private ToneGenerator toneGenerator;

    private final BroadcastReceiver actionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            try {
                switch (action) {
                    case ACTION_END_CALL:
                        Call ringing = CallManager.getRingingCall();
                        if (ringing != null) ringing.disconnect();
                        else if (CallManager.sCurrentCall != null) CallManager.sCurrentCall.disconnect();
                        break;
                    case ACTION_ANSWER_CALL:
                        int vState = intent.getIntExtra("VIDEO_STATE", VideoProfile.STATE_AUDIO_ONLY);
                        if (CallManager.getRingingCall() != null) CallManager.getRingingCall().answer(vState);
                        else if (CallManager.sCurrentCall != null && CallManager.sCurrentCall.getState() == Call.STATE_RINGING) CallManager.sCurrentCall.answer(vState);
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
            } catch (Exception e) { Log.e("InCallServiceImpl", "Action error", e); }
        }
    };

    private final Call.Callback callCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            super.onStateChanged(call, state);
            CallManager.updateState(state);
            
            if (state == Call.STATE_ACTIVE) {
                playTone(ToneGenerator.TONE_PROP_BEEP, 100);
                handleCallState(call, state);
                updateProximityWakeLock();
                checkAndAutoRecordCall(call);
            } else if (state == Call.STATE_DISCONNECTED) {
                playTone(ToneGenerator.TONE_PROP_PROMPT, 200);
                saveCallToLocalLog(call);
                cleanupCall(call);
                updateProximityWakeLock();
            } else {
                handleCallState(call, state);
                updateProximityWakeLock();
            }
        }
        @Override public void onDetailsChanged(Call call, Call.Details details) {
            super.onDetailsChanged(call, details);
            if (call.getState() == Call.STATE_ACTIVE || call.getState() == Call.STATE_DIALING || call.getState() == Call.STATE_CONNECTING) {
                showActiveCallNotification(getNumberFromCall(call));
            }
        }
    };

    private void playTone(int toneType, int durationMs) {
        try {
            if (toneGenerator == null) toneGenerator = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80);
            toneGenerator.startTone(toneType, durationMs);
        } catch (Exception e) { Log.e("InCallServiceImpl", "Tone error", e); }
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        super.onCallAudioStateChanged(audioState);
        CallManager.updateAudioState(audioState);
        Call current = CallManager.sCurrentCall;
        if (current != null && current.getState() != Call.STATE_DISCONNECTED && current.getState() != Call.STATE_RINGING) {
            showActiveCallNotification(getNumberFromCall(current));
        }
        updateProximityWakeLock();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        setupProximitySensor();
        createNotificationChannels();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_END_CALL); filter.addAction(ACTION_ANSWER_CALL); filter.addAction(ACTION_MUTE); filter.addAction(ACTION_SPEAKER);
        try { androidx.core.content.ContextCompat.registerReceiver(this, actionReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED); } catch (Exception ignored) {}
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        if (call == null) return;
        CallManager.addCall(call);
        call.registerCallback(callCallback);
        handleCallState(call, call.getState());
        if (call.getState() == Call.STATE_ACTIVE) checkAndAutoRecordCall(call);
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        cleanupCall(call);
    }

    private void cleanupCall(Call call) {
        if (call != null) {
            call.unregisterCallback(callCallback);
            CallManager.removeCall(call);
        }
        if (CallManager.getCalls().isEmpty()) onAllCallsEnded();
    }

    private void onAllCallsEnded() {
        setAudioRoute(CallAudioState.ROUTE_EARPIECE);
        setMuted(false);
        if (proximityWakeLock != null && proximityWakeLock.isHeld()) try { proximityWakeLock.release(); } catch (Exception ignored) {}
        try { Intent recordIntent = new Intent(this, RecordingService.class); recordIntent.setAction(RecordingService.ACTION_STOP_RECORDING); startService(recordIntent); } catch (Exception ignored) {}
        stopForeground(STOP_FOREGROUND_REMOVE);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
        Intent intent = new Intent("com.gg_tech_bharat.gdialer.CALL_DISCONNECTED");
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
        if (toneGenerator != null) { toneGenerator.release(); toneGenerator = null; }
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        if (proximityWakeLock != null && proximityWakeLock.isHeld()) try { proximityWakeLock.release(); } catch (Exception ignored) {}
        try { unregisterReceiver(actionReceiver); } catch (Exception ignored) {}
        if (toneGenerator != null) { toneGenerator.release(); toneGenerator = null; }
        super.onDestroy();
    }

    private void handleCallState(Call call, int state) {
        try {
            String number = getNumberFromCall(call);
            if (state == Call.STATE_RINGING) {
                if (Objects.equals(number, currentRingingCallId)) return;
                currentRingingCallId = number;
                android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                boolean isLocked = km != null && km.isKeyguardLocked();
                boolean isHome = isDeviceAtHome();
                boolean isForeground = isAppInForeground();
                String name = getContactName(number);
                if (isLocked || isHome || isForeground || CallManager.getCalls().size() > 1) {
                    startCallActivity(IncomingCallActivity.class, number, name);
                }
                showRingingNotification(number, call.getDetails().getVideoState(), CHANNEL_ID_HIGH);
            } else if (state == Call.STATE_ACTIVE || state == Call.STATE_DIALING || state == Call.STATE_CONNECTING) {
                currentRingingCallId = null;
                String name = getContactName(number);
                startCallActivity(OngoingCallActivity.class, number, name);
                showActiveCallNotification(number);
            }
        } catch (Exception e) { Log.e("InCallServiceImpl", "State error", e); }
    }

    private String getContactName(String number) {
        ContactModel contact = ContactCache.getContactByNumber(number);
        if (contact != null) return contact.getName();
        return Utils.queryContactName(this, number);
    }

    private void showRingingNotification(String number, int videoState, String channelId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String name = Utils.queryContactName(this, number);
            final String finalName = (name != null) ? name : number;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                try {
                    Intent intent = new Intent(this, IncomingCallActivity.class).putExtra("EXTRA_NUMBER", number).putExtra("EXTRA_NAME", finalName);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
                    PendingIntent fullScreenPi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    Intent answerIntent = new Intent(ACTION_ANSWER_CALL).setPackage(getPackageName()).putExtra("VIDEO_STATE", videoState);
                    PendingIntent answerPi = PendingIntent.getBroadcast(this, 2, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    Intent declineIntent = new Intent(ACTION_END_CALL).setPackage(getPackageName());
                    PendingIntent declinePi = PendingIntent.getBroadcast(this, 3, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId);
                    builder.setSmallIcon(R.drawable.ic_phone).setContentTitle("Incoming Call").setContentText(finalName)
                            .setContentIntent(fullScreenPi).setFullScreenIntent(fullScreenPi, true).setOngoing(true)
                            .setCategory(NotificationCompat.CATEGORY_CALL).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                            .setAutoCancel(false).setPriority(NotificationCompat.PRIORITY_MAX);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        androidx.core.app.Person person = new androidx.core.app.Person.Builder().setName(finalName).setImportant(true).build();
                        builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(person, declinePi, answerPi));
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
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String name = getContactName(number);
            final String finalName = (name != null) ? name : number;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                try {
                    if (CallManager.sCurrentCall == null || CallManager.sCurrentCall.getState() == Call.STATE_DISCONNECTED) return;
                    Intent intent = new Intent(this, OngoingCallActivity.class).putExtra("EXTRA_NUMBER", number).putExtra("EXTRA_NAME", finalName);
                    PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    Intent endPiIntent = new Intent(ACTION_END_CALL).setPackage(getPackageName());
                    PendingIntent endPi = PendingIntent.getBroadcast(this, 1, endPiIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    Intent mutePiIntent = new Intent(ACTION_MUTE).setPackage(getPackageName());
                    PendingIntent mutePi = PendingIntent.getBroadcast(this, 4, mutePiIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    Intent speakerPiIntent = new Intent(ACTION_SPEAKER).setPackage(getPackageName());
                    PendingIntent speakerPi = PendingIntent.getBroadcast(this, 5, speakerPiIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                    RemoteViews rv = new RemoteViews(getPackageName(), R.layout.notification_ongoing_call);
                    rv.setTextViewText(R.id.tvNotifOngoingName, finalName);
                    rv.setOnClickPendingIntent(R.id.btnNotifEnd, endPi);
                    rv.setOnClickPendingIntent(R.id.btnNotifMute, mutePi);
                    rv.setOnClickPendingIntent(R.id.btnNotifSpeaker, speakerPi);

                    long connectTime = (CallManager.sCurrentCall != null) ? CallManager.sCurrentCall.getDetails().getConnectTimeMillis() : 0;
                    if (connectTime > 0) rv.setChronometer(R.id.chronometerNotif, SystemClock.elapsedRealtime() - (System.currentTimeMillis() - connectTime), null, true);

                    NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID_DEFAULT);
                    b.setSmallIcon(R.drawable.ic_phone).setContentIntent(pi).setOngoing(true).setCategory(NotificationCompat.CATEGORY_CALL)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setOnlyAlertOnce(true).setCustomContentView(rv).setCustomBigContentView(rv);
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        androidx.core.app.Person person = new androidx.core.app.Person.Builder().setName(finalName).setImportant(true).build();
                        b.setStyle(NotificationCompat.CallStyle.forOngoingCall(person, endPi));
                    }
                    startForegroundCompat(b.build());
                } catch (Exception e) { Log.e("InCallServiceImpl", "Active notif crash", e); }
            });
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
            Intent intent = new Intent(this, activityClass).putExtra("EXTRA_NUMBER", number);
            if (name != null) intent.putExtra("EXTRA_NAME", name);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel high = new NotificationChannel(CHANNEL_ID_HIGH, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH);
                high.enableVibration(true); high.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(high);
                NotificationChannel def = new NotificationChannel(CHANNEL_ID_DEFAULT, "Active Calls", NotificationManager.IMPORTANCE_LOW);
                def.setShowBadge(false);
                nm.createNotificationChannel(def);
            }
        }
    }

    private boolean isAppInForeground() {
        android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        List<android.app.ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes != null) for (android.app.ActivityManager.RunningAppProcessInfo p : processes) if (p.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) for (String pkg : p.pkgList) if (pkg.equals(getPackageName())) return true;
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
            if (call != null && call.getDetails() != null && call.getDetails().getHandle() != null) return call.getDetails().getHandle().getSchemeSpecificPart();
        } catch (Exception ignored) {}
        return "Unknown";
    }

    private void saveCallToLocalLog(Call call) {
        if (call == null || call.getDetails() == null) return;
        final String num = getNumberFromCall(call);
        final long sTime = call.getDetails().getCreationTimeMillis();
        long cTime = call.getDetails().getConnectTimeMillis();
        final long dur = (cTime > 0) ? (System.currentTimeMillis() - cTime) / 1000 : 0;
        final int cType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int dir = call.getDetails().getCallDirection();
            cType = (dir == Call.Details.DIRECTION_OUTGOING) ? 2 : (cTime <= 0 ? 3 : 1);
        } else cType = (cTime <= 0) ? 3 : 1;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String name = Utils.queryContactName(this, num);
            RecentModel recent = new RecentModel(num, name != null ? name : num, sTime, dur, cType, false, "");
            AppDatabase.getDatabase(this).recentDao().insert(recent);
        });
    }

    private void setupProximitySensor() {
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isWakeLockLevelSupported(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) proximityWakeLock = pm.newWakeLock(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "GDialer::ProximityWakeLock");
        } catch (Exception ignored) {}
    }

    private void updateProximityWakeLock() {
        try {
            if (proximityWakeLock == null) return;
            boolean active = false;
            for (Call c : CallManager.getCalls()) if (c.getState() == Call.STATE_ACTIVE || c.getState() == Call.STATE_DIALING || c.getState() == Call.STATE_CONNECTING) { active = true; break; }
            CallAudioState audio = getCallAudioState();
            boolean earpiece = (audio == null || audio.getRoute() == CallAudioState.ROUTE_EARPIECE || audio.getRoute() == CallAudioState.ROUTE_WIRED_HEADSET);
            if (active && earpiece) { if (!proximityWakeLock.isHeld()) proximityWakeLock.acquire(10 * 60 * 1000L); }
            else { if (proximityWakeLock.isHeld()) proximityWakeLock.release(); }
        } catch (Exception ignored) {}
    }

    private void checkAndAutoRecordCall(Call call) {
        android.content.SharedPreferences prefs = getSharedPreferences("DialerPrefs", MODE_PRIVATE);
        if (!prefs.getBoolean("auto_record_enabled", false)) return;
        String number = getNumberFromCall(call);
        String name = getContactName(number);
        String mode = prefs.getString("auto_record_mode", "all");
        boolean shouldRecord = "all".equals(mode) || ("unsaved".equals(mode) && (name == null || name.equals(number)));
        if (shouldRecord && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            Intent recordIntent = new Intent(this, RecordingService.class).setAction(RecordingService.ACTION_START_RECORDING).putExtra(RecordingService.EXTRA_PHONE_NUMBER, number).putExtra(RecordingService.EXTRA_CALLER_NAME, name != null ? name : number);
            try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(recordIntent); else startService(recordIntent); } catch (Exception ignored) {}
        }
    }

    public void silenceRingingNotification() {
        Call ringing = CallManager.getRingingCall();
        if (ringing != null) {
            String number = getNumberFromCall(ringing);
            showRingingNotification(number, ringing.getDetails().getVideoState(), CHANNEL_ID_HIGH);
        }
    }
}
