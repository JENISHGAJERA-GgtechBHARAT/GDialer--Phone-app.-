package com.gg_tech_bharat.gdialer.ai;

import android.content.Context;

/**
 * Interface representing a real-time AI-based speech enhancement / noise suppression model.
 * Provides abstract hooks for initialization, frame processing, and releasing resources,
 * allowing hot-swapping between RNNoise and TensorFlow Lite without breaking the pipeline.
 */
public interface IAiNoiseSuppressor {
    
    /**
     * Initializes the suppressor with context and configuration paths.
     * @param context Application context.
     * @return true if initialized successfully, false otherwise.
     */
    boolean init(Context context);

    /**
     * Processes a single frame of floating-point PCM audio samples in real-time.
     * The input frame size should match the model specifications (typically 10ms or 20ms).
     * @param inputFrame Input float PCM audio frame.
     * @param outputFrame Output float PCM audio frame.
     */
    void processFrame(float[] inputFrame, float[] outputFrame);

    /**
     * Releases native memory or interpreter instances.
     */
    void release();

    /**
     * Returns descriptive model metadata.
     * @return String describing the model type.
     */
    String getModelInfo();
}
