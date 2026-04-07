package com.parentcontrol.child.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Pairing ──────────────────────────────────────────────────────────

@Serializable
data class PairRequest(
    @SerialName("pairing_code") val pairingCode: String,
    @SerialName("device_model") val deviceModel: String? = null,
    @SerialName("android_version") val androidVersion: String? = null,
    @SerialName("fcm_token") val fcmToken: String? = null,
)

@Serializable
data class PairResponse(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_token") val deviceToken: String,
    val message: String,
)

// ── FCM ──────────────────────────────────────────────────────────────

@Serializable
data class UpdateFcmRequest(
    @SerialName("fcm_token") val fcmToken: String,
)

// ── Sync common ──────────────────────────────────────────────────────

@Serializable
data class SyncResponse(
    val synced: Int,
    val message: String = "ok",
)

// ── App Usage ────────────────────────────────────────────────────────

@Serializable
data class AppUsageSyncItem(
    @SerialName("package_name") val packageName: String,
    @SerialName("app_name") val appName: String,
    @SerialName("usage_seconds") val usageSeconds: Int,
    val date: String, // YYYY-MM-DD
)

@Serializable
data class AppUsageSyncRequest(
    val items: List<AppUsageSyncItem>,
)

// ── Call Log ─────────────────────────────────────────────────────────

@Serializable
data class CallLogSyncItem(
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("contact_name") val contactName: String? = null,
    @SerialName("call_type") val callType: String,
    @SerialName("duration_seconds") val durationSeconds: Int,
    @SerialName("called_at") val calledAt: String, // ISO datetime
)

@Serializable
data class CallLogSyncRequest(
    val items: List<CallLogSyncItem>,
)

// ── Messages ─────────────────────────────────────────────────────────

@Serializable
data class MessageSyncItem(
    val sender: String,
    val receiver: String? = null,
    val body: String,
    @SerialName("message_type") val messageType: String = "sms",
    @SerialName("is_incoming") val isIncoming: Boolean = true,
    @SerialName("sent_at") val sentAt: String, // ISO datetime
)

@Serializable
data class MessageSyncRequest(
    val items: List<MessageSyncItem>,
)

// ── Location ─────────────────────────────────────────────────────────

@Serializable
data class LocationSyncItem(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val altitude: Double? = null,
    val speed: Float? = null,
    @SerialName("recorded_at") val recordedAt: String, // ISO datetime
)

@Serializable
data class LocationSyncRequest(
    val items: List<LocationSyncItem>,
)

// ── Command ──────────────────────────────────────────────────────────

@Serializable
data class CommandAckRequest(
    @SerialName("command_id") val commandId: String,
    val status: String, // delivered, executed, failed
)

// ── WebSocket messages ───────────────────────────────────────────────

@Serializable
data class WsMessage(
    val type: String,
    @SerialName("command_id") val commandId: String? = null,
    @SerialName("command_type") val commandType: String? = null,
    val payload: String? = null,
    val sdp: String? = null,
    val candidate: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,
    @SerialName("recorded_at") val recordedAt: String? = null,
    val status: String? = null,
)
