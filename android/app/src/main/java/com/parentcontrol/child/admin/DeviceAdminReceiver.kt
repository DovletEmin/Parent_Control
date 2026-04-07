package com.parentcontrol.child.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w(TAG, "Device admin disabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Отключение администратора приведёт к остановке родительского контроля."
    }

    companion object {
        private const val TAG = "DeviceAdminReceiver"
    }
}
