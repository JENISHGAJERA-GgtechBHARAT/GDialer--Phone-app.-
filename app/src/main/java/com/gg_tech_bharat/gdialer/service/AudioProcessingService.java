package com.gg_tech_bharat.gdialer.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.gg_tech_bharat.gdialer.OngoingCallActivity;
import com.gg_tech_bharat.gdialer.R;
import com.gg_tech_bharat.gdialer.pipeline.AudioPipeline;

/**
 * Foreground Service that hosts the real-time AudioPipeline.
 * Declares MICROPHONE foreground service types to record and suppress noise in the background during calls.
 */
public class AudioProcessingService extends Service {

    private static final String TAG = "AudioProcessingService";
    private static final String CHANNEL_ID = "ai_noise_cancellation_channel";
    private static final int NOTIFICATION_ID = 303;

    public static final String ACTION_START_AI = "com.gg_tech_bharat.gdialer.START_AI_PROCESSING";
    public static final String ACTION_STOP_AI = "com.gg_tech_bharat.gdialer.STOP_AI_PROCESSING";

    private static AudioPipeline sAudioPipeline;
    private static boolean sIsServiceRunning = false;

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public AudioProcessingService getService() {
            return AudioProcessingService.this;
        }
    }

    public static boolean isServiceRunning() {
        return sIsServiceRunning;
    }

    public static AudioPipeline getAudioPipeline() {
        return sAudioPipeline;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        if (sAudioPipeline == null) {
            sAudioPipeline = new AudioPipeline(this);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_AI.equals(action)) {
                startForegroundNotification();
                startPipeline();
            } else if (ACTION_STOP_AI.equals(action)) {
                stopPipeline();
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    private void startForegroundNotification() {
        Intent notificationIntent = new Intent(this, OngoingCallActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String modelInfo = sAudioPipeline != null ? sAudioPipeline.getActiveModelInfo() : "Real-time Suppression";

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AI Voice Noise Cancellation")
                .setContentText("Active model: " + modelInfo)
                .setSmallIcon(R.drawable.ic_phone)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            sIsServiceRunning = true;
            Log.d(TAG, "AudioProcessingService running in foreground.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start AudioProcessingService in foreground.", e);
        }
    }

    private void startPipeline() {
        if (sAudioPipeline != null) {
            sAudioPipeline.start();
        }
    }

    private void stopPipeline() {
        if (sAudioPipeline != null) {
            sAudioPipeline.stop();
        }
        sIsServiceRunning = false;
    }

    @Override
    public void onDestroy() {
        stopPipeline();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "AI Voice Noise Cancellation",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background active noise suppression details during calls.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
