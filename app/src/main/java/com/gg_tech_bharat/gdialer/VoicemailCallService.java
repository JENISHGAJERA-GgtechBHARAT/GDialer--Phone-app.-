package com.gg_tech_bharat.gdialer;

import android.content.Intent;
import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;

/**
 * VoicemailCallService: Background service that captures the ringing call
 * and initiates the Live Voicemail activity.
 */
public class VoicemailCallService extends InCallService {

    private static final String TAG = "VoicemailCallService";

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        Log.d(TAG, "Call added to Voicemail Service");

        // Safely capture the ringing call for potential screening
        if (call.getState() == Call.STATE_RINGING) {
            CallManager.addCall(call);
        }
    }

    public void launchVoicemail(String number) {
        Intent intent = new Intent(this, VoicemailActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("EXTRA_NUMBER", number);
        startActivity(intent);
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        CallManager.removeCall(call);
    }
}
