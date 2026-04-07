package com.parentcontrol.child.ui

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.parentcontrol.child.App
import com.parentcontrol.child.R
import com.parentcontrol.child.data.model.PairRequest
import com.parentcontrol.child.data.remote.ApiClient
import com.parentcontrol.child.databinding.ActivityPairingBinding
import com.parentcontrol.child.service.MonitoringService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PairingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPairingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already paired, go directly to StatusActivity
        if (App.instance.prefs.isPaired) {
            navigateToStatus()
            return
        }

        binding = ActivityPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPair.setOnClickListener {
            val code = binding.etCode.text?.toString()?.trim() ?: ""
            if (code.length != 6) {
                showError(getString(R.string.pairing_error))
                return@setOnClickListener
            }
            pair(code)
        }
    }

    private fun pair(code: String) {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val request = PairRequest(
                    pairingCode = code,
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                    androidVersion = Build.VERSION.RELEASE,
                )

                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.pairDevice(request)
                }

                // Save credentials
                App.instance.prefs.deviceToken = response.deviceToken
                App.instance.prefs.deviceId = response.deviceId

                // Request all permissions, then start monitoring
                requestAllPermissions()

            } catch (e: retrofit2.HttpException) {
                setLoading(false)
                showError(getString(R.string.pairing_error))
            } catch (e: Exception) {
                setLoading(false)
                showError("Ошибка подключения: ${e.localizedMessage}")
            }
        }
    }

    // ── Permission handling ──────────────────────────────────────────

    private val runtimePermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Regardless of grant results, proceed with background location if fine location was granted
        requestBackgroundLocationThenProceed()
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        requestSpecialPermissions()
    }

    private fun requestAllPermissions() {
        // Filter to only not-yet-granted permissions
        val needed = runtimePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed)
        } else {
            requestBackgroundLocationThenProceed()
        }
    }

    private fun requestBackgroundLocationThenProceed() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            requestSpecialPermissions()
        }
    }

    private fun requestSpecialPermissions() {
        // 1. Usage Stats
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "Разрешите доступ к статистике использования", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        // 2. Device Admin
        if (!isDeviceAdmin()) {
            val admin = ComponentName(this, com.parentcontrol.child.admin.DeviceAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.admin_description)
                )
            }
            startActivity(intent)
        }

        // 3. Battery optimization ignore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }

        // Start monitoring and go to status screen
        startMonitoringAndNavigate()
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isDeviceAdmin(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, com.parentcontrol.child.admin.DeviceAdminReceiver::class.java)
        return dpm.isAdminActive(admin)
    }

    private fun startMonitoringAndNavigate() {
        // Start foreground service
        val intent = Intent(this, MonitoringService::class.java)
        startForegroundService(intent)

        navigateToStatus()
    }

    private fun navigateToStatus() {
        startActivity(Intent(this, StatusActivity::class.java))
        finish()
    }

    // ── UI helpers ───────────────────────────────────────────────────

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnPair.isEnabled = !loading
        binding.etCode.isEnabled = !loading
        binding.tvError.visibility = View.GONE
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }
}
