package com.gg_tech_bharat.gdialer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.IOException;

public class RecordingService extends Service {

    private static final String TAG = "RecordingService";
    private static final String CHANNEL_ID = "call_recordings_channel";
    private static final int NOTIFICATION_ID = 202;

    public static final String ACTION_START_RECORDING = "com.gg_tech_bharat.gdialer.START_RECORDING";
    public static final String ACTION_STOP_RECORDING = "com.gg_tech_bharat.gdialer.STOP_RECORDING";
    public static final String EXTRA_PHONE_NUMBER = "extra_phone_number";
    public static final String EXTRA_CALLER_NAME = "extra_caller_name";

    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private String currentFilePath;
    private String phoneNumber;
    private String callerName;
    private long startTime;

    private static boolean sIsServiceRunning = false;

    public static boolean isServiceRunning() {
        return sIsServiceRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_RECORDING.equals(action)) {
                phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER);
                callerName = intent.getStringExtra(EXTRA_CALLER_NAME);
                if (phoneNumber == null) phoneNumber = "Unknown";
                if (callerName == null) callerName = phoneNumber;
                
                startForegroundNotification();
                startRecording();
            } else if (ACTION_STOP_RECORDING.equals(action)) {
                stopRecording();
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

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Recording Call")
                .setContentText("Recording conversation with " + callerName)
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
        } catch (Exception e) {
            Log.e(TAG, "Failed to start service in foreground", e);
        }
    }

    private void startRecording() {
        if (isRecording) return;

        File dir = new File(getFilesDir(), "recordings");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String safeNumber = phoneNumber.replaceAll("[^0-9+]", "");
        if (safeNumber.isEmpty()) {
            safeNumber = "Unknown";
        }
        String fileName = "REC_" + safeNumber + "_" + System.currentTimeMillis() + ".m4a";
        File file = new File(dir, fileName);
        currentFilePath = file.getAbsolutePath();

        if (tryStartRecorder(file)) {
            isRecording = true;
            startTime = System.currentTimeMillis();
            Log.d(TAG, "Call recording started, saving to: " + currentFilePath);
        } else {
            Log.e(TAG, "Failed to start call recording after trying all source/format combinations");
            stopSelf();
        }
    }

    private boolean tryStartRecorder(File file) {
        int[] sources = {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.MIC
        };
        
        int[] formats = {
            MediaRecorder.OutputFormat.MPEG_4,
            MediaRecorder.OutputFormat.THREE_GPP,
            MediaRecorder.OutputFormat.AMR_NB
        };
        
        int[] encoders = {
            MediaRecorder.AudioEncoder.AAC,
            MediaRecorder.AudioEncoder.AMR_NB,
            MediaRecorder.AudioEncoder.DEFAULT
        };

        for (int source : sources) {
            for (int format : formats) {
                for (int encoder : encoders) {
                    try {
                        mediaRecorder = new MediaRecorder();
                        mediaRecorder.setAudioSource(source);
                        mediaRecorder.setOutputFormat(format);
                        mediaRecorder.setAudioEncoder(encoder);
                        mediaRecorder.setOutputFile(file.getAbsolutePath());
                        mediaRecorder.prepare();
                        mediaRecorder.start();
                        
                        Log.d(TAG, "RecordingService started successfully: source=" + source + ", format=" + format + ", encoder=" + encoder);
                        return true;
                    } catch (Throwable t) {
                        Log.w(TAG, "RecordingService config failed: source=" + source + ", format=" + format + ", encoder=" + encoder + ". Error: " + t.getMessage());
                        try {
                            mediaRecorder.release();
                        } catch (Exception ignored) {}
                        mediaRecorder = null;
                    }
                }
            }
        }
        return false;
    }

    private void stopRecording() {
        if (isRecording && mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                Log.d(TAG, "Recording stopped successfully.");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MediaRecorder (call might have been too short)", e);
                // Clean up file if empty or corrupted
                try {
                    new File(currentFilePath).delete();
                } catch (Exception ignored) {}
            } finally {
                mediaRecorder.release();
                mediaRecorder = null;
                isRecording = false;
                sIsServiceRunning = false;
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopRecording();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Call Recordings",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows active call recording notifications");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
