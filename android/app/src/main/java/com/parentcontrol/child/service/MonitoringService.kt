package com.parentcontrol.child.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.parentcontrol.child.App
import com.parentcontrol.child.R
import com.parentcontrol.child.collector.LocationCollector
import com.parentcontrol.child.data.model.WsMessage
import com.parentcontrol.child.sync.SyncScheduler
import com.parentcontrol.child.ui.StatusActivity
import com.parentcontrol.child.ws.CommandHandler
import com.parentcontrol.child.ws.DeviceWebSocket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch

class MonitoringService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var commandHandler: CommandHandler? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MonitoringService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )

        startMonitoring()

        return START_STICKY
    }

    private fun startMonitoring() {
        // 1. Connect WebSocket
        DeviceWebSocket.instance.connect()

        // 2. Start command handler
        commandHandler = CommandHandler(this).also { it.start() }

        // 3. Schedule periodic sync workers
        SyncScheduler.scheduleAll(this)

        // 4. Continuous location tracking → send via WebSocket
        scope.launch {
            val locationCollector = LocationCollector(this@MonitoringService)
            locationCollector.locationUpdates(intervalMs = 60_000L)
                .catch { e -> Log.e(TAG, "Location flow error", e) }
                .collect { loc ->
                    DeviceWebSocket.instance.send(
                        WsMessage(
                            type = "location",
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            accuracy = loc.accuracy,
                            recordedAt = loc.recordedAt,
                        )
                    )
                }
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, StatusActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, App.CHANNEL_MONITORING)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_monitoring))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        Log.i(TAG, "MonitoringService destroyed")
        commandHandler?.stop()
        DeviceWebSocket.instance.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MonitoringService"
        const val NOTIFICATION_ID = 1001
    }
}
