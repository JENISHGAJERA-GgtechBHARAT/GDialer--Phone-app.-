package com.gg_tech_bharat.gdialer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;

public class RecordingService extends Service {

    private static final String CHANNEL_ID = "CallRecordingChannel";
    private static final int NOTIFICATION_ID = 2002;
    private static final int SAVE_NOTIFICATION_ID = 2003;

    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private String outputFilePath;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if ("START_RECORDING".equals(action)) {
            String callerName = intent.getStringExtra("CALLER_NAME");
            // Delay to allow audio focus and routing to stabilize
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> startRecording(callerName), 2000);
        } else if ("STOP_RECORDING".equals(action)) {
            stopRecording();
        }
        return START_NOT_STICKY;
    }

    private synchronized void startRecording(String callerName) {
        if (isRecording) return;

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Recording Active")
                .setContentText("Recording call with " + (callerName != null ? callerName : "Unknown"))
                .setSmallIcon(R.drawable.ic_record)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        try {
            File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            File sampleDir = new File(musicDir, "Call Recording");
            if (!sampleDir.exists()) sampleDir.mkdirs();

            outputFilePath = sampleDir.getAbsolutePath() + "/Call_" + System.currentTimeMillis() + ".m4a";

            // Optimization for audible conversation:
            // 1. Force Audio Manager to IN_COMMUNICATION mode
            // 2. Use MediaRecorder with a small handshake delay
            android.media.AudioManager audioManager = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setMode(android.media.AudioManager.MODE_IN_COMMUNICATION);
            }

            // Delay recording start by 1.5 seconds for complete hardware stabilization
            new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try {
                    mediaRecorder = new MediaRecorder();
                    // Switched to VOICE_COMMUNICATION as a primary attempt for 2-way audio
                    // on some devices this is the only way to get both sides.
                    mediaRecorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION); 
                    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                    mediaRecorder.setAudioSamplingRate(48000); 
                    mediaRecorder.setAudioEncodingBitRate(256000); 
                    mediaRecorder.setOutputFile(outputFilePath);

                    mediaRecorder.prepare();
                    mediaRecorder.start();
                    isRecording = true;
                    Log.d("RecordingService", "VOICE_COMMUNICATION recording started: " + outputFilePath);
                    
                } catch (Exception ex) {
                    Log.e("RecordingService", "VOICE_COMMUNICATION failed, trying high-gain MIC", ex);
                    try {
                        if (mediaRecorder != null) mediaRecorder.release();
                        mediaRecorder = new MediaRecorder();
                        // CAMCORDER often has higher gain and bypasses noise cancellation
                        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                        mediaRecorder.setOutputFile(outputFilePath);
                        mediaRecorder.prepare();
                        mediaRecorder.start();
                        isRecording = true;
                    } catch (Exception fatal) {
                        Log.e("RecordingService", "Fatal audio error - trying raw MIC as last resort", fatal);
                        try {
                            if (mediaRecorder != null) mediaRecorder.release();
                            mediaRecorder = new MediaRecorder();
                            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                            mediaRecorder.setOutputFile(outputFilePath);
                            mediaRecorder.prepare();
                            mediaRecorder.start();
                            isRecording = true;
                        } catch (Exception extremelyFatal) {
                             Log.e("RecordingService", "All sources failed", extremelyFatal);
                        }
                    }
                }
            }, 1500);
            
        } catch (Exception e) {
            Log.e("RecordingService", "Setup failed", e);
            stopSelf();
        }
    }

    private synchronized void stopRecording() {
        if (!isRecording) return;
        isRecording = false;
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
            }
            mediaRecorder = null;
            Log.d("RecordingService", "Stopped recording: " + outputFilePath);
            
            showSaveNotification(outputFilePath);
            
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(android.net.Uri.fromFile(new File(outputFilePath)));
            sendBroadcast(mediaScanIntent);

            Intent intent = new Intent("com.gg_tech_bharat.gdialer.RECORDING_COMPLETED");
            intent.setPackage(getPackageName());
            intent.putExtra("RECORDING_PATH", outputFilePath);
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.e("RecordingService", "Error stopping recording", e);
            if (mediaRecorder != null) mediaRecorder.release();
            mediaRecorder = null;
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            stopSelf();
        }
    }

    private void showSaveNotification(String path) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Call Recorded")
                .setContentText("Saved to Audio/Call Recording")
                .setSmallIcon(R.drawable.ic_record)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
        nm.notify(SAVE_NOTIFICATION_ID, n);
    }

    @Override
    public void onDestroy() {
        if (isRecording) stopRecording();
        else if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Call Recording", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    public static void start(Context context, String callerName) {
        Intent intent = new Intent(context, RecordingService.class);
        intent.setAction("START_RECORDING");
        intent.putExtra("CALLER_NAME", callerName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, RecordingService.class);
        intent.setAction("STOP_RECORDING");
        context.startService(intent);
    }
}
