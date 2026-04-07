package com.parentcontrol.child.ui

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.parentcontrol.child.App
import com.parentcontrol.child.R
import com.parentcontrol.child.databinding.ActivityStatusBinding
import com.parentcontrol.child.ws.DeviceWebSocket
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatusBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showDeviceInfo()
        observeConnectionState()
    }

    private fun showDeviceInfo() {
        val prefs = App.instance.prefs
        val model = "${Build.MANUFACTURER} ${Build.MODEL}"
        val id = prefs.deviceId?.take(8) ?: "—"
        binding.tvDeviceInfo.text = "$model • ID: $id"

        updateLastSyncTime()
    }

    private fun updateLastSyncTime() {
        val prefs = App.instance.prefs
        val timestamps = listOf(
            prefs.lastAppUsageSyncMs,
            prefs.lastCallSyncMs,
            prefs.lastSmsSyncMs,
            prefs.lastMediaSyncMs,
        )

        val lastSync = timestamps.maxOrNull() ?: 0L
        val text = if (lastSync > 0L) {
            val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            "Последняя синхронизация: ${fmt.format(Date(lastSync))}"
        } else {
            "Последняя синхронизация: —"
        }
        binding.tvLastSync.text = text
    }

    private fun observeConnectionState() {
        lifecycleScope.launch {
            DeviceWebSocket.instance.connectionState.collectLatest { connected ->
                runOnUiThread {
                    if (connected) {
                        binding.tvConnectionStatus.text = getString(R.string.status_connected)
                        binding.statusIndicator.setBackgroundResource(0)
                        binding.statusIndicator.setBackgroundColor(getColor(R.color.green))
                    } else {
                        binding.tvConnectionStatus.text = getString(R.string.status_disconnected)
                        binding.statusIndicator.setBackgroundColor(getColor(R.color.red))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateLastSyncTime()
    }
}
