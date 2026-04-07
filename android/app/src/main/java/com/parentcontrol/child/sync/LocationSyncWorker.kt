package com.parentcontrol.child.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.parentcontrol.child.App
import com.parentcontrol.child.collector.LocationCollector
import com.parentcontrol.child.data.model.LocationSyncRequest
import com.parentcontrol.child.data.remote.ApiClient
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class LocationSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val collector = LocationCollector(applicationContext)
            val location = collector.locationUpdates(intervalMs = 5_000L).first()

            ApiClient.api.syncLocation(LocationSyncRequest(listOf(location)))

            Log.i(TAG, "Synced location: ${location.latitude}, ${location.longitude}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Location sync failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "LocationSyncWorker"
        private const val WORK_NAME = "location_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<LocationSyncWorker>(
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
