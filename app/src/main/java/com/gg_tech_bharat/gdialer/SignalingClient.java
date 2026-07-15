package com.gg_tech_bharat.gdialer;

import android.util.Log;
import androidx.annotation.NonNull;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONObject;

public class SignalingClient {
    private static final String TAG = "SignalingClient";
    
    public interface SignalingListener {
        void onOfferReceived(String sdpDescription);
        void onAnswerReceived(String sdpDescription);
        void onIceCandidateReceived(String sdpMid, int sdpMLineIndex, String sdpCandidate);
        void onConnected();
        void onDisconnected();
    }

    private final OkHttpClient client;
    private WebSocket webSocket;
    private final SignalingListener listener;
    private final String serverUrl;

    public SignalingClient(String serverUrl, SignalingListener listener) {
        this.client = new OkHttpClient();
        this.serverUrl = serverUrl;
        this.listener = listener;
    }

    public void connect() {
        Request request = new Request.Builder().url(serverUrl).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                Log.d(TAG, "Signaling connection opened");
                listener.onConnected();
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                try {
                    JSONObject json = new JSONObject(text);
                    String type = json.optString("type");
                    if ("offer".equals(type)) {
                        listener.onOfferReceived(json.optString("payload"));
                    } else if ("answer".equals(type)) {
                        listener.onAnswerReceived(json.optString("payload"));
                    } else if ("candidate".equals(type)) {
                        JSONObject payload = json.optJSONObject("payload");
                        if (payload != null) {
                            listener.onIceCandidateReceived(
                                    payload.optString("sdpMid"),
                                    payload.optInt("sdpMLineIndex"),
                                    payload.optString("candidate")
                            );
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing message: " + text, e);
                }
            }

            @Override
            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                Log.d(TAG, "Signaling connection closing: " + reason);
                listener.onDisconnected();
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
                Log.e(TAG, "Signaling connection failure", t);
                listener.onDisconnected();
            }
        });
    }

    public void sendOffer(String sdpDescription) {
        sendMessage("offer", sdpDescription);
    }

    public void sendAnswer(String sdpDescription) {
        sendMessage("answer", sdpDescription);
    }

    public void sendIceCandidate(String sdpMid, int sdpMLineIndex, String sdpCandidate) {
        try {
            JSONObject candidateJson = new JSONObject();
            candidateJson.put("sdpMid", sdpMid);
            candidateJson.put("sdpMLineIndex", sdpMLineIndex);
            candidateJson.put("candidate", sdpCandidate);
            
            JSONObject wrapper = new JSONObject();
            wrapper.put("type", "candidate");
            wrapper.put("payload", candidateJson);
            
            sendText(wrapper.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error building candidate json", e);
        }
    }

    private void sendMessage(String type, Object payload) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("payload", payload);
            sendText(json.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error building message: " + type, e);
        }
    }

    private void sendText(String text) {
        if (webSocket != null) {
            webSocket.send(text);
        } else {
            Log.w(TAG, "WebSocket is not connected");
        }
    }

    public void disconnect() {
        if (webSocket != null) {
            try {
                webSocket.close(1000, "Normal closure");
            } catch (Exception ignored) {}
            webSocket = null;
        }
    }
}
