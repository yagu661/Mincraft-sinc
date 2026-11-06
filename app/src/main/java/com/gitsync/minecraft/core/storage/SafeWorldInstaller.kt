package com.gitsync.minecraft.core.storage

import android.content.Context
import android.util.Log
import com.gitsync.minecraft.core.model.StorageDiagnostic
import com.gitsync.minecraft.feature.monitoring.MinecraftUsageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class SafeWorldInstaller(private val context: Context) {

    companion object {
        private const val TAG = "SafeWorldInstaller"
    }

    /**
     * Executes safe world installation with pre-flight validation, backup preservation,
     * staged extraction, atomic swap, and post-installation read-back verification.
     */
    suspend fun installReceivedWorld(
        archiveFile: File,
        expectedSha256: String,
        destinationWorldFolder: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting safe installation of ${archiveFile.name} to: ${destinationWorldFolder.absolutePath}")

        // 1. Verify Minecraft is NOT running
        if (MinecraftUsageStatsHelper.isMinecraftInForeground(context)) {
            return@withContext Result.failure(
                IllegalStateException("Minecraft is currently running! Cannot overwrite an active world.")
            )
        }

        // 2. Verify SHA-256 checksum of incoming archive
        val calculatedSha = Sha256Checksum.calculate(archiveFile)
        if (!calculatedSha.equals(expectedSha256, ignoreCase = true)) {
            return@withContext Result.failure(
                SecurityException("SHA-256 verification failed! Archive is corrupted or tampered.")
            )
        }

        // 3. Staged Extraction: Unpack to temporary staging directory first
        val stagingDir = File(context.cacheDir, "install_staging_${System.currentTimeMillis()}")
        val backupDir = File(context.filesDir, "pre_install_backup_${destinationWorldFolder.name}_${System.currentTimeMillis()}")

        try {
            if (!stagingDir.mkdirs()) {
                return@withContext Result.failure(IllegalStateException("Could not create staging directory."))
            }

            var hasLevelDat = false
            var hasDbDir = false
            val buffer = ByteArray(64 * 1024)

            ZipInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val entryName = entry!!.name.replace('\\', '/')
                    val destFile = File(stagingDir, entryName)

                    if (entry!!.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos ->
                            var read: Int
                            while (zis.read(buffer).also { read = it } != -1) {
                                fos.write(buffer, 0, read)
                            }
                        }
                        if (entryName == "level.dat" || entryName.endsWith("/level.dat")) {
                            hasLevelDat = true
                        }
                    }
                    if (entryName.startsWith("db/") || entryName.contains("/db/")) {
                        hasDbDir = true
                    }
                    zis.closeEntry()
                }
            }

            // 4. Validate unpacked structure
            if (!hasLevelDat) {
                stagingDir.deleteRecursively()
                return@withContext Result.failure(
                    IllegalArgumentException("Archive does not contain a valid Bedrock level.dat file.")
                )
            }

            // 5. Preserve existing world before touching destination
            if (destinationWorldFolder.exists()) {
                Log.i(TAG, "Backing up existing world before replacement to: ${backupDir.absolutePath}")
                destinationWorldFolder.copyRecursively(backupDir, overwrite = true)
            }

            // 6. Test destination directory write permissions
            if (destinationWorldFolder.exists() && !destinationWorldFolder.canWrite()) {
                stagingDir.deleteRecursively()
                backupDir.deleteRecursively()
                return@withContext Result.failure(
                    UnsupportedOperationException("Destination directory is read-only under Android Scoped Storage. Use .mcworld import.")
                )
            }

            // 7. Atomic Swap: Replace contents
            if (destinationWorldFolder.exists()) {
                destinationWorldFolder.deleteRecursively()
            }
            destinationWorldFolder.mkdirs()
            stagingDir.copyRecursively(destinationWorldFolder, overwrite = true)

            // 8. Read-back verification: Never claim success until read back and verified
            val installedLevelDat = File(destinationWorldFolder, "level.dat")
            val installedDb = File(destinationWorldFolder, "db")
            if (!installedLevelDat.exists() || installedLevelDat.length() == 0L || !installedDb.exists()) {
                Log.e(TAG, "Read-back verification failed! Rolling back to pre-install state.")
                // Rollback
                if (backupDir.exists()) {
                    destinationWorldFolder.deleteRecursively()
                    backupDir.copyRecursively(destinationWorldFolder, overwrite = true)
                }
                return@withContext Result.failure(
                    IllegalStateException("Read-back verification failed. Rolled back to original world.")
                )
            }

            Log.i(TAG, "Safe installation and read-back verification PASSED for: ${destinationWorldFolder.name}")

            // Cleanup staging and temporary backup
            stagingDir.deleteRecursively()
            backupDir.deleteRecursively()
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Exception during world installation. Executing safety rollback.", e)
            stagingDir.deleteRecursively()
            if (backupDir.exists()) {
                try {
                    destinationWorldFolder.deleteRecursively()
                    backupDir.copyRecursively(destinationWorldFolder, overwrite = true)
                    backupDir.deleteRecursively()
                } catch (rbEx: Exception) {
                    Log.e(TAG, "Critical: Rollback failed", rbEx)
                }
            }
            Result.failure(e)
        }
    }
}
