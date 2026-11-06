package com.gitsync.minecraft.core.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.gitsync.minecraft.core.model.DiscoveredWorld
import java.io.File
import java.security.MessageDigest

class WorldDiscovery(private val context: Context) {

    companion object {
        private const val TAG = "WorldDiscovery"
    }

    /**
     * Scans the external Minecraft directory and enumerates all discovered worlds.
     * Evaluates integrity of level.dat, db/ directory, and reads human-readable world names.
     */
    fun scanLocalMinecraftWorlds(parentDir: File): List<DiscoveredWorld> {
        val results = mutableListOf<DiscoveredWorld>()
        if (!parentDir.exists() || !parentDir.isDirectory) {
            Log.w(TAG, "Cannot scan non-existent directory: ${parentDir.absolutePath}")
            return emptyList()
        }

        val folders = parentDir.listFiles { f -> f.isDirectory } ?: return emptyList()

        for (worldFolder in folders) {
            try {
                val folderName = worldFolder.name
                val levelNameFile = File(worldFolder, "levelname.txt")
                val levelDatFile = File(worldFolder, "level.dat")
                val dbDir = File(worldFolder, "db")
                val iconFile = File(worldFolder, "world_icon.jpeg")

                val displayName = if (levelNameFile.exists() && levelNameFile.canRead()) {
                    runCatching { levelNameFile.readText().trim() }.getOrDefault(folderName)
                } else {
                    folderName
                }

                val hasLevelDat = levelDatFile.exists() && levelDatFile.length() > 0
                val hasDb = dbDir.exists() && dbDir.isDirectory

                // Calculate file inventory & total size
                var totalBytes = 0L
                var fileCount = 0
                var latestMod = worldFolder.lastModified()

                worldFolder.walkTopDown().forEach { f ->
                    if (f.isFile) {
                        totalBytes += f.length()
                        fileCount++
                        if (f.lastModified() > latestMod) {
                            latestMod = f.lastModified()
                        }
                    }
                }

                val isEligible = hasLevelDat && hasDb
                val ineligibleReason = when {
                    !hasLevelDat -> "Missing required Level.dat file"
                    !hasDb -> "Missing LevelDB 'db' directory"
                    else -> null
                }

                // Generate stable world ID from folder name & initial level name
                val stableId = generateStableWorldId(folderName, displayName)

                results.add(
                    DiscoveredWorld(
                        worldId = stableId,
                        folderName = folderName,
                        displayName = displayName,
                        absolutePath = worldFolder.absolutePath,
                        folderSizeBytes = totalBytes,
                        lastModifiedEpoch = latestMod,
                        fileCount = fileCount,
                        hasLevelDat = hasLevelDat,
                        hasDbDirectory = hasDb,
                        iconFile = if (iconFile.exists()) iconFile else null,
                        isEligibleForSync = isEligible,
                        ineligibleReason = ineligibleReason
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error inspecting world folder: ${worldFolder.name}", e)
            }
        }

        return results.sortedByDescending { it.lastModifiedEpoch }
    }

    /**
     * Scans worlds authorized via Storage Access Framework (SAF) DocumentFile tree.
     */
    fun scanSafMinecraftWorlds(treeUri: Uri): List<DiscoveredWorld> {
        val results = mutableListOf<DiscoveredWorld>()
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()

        for (doc in root.listFiles()) {
            if (doc.isDirectory) {
                val folderName = doc.name.orEmpty()
                val levelNameFile = doc.findFile("levelname.txt")
                val levelDatFile = doc.findFile("level.dat")
                val dbDir = doc.findFile("db")
                val iconFile = doc.findFile("world_icon.jpeg")

                val displayName = if (levelNameFile != null) {
                    StorageAccessHelper.readTextContent(context, levelNameFile.uri) ?: folderName
                } else {
                    folderName
                }

                val hasLevelDat = levelDatFile != null && levelDatFile.length() > 0
                val hasDb = dbDir != null && dbDir.isDirectory

                val stableId = generateStableWorldId(folderName, displayName)

                results.add(
                    DiscoveredWorld(
                        worldId = stableId,
                        folderName = folderName,
                        displayName = displayName,
                        absolutePath = doc.uri.toString(),
                        folderSizeBytes = doc.length(),
                        lastModifiedEpoch = doc.lastModified(),
                        fileCount = 0,
                        hasLevelDat = hasLevelDat,
                        hasDbDirectory = hasDb,
                        iconFile = null,
                        isEligibleForSync = hasLevelDat && hasDb
                    )
                )
            }
        }

        return results.sortedByDescending { it.lastModifiedEpoch }
    }

    private fun generateStableWorldId(folderName: String, displayName: String): String {
        val input = "$folderName:$displayName"
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(input.toByteArray())
        return "w_" + hash.take(6).joinToString("") { "%02x".format(it) }
    }
}
