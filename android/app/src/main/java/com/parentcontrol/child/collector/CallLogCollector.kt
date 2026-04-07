package com.parentcontrol.child.collector

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import com.parentcontrol.child.data.model.CallLogSyncItem
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class CallLogCollector(private val context: Context) {

    fun collect(sinceMs: Long): List<CallLogSyncItem> {
        val items = mutableListOf<CallLogSyncItem>()

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE,
        )

        val selection = "${CallLog.Calls.DATE} > ?"
        val selectionArgs = arrayOf(sinceMs.toString())
        val sortOrder = "${CallLog.Calls.DATE} ASC"

        val cursor: Cursor? = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )

        cursor?.use { c ->
            val numberIdx = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val nameIdx = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val typeIdx = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val durationIdx = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val dateIdx = c.getColumnIndexOrThrow(CallLog.Calls.DATE)

            while (c.moveToNext()) {
                val callType = when (c.getInt(typeIdx)) {
                    CallLog.Calls.INCOMING_TYPE -> "incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                    CallLog.Calls.MISSED_TYPE -> "missed"
                    CallLog.Calls.REJECTED_TYPE -> "rejected"
                    else -> "incoming"
                }

                val dateMs = c.getLong(dateIdx)
                val isoDate = Instant.ofEpochMilli(dateMs)
                    .atOffset(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                items.add(
                    CallLogSyncItem(
                        phoneNumber = c.getString(numberIdx) ?: "unknown",
                        contactName = c.getString(nameIdx),
                        callType = callType,
                        durationSeconds = c.getInt(durationIdx),
                        calledAt = isoDate,
                    )
                )
            }
        }

        return items
    }
}
