package com.gg_tech_bharat.gdialer;

import android.telecom.Call;
import android.telecom.CallAudioState;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CallManager {

    public static Call sCurrentCall;
    public static boolean isVoicemailScreening = false;
    private static final List<Call> sCalls = new CopyOnWriteArrayList<>();

    public interface CallStateListener {
        void onStateChanged(int state);
        void onCallListChanged();
        default void onAudioStateChanged(CallAudioState audioState) {}
    }

    private static final List<CallStateListener> sListeners = new CopyOnWriteArrayList<>();

    public static void registerListener(CallStateListener listener) {
        if (listener != null && !sListeners.contains(listener)) {
            sListeners.add(listener);
        }
    }

    public static void unregisterListener(CallStateListener listener) {
        if (listener != null) {
            sListeners.remove(listener);
        }
    }

    public static void unregisterListener() {
        sListeners.clear();
    }

    public static void addCall(Call call) {
        if (call != null && !sCalls.contains(call)) {
            sCalls.add(call);
            updateCurrentCall();
            notifyCallListChanged();
        }
    }

    public static void removeCall(Call call) {
        if (call != null) {
            sCalls.remove(call);
            updateCurrentCall();
            notifyCallListChanged();
        }
    }

    private static synchronized void updateCurrentCall() {
        if (sCalls.isEmpty()) {
            sCurrentCall = null;
            return;
        }
        
        // Priority: Ringing > Active > Dialing > Connecting > Holding
        for (Call c : sCalls) {
            if (c.getState() == Call.STATE_RINGING) {
                sCurrentCall = c;
                return;
            }
        }
        for (Call c : sCalls) {
            if (c.getState() == Call.STATE_ACTIVE) {
                sCurrentCall = c;
                return;
            }
        }
        for (Call c : sCalls) {
            int s = c.getState();
            if (s == Call.STATE_DIALING || s == Call.STATE_CONNECTING) {
                sCurrentCall = c;
                return;
            }
        }
        // Fallback to first available call
        if (!sCalls.isEmpty()) {
            sCurrentCall = sCalls.get(0);
        } else {
            sCurrentCall = null;
        }
    }

    public static List<Call> getCalls() {
        return new ArrayList<>(sCalls);
    }

    public static void updateState(int state) {
        updateCurrentCall();
        for (CallStateListener l : sListeners) {
            l.onStateChanged(state);
        }
    }

    public static void updateAudioState(CallAudioState audioState) {
        for (CallStateListener l : sListeners) {
            l.onAudioStateChanged(audioState);
        }
    }
    
    public static Call getRingingCall() {
        for (Call c : sCalls) {
            if (c.getState() == Call.STATE_RINGING) return c;
        }
        return null;
    }

    private static void notifyCallListChanged() {
        for (CallStateListener l : sListeners) {
            l.onCallListChanged();
        }
    }
}
