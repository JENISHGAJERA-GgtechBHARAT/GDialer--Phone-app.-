package com.gg_tech_bharat.gdialer.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.util.Log;
import com.gg_tech_bharat.gdialer.pipeline.AudioPipeline;
import com.gg_tech_bharat.gdialer.service.AudioProcessingService;

/**
 * BroadcastReceiver that monitors changes in audio routing devices (Bluetooth headsets, USB earbuds, wired microphones).
 * Dynamically re-adjusts microphone gain and DSP filters inside the active AudioPipeline when hardware targets change.
 */
public class NoiseReceiver extends BroadcastReceiver {

    private static final String TAG = "NoiseReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        Log.d(TAG, "Audio device state changed. Action: " + action);

        AudioPipeline pipeline = AudioProcessingService.getAudioPipeline();
        if (pipeline == null || !AudioProcessingService.isServiceRunning()) {
            return;
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;

        // Query active input devices
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            for (AudioDeviceInfo device : devices) {
                int type = device.getType();
                if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    Log.d(TAG, "Bluetooth headset routing detected. Tweak echo cancellation and boost microphone gain.");
                    pipeline.setMicGainFactor(1.5f); // Boost gain for bluetooth microphone
                    return;
                } else if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_USB_HEADSET) {
                    Log.d(TAG, "Wired/USB headset routing detected. Set standard microphone gain.");
                    pipeline.setMicGainFactor(1.1f);
                    return;
                }
            }
        }

        // Default phone earpiece / speaker microphone gain
        pipeline.setMicGainFactor(1.0f);
    }
}
