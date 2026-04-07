package com.parentcontrol.child.collector

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import com.parentcontrol.child.data.model.MessageSyncItem
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class SmsCollector(private val context: Context) {

    fun collect(sinceMs: Long): List<MessageSyncItem> {
        val items = mutableListOf<MessageSyncItem>()

        collectFromUri(Telephony.Sms.Inbox.CONTENT_URI, sinceMs, true, items)
        collectFromUri(Telephony.Sms.Sent.CONTENT_URI, sinceMs, false, items)

        return items.sortedBy { it.sentAt }
    }

    private fun collectFromUri(
        uri: Uri,
        sinceMs: Long,
        isIncoming: Boolean,
        out: MutableList<MessageSyncItem>,
    ) {
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )

        val selection = "${Telephony.Sms.DATE} > ?"
        val selectionArgs = arrayOf(sinceMs.toString())
        val sortOrder = "${Telephony.Sms.DATE} ASC"

        val cursor: Cursor? = context.contentResolver.query(
            uri, projection, selection, selectionArgs, sortOrder
        )

        cursor?.use { c ->
            val addressIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (c.moveToNext()) {
                val address = c.getString(addressIdx) ?: "unknown"
                val body = c.getString(bodyIdx) ?: ""
                val dateMs = c.getLong(dateIdx)

                val isoDate = Instant.ofEpochMilli(dateMs)
                    .atOffset(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                out.add(
                    MessageSyncItem(
                        sender = if (isIncoming) address else "me",
                        receiver = if (isIncoming) "me" else address,
                        body = body,
                        messageType = "sms",
                        isIncoming = isIncoming,
                        sentAt = isoDate,
                    )
                )
            }
        }
    }
}
