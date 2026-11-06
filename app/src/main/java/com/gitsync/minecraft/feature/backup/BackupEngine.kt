package com.gitsync.minecraft.feature.backup

import android.content.Context
import android.util.Log
import com.gitsync.minecraft.core.database.AppDatabase
import com.gitsync.minecraft.core.database.CommitEntity
import com.gitsync.minecraft.core.database.SyncQueueEntity
import com.gitsync.minecraft.core.model.Commit
import com.gitsync.minecraft.core.model.MinecraftStorageType
import com.gitsync.minecraft.core.model.SyncStatus
import com.gitsync.minecraft.core.model.WorldSnapshot
import com.gitsync.minecraft.core.security.KeystoreManager
import com.gitsync.minecraft.core.storage.MinecraftStorageDetector
import com.gitsync.minecraft.core.storage.WorldArchiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class BackupEngine(
    private val context: Context,
    private val database: AppDatabase,
    private val keystoreManager: KeystoreManager
) {
    companion object {
        private const val TAG = "BackupEngine"
    }

    private val archiver = WorldArchiver(context)
    private val detector = MinecraftStorageDetector(context)

    suspend fun createSnapshotForWorld(
        worldId: String,
        customMessage: String? = null
    ): Result<Commit> = withContext(Dispatchers.IO) {
        val capability = detector.detectCapabilities()
        val latestCommitEntity = database.commitDao().getLatestCommit(worldId)
        val parentCommit = latestCommitEntity?.toModel()

        // 1. Create the snapshot archive
        val snapshotResult = when (capability.storageType) {
            MinecraftStorageType.LEGACY_GAMES_FOLDER -> {
                val worldDir = File(MinecraftStorageDetector.LEGACY_WORLDS_DIR, worldId)
                archiver.archiveLocalWorld(worldDir, worldId, worldId)
            }
            MinecraftStorageType.SAF_AUTHORIZED_TREE -> {
                val uri = capability.authorizedTreeUri ?: return@withContext Result.failure(
                    IllegalStateException("No authorized SAF URI found.")
                )
                archiver.archiveSafWorld(uri, worldId, worldId)
            }
            else -> {
                return@withContext Result.failure(
                    IllegalStateException(
                        capability.restrictionReason ?: "Direct world folder access not permitted. Please authorize folder in Storage Setup."
                    )
                )
            }
        }

        if (snapshotResult.isFailure) {
            return@withContext Result.failure(snapshotResult.exceptionOrNull()!!)
        }

        val snapshot: WorldSnapshot = snapshotResult.getOrThrow()

        // 2. Check if the world has actually changed compared to the latest commit
        if (parentCommit != null && parentCommit.sha256Checksum.equals(snapshot.sha256, ignoreCase = true)) {
            Log.i(TAG, "World has not changed since commit ${parentCommit.commitId}. Skipping duplicate snapshot.")
            // Delete redundant archive
            snapshot.archiveFile.delete()
            return@withContext Result.success(parentCommit)
        }

        // 3. Compute commit message
        val commitCount = database.commitDao().getCommitsForWorld(worldId).size + 1
        val identity = keystoreManager.getDeviceIdentity()
        val message = customMessage ?: "v$commitCount - Session by ${identity.displayName} (${snapshot.fileSizeBytes / 1024} KB)"

        val commitId = "c_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val commit = Commit(
            commitId = commitId,
            worldId = worldId,
            parentCommitId = parentCommit?.commitId,
            timestamp = snapshot.timestamp,
            deviceId = identity.deviceId,
            authorName = identity.displayName,
            commitMessage = message,
            fileSizeBytes = snapshot.fileSizeBytes,
            sha256Checksum = snapshot.sha256,
            snapshotFilePath = snapshot.archiveFile.absolutePath,
            syncStatus = SyncStatus.PENDING_PUSH
        )

        // 4. Save to Room database
        database.commitDao().insertCommit(CommitEntity.fromModel(commit))
        Log.i(TAG, "Recorded new commit $commitId for world $worldId (SHA-256: ${snapshot.sha256})")

        // 5. Add to persistent sync queue if paired device exists
        val pairedId = keystoreManager.getPairedDeviceId()
        if (pairedId != null) {
            database.syncQueueDao().enqueue(
                SyncQueueEntity(
                    commitId = commitId,
                    targetDeviceId = pairedId,
                    worldId = worldId,
                    addedTimestamp = System.currentTimeMillis()
                )
            )
            Log.i(TAG, "Commit $commitId enqueued for automatic synchronization to $pairedId")
        }

        Result.success(commit)
    }

    suspend fun restoreSnapshot(commit: Commit): Result<Unit> = withContext(Dispatchers.IO) {
        val archive = File(commit.snapshotFilePath)
        if (!archive.exists()) {
            return@withContext Result.failure(IllegalArgumentException("Snapshot archive file not found: ${commit.snapshotFilePath}"))
        }

        val capability = detector.detectCapabilities()
        val importer = com.gitsync.minecraft.core.storage.McWorldImporter(context)

        when (capability.storageType) {
            MinecraftStorageType.LEGACY_GAMES_FOLDER -> {
                val worldDir = File(MinecraftStorageDetector.LEGACY_WORLDS_DIR, commit.worldId)
                importer.atomicStagedReplacement(archive, worldDir)
            }
            else -> {
                // Return import intent instruction for user
                Result.failure(
                    UnsupportedOperationException("Automatic replacement is restricted by Android OS. Use .mcworld import to restore this snapshot.")
                )
            }
        }
    }
}
