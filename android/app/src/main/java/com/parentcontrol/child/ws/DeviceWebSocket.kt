package com.parentcontrol.child.ws

import android.util.Log
import com.parentcontrol.child.App
import com.parentcontrol.child.data.model.WsMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import okhttp3.*
import java.util.concurrent.TimeUnit

class DeviceWebSocket {

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val _messages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<WsMessage> = _messages

    private val _connectionState = MutableSharedFlow<Boolean>(replay = 1, extraBufferCapacity = 4)
    val connectionState: SharedFlow<Boolean> = _connectionState

    @Volatile
    private var isConnected = false

    @Volatile
    private var shouldReconnect = true

    fun connect() {
        val token = App.instance.prefs.deviceToken ?: return
        val baseUrl = App.instance.prefs.serverUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/')

        val url = "$baseUrl/ws/device/$token"

        shouldReconnect = true

        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                isConnected = true
                _connectionState.tryEmit(true)
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = json.decodeFromString<WsMessage>(text)
                    _messages.tryEmit(msg)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse WS message: $text", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code")
                isConnected = false
                _connectionState.tryEmit(false)
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                isConnected = false
                _connectionState.tryEmit(false)
                scheduleReconnect()
            }
        })
    }

    fun send(message: WsMessage) {
        if (!isConnected) return
        try {
            val text = json.encodeToString(WsMessage.serializer(), message)
            webSocket?.send(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send WS message", e)
        }
    }

    fun sendRaw(jsonString: String) {
        if (!isConnected) return
        webSocket?.send(jsonString)
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isConnected = false
    }

    private fun startHeartbeat() {
        scope.launch {
            while (isConnected) {
                delay(30_000)
                if (isConnected) {
                    send(WsMessage(type = "heartbeat"))
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5_000)
            Log.i(TAG, "Attempting reconnect...")
            connect()
        }
    }

    companion object {
        private const val TAG = "DeviceWebSocket"

        val instance: DeviceWebSocket by lazy { DeviceWebSocket() }
    }
}
