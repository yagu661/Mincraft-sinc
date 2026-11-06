package com.gitsync.minecraft.core.networking

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SignalingClient(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SignalingClient"
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isRemoteOnline = MutableStateFlow(false)
    val isRemoteOnline: StateFlow<Boolean> = _isRemoteOnline.asStateFlow()

    var onOfferReceived: ((sdp: String, senderId: String) -> Unit)? = null
    var onAnswerReceived: ((sdp: String, senderId: String) -> Unit)? = null
    var onIceCandidateReceived: ((sdpMid: String, sdpMLineIndex: Int, candidate: String) -> Unit)? = null
    var onPendingUpdateAvailable: ((commitId: String, worldId: String, senderId: String) -> Unit)? = null

    fun connect(
        serverUrl: String,
        deviceId: String,
        token: String,
        pairedDeviceId: String?
    ) {
        disconnect()

        val wsUrl = if (serverUrl.startsWith("http://")) serverUrl.replace("http://", "ws://")
        else if (serverUrl.startsWith("https://")) serverUrl.replace("https://", "wss://")
        else serverUrl

        Log.i(TAG, "Connecting to signaling server: $wsUrl for device: $deviceId")

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("X-Device-Id", deviceId)
            .addHeader("X-Auth-Token", token)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "Signaling WebSocket connected successfully")
                _isConnected.value = true

                // Send register presence message
                val registerMsg = JSONObject().apply {
                    put("type", "REGISTER")
                    put("deviceId", deviceId)
                    put("token", token)
                    if (pairedDeviceId != null) {
                        put("pairedDeviceId", pairedDeviceId)
                    }
                }
                ws.send(registerMsg.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Signaling WebSocket closing: $code / $reason")
                _isConnected.value = false
                _isRemoteOnline.value = false
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Signaling WebSocket error", t)
                _isConnected.value = false
                _isRemoteOnline.value = false
            }
        })
    }

    private fun handleServerMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "PRESENCE_UPDATE" -> {
                    val remoteOnline = json.optBoolean("online", false)
                    _isRemoteOnline.value = remoteOnline
                    Log.i(TAG, "Remote partner online status: $remoteOnline")
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
                "PENDING_UPDATE" -> {
                    val commitId = json.getString("commitId")
                    val worldId = json.getString("worldId")
                    val senderId = json.getString("senderId")
                    scope.launch(Dispatchers.Main) {
                        onPendingUpdateAvailable?.invoke(commitId, worldId, senderId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing signaling message: $text", e)
        }
    }

    fun sendOffer(targetDeviceId: String, sdp: String) {
        val msg = JSONObject().apply {
            put("type", "OFFER")
            put("targetDeviceId", targetDeviceId)
            put("sdp", sdp)
        }
        webSocket?.send(msg.toString())
    }

    fun sendAnswer(targetDeviceId: String, sdp: String) {
        val msg = JSONObject().apply {
            put("type", "ANSWER")
            put("targetDeviceId", targetDeviceId)
            put("sdp", sdp)
        }
        webSocket?.send(msg.toString())
    }

    fun sendIceCandidate(targetDeviceId: String, sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val msg = JSONObject().apply {
            put("type", "ICE_CANDIDATE")
            put("targetDeviceId", targetDeviceId)
            put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
            put("candidate", candidate)
        }
        webSocket?.send(msg.toString())
    }

    fun notifyPendingUpdate(targetDeviceId: String, commitId: String, worldId: String) {
        val msg = JSONObject().apply {
            put("type", "PENDING_UPDATE_NOTICE")
            put("targetDeviceId", targetDeviceId)
            put("commitId", commitId)
            put("worldId", worldId)
        }
        webSocket?.send(msg.toString())
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Normal Closure")
            webSocket = null
            _isConnected.value = false
            _isRemoteOnline.value = false
        } catch (e: Exception) {
            Log.w(TAG, "Error closing signaling client", e)
        }
    }
}
