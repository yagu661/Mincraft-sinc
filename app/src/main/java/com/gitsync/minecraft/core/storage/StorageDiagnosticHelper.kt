package com.gitsync.minecraft.core.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import com.gitsync.minecraft.core.model.StorageDiagnostic
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class StorageDiagnosticHelper(private val context: Context) {

    companion object {
        private const val TAG = "StorageDiagnostic"

        // Expected Minecraft Bedrock External Storage Directory on Android 11+
        const val PRIMARY_MINECRAFT_WORLDS_PATH =
            "/storage/emulated/0/Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds"

        // Legacy external games directory (Android 10 and below)
        const val LEGACY_MINECRAFT_WORLDS_PATH =
            "/storage/emulated/0/games/com.mojang/minecraftWorlds"
    }

    /**
     * Executes comprehensive diagnostic evaluation of the Minecraft external storage capability.
     * Tests read and write capabilities independently, with zero risk of modifying Minecraft world data.
     */
    fun runFullDiagnostic(): StorageDiagnostic {
        val osVersion = Build.VERSION.SDK_INT
        val targetSdk = context.applicationInfo.targetSdkVersion

        @Suppress("DEPRECATION")
        val isLegacyStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Environment.isExternalStorageLegacy()
        } else {
            true
        }

        val hasReadPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val hasWritePerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        // Check primary expected directory first
        val primaryDir = File(PRIMARY_MINECRAFT_WORLDS_PATH)
        val legacyDir = File(LEGACY_MINECRAFT_WORLDS_PATH)

        val targetDir = if (primaryDir.exists()) primaryDir else legacyDir
        val pathTested = targetDir.absolutePath

        val exists = targetDir.exists() && targetDir.isDirectory
        var canRead = false
        var enumerationSuccess = false
        var discoveredWorlds = 0
        var allWorldsHaveStructure = false

        if (exists) {
            try {
                val worldFolders = targetDir.listFiles { f -> f.isDirectory }
                if (worldFolders != null) {
                    canRead = true
                    enumerationSuccess = true
                    discoveredWorlds = worldFolders.size

                    // Check internal structure of worlds
                    var validCount = 0
                    for (folder in worldFolders) {
                        val levelDat = File(folder, "level.dat")
                        val dbDir = File(folder, "db")
                        if (levelDat.exists() && dbDir.exists() && dbDir.isDirectory) {
                            validCount++
                        }
                    }
                    allWorldsHaveStructure = (discoveredWorlds == 0 || validCount > 0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception during world directory enumeration", e)
                canRead = false
            }
        }

        // Test write access safely: NEVER in the Minecraft folder!
        // We write to GitSync's own external files directory to test filesystem write validity
        val safeWritePassed = testSafeWriteCapability()

        // Determine if safe in-place replacement is supported
        // On Android 11, in-place replacement into another app's Android/data is only possible
        // if targetSdk <= 29 + requestLegacyExternalStorage="true" + Environment.isExternalStorageLegacy() == true
        // AND the OS vendor has not restricted cross-app Android/data access.
        var safeReplacementSupported = false
        if (canRead && safeWritePassed) {
            try {
                // Check if directory can be written to by creating a test file in parent if owned,
                // or checking canWrite() on target directory without mutating files
                safeReplacementSupported = targetDir.canWrite() && isLegacyStorage
            } catch (_: Exception) {
                safeReplacementSupported = false
            }
        }

        val snapshotCreationSupported = canRead && enumerationSuccess
        val autoSyncEligible = snapshotCreationSupported

        val restrictionDetails = when {
            !hasReadPerm -> "READ_EXTERNAL_STORAGE runtime permission has not been granted by user."
            !exists -> "Minecraft world directory not found at $pathTested. Ensure Minecraft Bedrock Storage Location is set to 'External' in Minecraft Settings -> Storage."
            !canRead -> "Android 11+ Scoped Storage policy blocked direct file read access to $pathTested."
            !safeReplacementSupported -> "Direct write replacement into Minecraft's folder is restricted by Android OS. Snapshots can be captured automatically, but received worlds must use .mcworld native import."
            else -> null
        }

        val fallback = if (safeReplacementSupported) {
            "Direct in-place replacement is supported on this Android 11 compatibility build."
        } else {
            "Use GitSync Storage Access Framework (SAF) folder picker or one-tap .mcworld container import."
        }

        return StorageDiagnostic(
            androidOsVersion = osVersion,
            targetSdkVersion = targetSdk,
            isExternalStorageLegacy = isLegacyStorage,
            hasReadPermission = hasReadPerm,
            hasWritePermission = hasWritePerm,
            testedPath = pathTested,
            directoryExists = exists,
            readAccessAvailable = canRead,
            worldEnumerationSuccessful = enumerationSuccess,
            discoveredWorldsCount = discoveredWorlds,
            safeWriteTestPassed = safeWritePassed,
            snapshotCreationSupported = snapshotCreationSupported,
            safeReplacementSupported = safeReplacementSupported,
            automaticSyncEligibility = autoSyncEligible,
            restrictionDetails = restrictionDetails,
            recommendedFallback = fallback
        )
    }

    /**
     * Performs a non-destructive write test inside GitSync's private staging directory.
     * Never touches or modifies any Minecraft directory.
     */
    private fun testSafeWriteCapability(): Boolean {
        return try {
            val testDir = File(context.cacheDir, "storage_test_${UUID.randomUUID()}")
            if (!testDir.mkdirs()) return false

            val testFile = File(testDir, "write_test.tmp")
            FileOutputStream(testFile).use { fos ->
                fos.write("GitSync Storage Test Payload".toByteArray())
                fos.flush()
            }

            val readBack = testFile.readText()
            val matches = readBack == "GitSync Storage Test Payload"

            testFile.delete()
            testDir.delete()
            matches
        } catch (e: Exception) {
            Log.e(TAG, "Safe write test failed", e)
            false
        }
    }
}
