package com.gg_tech_bharat.gdialer;

import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.N)
public class GdialerCallScreeningService extends CallScreeningService {
    private static final String TAG = "GdialerCallScreening";

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        Log.d(TAG, "onScreenCall triggered for: " + (callDetails.getHandle() != null ? callDetails.getHandle().toString() : "Unknown"));
        
        // Respond with default allow
        CallResponse.Builder response = new CallResponse.Builder();
        response.setDisallowCall(false);
        response.setRejectCall(false);
        response.setSilenceCall(false);
        response.setSkipCallLog(false);
        response.setSkipNotification(false);
        
        respondToCall(callDetails, response.build());
    }
}
