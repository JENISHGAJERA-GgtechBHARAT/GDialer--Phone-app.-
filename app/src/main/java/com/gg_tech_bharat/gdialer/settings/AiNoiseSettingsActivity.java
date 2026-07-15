package com.gg_tech_bharat.gdialer.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.gg_tech_bharat.gdialer.R;
import com.gg_tech_bharat.gdialer.pipeline.AudioPipeline;
import com.gg_tech_bharat.gdialer.service.AudioProcessingService;
import com.gg_tech_bharat.gdialer.Utils;

/**
 * Settings Activity providing a Material 3 dashboard interface for the AI Noise Cancellation system.
 * Allows users to enable/disable settings, adjust levels, and inspect real-time audio statistics.
 */
public class AiNoiseSettingsActivity extends AppCompatActivity {

    private SwitchMaterial switchMasterAi;
    private RadioGroup rgNoiseLevel;
    private SwitchMaterial switchVoiceClarity;
    private SwitchMaterial switchWindReduction;
    private SwitchMaterial switchKeyboardRemoval;
    private SwitchMaterial switchAdaptiveEnvironment;
    private Slider sliderMicGain;
    private TextView tvMicGainVal;
    private TextView tvEnvironmentInfo;
    private TextView tvModelNameInfo;
    private TextView tvAiStatusBadge;
    private AudioWaveformView audioWaveform;

