package com.gitsync.minecraft.feature.sync

import android.content.Context
import android.os.Build
import android.util.Log
import com.gitsync.minecraft.core.database.AppDatabase
import com.gitsync.minecraft.core.database.CommitEntity
import com.gitsync.minecraft.core.model.Commit
import com.gitsync.minecraft.core.model.MinecraftStorageType
import com.gitsync.minecraft.core.model.SyncState
import com.gitsync.minecraft.core.model.SyncStatus
import com.gitsync.minecraft.core.networking.ChunkProtocol
import com.gitsync.minecraft.core.networking.CloudflareStorageClient
import com.gitsync.minecraft.core.networking.FileTransferSession
import com.gitsync.minecraft.core.networking.SignalingClient
import com.gitsync.minecraft.core.networking.WebRtcDataChannelManager
import com.gitsync.minecraft.core.security.KeystoreManager
import com.gitsync.minecraft.core.storage.SafDriveFolderManager
import com.gitsync.minecraft.core.storage.McWorldImporter
import com.gitsync.minecraft.core.storage.MinecraftStorageDetector
import com.gitsync.minecraft.core.storage.StorageAccessHelper
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
        val serverUrl = keystoreManager.getSignalingServerUrl()
        val roomId = keystoreManager.getRoomId()

        val isDriveConnected = keystoreManager.isSafDriveFolderConnected()
        val driveFolderName = keystoreManager.getSafDriveFolderName() ?: "Minecraft Sync"
        val driveFolderUri = keystoreManager.getSafDriveFolderUri()?.toString()

        _syncState.update {
            it.copy(
                pairedDeviceId = pairedId,
                pairedDeviceName = keystoreManager.getPairedDeviceName(),
                isGoogleDriveConnected = isDriveConnected,
                googleDriveFolderName = driveFolderName,
                googleDriveFolderUri = driveFolderUri
            )
        }

        signalingClient.connect(
            serverUrl = serverUrl,
            roomId = roomId,
            deviceId = identity.deviceId,
            displayName = identity.displayName,
            role = if (Build.VERSION.SDK_INT <= 29) "OWNER_DEVICE_A" else "FRIEND_DEVICE_C",
            androidVersion = Build.VERSION.SDK_INT
        )

        scope.launch {
            signalingClient.isConnected.collect { online ->
                _syncState.update { it.copy(isLocalOnline = online) }
            }
        }

        scope.launch {
            signalingClient.connectedPeers.collect { peers ->
                val hasPeers = peers.isNotEmpty()
                _syncState.update {
                    it.copy(
                        isRemoteOnline = hasPeers,
                        pairedDeviceName = peers.values.firstOrNull() ?: it.pairedDeviceName
                    )
                }
                if (hasPeers) {
                    Log.i(TAG, "Peers connected in Cloudflare room $roomId: ${peers.values.joinToString()}. Checking pending sync.")
                    triggerPendingPushSync()
                }
            }
        }
    }

    private fun setupSignalingCallbacks() {
        signalingClient.onSyncNotificationReceived = { worldId, commitId, sha256, downloadUrl, authorName ->
            scope.launch {
                handleRemoteSyncNotification(worldId, commitId, sha256, downloadUrl, authorName)
            }
        }

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
     * Handles an incoming synchronization notification broadcast through Cloudflare WebSocket.
     * Downloads the snapshot from Cloudflare R2, verifies checksum, and installs safely.
     */
    suspend fun handleRemoteSyncNotification(
        worldId: String,
        commitId: String,
        sha256: String,
        downloadUrl: String,
        authorName: String
    ) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Handling remote sync notice: world=$worldId, commit=$commitId from $authorName")

        // 1. Check if we already have this commit
        val existing = database.commitDao().getCommitById(commitId)
        if (existing != null) {
            Log.i(TAG, "Commit $commitId is already in local database. Skipping redundant download.")
            return@withContext
        }

        // 2. Check if Minecraft is running on this device
        if (MinecraftUsageStatsHelper.isMinecraftInForeground(context)) {
            Log.w(TAG, "Minecraft is currently running! Staging snapshot for post-game import.")
            _syncState.update {
                it.copy(
                    currentSyncStatus = SyncStatus.AWAITING_IMPORT,
                    lastError = "Minecraft is currently running. Snapshot preserved for safe import."
                )
            }
            NotificationHelper.showImportReadyNotification(context, worldId, "New update from $authorName")
            return@withContext
        }

        _syncState.update {
            it.copy(
                currentSyncStatus = SyncStatus.RECEIVING,
                activeTransferProgress = 0f
            )
        }

        // 3. Download snapshot binary from SAF Google Drive Shared Folder (or Cloudflare fallback)
        val tempDir = StorageAccessHelper.getTempDirectory(context)
        val targetArchive = File(tempDir, "$commitId.mcworld")
        var downloadSuccess = false
        var downloadErrorMsg: String? = null

        val safDriveUri = keystoreManager.getSafDriveFolderUri()
        if (safDriveUri != null) {
            val safManager = SafDriveFolderManager(context) { keystoreManager.getSafDriveFolderUri() }
            val driveListRes = safManager.listBackupsInDrive(worldId)
            if (driveListRes.isSuccess) {
                val candidate = driveListRes.getOrNull()?.find {
                    it.commitId == commitId || it.fileName.contains(commitId) || (it.sha256Checksum != null && sha256.startsWith(it.sha256Checksum))
                }

                if (candidate != null) {
                    Log.i(TAG, "Downloading world snapshot from SAF Google Drive folder: ${candidate.fileName}")
                    val downloadRes = safManager.downloadBackupFromDrive(
                        backupFile = candidate,
                        destinationFile = targetArchive,
                        expectedSha256 = sha256,
                        onProgress = { bytesDownloaded: Long, totalBytes: Long ->
                            val progress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes.toFloat() else 0f
                            _syncState.update {
                                it.copy(currentSyncStatus = SyncStatus.RECEIVING, activeTransferProgress = progress)
                            }
                        }
                    )

                    if (downloadRes.isSuccess) {
                        downloadSuccess = true
                    } else {
                        downloadErrorMsg = downloadRes.exceptionOrNull()?.message
                        Log.w(TAG, "SAF Google Drive download failed: $downloadErrorMsg, testing fallback")
                    }
                }
            }
        }

        if (!downloadSuccess) {
            val storageClient = CloudflareStorageClient(keystoreManager.getStorageUrl())
            val downloadResult = storageClient.downloadSnapshot(
                worldId = worldId,
                commitId = commitId,
                expectedSha256 = sha256,
                targetFile = targetArchive
            ) { bytesRead, totalBytes ->
                val progress = if (totalBytes > 0) bytesRead.toFloat() / totalBytes.toFloat() else 0f
                _syncState.update {
                    it.copy(
                        currentSyncStatus = SyncStatus.RECEIVING,
                        activeTransferProgress = progress
                    )
                }
            }

            if (downloadResult.isFailure) {
                val err = downloadResult.exceptionOrNull()?.message ?: downloadErrorMsg ?: "Download failed"
                Log.e(TAG, "Download failed: $err")
                _syncState.update {
                    it.copy(currentSyncStatus = SyncStatus.FAILED, lastError = err)
                }
                return@withContext
            }
        }

        val downloadedArchive = targetArchive

        // 4. Validate archive internal Bedrock structure
        if (!importer.validateArchiveStructure(downloadedArchive)) {
            Log.e(TAG, "Downloaded file does not contain valid Bedrock level.dat structure.")
            _syncState.update {
                it.copy(currentSyncStatus = SyncStatus.FAILED, lastError = "Corrupt Bedrock archive structure")
            }
            return@withContext
        }

        val incomingCommit = Commit(
            commitId = commitId,
            worldId = worldId,
            parentCommitId = null,
            timestamp = System.currentTimeMillis(),
            deviceId = "remote",
            authorName = authorName,
            commitMessage = "Synced from $authorName via Cloudflare R2",
            fileSizeBytes = downloadedArchive.length(),
            sha256Checksum = sha256,
            snapshotFilePath = downloadedArchive.absolutePath,
            syncStatus = SyncStatus.SYNCED
        )

        // 5. Evaluate Git-style divergence
        val localHead = database.commitDao().getLatestCommit(worldId)?.toModel()
        val allLocalCommits = database.commitDao().getCommitsForWorld(worldId).map { it.toModel() }
        val relationship = DivergenceDetector.evaluate(localHead, incomingCommit, allLocalCommits)

        if (relationship == DivergenceDetector.SyncRelationship.DIVERGENT_BRANCH) {
            Log.w(TAG, "Divergent branch detected! Both devices independently modified world.")
            database.commitDao().insertCommit(CommitEntity.fromModel(incomingCommit.copy(syncStatus = SyncStatus.CONFLICT)))
            _syncState.update {
                it.copy(
                    currentSyncStatus = SyncStatus.CONFLICT,
                    hasUnresolvedConflict = true,
                    conflictingCommitA = localHead,
                    conflictingCommitB = incomingCommit
                )
            }
            NotificationHelper.showConflictNotification(context, worldId)
            return@withContext
        }

        // 6. Safe staged atomic replacement into Minecraft folder
        val capability = detector.detectCapabilities()
        val worldsDir = if (MinecraftStorageDetector.LEGACY_WORLDS_DIR.exists() && MinecraftStorageDetector.LEGACY_WORLDS_DIR.canWrite()) {
            MinecraftStorageDetector.LEGACY_WORLDS_DIR
        } else {
            MinecraftStorageDetector.SCOPED_WORLDS_DIR
        }
        val targetFolder = File(worldsDir, worldId)

        if (capability.isWriteAvailable) {
            val replaceResult = importer.atomicStagedReplacement(downloadedArchive, targetFolder)
            if (replaceResult.isSuccess) {
                database.commitDao().insertCommit(CommitEntity.fromModel(incomingCommit))
                _syncState.update {
                    it.copy(
                        currentSyncStatus = SyncStatus.SYNCED,
                        latestLocalCommit = incomingCommit
                    )
                }
                NotificationHelper.showSyncCompletedNotification(context, worldId)
                Log.i(TAG, "World '$worldId' automatically replaced and updated from Cloudflare R2!")
            } else {
                database.commitDao().insertCommit(CommitEntity.fromModel(incomingCommit.copy(syncStatus = SyncStatus.AWAITING_IMPORT)))
                _syncState.update {
                    it.copy(
                        currentSyncStatus = SyncStatus.AWAITING_IMPORT,
                        lastError = "Replacement failed: ${replaceResult.exceptionOrNull()?.message}. Snapshot saved for manual import."
                    )
                }
                NotificationHelper.showImportReadyNotification(context, worldId, "Ready to import")
            }
        } else {
            // Android 15 SAF fallback
            database.commitDao().insertCommit(CommitEntity.fromModel(incomingCommit.copy(syncStatus = SyncStatus.AWAITING_IMPORT)))
            _syncState.update {
                it.copy(
                    currentSyncStatus = SyncStatus.AWAITING_IMPORT,
                    lastError = "Android 15 storage restrictions: World staged for one-tap import into Minecraft."
                )
            }
            NotificationHelper.showImportReadyNotification(context, worldId, "Ready to import")
        }
    }

    private suspend fun processReceivedSnapshot(
        archiveFile: File,
        metadata: ChunkProtocol.TransferMetadata
    ) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Processing verified incoming P2P snapshot: ${archiveFile.name}")

        if (MinecraftUsageStatsHelper.isMinecraftInForeground(context)) {
            Log.w(TAG, "Minecraft is currently active! Staging snapshot for post-game import.")
            _syncState.update {
                it.copy(
                    currentSyncStatus = SyncStatus.AWAITING_IMPORT,
                    lastError = "Minecraft is currently running. Snapshot preserved for safe import."
                )
            }
            NotificationHelper.showImportReadyNotification(context, metadata.worldId, metadata.commitMessage)
            return@withContext
        }

        if (!importer.validateArchiveStructure(archiveFile)) {
            _syncState.update { it.copy(currentSyncStatus = SyncStatus.FAILED, lastError = "Invalid Bedrock world archive") }
            return@withContext
        }

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
        when (relationship) {
            DivergenceDetector.SyncRelationship.UP_TO_DATE -> {
                _syncState.update { it.copy(currentSyncStatus = SyncStatus.SYNCED) }
                return@withContext
            }
            DivergenceDetector.SyncRelationship.OUTDATED_REMOTE -> {
                _syncState.update { it.copy(lastError = "Remote sent older commit than local head.") }
                return@withContext
            }
            DivergenceDetector.SyncRelationship.DIVERGENT_BRANCH -> {
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
                return@withContext
            }
            DivergenceDetector.SyncRelationship.FAST_FORWARD -> {
                // proceed
            }
        }

        val capability = detector.detectCapabilities()
        val worldsDir = if (MinecraftStorageDetector.LEGACY_WORLDS_DIR.exists() && MinecraftStorageDetector.LEGACY_WORLDS_DIR.canWrite()) {
            MinecraftStorageDetector.LEGACY_WORLDS_DIR
        } else {
            MinecraftStorageDetector.SCOPED_WORLDS_DIR
        }
        val targetFolder = File(worldsDir, metadata.worldId)

        if (capability.isWriteAvailable) {
            val replaceResult = importer.atomicStagedReplacement(archiveFile, targetFolder)
            if (replaceResult.isSuccess) {
                database.commitDao().insertCommit(CommitEntity.fromModel(incomingCommit))
                _syncState.update {
                    it.copy(
                        currentSyncStatus = SyncStatus.SYNCED,
                        latestLocalCommit = incomingCommit
                    )
                }
            } else {
                database.commitDao().insertCommit(CommitEntity.fromModel(incomingCommit.copy(syncStatus = SyncStatus.AWAITING_IMPORT)))
                _syncState.update {
                    it.copy(
                        currentSyncStatus = SyncStatus.AWAITING_IMPORT,
                        lastError = "Replacement failed: ${replaceResult.exceptionOrNull()?.message}"
                    )
                }
                NotificationHelper.showImportReadyNotification(context, metadata.worldId, metadata.commitMessage)
            }
        } else {
            database.commitDao().insertCommit(CommitEntity.fromModel(incomingCommit.copy(syncStatus = SyncStatus.AWAITING_IMPORT)))
            _syncState.update {
                it.copy(currentSyncStatus = SyncStatus.AWAITING_IMPORT)
            }
            NotificationHelper.showImportReadyNotification(context, metadata.worldId, metadata.commitMessage)
        }
    }

    /**
     * Uploads the local snapshot to Google Drive (with Cloudflare fallback) and broadcasts the sync notice to the room.
     */
    suspend fun pushCommit(commit: Commit): Result<Unit> = withContext(Dispatchers.IO) {
        val file = File(commit.snapshotFilePath)
        if (!file.exists()) {
            return@withContext Result.failure(IllegalArgumentException("Snapshot file missing: ${commit.snapshotFilePath}"))
        }

        _syncState.update {
            it.copy(
                currentSyncStatus = SyncStatus.PUSHING,
                activeTransferProgress = 0f
            )
        }

        val safDriveUri = keystoreManager.getSafDriveFolderUri()
        var uploadSuccess = false
        var publishedUrl = ""
        var uploadError: Throwable? = null

        // 1. Primary: SAF Google Drive Shared Folder Upload
        if (safDriveUri != null) {
            Log.i(TAG, "Uploading world snapshot to SAF Google Drive folder: $safDriveUri")
            val safManager = SafDriveFolderManager(context) { keystoreManager.getSafDriveFolderUri() }
            val uploadRes = safManager.uploadBackupToDrive(
                worldId = commit.worldId,
                commitId = commit.commitId,
                localBackupFile = file,
                expectedSha256 = commit.sha256Checksum,
                authorName = commit.authorName,
                deviceId = commit.deviceId,
                onProgress = { sent: Long, total: Long ->
                    val progress = if (total > 0) sent.toFloat() / total.toFloat() else 0f
                    _syncState.update { it.copy(activeTransferProgress = progress) }
                }
            )

            if (uploadRes.isSuccess) {
                val uploaded = uploadRes.getOrThrow()
                publishedUrl = "saf_drive:${uploaded.fileName}"
                uploadSuccess = true
                Log.i(TAG, "Snapshot uploaded to SAF Google Drive folder: ${uploaded.fileName}. Enforcing retention.")
                safManager.enforceRetention(commit.worldId, keystoreManager.getBackupRetentionCount())
            } else {
                uploadError = uploadRes.exceptionOrNull()
                Log.w(TAG, "SAF Google Drive upload failed: ${uploadError?.message}. Attempting fallback.")
            }
        }

        // 2. Fallback / Parallel Storage: Cloudflare
        if (!uploadSuccess) {
            val storageClient = CloudflareStorageClient(keystoreManager.getStorageUrl())
            val uploadResult = storageClient.uploadSnapshot(
                worldId = commit.worldId,
                commit = commit,
                snapshotFile = file
            ) { sent, total ->
                val progress = if (total > 0) sent.toFloat() / total.toFloat() else 0f
                _syncState.update { it.copy(activeTransferProgress = progress) }
            }

            if (uploadResult.isSuccess) {
                publishedUrl = uploadResult.getOrThrow()
                uploadSuccess = true
            } else {
                uploadError = uploadResult.exceptionOrNull()
            }
        }

        if (uploadSuccess) {
            val downloadUrl = publishedUrl
            database.commitDao().updateSyncStatus(commit.commitId, SyncStatus.SYNCED.name)
            database.syncQueueDao().markComplete(commit.commitId)

            _syncState.update {
                it.copy(
                    currentSyncStatus = SyncStatus.SYNCED,
                    latestLocalCommit = commit
                )
            }

            // Broadcast sync announcement to Cloudflare room friendgroup123
            signalingClient.broadcastSyncNotification(
                worldId = commit.worldId,
                commitId = commit.commitId,
                sha256 = commit.sha256Checksum,
                downloadUrl = downloadUrl,
                authorName = commit.authorName
            )

            Log.i(TAG, "Push to Cloudflare R2 completed & broadcast to room: ${commit.commitId}")
            Result.success(Unit)
        } else {
            val err = uploadError?.message ?: "Upload failed"
            Log.e(TAG, "World snapshot upload failed: $err")
            _syncState.update {
                it.copy(
                    currentSyncStatus = SyncStatus.FAILED,
                    lastError = err
                )
            }
            Result.failure(uploadError ?: Exception(err))
        }
    }

    /**
     * Manual PULL or device-switching check: fetches latest remote snapshot from Google Drive (or Cloudflare fallback).
     */
    suspend fun pullLatest(worldId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val safDriveUri = keystoreManager.getSafDriveFolderUri()

        // 1. Primary: Check SAF Google Drive Shared Folder for latest backup
        if (safDriveUri != null) {
            val safManager = SafDriveFolderManager(context) { keystoreManager.getSafDriveFolderUri() }
            val listRes = safManager.listBackupsInDrive(worldId)
            if (listRes.isSuccess) {
                val latestDriveFile = listRes.getOrNull()?.firstOrNull()
                if (latestDriveFile != null) {
                    val commitId = latestDriveFile.commitId
                    val sha256 = latestDriveFile.sha256Checksum ?: ""
                    val authorName = "Drive Backup (${latestDriveFile.fileName})"
                    val downloadUrl = "saf_drive:${latestDriveFile.fileName}"

                    Log.i(TAG, "Pulling newest snapshot from SAF Google Drive folder: commit=$commitId, file=${latestDriveFile.fileName}")
                    handleRemoteSyncNotification(worldId, commitId, sha256, downloadUrl, authorName)
                    return@withContext Result.success(Unit)
                }
            }
        }

        // 2. Fallback: Query Cloudflare Worker
        val storageClient = CloudflareStorageClient(keystoreManager.getStorageUrl())
        val latestMetaResult = storageClient.getLatestRemoteSnapshot(worldId)
        val json = latestMetaResult.getOrNull()
            ?: return@withContext Result.failure(Exception("No remote snapshots found in Google Drive or Cloudflare for world $worldId"))

        val commitId = json.optString("commitId")
        val sha256 = json.optString("sha256Checksum")
        val authorName = json.optString("authorName", "Remote Player")
        val downloadUrl = json.optString("downloadUrl", "")

        handleRemoteSyncNotification(worldId, commitId, sha256, downloadUrl, authorName)
        Result.success(Unit)
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
