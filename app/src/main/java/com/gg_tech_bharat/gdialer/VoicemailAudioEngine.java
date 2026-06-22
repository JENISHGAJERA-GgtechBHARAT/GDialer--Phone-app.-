package com.gg_tech_bharat.gdialer;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;
import java.io.IOException;

public class VoicemailAudioEngine {

    private MediaPlayer mediaPlayer;

    /**
     * Streams free cloud-hosted voicemail from URL.
     * Uses USAGE_MEDIA to comply with standard Play Store policies.
     */
    public void playCloudVoicemail(String url) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }

        mediaPlayer = new MediaPlayer();
        
        // Configure for standard media channel
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        mediaPlayer.setAudioAttributes(attributes);

        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d("VoicemailEngine", "Streaming started...");
                mp.start();
            });
            mediaPlayer.prepareAsync(); // Don't block UI thread
        } catch (IOException e) {
            Log.e("VoicemailEngine", "Streaming error: " + e.getMessage());
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
