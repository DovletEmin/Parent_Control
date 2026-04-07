package com.parentcontrol.child.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.parentcontrol.child.App
import com.parentcontrol.child.collector.CallLogCollector
import com.parentcontrol.child.data.model.CallLogSyncRequest
import com.parentcontrol.child.data.remote.ApiClient
import java.util.concurrent.TimeUnit

class CallLogSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = App.instance.prefs
            val collector = CallLogCollector(applicationContext)
            val items = collector.collect(prefs.lastCallSyncMs)

            if (items.isNotEmpty()) {
                ApiClient.api.syncCallLogs(CallLogSyncRequest(items))
                prefs.lastCallSyncMs = System.currentTimeMillis()
            }

            Log.i(TAG, "Synced ${items.size} call log items")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Call log sync failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CallLogSyncWorker"
        private const val WORK_NAME = "call_log_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<CallLogSyncWorker>(
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
