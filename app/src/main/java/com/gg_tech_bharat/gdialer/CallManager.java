package com.gg_tech_bharat.gdialer;

import android.telecom.Call;
import android.telecom.CallAudioState;
import java.util.ArrayList;
import java.util.List;

public class CallManager {

    public static Call sCurrentCall;
    private static final List<Call> sCalls = new ArrayList<>();

    public interface CallStateListener {
        void onStateChanged(int state);
        void onCallListChanged();
        default void onAudioStateChanged(CallAudioState audioState) {}
    }

    private static CallStateListener sListener;

    public static void registerListener(CallStateListener listener) {
        sListener = listener;
    }

    public static void unregisterListener() {
        sListener = null;
    }

    public static void addCall(Call call) {
        if (!sCalls.contains(call)) {
            sCalls.add(call);
            if (sCurrentCall == null) sCurrentCall = call;
            if (sListener != null) sListener.onCallListChanged();
        }
    }

    public static void removeCall(Call call) {
        sCalls.remove(call);
        if (sCurrentCall == call) {
            sCurrentCall = sCalls.isEmpty() ? null : sCalls.get(0);
        }
        if (sListener != null) sListener.onCallListChanged();
    }

    public static List<Call> getCalls() {
        return new ArrayList<>(sCalls);
    }

    public static void updateState(int state) {
        if (sListener != null) {
            sListener.onStateChanged(state);
        }
    }

    public static void updateAudioState(CallAudioState audioState) {
        if (sListener != null) {
            sListener.onAudioStateChanged(audioState);
        }
    }
}
