package com.gitsync.minecraft.core.networking

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class WebRtcDataChannelManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "WebRtcManager"
        private const val DATA_CHANNEL_LABEL = "minecraft-gitsync-channel"
        private const val MAX_BUFFERED_AMOUNT = 2 * 1024 * 1024L // 2MB backpressure ceiling
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    private val _connectionState = MutableStateFlow(PeerConnection.PeerConnectionState.NEW)
    val connectionState: StateFlow<PeerConnection.PeerConnectionState> = _connectionState.asStateFlow()

    private val _isDataChannelOpen = MutableStateFlow(false)
    val isDataChannelOpen: StateFlow<Boolean> = _isDataChannelOpen.asStateFlow()

    var onIceCandidateGenerated: ((IceCandidate) -> Unit)? = null
    var onLocalSdpCreated: ((SessionDescription) -> Unit)? = null
    var onBinaryChunkReceived: ((ByteBuffer) -> Unit)? = null

    init {
        initializePeerConnectionFactory()
    }

    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(): PeerConnection? {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            keyType = PeerConnection.KeyType.ECDSA // High performance modern cryptography
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "Generated ICE Candidate: ${candidate.sdpMid}")
                onIceCandidateGenerated?.invoke(candidate)
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                Log.i(TAG, "PeerConnection state changed: $newState")
                _connectionState.value = newState
            }

            override fun onDataChannel(dc: DataChannel) {
                Log.i(TAG, "Remote DataChannel received: ${dc.label()}")
                setupDataChannel(dc)
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onAddStream(stream: org.webrtc.MediaStream) {}
            override fun onRemoveStream(stream: org.webrtc.MediaStream) {}
            override fun onRenegotiationNeeded() {}
        })

        return peerConnection
    }

    fun createDataChannelAsSender(): DataChannel? {
        val init = DataChannel.Init().apply {
            ordered = true
            maxRetransmits = -1 // Reliable mode
        }
        val dc = peerConnection?.createDataChannel(DATA_CHANNEL_LABEL, init)
        if (dc != null) {
            setupDataChannel(dc)
        }
        return dc
    }

    private fun setupDataChannel(dc: DataChannel) {
        dataChannel = dc
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                val state = dataChannel?.state()
                Log.i(TAG, "DataChannel state changed: $state")
                _isDataChannelOpen.value = (state == DataChannel.State.OPEN)
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary) {
                    onBinaryChunkReceived?.invoke(buffer.data)
                }
            }
        })
    }

    fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.i(TAG, "Local Offer SDP set successfully")
                        onLocalSdpCreated?.invoke(desc)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(err: String?) {
                        Log.e(TAG, "Failed to set local Offer SDP: $err")
                    }
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "Failed creating Offer SDP: $err")
            }
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun handleRemoteOffer(remoteSdp: String) {
        val sessionDesc = SessionDescription(SessionDescription.Type.OFFER, remoteSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                createAnswer()
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "Failed to set remote Offer SDP: $err")
            }
        }, sessionDesc)
    }

    private fun createAnswer() {
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.i(TAG, "Local Answer SDP set successfully")
                        onLocalSdpCreated?.invoke(desc)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(err: String?) {
                        Log.e(TAG, "Failed to set local Answer SDP: $err")
                    }
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "Failed creating Answer SDP: $err")
            }
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun handleRemoteAnswer(remoteSdp: String) {
        val sessionDesc = SessionDescription(SessionDescription.Type.ANSWER, remoteSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.i(TAG, "Remote Answer SDP set successfully")
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "Failed to set remote Answer SDP: $err")
            }
        }, sessionDesc)
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        peerConnection?.addIceCandidate(iceCandidate)
    }

    /**
     * Streams a file through WebRTC DataChannel with backpressure flow control.
     */
    suspend fun streamFileWithBackpressure(
        file: File,
        metadata: ChunkProtocol.TransferMetadata,
        onProgress: (bytesSent: Long, totalBytes: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val dc = dataChannel ?: return@withContext Result.failure(IllegalStateException("DataChannel is not connected"))
        if (dc.state() != DataChannel.State.OPEN) {
            return@withContext Result.failure(IllegalStateException("DataChannel is not open"))
        }

        try {
            // 1. Send metadata header
            val metaPacket = ChunkProtocol.packMetadata(metadata)
            dc.send(DataChannel.Buffer(ByteBuffer.wrap(metaPacket), true))

            // 2. Stream chunks with backpressure
            val buffer = ByteArray(ChunkProtocol.CHUNK_PAYLOAD_SIZE)
            var bytesSent = 0L
            val totalBytes = file.length()
            var chunkIndex = 0

            FileInputStream(file).use { fis ->
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    // Backpressure check: wait if buffer exceeds ceiling
                    while (dc.bufferedAmount() > MAX_BUFFERED_AMOUNT) {
                        delay(15)
                    }

                    val chunkPacket = ChunkProtocol.packChunk(chunkIndex, buffer, 0, read)
                    dc.send(DataChannel.Buffer(ByteBuffer.wrap(chunkPacket), true))

                    bytesSent += read
                    chunkIndex++
                    onProgress(bytesSent, totalBytes)
                }
            }

            Log.i(TAG, "All $chunkIndex chunks dispatched successfully ($bytesSent bytes). Waiting for remote confirmation.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "File stream over DataChannel failed", e)
            Result.failure(e)
        }
    }

    fun sendBinaryPacket(packet: ByteArray) {
        dataChannel?.send(DataChannel.Buffer(ByteBuffer.wrap(packet), true))
    }

    fun close() {
        try {
            dataChannel?.close()
            dataChannel?.dispose()
            dataChannel = null
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null
            _isDataChannelOpen.value = false
        } catch (e: Exception) {
            Log.w(TAG, "Error closing WebRTC resources", e)
        }
    }
}
