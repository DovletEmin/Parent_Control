package com.parentcontrol.child.data.remote

import com.parentcontrol.child.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface ApiService {

    // ── Device ───────────────────────────────────────────────────────

    @POST("devices/pair")
    suspend fun pairDevice(@Body request: PairRequest): PairResponse

    @POST("devices/fcm")
    suspend fun updateFcm(@Body request: UpdateFcmRequest)

    @POST("devices/heartbeat")
    suspend fun heartbeat()

    // ── App Usage ────────────────────────────────────────────────────

    @POST("devices/apps/sync")
    suspend fun syncAppUsage(@Body request: AppUsageSyncRequest): SyncResponse

    // ── Call Logs ────────────────────────────────────────────────────

    @POST("devices/calls/sync")
    suspend fun syncCallLogs(@Body request: CallLogSyncRequest): SyncResponse

    // ── Messages ─────────────────────────────────────────────────────

    @POST("devices/messages/sync")
    suspend fun syncMessages(@Body request: MessageSyncRequest): SyncResponse

    // ── Location ─────────────────────────────────────────────────────

    @POST("devices/location/sync")
    suspend fun syncLocation(@Body request: LocationSyncRequest): SyncResponse

    // ── Media ────────────────────────────────────────────────────────

    @Multipart
    @POST("devices/media/upload")
    suspend fun uploadMedia(
        @Part file: MultipartBody.Part,
        @Part("created_at_device") createdAt: RequestBody? = null,
    ): Unit

    // ── Commands ─────────────────────────────────────────────────────

    @GET("devices/commands/pending")
    suspend fun getPendingCommands(): List<CommandResponse>

    @POST("devices/commands/ack")
    suspend fun ackCommand(@Body request: CommandAckRequest)
}

@kotlinx.serialization.Serializable
data class CommandResponse(
    val id: String,
    @kotlinx.serialization.SerialName("device_id") val deviceId: String,
    @kotlinx.serialization.SerialName("command_type") val commandType: String,
    val payload: String? = null,
    val status: String,
)
