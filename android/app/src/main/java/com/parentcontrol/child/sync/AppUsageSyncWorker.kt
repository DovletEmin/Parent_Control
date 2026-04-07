package com.parentcontrol.child.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.parentcontrol.child.App
import com.parentcontrol.child.collector.AppUsageCollector
import com.parentcontrol.child.data.model.AppUsageSyncRequest
import com.parentcontrol.child.data.remote.ApiClient
import java.util.concurrent.TimeUnit

class AppUsageSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val collector = AppUsageCollector(applicationContext)
            val items = collector.collect()

            if (items.isNotEmpty()) {
                ApiClient.api.syncAppUsage(AppUsageSyncRequest(items))
                App.instance.prefs.lastAppUsageSyncMs = System.currentTimeMillis()
            }

            Log.i(TAG, "Synced ${items.size} app usage items")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "App usage sync failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "AppUsageSyncWorker"
        private const val WORK_NAME = "app_usage_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<AppUsageSyncWorker>(
                15, TimeUnit.MINUTES,
            ).setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
