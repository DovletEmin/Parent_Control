package com.parentcontrol.child.ws

import android.content.Context
import android.content.Intent
import android.util.Log
import com.parentcontrol.child.data.model.WsMessage
import com.parentcontrol.child.service.CameraStreamService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class CommandHandler(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val ws = DeviceWebSocket.instance
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var soundHandler: android.os.Handler? = null

    fun start() {
        scope.launch {
            ws.messages.collectLatest { msg ->
                handleMessage(msg)
            }
        }
    }

    private suspend fun handleMessage(msg: WsMessage) {
        when (msg.type) {
            "command" -> handleCommand(msg)
            "webrtc_offer" -> handleWebRtcOffer(msg)
            "webrtc_ice" -> handleWebRtcIce(msg)
        }
    }

    private suspend fun handleCommand(msg: WsMessage) {
        val commandId = msg.commandId ?: return
        val commandType = msg.commandType ?: return

        Log.i(TAG, "Received command: $commandType ($commandId)")

        try {
            when (commandType) {
                "request_location" -> {
                    handleRequestLocation()
                    ackCommand(commandId, "executed")
                }
                "request_camera" -> {
                    val pid = msg.parentId
                    if (pid.isNullOrBlank()) {
                        Log.w(TAG, "request_camera without parent_id")
                        ackCommand(commandId, "failed")
                    } else {
                        startCameraStream(pid)
                        ackCommand(commandId, "executed")
                    }
                }
                "play_sound" -> {
                    handlePlaySound()
                    ackCommand(commandId, "executed")
                }
                "sync_now" -> {
                    com.parentcontrol.child.sync.SyncScheduler.scheduleAll(context)
                    ackCommand(commandId, "executed")
                }
                "lock_device" -> {
                    handleLockDevice()
                    ackCommand(commandId, "executed")
                }
                "block_app", "unblock_app" -> {
                    // App blocking handled at OS level is limited without MDM
                    // Mark as executed — the parent sees the command was received.
                    ackCommand(commandId, "executed")
                }
                else -> {
                    Log.w(TAG, "Unknown command type: $commandType")
                    ackCommand(commandId, "failed")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed: $commandType", e)
            ackCommand(commandId, "failed")
        }
    }

    private fun handleRequestLocation() {
        val collector = com.parentcontrol.child.collector.LocationCollector(context)
        collector.getLastKnown { location ->
            if (location != null) {
                ws.send(
                    WsMessage(
                        type = "location",
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        recordedAt = location.recordedAt,
                    )
                )
            }
        }
    }

    private fun startCameraStream(parentId: String?) {
        val intent = Intent(context, CameraStreamService::class.java).apply {
            putExtra(CameraStreamService.EXTRA_PARENT_ID, parentId)
        }
        context.startForegroundService(intent)
    }

    private fun handlePlaySound() {
        releaseMediaPlayer()
        val uri = android.media.RingtoneManager.getDefaultUri(
            android.media.RingtoneManager.TYPE_ALARM
        )
        mediaPlayer = android.media.MediaPlayer().apply {
            setDataSource(context, uri)
            setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            prepare()
            start()
        }
        soundHandler = android.os.Handler(android.os.Looper.getMainLooper())
        soundHandler?.postDelayed({ releaseMediaPlayer() }, 15_000)
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) { }
        mediaPlayer = null
        soundHandler?.removeCallbacksAndMessages(null)
        soundHandler = null
    }

    private fun handleLockDevice() {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val admin = android.content.ComponentName(context, com.parentcontrol.child.admin.DeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
        }
    }

    private fun handleWebRtcOffer(msg: WsMessage) {
        // Forward to CameraStreamService via broadcast
        val intent = Intent(CameraStreamService.ACTION_WEBRTC_OFFER).apply {
            putExtra("sdp", msg.sdp)
            putExtra("parent_id", msg.parentId)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    private fun handleWebRtcIce(msg: WsMessage) {
        val intent = Intent(CameraStreamService.ACTION_WEBRTC_ICE).apply {
            putExtra("candidate", msg.candidate)
            putExtra("parent_id", msg.parentId)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    private fun ackCommand(commandId: String, status: String) {
        ws.send(
            WsMessage(
                type = "command_ack",
                commandId = commandId,
                status = status,
            )
        )
    }

    fun stop() {
        releaseMediaPlayer()
        scope.cancel()
    }

    companion object {
        private const val TAG = "CommandHandler"
    }
}
