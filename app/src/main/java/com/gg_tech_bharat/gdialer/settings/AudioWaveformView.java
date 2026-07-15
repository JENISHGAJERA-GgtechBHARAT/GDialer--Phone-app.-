package com.gg_tech_bharat.gdialer.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Custom View that renders real-time dual waveforms (noisy input vs clean suppressed output)
 * to visually demonstrate the active AI noise cancellation performance.
 */
public class AudioWaveformView extends View {

    private final Paint inputPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outputPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Float> inputAmplitudes = new ArrayList<>();
    private final List<Float> outputAmplitudes = new ArrayList<>();
    private final int maxBars = 45;
    private final Random random = new Random();

    public AudioWaveformView(Context context) {
        super(context);
        init();
    }

    public AudioWaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AudioWaveformView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Red/Orange showing noisy background elements
        inputPaint.setColor(0xFFFF5252);
        inputPaint.setStyle(Paint.Style.FILL);
        inputPaint.setStrokeCap(Paint.Cap.ROUND);

        // Teal/Green showing clear clean voice signal
        outputPaint.setColor(0xFF34C759);
        outputPaint.setStyle(Paint.Style.FILL);
        outputPaint.setStrokeCap(Paint.Cap.ROUND);

        // Populate with silent defaults
        for (int i = 0; i < maxBars; i++) {
            inputAmplitudes.add(0.05f);
            outputAmplitudes.add(0.02f);
        }
    }

    /**
     * Updates the waveform with new decibel levels.
     * Converts dB to normalized linear amplitudes.
     */
    public void updateLevels(float inputDb, float outputDb, boolean isVoiceActive) {
        // Map dB [-80, 0] to linear scale [0.02, 1.0]
        float inputNorm = (inputDb + 80f) / 80f;
        if (inputNorm < 0.02f) inputNorm = 0.02f;
        if (inputNorm > 1.0f) inputNorm = 1.0f;

        float outputNorm = (outputDb + 80f) / 80f;
        if (outputNorm < 0.02f) outputNorm = 0.02f;
        if (outputNorm > 1.0f) outputNorm = 1.0f;

        // Introduce minor visual dynamics to make it look organic
        if (isVoiceActive) {
            inputNorm += random.nextFloat() * 0.08f;
            outputNorm += random.nextFloat() * 0.08f;
        }

        inputAmplitudes.remove(0);
        inputAmplitudes.add(inputNorm);

        outputAmplitudes.remove(0);
        outputAmplitudes.add(outputNorm);

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        float spacing = 6f;
        float barWidth = (width - (spacing * (maxBars - 1))) / maxBars;
        float centerY = height / 2f;

        for (int i = 0; i < maxBars; i++) {
            float x = i * (barWidth + spacing);
            
            // Draw Input (Noisy) amplitude bar in the background (taller)
            float inputAmp = inputAmplitudes.get(i);
            float inputBarHeight = height * 0.8f * inputAmp;
            float inputTop = centerY - (inputBarHeight / 2f);
            float inputBottom = centerY + (inputBarHeight / 2f);
            
            // Draw background red noise bar (semi-transparent)
            inputPaint.setAlpha(80);
            canvas.drawRoundRect(x, inputTop, x + barWidth, inputBottom, barWidth / 2f, barWidth / 2f, inputPaint);

            // Draw Output (Suppressed Clean Voice) bar in the foreground (shorter, solid green)
            float outputAmp = outputAmplitudes.get(i);
            float outputBarHeight = height * 0.8f * outputAmp;
            float outputTop = centerY - (outputBarHeight / 2f);
            float outputBottom = centerY + (outputBarHeight / 2f);
            
            outputPaint.setAlpha(255);
            canvas.drawRoundRect(x, outputTop, x + barWidth, outputBottom, barWidth / 2f, barWidth / 2f, outputPaint);
        }
    }
}
