package com.gg_tech_bharat.gdialer.ai;

import android.content.Context;
import android.util.Log;
import java.io.InputStream;

/**
 * RNNoise wrapper implementation of IAiNoiseSuppressor using Android NDK and JNI.
 * Executes offline spectral subtraction and GRU models inside C++ for maximum performance.
 */
public class RNNoiseNative implements IAiNoiseSuppressor {

    private static final String TAG = "RNNoiseNative";
    private static boolean isLibLoaded = false;
    private long nativeHandle = 0;

    static {
        try {
            System.loadLibrary("rnnoise_wrapper");
            isLibLoaded = true;
            Log.d(TAG, "Native rnnoise_wrapper library loaded successfully.");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native rnnoise_wrapper library.", e);
        }
    }

    @Override
    public boolean init(Context context) {
        if (!isLibLoaded) {
            Log.e(TAG, "JNI Library not loaded. Initialization failed.");
            return false;
        }

        try {
            nativeHandle = nativeCreate();
            if (nativeHandle == 0) {
                Log.e(TAG, "Failed to create native state.");
                return false;
            }

            // Load model weights from assets folder dynamically
            byte[] weightsBuffer = null;
            try (InputStream is = context.getAssets().open("rnnoise_weights.bin")) {
                int size = is.available();
                weightsBuffer = new byte[size];
                int read = is.read(weightsBuffer);
                Log.d(TAG, "Loaded rnnoise_weights.bin: " + read + " bytes.");
            } catch (Exception e) {
                Log.w(TAG, "rnnoise_weights.bin asset not found. Using default DSP coefficients.");
            }

            int result = nativeInit(nativeHandle, weightsBuffer);
            if (result != 0) {
                Log.e(TAG, "Native initialization failed with code: " + result);
                release();
                return false;
            }

            Log.d(TAG, "Native RNNoise initialized successfully.");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Crash during RNNoise native initialization.", e);
            return false;
        }
    }

    @Override
    public void processFrame(float[] inputFrame, float[] outputFrame) {
        if (nativeHandle != 0) {
            nativeProcessFrame(nativeHandle, inputFrame, outputFrame);
        } else {
            System.arraycopy(inputFrame, 0, outputFrame, 0, inputFrame.length);
        }
    }

    @Override
    public void release() {
        if (nativeHandle != 0) {
            nativeDestroy(nativeHandle);
            nativeHandle = 0;
            Log.d(TAG, "Native RNNoise handle released.");
        }
    }

    @Override
    public String getModelInfo() {
        return "RNNoise (RNN Speech Enhancement) - Offline Native NDK Wrapper (10ms Frames)";
    }

    // Native JNI Methods
    private native long nativeCreate();
    private native int nativeInit(long handle, byte[] weights);
    private native void nativeProcessFrame(long handle, float[] inFrame, float[] outFrame);
    private native void nativeDestroy(long handle);
}
