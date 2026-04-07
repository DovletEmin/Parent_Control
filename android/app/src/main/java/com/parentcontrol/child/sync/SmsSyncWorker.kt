package com.parentcontrol.child.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.parentcontrol.child.App
import com.parentcontrol.child.collector.SmsCollector
import com.parentcontrol.child.data.model.MessageSyncRequest
import com.parentcontrol.child.data.remote.ApiClient
import java.util.concurrent.TimeUnit

class SmsSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = App.instance.prefs
            val collector = SmsCollector(applicationContext)
            val items = collector.collect(prefs.lastSmsSyncMs)

            if (items.isNotEmpty()) {
                ApiClient.api.syncMessages(MessageSyncRequest(items))
                prefs.lastSmsSyncMs = System.currentTimeMillis()
            }

            Log.i(TAG, "Synced ${items.size} SMS items")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "SMS sync failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SmsSyncWorker"
        private const val WORK_NAME = "sms_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SmsSyncWorker>(
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
