package com.parentcontrol.child.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.parentcontrol.child.App
import com.parentcontrol.child.R
import com.parentcontrol.child.data.model.WsMessage
import com.parentcontrol.child.ui.StatusActivity
import com.parentcontrol.child.ws.DeviceWebSocket
import kotlinx.coroutines.*
import org.webrtc.*

class CameraStreamService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var peerConnection: PeerConnection? = null
    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var parentId: String? = null
    @Volatile
    private var receiverRegistered = false

    private val webRtcReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_WEBRTC_OFFER -> {
                    val sdp = intent.getStringExtra("sdp") ?: return
                    parentId = intent.getStringExtra("parent_id")
                    handleOffer(sdp)
                }
                ACTION_WEBRTC_ICE -> {
                    val candidate = intent.getStringExtra("candidate") ?: return
                    handleIceCandidate(candidate)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
        )

        parentId = intent?.getStringExtra(EXTRA_PARENT_ID)
        initWebRtc()
        registerReceivers()

        return START_NOT_STICKY
    }

    private fun initWebRtc() {
        eglBase = EglBase.create()

        // PeerConnectionFactory.initialize() is called once in App.onCreate()

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase!!.eglBaseContext, true, true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        // Create video source
        videoCapturer = createCameraCapturer()
        val videoSource = peerConnectionFactory!!.createVideoSource(videoCapturer!!.isScreencast)
        val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase!!.eglBaseContext)
        videoCapturer!!.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
        videoCapturer!!.startCapture(640, 480, 30)

        localVideoTrack = peerConnectionFactory!!.createVideoTrack("video0", videoSource)
        localVideoTrack!!.setEnabled(true)

        // Create audio source
        val audioConstraints = MediaConstraints()
        val audioSource = peerConnectionFactory!!.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory!!.createAudioTrack("audio0", audioSource)
        localAudioTrack!!.setEnabled(true)

        // Create peer connection
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerConnectionFactory!!.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.i(TAG, "ICE connection state: $state")
                if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.FAILED
                ) {
                    scope.launch { stopSelf() }
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate) {
                // Send ICE candidate to parent via WebSocket
                val candidateStr = "${candidate.sdpMid}|${candidate.sdpMLineIndex}|${candidate.sdp}"
                DeviceWebSocket.instance.send(
                    WsMessage(
                        type = "webrtc_ice",
                        candidate = candidateStr,
                        parentId = parentId,
                    )
                )
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })

        // Add local tracks
        peerConnection?.addTrack(localVideoTrack)
        peerConnection?.addTrack(localAudioTrack)
    }

    private fun handleOffer(sdpString: String) {
        val sdp = SessionDescription(SessionDescription.Type.OFFER, sdpString)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                createAnswer()
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "setRemoteDescription create failure: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "setRemoteDescription failure: $error")
            }
        }, sdp)
    }

    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        // Send answer to parent via WebSocket
                        DeviceWebSocket.instance.send(
                            WsMessage(
                                type = "webrtc_answer",
                                sdp = sdp.description,
                                parentId = parentId,
                            )
                        )
                    }
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "setLocalDescription failure: $error")
                    }
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createAnswer failure: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun handleIceCandidate(candidateStr: String) {
        try {
            val parts = candidateStr.split("|", limit = 3)
            if (parts.size < 3) return
            val candidate = IceCandidate(parts[0], parts[1].toIntOrNull() ?: 0, parts[2])
            peerConnection?.addIceCandidate(candidate)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add ICE candidate", e)
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(this)
        // Prefer front camera
        for (name in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                return enumerator.createCapturer(name, null)
            }
        }
        // Fallback to back camera
        for (name in enumerator.deviceNames) {
            if (enumerator.isBackFacing(name)) {
                return enumerator.createCapturer(name, null)
            }
        }
        return null
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(ACTION_WEBRTC_OFFER)
            addAction(ACTION_WEBRTC_ICE)
        }
        registerReceiver(webRtcReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, StatusActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, App.CHANNEL_CAMERA)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_camera_active))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        Log.i(TAG, "CameraStreamService destroyed")
        if (receiverRegistered) {
            unregisterReceiver(webRtcReceiver)
            receiverRegistered = false
        }
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnectionFactory?.dispose()
        eglBase?.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CameraStreamService"
        const val NOTIFICATION_ID = 1002
        const val EXTRA_PARENT_ID = "extra_parent_id"
        const val ACTION_WEBRTC_OFFER = "com.parentcontrol.child.WEBRTC_OFFER"
        const val ACTION_WEBRTC_ICE = "com.parentcontrol.child.WEBRTC_ICE"
    }
}