    private SharedPreferences prefs;
    private AudioPipeline audioPipeline;
    private boolean isBound = false;
    private final Handler updateHandler = new Handler(Looper.getMainLooper());

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioProcessingService.LocalBinder binder = (AudioProcessingService.LocalBinder) service;
            audioPipeline = AudioProcessingService.getAudioPipeline();
            isBound = true;
            startUIPolling();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            audioPipeline = null;
        }
    };

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (isBound && audioPipeline != null) {
                float inputDb = audioPipeline.getInputLevelDb();
                float outputDb = audioPipeline.getOutputLevelDb();
                boolean isVoiceActive = audioPipeline.isVoiceActive();
                String env = audioPipeline.getDetectedEnvironment();
                String model = audioPipeline.getActiveModelInfo();
                boolean fallback = audioPipeline.isFallbackActive();

                // Update visualizer waveform
                if (audioWaveform != null) {
                    audioWaveform.updateLevels(inputDb, outputDb, isVoiceActive);
                }

                // Update text metrics
                if (tvEnvironmentInfo != null) {
                    tvEnvironmentInfo.setText(env);
                }
                if (tvModelNameInfo != null) {
                    tvModelNameInfo.setText(model);
                }

                // Update status badge
                if (tvAiStatusBadge != null) {
                    if (fallback) {
                        tvAiStatusBadge.setText("SYSTEM FALLBACK");
                        tvAiStatusBadge.setBackgroundResource(R.drawable.rounded_green_badge); // Green fallback
                        // Apply yellow/orange tint
                        tvAiStatusBadge.getBackground().setTint(0xFFFF9800);
                    } else if (switchMasterAi.isChecked()) {
                        tvAiStatusBadge.setText("AI ACTIVE");
                        tvAiStatusBadge.setBackgroundResource(R.drawable.rounded_green_badge);
                        tvAiStatusBadge.getBackground().setTint(0xFF34C759); // Emerald Green
                    } else {
                        tvAiStatusBadge.setText("BYPASS MODE");
                        tvAiStatusBadge.setBackgroundResource(R.drawable.rounded_green_badge);
                        tvAiStatusBadge.getBackground().setTint(0xFF8E8E93); // Slate Grey
                    }
                }
            }
            updateHandler.postDelayed(this, 60); // Poll visualizer at 60ms intervals (16 FPS)
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_noise_settings);

        prefs = getSharedPreferences("DialerPrefs", Context.MODE_PRIVATE);
        initViews();
        loadSavedSettings();
        setupListeners();
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            finish();
        });

        switchMasterAi = findViewById(R.id.switchMasterAi);
        rgNoiseLevel = findViewById(R.id.radioGroupNoiseLevel);
        switchVoiceClarity = findViewById(R.id.switchVoiceClarity);
        switchWindReduction = findViewById(R.id.switchWindReduction);
        switchKeyboardRemoval = findViewById(R.id.switchKeyboardRemoval);
        switchAdaptiveEnvironment = findViewById(R.id.switchAdaptiveEnvironment);
        sliderMicGain = findViewById(R.id.sliderMicGain);
        tvMicGainVal = findViewById(R.id.tvMicGainVal);
        tvEnvironmentInfo = findViewById(R.id.tvEnvironmentInfo);
        tvModelNameInfo = findViewById(R.id.tvModelNameInfo);
        tvAiStatusBadge = findViewById(R.id.tvAiStatusBadge);
        audioWaveform = findViewById(R.id.audioWaveform);

        Button btnResetSettings = findViewById(R.id.btnResetSettings);
        if (btnResetSettings != null) {
            btnResetSettings.setOnClickListener(v -> {
                Utils.triggerHaptic(v);
                resetToDefaults();
            });
        }
    }

    private void loadSavedSettings() {
        switchMasterAi.setChecked(prefs.getBoolean("ai_noise_cancellation", true));
        switchVoiceClarity.setChecked(prefs.getBoolean("voice_enhancement", true));
        switchWindReduction.setChecked(prefs.getBoolean("wind_reduction", true));
        switchKeyboardRemoval.setChecked(prefs.getBoolean("keyboard_noise_removal", true));
        switchAdaptiveEnvironment.setChecked(prefs.getBoolean("adaptive_environment", true));

        float gain = prefs.getFloat("microphone_gain", 1.0f);
        sliderMicGain.setValue(gain);
        tvMicGainVal.setText(String.format("%.1fx", gain));

        String level = prefs.getString("noise_reduction_level", "Adaptive AI");
        switch (level) {
            case "Low":
                rgNoiseLevel.check(R.id.rbLow);
                break;
            case "Medium":
                rgNoiseLevel.check(R.id.rbMedium);
                break;
            case "High":
                rgNoiseLevel.check(R.id.rbHigh);
                break;
            case "Adaptive AI":
            default:
                rgNoiseLevel.check(R.id.rbAdaptive);
                break;
        }
    }

    private void setupListeners() {
        switchMasterAi.setOnCheckedChangeListener((button, isChecked) -> {
            Utils.triggerHaptic(button);
            prefs.edit().putBoolean("ai_noise_cancellation", isChecked).apply();
            if (audioPipeline != null) {
                audioPipeline.setAiEnabled(isChecked);
            }
        });

        rgNoiseLevel.setOnCheckedChangeListener((group, checkedId) -> {
            Utils.triggerHaptic(group);
            String level = "Adaptive AI";
            if (checkedId == R.id.rbLow) level = "Low";
            else if (checkedId == R.id.rbMedium) level = "Medium";
            else if (checkedId == R.id.rbHigh) level = "High";
            
            prefs.edit().putString("noise_reduction_level", level).apply();
            if (audioPipeline != null) {
                audioPipeline.setNoiseReductionLevel(level);
            }
        });

        switchVoiceClarity.setOnCheckedChangeListener((button, isChecked) -> {
            Utils.triggerHaptic(button);
            prefs.edit().putBoolean("voice_enhancement", isChecked).apply();
            if (audioPipeline != null) {
                audioPipeline.setVoiceEnhancementEnabled(isChecked);
            }
        });

        switchWindReduction.setOnCheckedChangeListener((button, isChecked) -> {
            Utils.triggerHaptic(button);
            prefs.edit().putBoolean("wind_reduction", isChecked).apply();
            if (audioPipeline != null) {
                audioPipeline.setWindReductionEnabled(isChecked);
            }
        });

        switchKeyboardRemoval.setOnCheckedChangeListener((button, isChecked) -> {
            Utils.triggerHaptic(button);
            prefs.edit().putBoolean("keyboard_noise_removal", isChecked).apply();
            if (audioPipeline != null) {
                audioPipeline.setKeyboardNoiseRemovalEnabled(isChecked);
            }
        });

        switchAdaptiveEnvironment.setOnCheckedChangeListener((button, isChecked) -> {
            Utils.triggerHaptic(button);
            prefs.edit().putBoolean("adaptive_environment", isChecked).apply();
            if (audioPipeline != null) {
                audioPipeline.setAdaptiveEnvironmentEnabled(isChecked);
            }
        });

        sliderMicGain.addOnChangeListener((slider, value, fromUser) -> {
            tvMicGainVal.setText(String.format("%.1fx", value));
            prefs.edit().putFloat("microphone_gain", value).apply();
            if (audioPipeline != null) {
                audioPipeline.setMicGainFactor(value);
            }
        });
    }

    private void resetToDefaults() {
        prefs.edit()
                .putBoolean("ai_noise_cancellation", true)
                .putString("noise_reduction_level", "Adaptive AI")
                .putBoolean("voice_enhancement", true)
                .putBoolean("wind_reduction", true)
                .putBoolean("keyboard_noise_removal", true)
                .putBoolean("adaptive_environment", true)
                .putFloat("microphone_gain", 1.0f)
                .apply();
        loadSavedSettings();
        if (audioPipeline != null) {
            audioPipeline.setAiEnabled(true);
            audioPipeline.setNoiseReductionLevel("Adaptive AI");
            audioPipeline.setVoiceEnhancementEnabled(true);
            audioPipeline.setWindReductionEnabled(true);
            audioPipeline.setKeyboardNoiseRemovalEnabled(true);
            audioPipeline.setAdaptiveEnvironmentEnabled(true);
            audioPipeline.setMicGainFactor(1.0f);
        }
    }

    private void startUIPolling() {
        updateHandler.removeCallbacks(pollRunnable);
        updateHandler.post(pollRunnable);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, AudioProcessingService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
        updateHandler.removeCallbacks(pollRunnable);
        super.onStop();
    }
}
