package com.gitsync.minecraft.core.storage

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.gitsync.minecraft.core.model.MinecraftStorageType
import com.gitsync.minecraft.core.model.StorageCapability
import java.io.File

class MinecraftStorageDetector(private val context: Context) {

    companion object {
        const val MINECRAFT_PACKAGE = "com.mojang.minecraftpe"
        const val PREFS_STORAGE = "storage_configuration"
        const val KEY_SAVED_TREE_URI = "saved_tree_uri"

        // Traditional legacy path (pre-Android 11 or when Minecraft Storage is set to External legacy)
        val LEGACY_WORLDS_DIR: File
            get() = File(Environment.getExternalStorageDirectory(), "games/com.mojang/minecraftWorlds")

        // Modern Scoped Storage path
        val SCOPED_WORLDS_DIR: File
            get() = File(
                Environment.getExternalStorageDirectory(),
                "Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds"
            )
    }

    fun detectCapabilities(): StorageCapability {
        val savedUriString = context.getSharedPreferences(PREFS_STORAGE, Context.MODE_PRIVATE)
            .getString(KEY_SAVED_TREE_URI, null)

        // 1. Check if user already authorized a folder via Storage Access Framework
        if (savedUriString != null) {
            val uri = Uri.parse(savedUriString)
            val hasPersistedPermission = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && (it.isReadPermission || it.isWritePermission)
            }

            if (hasPersistedPermission) {
                val docFile = DocumentFile.fromTreeUri(context, uri)
                if (docFile != null && docFile.canRead()) {
                    val worldNames = listWorldsFromDocumentFile(docFile)
                    val canWrite = docFile.canWrite()
                    return StorageCapability(
                        storageType = MinecraftStorageType.SAF_AUTHORIZED_TREE,
                        authorizedTreeUri = uri,
                        isWorldDirectoryAccessible = true,
                        isReadAvailable = true,
                        isWriteAvailable = canWrite,
                        isPersistentUriGranted = true,
                        isAutomaticReplacementSupported = canWrite,
                        isExportImportRequired = !canWrite,
                        restrictionReason = if (!canWrite) "Authorized folder is read-only. Snapshots can be captured automatically, but received worlds must use .mcworld import." else null,
                        detectedWorldsCount = worldNames.size,
                        detectedWorldNames = worldNames
                    )
                }
            }
        }

        // 2. Check legacy external storage location (accessible on Android 10 and below, or custom ROMs)
        try {
            val legacy = LEGACY_WORLDS_DIR
            if (legacy.exists() && legacy.isDirectory && legacy.canRead()) {
                val worlds = legacy.listFiles { f -> f.isDirectory }?.map { readWorldNameFromFolder(it) } ?: emptyList()
                val canWrite = legacy.canWrite()
                return StorageCapability(
                    storageType = MinecraftStorageType.LEGACY_GAMES_FOLDER,
                    authorizedTreeUri = null,
                    isWorldDirectoryAccessible = true,
                    isReadAvailable = true,
                    isWriteAvailable = canWrite,
                    isPersistentUriGranted = false,
                    isAutomaticReplacementSupported = canWrite,
                    isExportImportRequired = false,
                    restrictionReason = null,
                    detectedWorldsCount = worlds.size,
                    detectedWorldNames = worlds
                )
            }
        } catch (_: SecurityException) {
            // Scoped storage security exception
        }

        // 3. Android 11+ (API 30+) scoped storage blocks direct access to Android/data/com.mojang.minecraftpe/
        val isAndroid11OrHigher = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val restrictionReason = if (isAndroid11OrHigher) {
            "Android 11+ Scoped Storage restricts direct file system access to 'Android/data/$MINECRAFT_PACKAGE'. " +
                    "Please authorize your Minecraft world backup folder using the Storage Access Framework (SAF) folder picker, " +
                    "or use the built-in .mcworld export/import mechanism."
        } else {
            "Minecraft world folder was not found at standard locations. Ensure Minecraft Bedrock storage location is set to 'External' in Minecraft Settings -> Storage."
        }

        return StorageCapability(
            storageType = MinecraftStorageType.STANDALONE_IMPORT_ONLY,
            authorizedTreeUri = null,
            isWorldDirectoryAccessible = false,
            isReadAvailable = false,
            isWriteAvailable = false,
            isPersistentUriGranted = false,
            isAutomaticReplacementSupported = false,
            isExportImportRequired = true,
            restrictionReason = restrictionReason,
            detectedWorldsCount = 0,
            detectedWorldNames = emptyList()
        )
    }

    fun saveAuthorizedUri(uri: Uri) {
        context.getSharedPreferences(PREFS_STORAGE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVED_TREE_URI, uri.toString())
            .apply()
    }

    fun clearAuthorizedUri() {
        context.getSharedPreferences(PREFS_STORAGE, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SAVED_TREE_URI)
            .apply()
    }

    private fun listWorldsFromDocumentFile(root: DocumentFile): List<String> {
        val names = mutableListOf<String>()
        val files = root.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                val levelNameFile = file.findFile("levelname.txt")
                val name = if (levelNameFile != null) {
                    StorageAccessHelper.readTextContent(context, levelNameFile.uri) ?: file.name
                } else {
                    file.name
                }
                names.add(name ?: "Unknown World")
            }
        }
        return names
    }

    private fun readWorldNameFromFolder(folder: File): String {
        val levelName = File(folder, "levelname.txt")
        return if (levelName.exists() && levelName.canRead()) {
            runCatching { levelName.readText().trim() }.getOrDefault(folder.name)
        } else {
            folder.name
        }
    }
}
