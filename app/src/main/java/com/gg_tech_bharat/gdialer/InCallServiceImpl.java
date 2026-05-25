package com.gg_tech_bharat.gdialer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
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
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;

public class InCallServiceImpl extends InCallService {

    public static InCallServiceImpl sInstance;
    private static final String CHANNEL_ID_HIGH = "incoming_calls_high";
    private static final String CHANNEL_ID_DEFAULT = "ongoing_calls_default";
    private static final String ACTION_END_CALL = "com.gg_tech_bharat.gdialer.ACTION_END_CALL";
    private static final String ACTION_ANSWER_CALL = "com.gg_tech_bharat.gdialer.ACTION_ANSWER_CALL";
    private static final String ACTION_MUTE = "com.gg_tech_bharat.gdialer.ACTION_MUTE";
    private static final String ACTION_SPEAKER = "com.gg_tech_bharat.gdialer.ACTION_SPEAKER";
    private static final int NOTIFICATION_ID = 101;

    private boolean isAnsweredViaNotification = false;

    private final BroadcastReceiver actionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d("InCallServiceImpl", "Broadcast action received: " + action);
            if (CallManager.sCurrentCall == null) return;
            
            if (ACTION_END_CALL.equals(action)) {
                CallManager.sCurrentCall.disconnect();
            } else if (ACTION_ANSWER_CALL.equals(action)) {
                isAnsweredViaNotification = true;
                int videoState = intent.getIntExtra("VIDEO_STATE", VideoProfile.STATE_AUDIO_ONLY);
                CallManager.sCurrentCall.answer(videoState);
            } else if (ACTION_MUTE.equals(action)) {
                CallAudioState state = getCallAudioState();
                if (state != null) setMuted(!state.isMuted());
            } else if (ACTION_SPEAKER.equals(action)) {
                CallAudioState state = getCallAudioState();
                if (state != null) {
                    int newRoute = (state.getRoute() == CallAudioState.ROUTE_SPEAKER) ? CallAudioState.ROUTE_EARPIECE : CallAudioState.ROUTE_SPEAKER;
                    setAudioRoute(newRoute);
                }
            }
        }
    };

    private final Call.Callback callCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            super.onStateChanged(call, state);
            Log.d("InCallServiceImpl", "Call State Changed: " + state);
            CallManager.updateState(state);
            if (state == Call.STATE_DISCONNECTED) {
                try { Utils.vibrateDevice(InCallServiceImpl.this, 100); } catch (Exception ignored) {} 
                saveCallToLocalLog(call);
                cleanupCall(call);
            } else if (state == Call.STATE_ACTIVE) {
                try { Utils.vibrateDevice(InCallServiceImpl.this, 80); } catch (Exception ignored) {}
                handleCallState(call, state);
            } else {
                handleCallState(call, state);
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
                            Log.d("InCallServiceImpl", "Accepting video request...");
                            videoCall.sendSessionModifyResponse(new VideoProfile(videoState));
                        }
                    }
                    @Override public void onSessionModifyResponseReceived(int status, VideoProfile requestedProfile, VideoProfile responseProfile) {
                        Log.d("InCallServiceImpl", "Video response received: " + status);
                        // Force a UI refresh in activity
                        Intent intent = new Intent("com.gg_tech_bharat.gdialer.VIDEO_STATE_CHANGED");
                        intent.setPackage(getPackageName());
                        sendBroadcast(intent);
                    }
                    @Override public void onCallSessionEvent(int event) {}
                    @Override public void onPeerDimensionsChanged(int width, int height) {
                        Log.d("InCallServiceImpl", "Peer dimensions: " + width + "x" + height);
                    }
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
        Log.d("InCallServiceImpl", "Audio State Changed: " + audioState);
        CallManager.updateAudioState(audioState);
        if (CallManager.sCurrentCall != null && CallManager.sCurrentCall.getState() == Call.STATE_ACTIVE) {
            String number = getNumberFromCall(CallManager.sCurrentCall);
            showActiveCallNotification(number);
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
        Log.d("InCallServiceImpl", "onCallAdded");
        isAnsweredViaNotification = false;
        CallManager.addCall(call);
        call.registerCallback(callCallback);
        handleCallState(call, call.getState());
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        Log.d("InCallServiceImpl", "onCallRemoved");
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
        Log.d("InCallServiceImpl", "onAllCallsEnded");
        RecordingService.stop(this); 
        isAnsweredViaNotification = false;
        
        // Ensure audio routing is reset to earpiece
        setAudioRoute(CallAudioState.ROUTE_EARPIECE);
        setMuted(false);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        
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
            if (state == Call.STATE_RINGING) {
                showRingingNotification(number, call.getDetails().getVideoState());
            } else if (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING) {
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    String name = number;
                    ContactDao dao = AppDatabase.getDatabase(this).contactDao();
                    String normalized = Utils.normalizePhoneNumber(number);
                    ContactModel contact = dao.getContactByNormalizedNumber(normalized);
                    if (contact == null && normalized.length() >= 10) {
                        contact = dao.getContactByLastDigits(normalized.substring(normalized.length() - 10));
                    }
                    
                    if (contact != null) name = contact.getName();
                    final String finalName = name;
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        startCallActivity(OngoingCallActivity.class, number, finalName);
                        showActiveCallNotification(number);
                    });
                });
            } else if (state == Call.STATE_ACTIVE) {
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    String name = number;
                    ContactDao dao = AppDatabase.getDatabase(this).contactDao();
                    String normalized = Utils.normalizePhoneNumber(number);
                    ContactModel contact = dao.getContactByNormalizedNumber(normalized);
                    if (contact == null && normalized.length() >= 10) {
                        contact = dao.getContactByLastDigits(normalized.substring(normalized.length() - 10));
                    }
                    
                    if (contact != null) name = contact.getName();
                    final String finalName = name;
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        startCallActivity(OngoingCallActivity.class, number, finalName);
                        showActiveCallNotification(number);
                    });
                });
            }
        } catch (Exception e) {
            Log.e("InCallServiceImpl", "Error in handleCallState", e);
        }
    }

    private void showRingingNotification(String number, int videoState) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String name = number;
            ContactDao dao = AppDatabase.getDatabase(this).contactDao();
            String normalized = Utils.normalizePhoneNumber(number);
            ContactModel contact = dao.getContactByNormalizedNumber(normalized);
            if (contact == null && normalized.length() >= 10) {
                contact = dao.getContactByLastDigits(normalized.substring(normalized.length() - 10));
            }
            
            if (contact != null) name = contact.getName();
            final String finalName = name;
            
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                Intent intent = new Intent(this, IncomingCallActivity.class)
                        .putExtra("EXTRA_NUMBER", number)
                        .putExtra("EXTRA_NAME", finalName);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
                
                // IMPORTANT: High priority PendingIntent for fullScreenIntent
                PendingIntent fullScreenPi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                
                Intent answerIntent = new Intent(ACTION_ANSWER_CALL);
                answerIntent.setPackage(getPackageName());
                answerIntent.putExtra("VIDEO_STATE", videoState);
                PendingIntent answerPi = PendingIntent.getBroadcast(this, 2, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                
                Intent declineIntent = new Intent(ACTION_END_CALL);
                declineIntent.setPackage(getPackageName());
                PendingIntent declinePi = PendingIntent.getBroadcast(this, 3, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                String safeName = (finalName != null) ? finalName : number;

                Notification notification;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Person caller = new Person.Builder().setName(safeName).setImportant(true).build();
                    notification = new Notification.Builder(this, CHANNEL_ID_HIGH)
                            .setSmallIcon(Icon.createWithResource(this, R.drawable.ic_phone))
                            .setFullScreenIntent(fullScreenPi, true)
                            .setOngoing(true)
                            .setCategory(Notification.CATEGORY_CALL)
                            .setStyle(Notification.CallStyle.forIncomingCall(caller, declinePi, answerPi))
                            .build();
                } else {
                    notification = new NotificationCompat.Builder(this, CHANNEL_ID_HIGH)
                            .setSmallIcon(R.drawable.ic_phone)
                            .setContentTitle("Incoming Call")
                            .setContentText(safeName)
                            .setFullScreenIntent(fullScreenPi, true)
                            .setOngoing(true)
                            .setPriority(NotificationCompat.PRIORITY_MAX)
                            .setCategory(NotificationCompat.CATEGORY_CALL)
                            .addAction(R.drawable.ic_phone, "Answer", answerPi)
                            .addAction(R.drawable.ic_phone_end, "Decline", declinePi)
                            .build();
                }
                startForegroundCompat(notification);
            });
        });
    }

    private void showActiveCallNotification(String number) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String name = number;
            ContactDao dao = AppDatabase.getDatabase(this).contactDao();
            String normalized = Utils.normalizePhoneNumber(number);
            ContactModel contact = dao.getContactByNormalizedNumber(normalized);
            if (contact == null && normalized.length() >= 10) {
                contact = dao.getContactByLastDigits(normalized.substring(normalized.length() - 10));
            }
            
            if (contact != null) name = contact.getName();
            final String finalName = name;

            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
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

                RemoteViews rv = new RemoteViews(getPackageName(), R.layout.notification_ongoing_call);
                rv.setTextViewText(R.id.tvNotifOngoingName, finalName);
                rv.setOnClickPendingIntent(R.id.btnNotifEnd, endPi);
                rv.setOnClickPendingIntent(R.id.btnNotifMute, mutePi);
                rv.setOnClickPendingIntent(R.id.btnNotifSpeaker, speakerPi);

                // Update icons based on audio state
                CallAudioState audioState = getCallAudioState();
                if (audioState != null) {
                    if (audioState.getRoute() == CallAudioState.ROUTE_SPEAKER) {
                        rv.setInt(R.id.btnNotifSpeaker, "setBackgroundResource", R.drawable.blue_circle);
                    } else {
                        rv.setInt(R.id.btnNotifSpeaker, "setBackgroundResource", 0);
                    }

                    if (audioState.isMuted()) {
                        rv.setInt(R.id.btnNotifMute, "setBackgroundResource", R.drawable.gray_circle);
                    } else {
                        rv.setInt(R.id.btnNotifMute, "setBackgroundResource", 0);
                    }
                }
                
                // Start chronometer in notification
                long connectTime = (CallManager.sCurrentCall != null) ? CallManager.sCurrentCall.getDetails().getConnectTimeMillis() : 0;
                if (connectTime > 0) {
                    long base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - connectTime);
                    rv.setChronometer(R.id.chronometerNotif, base, null, true);
                }

                Notification notification;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Person caller = new Person.Builder().setName(finalName).setImportant(true).build();
                    notification = new Notification.Builder(this, CHANNEL_ID_DEFAULT)
                            .setSmallIcon(Icon.createWithResource(this, R.drawable.ic_phone))
                            .setCustomContentView(rv)
                            .setContentIntent(pi)
                            .setOngoing(true)
                            .setCategory(Notification.CATEGORY_CALL)
                            .setStyle(Notification.CallStyle.forOngoingCall(caller, endPi))
                            .build();
                } else {
                    notification = new NotificationCompat.Builder(this, CHANNEL_ID_DEFAULT)
                            .setSmallIcon(R.drawable.ic_phone)
                            .setCustomContentView(rv)
                            .setOngoing(true)
                            .setContentIntent(pi)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setCategory(NotificationCompat.CATEGORY_CALL)
                            .build();
                }
                startForegroundCompat(notification);
            });
        });
    }

    private void startForegroundCompat(Notification n) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private void startCallActivity(Class<?> activityClass, String number, String name) {
        Intent intent = new Intent(this, activityClass);
        intent.putExtra("EXTRA_NUMBER", number);
        if (name != null) intent.putExtra("EXTRA_NAME", name);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK 
                      | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT 
                      | Intent.FLAG_ACTIVITY_SINGLE_TOP
                      | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        
        // Premium Snappy Transition for smooth app feeling
        android.app.ActivityOptions options = android.app.ActivityOptions.makeCustomAnimation(this, 
                R.anim.premium_fade_in, R.anim.premium_fade_out);
        startActivity(intent, options.toBundle());
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                // Channel for Incoming Pop-ups
                NotificationChannel highChannel = new NotificationChannel(CHANNEL_ID_HIGH, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH);
                highChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                highChannel.enableVibration(true);
                highChannel.setSound(null, null);
                nm.createNotificationChannel(highChannel);

                // Channel for Ongoing Pill
                NotificationChannel defaultChannel = new NotificationChannel(CHANNEL_ID_DEFAULT, "Active Calls", NotificationManager.IMPORTANCE_LOW);
                nm.createNotificationChannel(defaultChannel);
            }
        }
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
            if (direction == Call.Details.DIRECTION_OUTGOING) {
                callType = 2; // Outgoing
            } else {
                callType = (connectTime <= 0) ? 3 : 1;
            }
        } else {
            // Fallback for older versions: use a simple logic or default to incoming
            // usually dialers handle this by tracking the start action
            callType = (connectTime <= 0) ? 3 : 1;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            String name = number;
            ContactDao dao = AppDatabase.getDatabase(this).contactDao();
            String normalized = Utils.normalizePhoneNumber(number);
            ContactModel contact = dao.getContactByNormalizedNumber(normalized);
            if (contact == null && normalized.length() >= 10) {
                contact = dao.getContactByLastDigits(normalized.substring(normalized.length() - 10));
            }
            if (contact != null) name = contact.getName();
            
            RecentModel recent = new RecentModel(number, name, startTime, duration, callType, false, "");
            AppDatabase.getDatabase(this).recentDao().insert(recent);
            Log.d("InCallServiceImpl", "Call saved to local log: " + name);
        });
    }
}
