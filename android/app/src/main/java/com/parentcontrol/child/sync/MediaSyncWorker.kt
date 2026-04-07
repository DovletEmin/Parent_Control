package com.parentcontrol.child.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.parentcontrol.child.App
import com.parentcontrol.child.collector.MediaCollector
import com.parentcontrol.child.data.remote.ApiClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class MediaSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = App.instance.prefs
            val collector = MediaCollector(applicationContext)

            val photos = collector.collectNewPhotos(prefs.lastMediaSyncMs)
            val videos = collector.collectNewVideos(prefs.lastMediaSyncMs)
            val allMedia = photos + videos

            var uploaded = 0
            for (item in allMedia) {
                // Skip files larger than 100 MB
                if (item.size > 100 * 1024 * 1024) continue

                val tempFile = collector.copyToTempFile(item) ?: continue
                try {
                    val mediaType = item.mimeType.toMediaTypeOrNull()
                    val requestFile = tempFile.asRequestBody(mediaType)
                    val filePart = MultipartBody.Part.createFormData(
                        "file", item.displayName, requestFile
                    )

                    val createdAt = Instant.ofEpochMilli(item.dateAddedMs)
                        .atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    val createdAtBody = createdAt.toRequestBody("text/plain".toMediaTypeOrNull())

                    ApiClient.api.uploadMedia(filePart, createdAtBody)
                    uploaded++
                } finally {
                    tempFile.delete()
                }
            }

            prefs.lastMediaSyncMs = System.currentTimeMillis()
            Log.i(TAG, "Uploaded $uploaded media files")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Media sync failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MediaSyncWorker"
        private const val WORK_NAME = "media_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<MediaSyncWorker>(
                30, TimeUnit.MINUTES,
            ).setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
