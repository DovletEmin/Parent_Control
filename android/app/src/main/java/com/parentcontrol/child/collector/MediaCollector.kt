package com.parentcontrol.child.collector

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

data class MediaItem(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val dateAddedMs: Long,
    val size: Long,
)

class MediaCollector(private val context: Context) {

    fun collectNewPhotos(sinceMs: Long): List<MediaItem> {
        return collectMedia(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            sinceMs,
        )
    }

    fun collectNewVideos(sinceMs: Long): List<MediaItem> {
        return collectMedia(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            sinceMs,
        )
    }

    private fun collectMedia(
        collectionUri: Uri,
        sinceMs: Long,
    ): List<MediaItem> {
        val items = mutableListOf<MediaItem>()

        val sinceSeconds = sinceMs / 1000

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
        )

        val selection = "${MediaStore.MediaColumns.DATE_ADDED} > ?"
        val selectionArgs = arrayOf(sinceSeconds.toString())
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} ASC"

        val cursor = context.contentResolver.query(
            collectionUri, projection, selection, selectionArgs, sortOrder
        )

        cursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val contentUri = ContentUris.withAppendedId(collectionUri, id)

                items.add(
                    MediaItem(
                        uri = contentUri,
                        displayName = c.getString(nameIdx) ?: "unknown",
                        mimeType = c.getString(mimeIdx) ?: "application/octet-stream",
                        dateAddedMs = c.getLong(dateIdx) * 1000,
                        size = c.getLong(sizeIdx),
                    )
                )
            }
        }

        return items
    }

    fun copyToTempFile(item: MediaItem): File? {
        return try {
            val tempFile = File.createTempFile("upload_", "_${item.displayName}", context.cacheDir)
            context.contentResolver.openInputStream(item.uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }
}
