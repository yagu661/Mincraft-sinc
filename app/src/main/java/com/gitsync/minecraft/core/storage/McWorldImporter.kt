package com.gitsync.minecraft.core.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.gitsync.minecraft.core.model.StorageCapability
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class McWorldImporter(private val context: Context) {

    companion object {
        private const val TAG = "McWorldImporter"
    }

    /**
     * Validates that an archive file is a valid .mcworld ZIP containing Bedrock world metadata.
     */
    fun validateArchiveStructure(archiveFile: File): Boolean {
        if (!archiveFile.exists() || archiveFile.length() == 0L) return false

        var hasLevelDat = false
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val name = entry!!.name.replace('\\', '/')
                    if (name == "level.dat" || name.endsWith("/level.dat")) {
                        hasLevelDat = true
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Archive structure validation failed for: ${archiveFile.name}", e)
            return false
        }
        return hasLevelDat
    }

    /**
     * Attempts atomic staged replacement into the destination world folder.
     * 1. Unpacks into a temporary staged directory
     * 2. Takes a backup of the existing world first
     * 3. Performs replacement
     * 4. If anything fails, restores the backup.
     */
    fun atomicStagedReplacement(
        archiveFile: File,
        targetWorldFolder: File
    ): Result<Unit> {
        val stagingUnpackDir = File(context.cacheDir, "unpack_staging_${System.currentTimeMillis()}")
        val backupDir = File(context.cacheDir, "world_pre_replace_backup_${System.currentTimeMillis()}")

        try {
            if (!stagingUnpackDir.mkdirs()) {
                return Result.failure(IllegalStateException("Could not create staging directory."))
            }

            // 1. Unpack archive to staging directory
            ZipInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { zis ->
                var entry: ZipEntry?
                val buffer = ByteArray(64 * 1024)
                while (zis.nextEntry.also { entry = it } != null) {
                    val curEntry = entry!!
                    val destFile = File(stagingUnpackDir, curEntry.name)
                    if (curEntry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos ->
                            var read: Int
                            while (zis.read(buffer).also { read = it } != -1) {
                                fos.write(buffer, 0, read)
                            }
                        }
                    }
                    zis.closeEntry()
                }
            }

            // 2. Backup existing world folder if it exists
            if (targetWorldFolder.exists()) {
                targetWorldFolder.copyRecursively(backupDir, overwrite = true)
            }

            // 3. Clear target world folder and replace with unpacked staged files
            if (targetWorldFolder.exists()) {
                targetWorldFolder.deleteRecursively()
            }
            targetWorldFolder.mkdirs()
            stagingUnpackDir.copyRecursively(targetWorldFolder, overwrite = true)

            Log.i(TAG, "Successfully performed atomic staged replacement for: ${targetWorldFolder.name}")
            // Cleanup backup and staging
            backupDir.deleteRecursively()
            stagingUnpackDir.deleteRecursively()
            return Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error during staged world replacement. Rolling back to pre-replacement state.", e)
            // Rollback from backupDir if it exists
            if (backupDir.exists()) {
                try {
                    targetWorldFolder.deleteRecursively()
                    targetWorldFolder.mkdirs()
                    backupDir.copyRecursively(targetWorldFolder, overwrite = true)
                    backupDir.deleteRecursively()
                } catch (rbEx: Exception) {
                    Log.e(TAG, "CRITICAL: Rollback failed during replacement error recovery", rbEx)
                }
            }
            stagingUnpackDir.deleteRecursively()
            return Result.failure(e)
        }
    }

    /**
     * Fallback for scoped storage restrictions where direct replacement is forbidden by Android OS:
     * Creates an Intent to launch Minecraft Bedrock with the .mcworld file for native one-tap import.
     */
    fun createMinecraftImportIntent(archiveFile: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, archiveFile)

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/mcworld")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            setPackage(MinecraftStorageDetector.MINECRAFT_PACKAGE)
        }
    }
}
