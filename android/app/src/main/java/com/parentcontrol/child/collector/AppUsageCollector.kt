package com.parentcontrol.child.collector

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.parentcontrol.child.data.model.AppUsageSyncItem
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AppUsageCollector(private val context: Context) {

    fun collect(): List<AppUsageSyncItem> {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyList()

        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDay,
            now,
        )

        if (stats.isNullOrEmpty()) return emptyList()

        val pm = context.packageManager
        val dateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        return stats
            .filter { it.totalTimeInForeground > 0 }
            .mapNotNull { stat ->
                val appName = try {
                    val appInfo = pm.getApplicationInfo(stat.packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    stat.packageName
                }

                AppUsageSyncItem(
                    packageName = stat.packageName,
                    appName = appName,
                    usageSeconds = (stat.totalTimeInForeground / 1000).toInt(),
                    date = dateStr,
                )
            }
    }

    companion object {
        fun hasPermission(context: Context): Boolean {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return false
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 60_000,
                now,
            )
            return stats != null && stats.isNotEmpty()
        }
    }
}
