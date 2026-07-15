package com.gg_tech_bharat.gdialer.ai;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * TensorFlow Lite Speech Enhancement implementation of IAiNoiseSuppressor.
 * Executes deep learning inference dynamically using Java reflection to avoid strict compile-time dependency clashes.
 */
public class TFLiteSpeechEnhancer implements IAiNoiseSuppressor {

    private static final String TAG = "TFLiteSpeechEnhancer";
    private static final String MODEL_FILE_NAME = "noise_suppression.tflite";

    private Object interpreterInstance;
    private Method runMethod;
    private Method closeMethod;
    private boolean isInitialized = false;
    private String accelerationMode = "CPU (Fallback)";

    @Override
    public boolean init(Context context) {
        try {
            MappedByteBuffer modelBuffer = loadModelFile(context);
            if (modelBuffer == null) {
                Log.e(TAG, "TFLite model file not found in assets.");
                return false;
            }

            // Load TensorFlow Lite classes via reflection
            Class<?> interpreterClass;
            Class<?> optionsClass;
            try {
                interpreterClass = Class.forName("org.tensorflow.lite.Interpreter");
                optionsClass = Class.forName("org.tensorflow.lite.Interpreter$Options");
            } catch (ClassNotFoundException e) {
                // Try Play Services package coordinates fallback
                try {
                    interpreterClass = Class.forName("com.google.android.gms.tflite.Interpreter");
                    optionsClass = Class.forName("com.google.android.gms.tflite.Interpreter$Options");
                } catch (ClassNotFoundException ex) {
                    Log.w(TAG, "TensorFlow Lite classes not found on classpath. TFLite engine disabled.");
                    return false;
                }
            }

            // Setup Options
            Object optionsInstance = optionsClass.getDeclaredConstructor().newInstance();
            Method setNumThreadsMethod = optionsClass.getMethod("setNumThreads", int.class);
            int threads = Runtime.getRuntime().availableProcessors() >= 4 ? 4 : 2;
            setNumThreadsMethod.invoke(optionsInstance, threads);

            // Try NNAPI for hardware acceleration
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Method setUseNNAPIMethod = optionsClass.getMethod("setUseNNAPI", boolean.class);
                    setUseNNAPIMethod.invoke(optionsInstance, true);
                    accelerationMode = "NNAPI (DSP/NPU/TPU)";
                    Log.d(TAG, "NNAPI Hardware Acceleration enabled via reflection.");
                } else {
                    accelerationMode = "CPU (Optimized)";
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to enable NNAPI. Using CPU mode.", e);
                accelerationMode = "CPU (Optimized)";
            }

            // Instantiate Interpreter
            Constructor<?> constructor = interpreterClass.getConstructor(java.nio.ByteBuffer.class, optionsClass);
            interpreterInstance = constructor.newInstance(modelBuffer, optionsInstance);

            // Fetch methods
            runMethod = interpreterClass.getMethod("run", Object.class, Object.class);
            closeMethod = interpreterClass.getMethod("close");

            isInitialized = true;
            Log.d(TAG, "TFLite Speech Enhancement model initialized successfully via reflection. Acceleration: " + accelerationMode);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TFLite Speech Enhancement model via reflection.", e);
            return false;
        }
    }

    @Override
    public void processFrame(float[] inputFrame, float[] outputFrame) {
        if (!isInitialized || interpreterInstance == null || runMethod == null) {
            System.arraycopy(inputFrame, 0, outputFrame, 0, inputFrame.length);
            return;
        }

        try {
            // Model expects float[][] input [1, frame_size] and returns float[][] output [1, frame_size]
            float[][] inputTensor = new float[1][inputFrame.length];
            System.arraycopy(inputFrame, 0, inputTensor[0], 0, inputFrame.length);

            float[][] outputTensor = new float[1][outputFrame.length];

            // Invoke run(input, output)
            runMethod.invoke(interpreterInstance, inputTensor, outputTensor);

            System.arraycopy(outputTensor[0], 0, outputFrame, 0, outputFrame.length);
        } catch (Exception e) {
            Log.e(TAG, "Error during speech enhancement inference via reflection.", e);
            // Safety fallback: bypass signal
            System.arraycopy(inputFrame, 0, outputFrame, 0, inputFrame.length);
        }
    }

    @Override
    public void release() {
        if (interpreterInstance != null && closeMethod != null) {
            try {
                closeMethod.invoke(interpreterInstance);
            } catch (Exception e) {
                Log.e(TAG, "Error closing TFLite interpreter.", e);
            }
            interpreterInstance = null;
        }
        isInitialized = false;
        Log.d(TAG, "TFLite Speech Enhancement model interpreter released.");
    }

    @Override
    public String getModelInfo() {
        return "TFLite Speech Enhancement (" + accelerationMode + ") - Offline Deep Speech Model";
    }

    private MappedByteBuffer loadModelFile(Context context) throws MappedByteBufferCustomException {
        try {
            android.content.res.AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE_NAME);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } catch (IOException e) {
            Log.w(TAG, "TFLite asset openFd failed. Model fallback engaged.");
            return null;
        }
    }

    // Custom non-checked exception to bypass throws signature restrictions
    private static class MappedByteBufferCustomException extends Exception {
        public MappedByteBufferCustomException(String msg) {
            super(msg);
        }
    }
}
