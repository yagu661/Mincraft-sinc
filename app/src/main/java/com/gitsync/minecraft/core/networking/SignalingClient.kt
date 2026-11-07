package com.gitsync.minecraft.core.networking

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min

class SignalingClient(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SignalingClient"
        private const val HEARTBEAT_INTERVAL_MS = 25_000L
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
    }

    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var retryDelayMs = INITIAL_RETRY_DELAY_MS
    private var isExplicitDisconnect = false

    // Target parameters for auto-reconnect
    private var currentServerUrl: String = ""
    private var currentRoomId: String = "friendgroup123"
    private var currentDeviceId: String = ""
    private var currentDisplayName: String = ""
    private var currentRole: String = ""
    private var currentAndroidVersion: Int = 11

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedPeers = MutableStateFlow<Map<String, String>>(emptyMap())
    val connectedPeers: StateFlow<Map<String, String>> = _connectedPeers.asStateFlow()

    // Callbacks
    var onOfferReceived: ((sdp: String, senderId: String) -> Unit)? = null
    var onAnswerReceived: ((sdp: String, senderId: String) -> Unit)? = null
    var onIceCandidateReceived: ((sdpMid: String, sdpMLineIndex: Int, candidate: String) -> Unit)? = null
    var onSyncNotificationReceived: ((worldId: String, commitId: String, sha256: String, downloadUrl: String, authorName: String) -> Unit)? = null
    var onLeaseUpdateReceived: ((type: String, worldId: String, holderDeviceId: String, holderDisplayName: String) -> Unit)? = null
    var onPeerPresenceChanged: ((deviceId: String, displayName: String, isOnline: Boolean) -> Unit)? = null

    fun connect(
        serverUrl: String,
        roomId: String = "friendgroup123",
        deviceId: String,
        displayName: String = "Player Device",
        role: String = "OWNER_DEVICE_A",
        androidVersion: Int = 11
    ) {
        isExplicitDisconnect = false
        currentServerUrl = serverUrl
        currentRoomId = roomId
        currentDeviceId = deviceId
        currentDisplayName = displayName
        currentRole = role
        currentAndroidVersion = androidVersion

        reconnectJob?.cancel()
        performConnection()
    }

    private fun performConnection() {
        disconnectInternal(keepExplicitFlag = true)

        var wsUrl = currentServerUrl.trim()
        if (wsUrl.startsWith("http://")) wsUrl = wsUrl.replace("http://", "ws://")
        else if (wsUrl.startsWith("https://")) wsUrl = wsUrl.replace("https://", "wss://")

        // Ensure room parameter is attached
        if (!wsUrl.contains("?room=")) {
            wsUrl = if (wsUrl.contains("?")) "$wsUrl&room=$currentRoomId" else "$wsUrl?room=$currentRoomId"
        }

        Log.i(TAG, "Connecting to Cloudflare WebSocket: $wsUrl for device: $currentDeviceId (Room: $currentRoomId)")
        _connectionStatus.value = if (retryDelayMs > INITIAL_RETRY_DELAY_MS) ConnectionStatus.RECONNECTING else ConnectionStatus.CONNECTING

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("X-Device-Id", currentDeviceId)
            .addHeader("X-Device-Name", currentDisplayName)
            .addHeader("X-Room-Id", currentRoomId)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "Connected to Cloudflare room: $currentRoomId")
                _isConnected.value = true
                _connectionStatus.value = ConnectionStatus.CONNECTED
                retryDelayMs = INITIAL_RETRY_DELAY_MS

                // Send Device Registration into room
                val registerMsg = JSONObject().apply {
                    put("type", "DEVICE_REGISTER")
                    put("deviceId", currentDeviceId)
                    put("displayName", currentDisplayName)
                    put("role", currentRole)
                    put("androidVersion", currentAndroidVersion)
                    put("room", currentRoomId)
                    put("timestamp", System.currentTimeMillis())
                }
                ws.send(registerMsg.toString())

                startHeartbeat()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closing ($code): $reason")
                _isConnected.value = false
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closed ($code): $reason")
                _isConnected.value = false
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket connection error: ${t.message}")
                _isConnected.value = false
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                stopHeartbeat()
                scheduleReconnect()
            }
        })
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")

            when (type) {
                "PONG", "HEARTBEAT_ACK" -> {
                    // Heartbeat acknowledgment from Cloudflare Worker
                }

                "DEVICE_REGISTER" -> {
                    val peerId = json.optString("deviceId")
                    val peerName = json.optString("displayName", "Remote Phone")
                    if (peerId.isNotBlank() && peerId != currentDeviceId) {
                        Log.i(TAG, "Peer joined room $currentRoomId: $peerName ($peerId)")
                        val currentMap = _connectedPeers.value.toMutableMap()
                        currentMap[peerId] = peerName
                        _connectedPeers.value = currentMap
                        scope.launch(Dispatchers.Main) {
                            onPeerPresenceChanged?.invoke(peerId, peerName, true)
                        }
                    }
                }

                "PEER_DISCONNECTED" -> {
                    val peerId = json.optString("deviceId")
                    if (peerId.isNotBlank()) {
                        val currentMap = _connectedPeers.value.toMutableMap()
                        val name = currentMap.remove(peerId) ?: "Peer"
                        _connectedPeers.value = currentMap
                        scope.launch(Dispatchers.Main) {
                            onPeerPresenceChanged?.invoke(peerId, name, false)
                        }
                    }
                }

                "SYNC_NOTIFICATION" -> {
                    val worldId = json.getString("worldId")
                    val commitId = json.getString("commitId")
                    val sha256 = json.optString("sha256", "")
                    val downloadUrl = json.optString("downloadUrl", "")
                    val authorName = json.optString("authorName", "Friend")
                    val senderId = json.optString("senderDeviceId", "")

                    if (senderId != currentDeviceId) {
                        Log.i(TAG, "Received SYNC_NOTIFICATION for world=$worldId, commit=$commitId from $authorName")
                        scope.launch(Dispatchers.Main) {
                            onSyncNotificationReceived?.invoke(worldId, commitId, sha256, downloadUrl, authorName)
                        }
                    }
                }

                "LEASE_REQUEST", "LEASE_GRANTED", "LEASE_RELEASED" -> {
                    val worldId = json.optString("worldId")
                    val holderId = json.optString("holderDeviceId")
                    val holderName = json.optString("holderDisplayName")
                    scope.launch(Dispatchers.Main) {
                        onLeaseUpdateReceived?.invoke(type, worldId, holderId, holderName)
                    }
                }

                "OFFER" -> {
                    val sdp = json.getString("sdp")
                    val senderId = json.getString("senderId")
                    scope.launch(Dispatchers.Main) {
                        onOfferReceived?.invoke(sdp, senderId)
                    }
                }

                "ANSWER" -> {
                    val sdp = json.getString("sdp")
                    val senderId = json.getString("senderId")
                    scope.launch(Dispatchers.Main) {
                        onAnswerReceived?.invoke(sdp, senderId)
                    }
                }

                "ICE_CANDIDATE" -> {
                    val sdpMid = json.getString("sdpMid")
                    val sdpMLineIndex = json.getInt("sdpMLineIndex")
                    val candidate = json.getString("candidate")
                    scope.launch(Dispatchers.Main) {
                        onIceCandidateReceived?.invoke(sdpMid, sdpMLineIndex, candidate)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unrecognized message or parsing error: $text", e)
        }
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive && _isConnected.value) {
                delay(HEARTBEAT_INTERVAL_MS)
                try {
                    val ping = JSONObject().apply {
                        put("type", "PING")
                        put("deviceId", currentDeviceId)
                        put("room", currentRoomId)
                        put("timestamp", System.currentTimeMillis())
                    }
                    webSocket?.send(ping.toString())
                } catch (e: Exception) {
                    Log.w(TAG, "Heartbeat error", e)
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun scheduleReconnect() {
        if (isExplicitDisconnect) return
        stopHeartbeat()

        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            Log.w(TAG, "Scheduling reconnect in ${retryDelayMs}ms...")
            _connectionStatus.value = ConnectionStatus.RECONNECTING
            delay(retryDelayMs)
            retryDelayMs = min(retryDelayMs * 2, MAX_RETRY_DELAY_MS)
            performConnection()
        }
    }

    fun broadcastSyncNotification(
        worldId: String,
        commitId: String,
        sha256: String,
        downloadUrl: String,
        authorName: String
    ) {
        val msg = JSONObject().apply {
            put("type", "SYNC_NOTIFICATION")
            put("worldId", worldId)
            put("commitId", commitId)
            put("sha256", sha256)
            put("downloadUrl", downloadUrl)
            put("authorName", authorName)
            put("senderDeviceId", currentDeviceId)
            put("room", currentRoomId)
            put("timestamp", System.currentTimeMillis())
        }
        webSocket?.send(msg.toString())
    }

    fun broadcastLeaseEvent(
        eventType: String, // LEASE_REQUEST, LEASE_GRANTED, LEASE_RELEASED
        worldId: String,
        holderDeviceId: String,
        holderDisplayName: String
    ) {
        val msg = JSONObject().apply {
            put("type", eventType)
            put("worldId", worldId)
            put("holderDeviceId", holderDeviceId)
            put("holderDisplayName", holderDisplayName)
            put("room", currentRoomId)
            put("timestamp", System.currentTimeMillis())
        }
        webSocket?.send(msg.toString())
    }

    fun sendOffer(targetDeviceId: String, sdp: String) {
        val msg = JSONObject().apply {
            put("type", "OFFER")
            put("targetDeviceId", targetDeviceId)
            put("senderId", currentDeviceId)
            put("sdp", sdp)
        }
        webSocket?.send(msg.toString())
    }

    fun sendAnswer(targetDeviceId: String, sdp: String) {
        val msg = JSONObject().apply {
            put("type", "ANSWER")
            put("targetDeviceId", targetDeviceId)
            put("senderId", currentDeviceId)
            put("sdp", sdp)
        }
        webSocket?.send(msg.toString())
    }

    fun sendIceCandidate(targetDeviceId: String, sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val msg = JSONObject().apply {
            put("type", "ICE_CANDIDATE")
            put("targetDeviceId", targetDeviceId)
            put("senderId", currentDeviceId)
            put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
            put("candidate", candidate)
        }
        webSocket?.send(msg.toString())
    }

    fun disconnect() {
        isExplicitDisconnect = true
        disconnectInternal(keepExplicitFlag = false)
    }

    private fun disconnectInternal(keepExplicitFlag: Boolean) {
        if (!keepExplicitFlag) {
            isExplicitDisconnect = true
        }
        reconnectJob?.cancel()
        reconnectJob = null
        stopHeartbeat()
        try {
            webSocket?.close(1000, "Client Disconnect")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing websocket", e)
        }
        webSocket = null
        _isConnected.value = false
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }
}
