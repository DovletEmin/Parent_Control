package com.parentcontrol.child.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.parentcontrol.child.data.local.Prefs
import com.parentcontrol.child.service.MonitoringService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = Prefs(context)
        if (!prefs.isPaired) {
            Log.i(TAG, "Device not paired, skipping service start on boot")
            return
        }

        Log.i(TAG, "Boot completed — starting MonitoringService")
        val serviceIntent = Intent(context, MonitoringService::class.java)
        context.startForegroundService(serviceIntent)
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
