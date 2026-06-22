package com.gg_tech_bharat.gdialer;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Accessibility Service for GDialer.
 * Automatic speakerphone activation has been disabled to prevent pickup issues.
 */
public class CallAudioAccessibilityService extends AccessibilityService {
    private static final String TAG = "CallAccessibility";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Disabled auto-speaker functionality.
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted");
    }
}
