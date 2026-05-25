package com.gg_tech_bharat.gdialer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // We handle everything via InCallService now for reliability as default dialer
    }
}
