package com.gitsync.minecraft.core.storage

import android.content.Context
import android.util.Log
import com.gitsync.minecraft.core.database.AppDatabase
import com.gitsync.minecraft.core.database.CommitEntity
import com.gitsync.minecraft.core.model.Commit
import com.gitsync.minecraft.core.model.SyncStatus
import com.gitsync.minecraft.core.security.KeystoreManager
import com.gitsync.minecraft.feature.monitoring.MinecraftUsageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WorldSnapshotManager(
    private val context: Context,
    private val database: AppDatabase,
    private val keystoreManager: KeystoreManager
) {
    companion object {
        private const val TAG = "WorldSnapshotManager"
        private const val MIN_REQUIRED_FREE_SPACE_BYTES = 75 * 1024 * 1024L // 75 MB safety margin
        private const val LEVELDB_SETTLE_WINDOW_MS = 4000L
    }

    /**
     * Executes robust snapshot generation with LevelDB settle verification.
     */
    suspend fun createValidatedSnapshot(
        worldFolder: File,
        worldId: String,
        displayName: String,
        customCommitMessage: String? = null
    ): Result<Commit> = withContext(Dispatchers.IO) {
        Log.i(TAG, "Initiating validated snapshot creation for '$displayName' at: ${worldFolder.absolutePath}")

        // 1. Verify Minecraft is NOT currently running
        if (MinecraftUsageStatsHelper.isMinecraftInForeground(context)) {
            return@withContext Result.failure(
                IllegalStateException("Minecraft is currently running in the foreground. Snapshot aborted.")
            )
        }

        // 2. Settle Check: Verify LevelDB is not actively writing (check modification timestamps)
        if (isLevelDbActivelyWriting(worldFolder)) {
            Log.w(TAG, "LevelDB files were modified recently. Waiting for disk flush settle window...")
            delay(LEVELDB_SETTLE_WINDOW_MS)
            if (isLevelDbActivelyWriting(worldFolder)) {
                return@withContext Result.failure(
                    IllegalStateException("LevelDB files continue changing. Retry scheduled.")
                )
            }
        }

        // 3. Validate world structure before snapshotting
        val levelDat = File(worldFolder, "level.dat")
        val dbDir = File(worldFolder, "db")
        if (!levelDat.exists() || !dbDir.exists() || !dbDir.isDirectory) {
            return@withContext Result.failure(
                IllegalArgumentException("World folder is corrupted or incomplete. Missing level.dat or db/ directory.")
            )
        }

        // 4. Check available storage
        val worldSize = worldFolder.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        val freeStorage = StorageAccessHelper.getAvailableStorageBytes(context.filesDir)
        if (freeStorage < (worldSize * 2 + MIN_REQUIRED_FREE_SPACE_BYTES)) {
            return@withContext Result.failure(
                IllegalStateException("Insufficient free disk space. Required: ${worldSize * 2}B, Available: ${freeStorage}B")
            )
        }

        // 5. Staged Private Copy: Copy to GitSync's private snapshot directory
        val snapshotsDir = StorageAccessHelper.getSnapshotsDirectory(context)
        val stagingDir = StorageAccessHelper.getTempDirectory(context)
        val timestamp = System.currentTimeMillis()
        val tempArchiveFile = File(stagingDir, "staging_${worldId}_$timestamp.tmp")

        try {
            // Compress into .mcworld (ZIP container)
            var fileCount = 0
            val baseUri = worldFolder.toURI()
            val buffer = ByteArray(64 * 1024)

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempArchiveFile), 64 * 1024)).use { zos ->
                zos.setLevel(6) // Balanced fast compression
                worldFolder.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relativePath = baseUri.relativize(file.toURI()).path
                    val entry = ZipEntry(relativePath).apply { time = file.lastModified() }
                    zos.putNextEntry(entry)

                    FileInputStream(file).use { fis ->
                        var read: Int
                        while (fis.read(buffer).also { read = it } != -1) {
                            zos.write(buffer, 0, read)
                        }
                    }
                    zos.closeEntry()
                    fileCount++
                }
            }

            if (!tempArchiveFile.exists() || tempArchiveFile.length() == 0L) {
                tempArchiveFile.delete()
                return@withContext Result.failure(IllegalStateException("Generated world archive is empty."))
            }

            // 6. Calculate streaming cryptographic SHA-256
            val sha256 = Sha256Checksum.calculate(tempArchiveFile)

            // 7. Check if identical to the latest local commit (avoid empty identical commits)
            val parentCommitEntity = database.commitDao().getLatestCommit(worldId)
            val parentCommit = parentCommitEntity?.toModel()
            if (parentCommit != null && parentCommit.sha256Checksum.equals(sha256, ignoreCase = true)) {
                Log.i(TAG, "World has not changed since commit ${parentCommit.commitId}. Skipping duplicate snapshot.")
                tempArchiveFile.delete()
                return@withContext Result.success(parentCommit)
            }

            // 8. Commit final archive file into snapshots directory
            val finalArchiveName = "${worldId}_${timestamp}_${sha256.take(8)}.mcworld"
            val finalArchiveFile = File(snapshotsDir, finalArchiveName)
            if (!tempArchiveFile.renameTo(finalArchiveFile)) {
                tempArchiveFile.copyTo(finalArchiveFile, overwrite = true)
                tempArchiveFile.delete()
            }

            // 9. Record commit in Room database
            val identity = keystoreManager.getDeviceIdentity()
            val commitId = "c_${UUID.randomUUID().toString().replace("-", "").take(10)}"
            val commitNumber = database.commitDao().getCommitsForWorld(worldId).size + 1
            val commitMessage = customCommitMessage ?: "v$commitNumber - Session by ${identity.displayName} (${finalArchiveFile.length() / 1024} KB)"

            val commit = Commit(
                commitId = commitId,
                worldId = worldId,
                parentCommitId = parentCommit?.commitId,
                timestamp = timestamp,
                deviceId = identity.deviceId,
                authorName = identity.displayName,
                commitMessage = commitMessage,
                fileSizeBytes = finalArchiveFile.length(),
                sha256Checksum = sha256,
                snapshotFilePath = finalArchiveFile.absolutePath,
                syncStatus = SyncStatus.LOCAL_ONLY
            )

            database.commitDao().insertCommit(CommitEntity.fromModel(commit))
            Log.i(TAG, "Successfully created and stored snapshot commit: $commitId (SHA-256: $sha256)")

            Result.success(commit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating world snapshot", e)
            if (tempArchiveFile.exists()) tempArchiveFile.delete()
            Result.failure(e)
        }
    }

    private fun isLevelDbActivelyWriting(worldFolder: File): Boolean {
        val dbDir = File(worldFolder, "db")
        if (!dbDir.exists()) return false

        val now = System.currentTimeMillis()
        val files = dbDir.listFiles() ?: return false
        // If any LevelDB file (*.log, MANIFEST-*, *.ldb) was modified within 4 seconds, writes are ongoing
        return files.any { (now - it.lastModified()) < 4000L }
    }
}
