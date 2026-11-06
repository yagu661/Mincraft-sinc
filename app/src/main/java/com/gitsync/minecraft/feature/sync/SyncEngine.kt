package com.gitsync.minecraft.feature.sync

import android.content.Context
import android.util.Log
import com.gitsync.minecraft.core.database.AppDatabase
import com.gitsync.minecraft.core.database.CommitEntity
import com.gitsync.minecraft.core.model.Commit
import com.gitsync.minecraft.core.model.MinecraftStorageType
import com.gitsync.minecraft.core.model.SyncState
import com.gitsync.minecraft.core.model.SyncStatus
import com.gitsync.minecraft.core.networking.ChunkProtocol
import com.gitsync.minecraft.core.networking.FileTransferSession
import com.gitsync.minecraft.core.networking.SignalingClient
import com.gitsync.minecraft.core.networking.WebRtcDataChannelManager
import com.gitsync.minecraft.core.security.KeystoreManager
import com.gitsync.minecraft.core.storage.McWorldImporter
import com.gitsync.minecraft.core.storage.MinecraftStorageDetector
import com.gitsync.minecraft.feature.backup.DivergenceDetector
import com.gitsync.minecraft.feature.monitoring.MinecraftUsageStatsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class SyncEngine(
    private val context: Context,
    private val database: AppDatabase,
    private val keystoreManager: KeystoreManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SyncEngine"
    }

    private val detector = MinecraftStorageDetector(context)
    private val importer = McWorldImporter(context)

    val signalingClient = SignalingClient(scope)
    private var webRtcManager: WebRtcDataChannelManager? = null
    private var receivingSession: FileTransferSession? = null

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init {
        setupSignalingCallbacks()
    }

    fun start() {
        val identity = keystoreManager.getDeviceIdentity()
        val pairedId = keystoreManager.getPairedDeviceId()
        val token = keystoreManager.getPairedToken() ?: identity.pairingSecret
        val serverUrl = keystoreManager.getSignalingServerUrl()

        _syncState.update {
            it.copy(
                pairedDeviceId = pairedId,
                pairedDeviceName = keystoreManager.getPairedDeviceName()
            )
        }

        signalingClient.connect(serverUrl, identity.deviceId, token, pairedId)

        scope.launch {
            signalingClient.isConnected.collect { online ->
                _syncState.update { it.copy(isLocalOnline = online) }
            }
        }

        scope.launch {
            signalingClient.isRemoteOnline.collect { remoteOnline ->
                _syncState.update { it.copy(isRemoteOnline = remoteOnline) }
                if (remoteOnline) {
                    Log.i(TAG, "Remote partner came online! Delivering any queued updates (Rule C).")
                    triggerPendingPushSync()
                }
            }
        }
    }

    private fun setupSignalingCallbacks() {
        signalingClient.onOfferReceived = { sdp, senderId ->
            Log.i(TAG, "Incoming WebRTC Offer from $senderId. Preparing answer.")
            val rtc = getOrCreateWebRtcManager()
            rtc.createPeerConnection()
            rtc.handleRemoteOffer(sdp)
        }

        signalingClient.onAnswerReceived = { sdp, _ ->
            Log.i(TAG, "Incoming WebRTC Answer. Setting remote description.")
            webRtcManager?.handleRemoteAnswer(sdp)
        }

        signalingClient.onIceCandidateReceived = { sdpMid, sdpMLineIndex, candidate ->
            webRtcManager?.addRemoteIceCandidate(sdpMid, sdpMLineIndex, candidate)
        }

        signalingClient.onPendingUpdateAvailable = { commitId, worldId, senderId ->
            Log.i(TAG, "Partner announced pending update commit=$commitId for world=$worldId")
        }
    }

    private fun getOrCreateWebRtcManager(): WebRtcDataChannelManager {
        webRtcManager?.let { return it }

        val rtc = WebRtcDataChannelManager(context, scope)
        rtc.onIceCandidateGenerated = { candidate ->
            val pairedId = keystoreManager.getPairedDeviceId()
            if (pairedId != null) {
                signalingClient.sendIceCandidate(pairedId, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
            }
        }

        rtc.onLocalSdpCreated = { desc ->
            val pairedId = keystoreManager.getPairedDeviceId()
            if (pairedId != null) {
                if (desc.type == org.webrtc.SessionDescription.Type.OFFER) {
                    signalingClient.sendOffer(pairedId, desc.description)
                } else {
                    signalingClient.sendAnswer(pairedId, desc.description)
                }
            }
        }

        rtc.onBinaryChunkReceived = { buffer ->
            handleIncomingDataBuffer(buffer)
        }

        webRtcManager = rtc
        return rtc
    }

    private fun handleIncomingDataBuffer(buffer: java.nio.ByteBuffer) {
        if (receivingSession == null) {
            receivingSession = FileTransferSession(
                context = context,
                onProgress = { received, total ->
                    val progress = if (total > 0) received.toFloat() / total.toFloat() else 0f
                    _syncState.update {
                        it.copy(
                            currentSyncStatus = SyncStatus.RECEIVING,
                            activeTransferProgress = progress
                        )
                    }
                },
                onComplete = { archiveFile, metadata ->
                    scope.launch {
                        processReceivedSnapshot(archiveFile, metadata)
                    }
                },
                onError = { error ->
                    Log.e(TAG, "Transfer session error: $error")
                    _syncState.update {
                        it.copy(
                            currentSyncStatus = SyncStatus.FAILED,
                            lastError = error
                        )
                    }
                }
            )
        }

        receivingSession?.handleIncomingBuffer(buffer)
    }

    /**
     * Executes Rule E, F, G, H, I on received snapshot.
     */
    private suspend fun processReceivedSnapshot(
        archiveFile: File,
        metadata: ChunkProtocol.TransferMetadata
    ) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Processing verified incoming snapshot: ${archiveFile.name}")

        // 1. Rule F: Never synchronize while the destination Minecraft world is actively being used.
        if (MinecraftUsageStatsHelper.isMinecraftInForeground(context)) {
            Log.w(TAG, "Minecraft is currently active! Staging snapshot for post-game import (Rule F).")
            _syncState.update {
                it.copy(
                    currentSyncStatus = SyncStatus.AWAITING_IMPORT,
                    lastError = "Minecraft is currently running. Snapshot preserved for safe import."
                )
            }
            NotificationHelper.showImportReadyNotification(context, metadata.worldId, metadata.commitMessage)
            return@withContext
        }

        // 2. Validate archive structure
        if (!importer.validateArchiveStructure(archiveFile)) {
            Log.e(TAG, "Received archive does not contain valid Minecraft Bedrock level.dat structure.")
            _syncState.update { it.copy(currentSyncStatus = SyncStatus.FAILED, lastError = "Invalid Bedrock world archive") }
            return@withContext
        }

        // 3. Check Git-style divergence & history (Rule G & Rule H)
        val localHead = database.commitDao().getLatestCommit(metadata.worldId)?.toModel()
        val allLocalCommits = database.commitDao().getCommitsForWorld(metadata.worldId).map { it.toModel() }

        val incomingCommit = Commit(
            commitId = metadata.commitId,
            worldId = metadata.worldId,
            parentCommitId = metadata.parentCommitId,
            timestamp = metadata.timestamp,
            deviceId = metadata.senderDeviceId,
            authorName = metadata.senderDisplayName,
            commitMessage = metadata.commitMessage,
            fileSizeBytes = metadata.totalSizeBytes,
            sha256Checksum = metadata.sha256Checksum,
            snapshotFilePath = archiveFile.absolutePath,
            syncStatus = SyncStatus.SYNCED
        )

        val relationship = DivergenceDetector.evaluate(localHead, incomingCommit, allLocalCommits)
        Log.i(TAG, "Divergence analysis result: $relationship")

        when (relationship) {
            DivergenceDetector.SyncRelationship.UP_TO_DATE -> {
                Log.i(TAG, "Local world is already up to date with this commit.")
                _syncState.update { it.copy(currentSyncStatus = SyncStatus.SYNCED) }
                sendStorageAck(metadata.commitId, true)
                return@withContext
            }
            DivergenceDetector.SyncRelationship.OUTDATED_REMOTE -> {
                Log.w(TAG, "Remote sent an older commit than current local head. Rejecting overwrite (Rule E).")
                _syncState.update { it.copy(lastError = "Remote sent older commit than local head.") }
                sendStorageAck(metadata.commitId, false)
                return@withContext
            }
            DivergenceDetector.SyncRelationship.DIVERGENT_BRANCH -> {
                Log.w(TAG, "Divergent branch detected! Both players modified the world concurrently (Rule G & H).")
                database.commitDao().insertCommit(CommitEntity.fromModel(incomingCommit.copy(syncStatus = SyncStatus.CONFLICT)))
                _syncState.update {
                    it.copy(
                        currentSyncStatus = SyncStatus.CONFLICT,
                        hasUnresolvedConflict = true,
                        conflictingCommitA = localHead,
                        conflictingCommitB = incomingCommit
                    )
                }
                NotificationHelper.showConflictNotification(context, metadata.worldId)
                sendStorageAck(metadata.commitId, true)
                return@withContext
            }
            DivergenceDetector.SyncRelationship.FAST_FORWARD -> {
                // Sequential progression - proceed with installation
            }
        }

        // 4. Perform atomic staged replacement if permitted (Rule I)
        val capability = detector.detectCapabilities()
        if (capability.storageType == MinecraftStorageType.LEGACY_GAMES_FOLDER && capability.isWriteAvailable) {
            val targetFolder = File(MinecraftStorageDetector.LEGACY_WORLDS_DIR, metadata.worldId)
            val replaceResult = importer.atomicStagedReplacement(archiveFile, targetFolder)

            if (replaceResult.isSuccess) {
                database.commitDao().insertCommit(CommitEntity.fromModel(incomingCommit))
                _syncState.update {
                    it.copy(
                        currentSyncStatus = SyncStatus.SYNCED,
                        latestLocalCommit = incomingCommit
                    )
                }
                sendStorageAck(metadata.commitId, true)
                Log.i(TAG, "World '${metadata.worldId}' automatically updated and synced!")
            } else {
                database.commitDao().insertCommit(CommitEntity.fromModel(incomingCommit.copy(syncStatus = SyncStatus.AWAITING_IMPORT)))
                _syncState.update {
                    it.copy(
                        currentSyncStatus = SyncStatus.AWAITING_IMPORT,
                        lastError = "Replacement failed: ${replaceResult.exceptionOrNull()?.message}. Snapshot saved for manual import."
                    )
                }
                NotificationHelper.showImportReadyNotification(context, metadata.worldId, metadata.commitMessage)
                sendStorageAck(metadata.commitId, true)
            }
        } else {
            // Android 11+ SAF or restricted sandbox
            database.commitDao().insertCommit(CommitEntity.fromModel(incomingCommit.copy(syncStatus = SyncStatus.AWAITING_IMPORT)))
            _syncState.update {
                it.copy(
                    currentSyncStatus = SyncStatus.AWAITING_IMPORT,
                    lastError = "Android Scoped Storage requires one-tap .mcworld import."
                )
            }
            NotificationHelper.showImportReadyNotification(context, metadata.worldId, metadata.commitMessage)
            sendStorageAck(metadata.commitId, true)
        }
    }

    private fun sendStorageAck(commitId: String, success: Boolean) {
        val ack = ChunkProtocol.packStorageAck(commitId, success)
        webRtcManager?.sendBinaryPacket(ack)
    }

    /**
     * Rule A & Rule C: Pushes queued commit to remote partner.
     */
    suspend fun pushCommit(commit: Commit): Result<Unit> = withContext(Dispatchers.IO) {
        val file = File(commit.snapshotFilePath)
        if (!file.exists()) {
            return@withContext Result.failure(IllegalArgumentException("Snapshot file missing: ${commit.snapshotFilePath}"))
        }

        val pairedId = keystoreManager.getPairedDeviceId()
            ?: return@withContext Result.failure(IllegalStateException("No paired buddy configured."))

        if (!_syncState.value.isRemoteOnline) {
            Log.i(TAG, "Remote partner is offline. Queuing update for later delivery (Rule B).")
            signalingClient.notifyPendingUpdate(pairedId, commit.commitId, commit.worldId)
            _syncState.update { it.copy(currentSyncStatus = SyncStatus.PENDING_PUSH) }
            return@withContext Result.success(Unit)
        }

        _syncState.update {
            it.copy(
                currentSyncStatus = SyncStatus.PUSHING,
                activeTransferProgress = 0f
            )
        }

        val rtc = getOrCreateWebRtcManager()
        rtc.createPeerConnection()
        rtc.createDataChannelAsSender()
        rtc.createOffer()

        // Wait up to 10 seconds for DataChannel open
        var waited = 0
        while (!rtc.isDataChannelOpen.value && waited < 100) {
            kotlinx.coroutines.delay(100)
            waited++
        }

        if (!rtc.isDataChannelOpen.value) {
            _syncState.update { it.copy(currentSyncStatus = SyncStatus.FAILED, lastError = "Could not establish WebRTC DataChannel connection.") }
            return@withContext Result.failure(IllegalStateException("DataChannel timeout"))
        }

        val totalChunks = ((file.length() + ChunkProtocol.CHUNK_PAYLOAD_SIZE - 1) / ChunkProtocol.CHUNK_PAYLOAD_SIZE).toInt()
        val identity = keystoreManager.getDeviceIdentity()

        val metadata = ChunkProtocol.TransferMetadata(
            transferId = UUID.randomUUID().toString(),
            commitId = commit.commitId,
            worldId = commit.worldId,
            parentCommitId = commit.parentCommitId,
            totalSizeBytes = file.length(),
            totalChunks = totalChunks,
            sha256Checksum = commit.sha256Checksum,
            commitMessage = commit.commitMessage,
            senderDeviceId = identity.deviceId,
            senderDisplayName = identity.displayName,
            timestamp = commit.timestamp
        )

        val streamResult = rtc.streamFileWithBackpressure(file, metadata) { sent, total ->
            val progress = sent.toFloat() / total.toFloat()
            _syncState.update {
                it.copy(
                    activeTransferProgress = progress
                )
            }
        }

        if (streamResult.isSuccess) {
            database.commitDao().updateSyncStatus(commit.commitId, SyncStatus.SYNCED.name)
            database.syncQueueDao().markComplete(commit.commitId)
            _syncState.update {
                it.copy(
                    currentSyncStatus = SyncStatus.SYNCED,
                    latestLocalCommit = commit
                )
            }
            Log.i(TAG, "Push completed successfully for commit ${commit.commitId}")
        } else {
            _syncState.update {
                it.copy(
                    currentSyncStatus = SyncStatus.FAILED,
                    lastError = streamResult.exceptionOrNull()?.message
                )
            }
        }

        streamResult
    }

    suspend fun triggerPendingPushSync() {
        val pendingPushes = database.commitDao().getPendingPushes()
        for (entity in pendingPushes) {
            val commit = entity.toModel()
            Log.i(TAG, "Attempting auto-push of pending commit: ${commit.commitId}")
            pushCommit(commit)
        }
    }

    fun stop() {
        signalingClient.disconnect()
        webRtcManager?.close()
        webRtcManager = null
    }
}
