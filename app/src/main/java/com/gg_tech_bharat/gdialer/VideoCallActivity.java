package com.gg_tech_bharat.gdialer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.webrtc.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VideoCallActivity extends AppCompatActivity {
    private static final String TAG = "VideoCallActivity";
    private static final int PERMISSION_REQUEST_CODE = 3001;

    private SurfaceViewRenderer localVideoView;
    private SurfaceViewRenderer remoteVideoView;
    private TextView tvContactName, tvStatus;
    private FloatingActionButton btnMute, btnEnd, btnSwitchCamera;

    private EglBase rootEglBase;
    private PeerConnectionFactory factory;
    private VideoCapturer videoCapturer;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private PeerConnection peerConnection;
    private SignalingClient signalingClient;

    private boolean isCaller;
    private String phoneNumber;
    private String contactName;
    private String serverUrl;
    
    private boolean isMuted = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);

        localVideoView = findViewById(R.id.localVideoView);
        remoteVideoView = findViewById(R.id.remoteVideoView);
        tvContactName = findViewById(R.id.tvVideoCallContactName);
        tvStatus = findViewById(R.id.tvVideoCallStatus);
        btnMute = findViewById(R.id.btnMuteAudio);
        btnEnd = findViewById(R.id.btnEndVideoCall);
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera);

        Intent intent = getIntent();
        isCaller = intent.getBooleanExtra("IS_CALLER", true);
        phoneNumber = intent.getStringExtra("EXTRA_NUMBER");
        if (phoneNumber == null) phoneNumber = "Unknown";
        contactName = Utils.queryContactName(this, phoneNumber);
        if (contactName == null) contactName = phoneNumber;
        
        tvContactName.setText(contactName);

        // Dynamically point to local network or fallback
        serverUrl = intent.getStringExtra("SIGNALING_URL");
        if (serverUrl == null) {
            serverUrl = "ws://192.168.1.100:8080"; // Default fallback signaling server url
        }

        btnEnd.setOnClickListener(v -> endCall());
        btnMute.setOnClickListener(v -> toggleMute());
        btnSwitchCamera.setOnClickListener(v -> switchCamera());

        if (checkPermissions()) {
            initializeWebRtcAndConnect();
        } else {
            requestPermissions();
        }
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (checkPermissions()) {
                initializeWebRtcAndConnect();
            } else {
                Toast.makeText(this, "Camera and Audio permissions are required for video calls", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void initializeWebRtcAndConnect() {
        rootEglBase = EglBase.create();

        // Initialize PeerConnectionFactory
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory();

        // Initialize local/remote view renderers
        localVideoView.init(rootEglBase.getEglBaseContext(), null);
        localVideoView.setEnableHardwareScaler(true);
        localVideoView.setMirror(true);

        remoteVideoView.init(rootEglBase.getEglBaseContext(), null);
        remoteVideoView.setEnableHardwareScaler(true);

        // Start capturing local camera feed
        videoCapturer = createVideoCapturer();
        if (videoCapturer == null) {
            Log.e(TAG, "Failed to create video capturer");
            finish();
            return;
        }

        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
        VideoSource videoSource = factory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(surfaceTextureHelper, this, videoSource.getCapturerObserver());
        videoCapturer.startCapture(1280, 720, 30);

        localVideoTrack = factory.createVideoTrack("ARDMSV0", videoSource);
        localVideoTrack.addSink(localVideoView);

        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        localAudioTrack = factory.createAudioTrack("ARDMAS0", audioSource);

        // Setup signaling
        setupSignaling();
    }

    private VideoCapturer createVideoCapturer() {
        CameraEnumerator enumerator = new Camera2Enumerator(this);
        String[] deviceNames = enumerator.getDeviceNames();

        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) return capturer;
            }
        }

        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) return capturer;
            }
        }
        return null;
    }

    private void setupSignaling() {
        signalingClient = new SignalingClient(serverUrl, new SignalingClient.SignalingListener() {
            @Override
            public void onOfferReceived(String sdpDescription) {
                runOnUiThread(() -> handleOffer(sdpDescription));
            }

            @Override
            public void onAnswerReceived(String sdpDescription) {
                runOnUiThread(() -> handleAnswer(sdpDescription));
            }

            @Override
            public void onIceCandidateReceived(String sdpMid, int sdpMLineIndex, String sdpCandidate) {
                runOnUiThread(() -> {
                    if (peerConnection != null) {
                        peerConnection.addIceCandidate(new IceCandidate(sdpMid, sdpMLineIndex, sdpCandidate));
                    }
                });
            }

            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    tvStatus.setText("Signaling Connected. Establishing peer connection...");
                    initializePeerConnection();
                    if (isCaller) {
                        createOffer();
                    }
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    tvStatus.setText("Disconnected from signaling server");
                    endCall();
                });
            }
        });
        signalingClient.connect();
    }

    private void initializePeerConnection() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        peerConnection = factory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState signalingState) {}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                runOnUiThread(() -> {
                    if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                        tvStatus.setText("Call Connected");
                    } else if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                        tvStatus.setText("Peer disconnected");
                        endCall();
                    }
                });
            }
            @Override public void onIceConnectionReceivingChange(boolean b) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                if (signalingClient != null) {
                    signalingClient.sendIceCandidate(iceCandidate.sdpMid, iceCandidate.sdpMLineIndex, iceCandidate.sdp);
                }
            }
            @Override public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}
            @Override public void onAddStream(MediaStream mediaStream) {}
            @Override public void onRemoveStream(MediaStream mediaStream) {}
            @Override public void onDataChannel(DataChannel dataChannel) {}
            @Override public void onRenegotiationNeeded() {}
            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                if (rtpReceiver.track() instanceof VideoTrack) {
                    VideoTrack remoteVideoTrack = (VideoTrack) rtpReceiver.track();
                    runOnUiThread(() -> remoteVideoTrack.addSink(remoteVideoView));
                }
            }
        });

        peerConnection.addTrack(localVideoTrack, Collections.singletonList("ARDMS"));
        peerConnection.addTrack(localAudioTrack, Collections.singletonList("ARDMS"));
    }

    private void createOffer() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        peerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SimpleSdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        if (signalingClient != null) {
                            signalingClient.sendOffer(sessionDescription.description);
                        }
                    }
                }, sessionDescription);
            }
        }, constraints);
    }

    private void handleOffer(String sdpDescription) {
        if (peerConnection == null) initializePeerConnection();
        SessionDescription remoteSdp = new SessionDescription(SessionDescription.Type.OFFER, sdpDescription);
        peerConnection.setRemoteDescription(new SimpleSdpObserver() {
            @Override
            public void onSetSuccess() {
                createAnswer();
            }
        }, remoteSdp);
    }

    private void createAnswer() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        peerConnection.createAnswer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SimpleSdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        if (signalingClient != null) {
                            signalingClient.sendAnswer(sessionDescription.description);
                        }
                    }
                }, sessionDescription);
            }
        }, constraints);
    }

    private void handleAnswer(String sdpDescription) {
        SessionDescription remoteSdp = new SessionDescription(SessionDescription.Type.ANSWER, sdpDescription);
        if (peerConnection != null) {
            peerConnection.setRemoteDescription(new SimpleSdpObserver(), remoteSdp);
        }
    }

    private void toggleMute() {
        isMuted = !isMuted;
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(!isMuted);
        }
        btnMute.setImageResource(isMuted ? R.drawable.ic_mic : R.drawable.ic_mic); // Mirror correct drawables
        btnMute.setImageAlpha(isMuted ? 128 : 255);
        Toast.makeText(this, isMuted ? "Microphone Muted" : "Microphone Active", Toast.LENGTH_SHORT).show();
    }

    private void switchCamera() {
        if (videoCapturer instanceof CameraVideoCapturer) {
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
            cameraVideoCapturer.switchCamera(null);
        }
    }

    private void endCall() {
        try {
            if (videoCapturer != null) {
                videoCapturer.stopCapture();
                videoCapturer.dispose();
            }
            if (peerConnection != null) {
                peerConnection.close();
                peerConnection.dispose();
            }
            if (signalingClient != null) {
                signalingClient.disconnect();
            }
            if (localVideoView != null) localVideoView.release();
            if (remoteVideoView != null) remoteVideoView.release();
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up WebRTC on end call", e);
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        endCall();
        super.onDestroy();
    }

    private static class SimpleSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sessionDescription) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String s) { Log.e(TAG, "Sdp creation error: " + s); }
        @Override public void onSetFailure(String s) { Log.e(TAG, "Sdp setting error: " + s); }
    }
}
