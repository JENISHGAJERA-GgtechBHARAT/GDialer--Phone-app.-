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

    private android.media.AudioRecord audioRecord;
    private Thread recordingThread;
    private android.media.AudioManager audioManager;
    private boolean isRecording = false;
    private String currentFilePath;
    private String rawFilePath;
    private String phoneNumber;
    private String callerName;
    private long startTime;
    private int bufferSize;

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

        File dir = new File(getExternalFilesDir(null) != null ? getExternalFilesDir(null) : getFilesDir(), "call_recordings");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String safeNumber = phoneNumber.replaceAll("[^0-9+]", "");
        if (safeNumber.isEmpty()) {
            safeNumber = "Unknown";
        }
        long timestamp = System.currentTimeMillis();
        String fileName = "REC_" + safeNumber + "_" + timestamp + ".wav";
        String rawFileName = "REC_" + safeNumber + "_" + timestamp + ".raw";
        File file = new File(dir, fileName);
        File rawFile = new File(dir, rawFileName);
        currentFilePath = file.getAbsolutePath();
        rawFilePath = rawFile.getAbsolutePath();

        int sampleRate = 48000;
        int channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT;
        bufferSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        if (bufferSize == android.media.AudioRecord.ERROR || bufferSize == android.media.AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = 9600;
        }

        try {
            audioManager = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setMode(android.media.AudioManager.MODE_IN_COMMUNICATION);
                audioManager.setSpeakerphoneOn(true);
            }

            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "RECORD_AUDIO permission not granted");
                stopSelf();
                return;
            }

            audioRecord = new android.media.AudioRecord(
                    android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
            );

            if (audioRecord.getState() != android.media.AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed, trying MIC source as fallback");
                audioRecord = new android.media.AudioRecord(
                        android.media.MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                );
            }

            audioRecord.startRecording();
            isRecording = true;
            startTime = System.currentTimeMillis();

            recordingThread = new Thread(() -> {
                try (java.io.FileOutputStream os = new java.io.FileOutputStream(rawFilePath)) {
                    byte[] data = new byte[bufferSize];
                    while (isRecording) {
                        int read = audioRecord.read(data, 0, bufferSize);
                        if (read > 0) {
                            os.write(data, 0, read);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error writing raw PCM file", e);
                }
            }, "CallRecordingThread");
            recordingThread.start();

            Log.d(TAG, "Call recording started using AudioRecord, saving raw to: " + rawFilePath);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start AudioRecord", e);
            if (audioManager != null) {
                audioManager.setMode(android.media.AudioManager.MODE_NORMAL);
                audioManager.setSpeakerphoneOn(false);
            }
            stopSelf();
        }
    }

    private void stopRecording() {
        if (isRecording) {
            isRecording = false;
            
            if (audioRecord != null) {
                try {
                    audioRecord.stop();
                    audioRecord.release();
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing AudioRecord", e);
                }
                audioRecord = null;
            }

            if (recordingThread != null) {
                try {
                    recordingThread.join();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Recording thread join interrupted", e);
                }
                recordingThread = null;
            }

            if (audioManager != null) {
                try {
                    audioManager.setMode(android.media.AudioManager.MODE_NORMAL);
                    audioManager.setSpeakerphoneOn(false);
                } catch (Exception e) {
                    Log.e(TAG, "Error resetting AudioManager settings", e);
                }
            }

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            if (duration < 1) {
                try {
                    new File(rawFilePath).delete();
                } catch (Exception ignored) {}
            } else {
                File rawFile = new File(rawFilePath);
                File wavFile = new File(currentFilePath);
                if (rawFile.exists()) {
                    convertPcmToWav(rawFile, wavFile, 48000, 1, 16);
                    rawFile.delete();
                    Log.d(TAG, "Recording stopped successfully. WAV saved to: " + currentFilePath);
                }
            }
        }
        sIsServiceRunning = false;
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

    private void convertPcmToWav(File pcmFile, File wavFile, int sampleRate, int channels, int bitsPerSample) {
        long totalAudioLen = pcmFile.length();
        long totalDataLen = totalAudioLen + 36;
        long byteRate = sampleRate * channels * bitsPerSample / 8;

        byte[] header = new byte[44];
        header[0] = 'R';  // RIFF
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';  // WAVE
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // fmt 
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // size of fmt chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1 (PCM)
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * bitsPerSample / 8); // block align
        header[33] = 0;
        header[34] = (byte) bitsPerSample;
        header[35] = 0;
        header[36] = 'd'; // data
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        try (java.io.FileInputStream in = new java.io.FileInputStream(pcmFile);
             java.io.FileOutputStream out = new java.io.FileOutputStream(wavFile)) {
            out.write(header);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert PCM to WAV", e);
        }
    }
}
