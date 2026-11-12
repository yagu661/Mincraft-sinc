package com.gitsync.minecraft.core.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest

/**
 * SAF-based Google Drive Shared Folder Manager.
 *
 * Implements Google Drive synchronization entirely via Android's Storage Access Framework (SAF)
 * and DocumentProvider. No Google Cloud Console billing, OAuth client IDs, API keys, or manual tokens.
 *
 * Each device selects the shared "Minecraft Sync" folder independently using its own Google account
 * via Android's native ACTION_OPEN_DOCUMENT_TREE folder picker.
 */
class SafDriveFolderManager(
    private val context: Context,
    private val getFolderTreeUri: () -> Uri?
) {
    companion object {
        private const val TAG = "SafDriveFolderManager"
        const val TEST_VERIFICATION_FILE = "git_sync_saf_test.tmp"
        private const val BUFFER_SIZE = 64 * 1024
    }

    data class SafBackupFileInfo(
        val uri: Uri,
        val fileName: String,
        val fileSizeBytes: Long,
        val lastModifiedEpoch: Long,
        val worldId: String,
        val commitId: String,
        val sha256Checksum: String?
    )

    data class SafVerificationResult(
        val isSuccessful: Boolean,
        val canRead: Boolean,
        val canWrite: Boolean,
        val canDelete: Boolean,
        val folderDisplayName: String?,
        val errorMessage: String? = null
    )

    /**
     * Resolves the root DocumentFile for the persisted Google Drive shared folder tree URI.
     */
    fun getRootDocumentFolder(): DocumentFile? {
        val uri = getFolderTreeUri() ?: return null
        return try {
            DocumentFile.fromTreeUri(context, uri)?.takeIf { it.exists() && it.isDirectory }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving DocumentFile from tree URI: $uri", e)
            null
        }
    }

    /**
     * Actively verifies that the selected Google Drive SAF folder supports read, write,
     * and delete operations by creating, reading, and removing a small verification file.
     */
    suspend fun verifyFolderReadWriteAccess(): SafVerificationResult = withContext(Dispatchers.IO) {
        val root = getRootDocumentFolder()
        if (root == null) {
            return@withContext SafVerificationResult(
                isSuccessful = false,
                canRead = false,
                canWrite = false,
                canDelete = false,
                folderDisplayName = null,
                errorMessage = "Folder tree URI is invalid or permission was revoked. Please select the folder again."
            )
        }

        val folderName = root.name ?: "Minecraft Sync"
        var canRead = false
        var canWrite = false
        var canDelete = false

        try {
            // Clean up any stale verification test file
            root.findFile(TEST_VERIFICATION_FILE)?.delete()

            // 1. Test WRITE
            val testFile = root.createFile("text/plain", TEST_VERIFICATION_FILE)
            if (testFile != null) {
                context.contentResolver.openOutputStream(testFile.uri, "w")?.use { os ->
                    os.write("GitSync SAF Verification Check: ${System.currentTimeMillis()}".toByteArray())
                    os.flush()
                }
                canWrite = true

                // 2. Test READ
                val readContent = context.contentResolver.openInputStream(testFile.uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                }
                if (!readContent.isNullOrBlank() && readContent.contains("GitSync SAF")) {
                    canRead = true
                }

                // 3. Test DELETE
                canDelete = testFile.delete()
            }

            val success = canRead && canWrite
            SafVerificationResult(
                isSuccessful = success,
                canRead = canRead,
                canWrite = canWrite,
                canDelete = canDelete,
                folderDisplayName = folderName,
                errorMessage = if (success) null else "Folder verification incomplete: read=$canRead, write=$canWrite, delete=$canDelete"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during SAF verification", e)
            SafVerificationResult(
                isSuccessful = false,
                canRead = canRead,
                canWrite = canWrite,
                canDelete = canDelete,
                folderDisplayName = folderName,
                errorMessage = e.message ?: "SAF file provider access failed"
            )
        }
    }

    /**
     * Uploads/copies a local Minecraft world backup file (.mcworld or .zip) into the shared
     * Google Drive SAF folder. Supports streaming progress and computes SHA-256 integrity.
     */
    suspend fun uploadBackupToDrive(
        worldId: String,
        commitId: String,
        localBackupFile: File,
        expectedSha256: String,
        authorName: String,
        deviceId: String,
        onProgress: ((bytesUploaded: Long, totalBytes: Long) -> Unit)? = null
    ): Result<SafBackupFileInfo> = withContext(Dispatchers.IO) {
        val root = getRootDocumentFolder()
            ?: return@withContext Result.failure(IOException("Google Drive folder not connected or inaccessible"))

        if (!localBackupFile.exists() || localBackupFile.length() <= 0) {
            return@withContext Result.failure(FileNotFoundException("Local backup file is missing or empty"))
        }

        val totalBytes = localBackupFile.length()
        // File naming format preserves worldId, commitId, author, and sha256
        val targetFileName = "${worldId}__${commitId}__${authorName.replace(" ", "_")}__${expectedSha256.take(8)}.mcworld"

        try {
            // Delete existing file with the same name if previously incomplete
            root.findFile(targetFileName)?.delete()

            val driveDoc = root.createFile("application/octet-stream", targetFileName)
                ?: return@withContext Result.failure(IOException("Google Drive document provider refused to create file: $targetFileName"))

            var uploadedBytes = 0L
            val digest = MessageDigest.getInstance("SHA-256")

            localBackupFile.inputStream().buffered(BUFFER_SIZE).use { input ->
                context.contentResolver.openOutputStream(driveDoc.uri, "w")?.buffered(BUFFER_SIZE).use { output ->
                    if (output == null) {
                        return@withContext Result.failure(IOException("Failed to open output stream for document: ${driveDoc.uri}"))
                    }

                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        uploadedBytes += bytesRead
                        onProgress?.invoke(uploadedBytes, totalBytes)
                    }
                    output.flush()
                }
            }

            val computedSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (!computedSha256.equals(expectedSha256, ignoreCase = true)) {
                driveDoc.delete()
                return@withContext Result.failure(SecurityException("Upload SHA-256 mismatch! Local: $expectedSha256, Streamed: $computedSha256"))
            }

            Log.i(TAG, "Uploaded $targetFileName ($uploadedBytes bytes) successfully to shared Google Drive folder")
            Result.success(
                SafBackupFileInfo(
                    uri = driveDoc.uri,
                    fileName = targetFileName,
                    fileSizeBytes = uploadedBytes,
                    lastModifiedEpoch = System.currentTimeMillis(),
                    worldId = worldId,
                    commitId = commitId,
                    sha256Checksum = computedSha256
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading backup via SAF", e)
            Result.failure(e)
        }
    }

    /**
     * Lists all available world snapshot backup files in the shared Google Drive folder.
     */
    suspend fun listBackupsInDrive(filterWorldId: String? = null): Result<List<SafBackupFileInfo>> = withContext(Dispatchers.IO) {
        val root = getRootDocumentFolder()
            ?: return@withContext Result.failure(IOException("Google Drive folder not connected"))

        try {
            val files = root.listFiles()
            val backups = mutableListOf<SafBackupFileInfo>()

            for (doc in files) {
                if (doc.isDirectory) continue
                val name = doc.name ?: continue
                if (!name.endsWith(".mcworld", ignoreCase = true) && !name.endsWith(".zip", ignoreCase = true)) continue

                // Check formatting: <worldId>__<commitId>__<author>__<sha>
                val parts = name.split("__")
                val worldId = if (parts.isNotEmpty()) parts[0] else name.substringBefore(".mcworld")
                val commitId = if (parts.size >= 2) parts[1] else "c_unknown"
                val sha = if (parts.size >= 4) parts[3].substringBefore(".mcworld") else null

                if (filterWorldId.isNullOrBlank() || worldId == filterWorldId) {
                    backups.add(
                        SafBackupFileInfo(
                            uri = doc.uri,
                            fileName = name,
                            fileSizeBytes = doc.length(),
                            lastModifiedEpoch = doc.lastModified(),
                            worldId = worldId,
                            commitId = commitId,
                            sha256Checksum = sha
                        )
                    )
                }
            }

            // Return sorted by newest last modified first
            Result.success(backups.sortedByDescending { it.lastModifiedEpoch })
        } catch (e: Exception) {
            Log.e(TAG, "Error listing SAF backups", e)
            Result.failure(e)
        }
    }

    /**
     * Downloads/copies a backup file from the shared Google Drive folder into local staging file.
     * Computes SHA-256 checksum during streaming and verifies file integrity.
     */
    suspend fun downloadBackupFromDrive(
        backupFile: SafBackupFileInfo,
        destinationFile: File,
        expectedSha256: String?,
        onProgress: ((bytesDownloaded: Long, totalBytes: Long) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val totalBytes = backupFile.fileSizeBytes
        val tempDownload = File(destinationFile.parentFile, "${destinationFile.name}.downloading")

        try {
            if (tempDownload.exists()) tempDownload.delete()
            tempDownload.parentFile?.mkdirs()

            var downloadedBytes = 0L
            val digest = MessageDigest.getInstance("SHA-256")

            context.contentResolver.openInputStream(backupFile.uri)?.buffered(BUFFER_SIZE).use { input ->
                if (input == null) {
                    return@withContext Result.failure(IOException("Failed to open input stream from Google Drive URI: ${backupFile.uri}"))
                }

                tempDownload.outputStream().buffered(BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        onProgress?.invoke(downloadedBytes, totalBytes)
                    }
                    output.flush()
                }
            }

            // Verify checksum if available
            val computedSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (!expectedSha256.isNullOrBlank() && !expectedSha256.startsWith(computedSha256.take(8), ignoreCase = true) && !computedSha256.equals(expectedSha256, ignoreCase = true)) {
                tempDownload.delete()
                return@withContext Result.failure(SecurityException("Downloaded file checksum mismatch! Expected: $expectedSha256, got: $computedSha256"))
            }

            if (destinationFile.exists()) destinationFile.delete()
            if (!tempDownload.renameTo(destinationFile)) {
                tempDownload.copyTo(destinationFile, overwrite = true)
                tempDownload.delete()
            }

            Log.i(TAG, "Downloaded ${backupFile.fileName} ($downloadedBytes bytes) successfully to ${destinationFile.absolutePath}")
            Result.success(destinationFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading backup from SAF Drive", e)
            tempDownload.delete()
            Result.failure(e)
        }
    }

    /**
     * Enforces configurable retention: keeps latest N backups per device in Google Drive,
     * preserving conflict backups.
     */
    suspend fun enforceRetention(worldId: String, keepCount: Int = 5): Result<Int> = withContext(Dispatchers.IO) {
        val listResult = listBackupsInDrive(worldId)
        if (listResult.isFailure) return@withContext Result.failure(listResult.exceptionOrNull()!!)

        val files = listResult.getOrThrow()
        var deletedCount = 0

        // Never delete backups with "conflict" in their filename
        val nonConflictFiles = files.filter { !it.fileName.contains("conflict", ignoreCase = true) }

        if (nonConflictFiles.size > keepCount) {
            val toDelete = nonConflictFiles.drop(keepCount)
            for (fileInfo in toDelete) {
                try {
                    val doc = DocumentFile.fromSingleUri(context, fileInfo.uri)
                    if (doc?.delete() == true) {
                        deletedCount++
                        Log.i(TAG, "Retention policy: removed old backup ${fileInfo.fileName}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete old backup ${fileInfo.fileName}", e)
                }
            }
        }

        Result.success(deletedCount)
    }
}
