package com.gg_tech_bharat.gdialer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.Objects;

/**
 * Automatically triggers the High-Gain recording workaround when call state changes.
 */
public class CallStateReceiver extends BroadcastReceiver {
    private static final String TAG = "CallStateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Objects.equals(intent.getAction(), TelephonyManager.ACTION_PHONE_STATE_CHANGED)) return;

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

        Log.d(TAG, "Telephony State Changed: " + state);

        if (Objects.equals(TelephonyManager.EXTRA_STATE_OFFHOOK, state)) {
            Log.d(TAG, "Telephony State OFFHOOK");
        } else if (Objects.equals(TelephonyManager.EXTRA_STATE_IDLE, state)) {
            Log.d(TAG, "Telephony State IDLE");
        }
    }
}
