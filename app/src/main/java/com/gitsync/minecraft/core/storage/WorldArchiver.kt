package com.gitsync.minecraft.core.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.gitsync.minecraft.core.model.WorldFileItem
import com.gitsync.minecraft.core.model.WorldSnapshot
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WorldArchiver(private val context: Context) {

    companion object {
        private const val TAG = "WorldArchiver"
        private const val BUFFER_SIZE = 64 * 1024
        private const val MIN_REQUIRED_FREE_SPACE_BYTES = 50 * 1024 * 1024L // 50MB safety margin
    }

    /**
     * Inspects a world directory (either File or DocumentFile SAF) and checks for active modifications.
     * Bedrock LevelDB writes to files ending in `.log` or `MANIFEST-*` while active.
     */
    fun isWorldLockedOrActive(worldFolder: File): Boolean {
        val dbFolder = File(worldFolder, "db")
        if (!dbFolder.exists()) return false

        val currentFiles = dbFolder.listFiles() ?: return false
        val now = System.currentTimeMillis()
        // If any file was modified within the last 5 seconds, Minecraft may still be flushing chunks
        return currentFiles.any { (now - it.lastModified()) < 5000 }
    }

    /**
     * Takes an inventory of files in the world folder to check if files changed.
     */
    fun calculateFolderInventory(folder: File): List<WorldFileItem> {
        val items = mutableListOf<WorldFileItem>()
        val baseUri = folder.toURI()

        folder.walkTopDown().filter { it.isFile }.forEach { file ->
            val relPath = baseUri.relativize(file.toURI()).path
            items.add(
                WorldFileItem(
                    relativePath = relPath,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    sha256 = "" // Calculated on demand to avoid excessive I/O during inventory
                )
            )
        }
        return items
    }

    /**
     * Creates a consistent .mcworld (ZIP format) snapshot from a local File directory.
     * Writes first to a temp file, computes SHA-256, and renames atomically.
     */
    fun archiveLocalWorld(
        worldFolder: File,
        worldId: String,
        worldDisplayName: String
    ): Result<WorldSnapshot> {
        Log.i(TAG, "Starting snapshot creation for local world: ${worldFolder.absolutePath}")

        if (!worldFolder.exists() || !worldFolder.isDirectory) {
            return Result.failure(IllegalArgumentException("World folder does not exist: ${worldFolder.absolutePath}"))
        }

        // 1. Verify world is not being modified
        if (isWorldLockedOrActive(worldFolder)) {
            return Result.failure(IllegalStateException("Minecraft world is still being modified. Postponing snapshot."))
        }

        // 2. Estimate required storage and check available disk space
        val totalSourceSize = worldFolder.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        val freeSpace = StorageAccessHelper.getAvailableStorageBytes(context.filesDir)
        if (freeSpace < (totalSourceSize + MIN_REQUIRED_FREE_SPACE_BYTES)) {
            return Result.failure(IllegalStateException("Insufficient disk space. Required: ${totalSourceSize}B, Available: ${freeSpace}B"))
        }

        // 3. Prepare staging temp file (never write directly to final destination)
        val stagingDir = StorageAccessHelper.getTempDirectory(context)
        val timestamp = System.currentTimeMillis()
        val tempArchive = File(stagingDir, "world_${worldId}_$timestamp.tmp")

        try {
            var fileCount = 0
            val baseUri = worldFolder.toURI()

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempArchive), BUFFER_SIZE)).use { zos ->
                zos.setLevel(5) // Fast yet good compression for Bedrock LevelDB data
                val buffer = ByteArray(BUFFER_SIZE)

                worldFolder.walkTopDown().filter { it.isFile }.forEach { file ->
                    val entryName = baseUri.relativize(file.toURI()).path
                    val zipEntry = ZipEntry(entryName).apply {
                        time = file.lastModified()
                    }
                    zos.putNextEntry(zipEntry)

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

            if (!tempArchive.exists() || tempArchive.length() == 0L) {
                tempArchive.delete()
                return Result.failure(IllegalStateException("Produced archive is empty."))
            }

            // 4. Calculate SHA-256 checksum on the completed archive
            val sha256 = Sha256Checksum.calculate(tempArchive)

            // 5. Atomically move completed archive to snapshots directory
            val snapshotsDir = StorageAccessHelper.getSnapshotsDirectory(context)
            val finalArchiveName = "${worldId}_${timestamp}_${sha256.take(8)}.mcworld"
            val finalArchiveFile = File(snapshotsDir, finalArchiveName)

            if (!tempArchive.renameTo(finalArchiveFile)) {
                // Fallback copy if renameTo fails across mounts
                tempArchive.copyTo(finalArchiveFile, overwrite = true)
                tempArchive.delete()
            }

            Log.i(TAG, "Created verified snapshot: ${finalArchiveFile.name}, size=${finalArchiveFile.length()}, sha256=$sha256")

            val snapshot = WorldSnapshot(
                worldId = worldId,
                worldName = worldDisplayName,
                archiveFile = finalArchiveFile,
                fileSizeBytes = finalArchiveFile.length(),
                sha256 = sha256,
                timestamp = timestamp,
                fileCount = fileCount
            )

            return Result.success(snapshot)
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating world snapshot", e)
            if (tempArchive.exists()) tempArchive.delete()
            return Result.failure(e)
        }
    }

    /**
     * Archive world from a SAF DocumentFile tree.
     */
    fun archiveSafWorld(
        treeUri: Uri,
        worldSubfolderName: String,
        worldDisplayName: String
    ): Result<WorldSnapshot> {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return Result.failure(IllegalArgumentException("Cannot access DocumentFile for URI: $treeUri"))

        val worldDir = root.findFile(worldSubfolderName)
            ?: return Result.failure(IllegalArgumentException("World folder '$worldSubfolderName' not found in authorized tree."))

        val stagingDir = StorageAccessHelper.getTempDirectory(context)
        val timestamp = System.currentTimeMillis()
        val tempArchive = File(stagingDir, "saf_${worldSubfolderName}_$timestamp.tmp")

        try {
            var fileCount = 0
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempArchive), BUFFER_SIZE)).use { zos ->
                zos.setLevel(5)
                val buffer = ByteArray(BUFFER_SIZE)

                fun archiveDocTree(dir: DocumentFile, relativePath: String) {
                    for (file in dir.listFiles()) {
                        val path = if (relativePath.isEmpty()) file.name.orEmpty() else "$relativePath/${file.name}"
                        if (file.isDirectory) {
                            archiveDocTree(file, path)
                        } else if (file.isFile) {
                            val zipEntry = ZipEntry(path).apply {
                                time = file.lastModified()
                            }
                            zos.putNextEntry(zipEntry)
                            context.contentResolver.openInputStream(file.uri)?.use { stream ->
                                var read: Int
                                while (stream.read(buffer).also { read = it } != -1) {
                                    zos.write(buffer, 0, read)
                                }
                            }
                            zos.closeEntry()
                            fileCount++
                        }
                    }
                }

                archiveDocTree(worldDir, "")
            }

            val sha256 = Sha256Checksum.calculate(tempArchive)
            val snapshotsDir = StorageAccessHelper.getSnapshotsDirectory(context)
            val finalArchiveName = "${worldSubfolderName}_${timestamp}_${sha256.take(8)}.mcworld"
            val finalArchiveFile = File(snapshotsDir, finalArchiveName)

            if (!tempArchive.renameTo(finalArchiveFile)) {
                tempArchive.copyTo(finalArchiveFile, overwrite = true)
                tempArchive.delete()
            }

            return Result.success(
                WorldSnapshot(
                    worldId = worldSubfolderName,
                    worldName = worldDisplayName,
                    archiveFile = finalArchiveFile,
                    fileSizeBytes = finalArchiveFile.length(),
                    sha256 = sha256,
                    timestamp = timestamp,
                    fileCount = fileCount
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "SAF Archive creation failed", e)
            if (tempArchive.exists()) tempArchive.delete()
            return Result.failure(e)
        }
    }
}
