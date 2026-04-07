package com.parentcontrol.child.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.parentcontrol.child.App
import com.parentcontrol.child.data.model.MessageSyncItem
import com.parentcontrol.child.data.model.MessageSyncRequest
import com.parentcontrol.child.data.remote.ApiClient
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue

class SocialNotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pendingItems = ConcurrentLinkedQueue<MessageSyncItem>()
    private var syncJob: Job? = null

    // Targeted messaging apps
    private val trackedApps = setOf(
        "org.telegram.messenger",           // Telegram
        "org.telegram.messenger.web",       // Telegram X
        "com.whatsapp",                     // WhatsApp
        "com.whatsapp.w4b",                // WhatsApp Business
        "com.instagram.android",            // Instagram
        "com.facebook.orca",                // Messenger
        "com.facebook.mlite",              // Messenger Lite
        "com.viber.voip",                  // Viber
        "com.snapchat.android",            // Snapchat
        "com.discord",                     // Discord
        "com.tencent.mm",                  // WeChat
        "jp.naver.line.android",           // LINE
        "com.skype.raider",               // Skype
        "com.vkontakte.android",           // VK
        "ru.ok.android",                   // OK
    )

    // Map package → readable app name
    private val appNames = mapOf(
        "org.telegram.messenger" to "Telegram",
        "org.telegram.messenger.web" to "Telegram X",
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "com.instagram.android" to "Instagram",
        "com.facebook.orca" to "Messenger",
        "com.facebook.mlite" to "Messenger Lite",
        "com.viber.voip" to "Viber",
        "com.snapchat.android" to "Snapchat",
        "com.discord" to "Discord",
        "com.tencent.mm" to "WeChat",
        "jp.naver.line.android" to "LINE",
        "com.skype.raider" to "Skype",
        "com.vkontakte.android" to "VK",
        "ru.ok.android" to "OK",
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg !in trackedApps) return
        if (!App.instance.prefs.isPaired) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence("android.title")?.toString() ?: return
        val text = extras.getCharSequence("android.text")?.toString() ?: return

        // Skip summary/group notifications
        if (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return

        val appName = appNames[pkg] ?: pkg
        val isoDate = Instant.ofEpochMilli(sbn.postTime)
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val item = MessageSyncItem(
            sender = "$title ($appName)",
            receiver = null,
            body = text,
            messageType = "notification",
            isIncoming = true,
            sentAt = isoDate,
        )

        Log.d(TAG, "Captured: $appName - $title: ${text.take(50)}")
        pendingItems.add(item)
        scheduleBatchSync()
    }

    private fun scheduleBatchSync() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            delay(5_000) // Batch: wait 5s to collect multiple notifications
            flushPending()
        }
    }

    private suspend fun flushPending() {
        if (pendingItems.isEmpty()) return

        val batch = mutableListOf<MessageSyncItem>()
        while (pendingItems.isNotEmpty()) {
            pendingItems.poll()?.let { batch.add(it) }
        }

        if (batch.isEmpty()) return

        try {
            ApiClient.api.syncMessages(MessageSyncRequest(batch))
            Log.i(TAG, "Synced ${batch.size} notification messages")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync notifications, re-queuing", e)
            // Re-queue failed items
            pendingItems.addAll(batch)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Not needed
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SocialNotifService"
    }
}
