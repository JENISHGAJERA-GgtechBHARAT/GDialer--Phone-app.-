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
            android.content.SharedPreferences prefs = context.getSharedPreferences("DialerPrefs", Context.MODE_PRIVATE);
            boolean autoRecord = prefs.getBoolean("auto_record_enabled", false);
            if (autoRecord) {
                String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                if (number == null || number.isEmpty()) {
                    number = "Unknown";
                }
                Intent recordIntent = new Intent(context, RecordingService.class);
                recordIntent.setAction(RecordingService.ACTION_START_RECORDING);
                recordIntent.putExtra(RecordingService.EXTRA_PHONE_NUMBER, number);
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(recordIntent);
                    } else {
                        context.startService(recordIntent);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start RecordingService on OFFHOOK", e);
                }
            }
        } else if (Objects.equals(TelephonyManager.EXTRA_STATE_IDLE, state)) {
            Intent recordIntent = new Intent(context, RecordingService.class);
            recordIntent.setAction(RecordingService.ACTION_STOP_RECORDING);
            try {
                context.startService(recordIntent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop RecordingService on IDLE", e);
            }
        }
    }
}
