package com.parentcontrol.child.ws

import android.content.Context
import android.content.Intent
import android.util.Log
import com.parentcontrol.child.data.model.WsMessage
import com.parentcontrol.child.service.CameraStreamService
import com.parentcontrol.child.service.ScreenStreamService
import com.parentcontrol.child.ui.ScreenCaptureActivity
import com.parentcontrol.child.ui.CameraRequestActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

class CommandHandler(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val ws = DeviceWebSocket.instance
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var soundHandler: android.os.Handler? = null

    fun start() {
        scope.launch {
            ws.messages.collect { msg ->
                handleMessage(msg)
            }
        }
    }

    private suspend fun handleMessage(msg: WsMessage) {
        when (msg.type) {
            "command" -> handleCommand(msg)
            "webrtc_offer" -> handleWebRtcOffer(msg)
            "webrtc_ice" -> handleWebRtcIce(msg)
            "screen_offer" -> handleScreenOffer(msg)
            "screen_ice" -> handleScreenIce(msg)
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
                        startCameraRequest(pid)
                        ackCommand(commandId, "executed")
                    }
                }
                "request_screen" -> {
                    val pid = msg.parentId
                    if (pid.isNullOrBlank()) {
                        Log.w(TAG, "request_screen without parent_id")
                        ackCommand(commandId, "failed")
                    } else {
                        startScreenCapture(pid)
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
                else -> {
                    ackCommand(commandId, "executed")
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

    private fun startCameraRequest(parentId: String) {
        val intent = Intent(context, CameraRequestActivity::class.java).apply {
            putExtra(CameraRequestActivity.EXTRA_PARENT_ID, parentId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
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

    private fun startScreenCapture(parentId: String) {
        val intent = Intent(context, ScreenCaptureActivity::class.java).apply {
            putExtra(ScreenCaptureActivity.EXTRA_PARENT_ID, parentId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun handleScreenOffer(msg: WsMessage) {
        val intent = Intent(ScreenStreamService.ACTION_SCREEN_OFFER).apply {
            putExtra("sdp", msg.sdp)
            putExtra("parent_id", msg.parentId)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    private fun handleScreenIce(msg: WsMessage) {
        val intent = Intent(ScreenStreamService.ACTION_SCREEN_ICE).apply {
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
