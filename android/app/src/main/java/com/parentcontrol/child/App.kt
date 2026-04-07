package com.parentcontrol.child

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import com.parentcontrol.child.data.local.Prefs
import org.webrtc.PeerConnectionFactory

class App : Application(), Configuration.Provider {

    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        createNotificationChannels()
        initWebRtc()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val monitoring = NotificationChannel(
            CHANNEL_MONITORING,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }

        val camera = NotificationChannel(
            CHANNEL_CAMERA,
            getString(R.string.notification_camera_channel),
            NotificationManager.IMPORTANCE_HIGH,
        )

        val screen = NotificationChannel(
            CHANNEL_SCREEN,
            getString(R.string.notification_screen_channel),
            NotificationManager.IMPORTANCE_HIGH,
        )

        manager.createNotificationChannel(monitoring)
        manager.createNotificationChannel(camera)
        manager.createNotificationChannel(screen)
    }

    private fun initWebRtc() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    companion object {
        const val CHANNEL_MONITORING = "monitoring_channel"
        const val CHANNEL_CAMERA = "camera_channel"
        const val CHANNEL_SCREEN = "screen_channel"

        lateinit var instance: App
            private set
    }
}
