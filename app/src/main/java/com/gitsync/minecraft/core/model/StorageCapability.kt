package com.gitsync.minecraft.core.model

import android.net.Uri

enum class MinecraftStorageType {
    APPLICATION_INTERNAL, // Private sandbox (/data/data/com.mojang.minecraftpe) - in accessible without root
    SCOPED_EXTERNAL_DATA, // Android/data/com.mojang.minecraftpe/files/games/com.mojang - blocked on Android 11+ SAF
    LEGACY_GAMES_FOLDER,  // /sdcard/games/com.mojang/minecraftWorlds - accessible on older Android versions or custom ROMs
    SAF_AUTHORIZED_TREE,  // Authorized via DocumentFile / Storage Access Framework ACTION_OPEN_DOCUMENT_TREE
    STANDALONE_IMPORT_ONLY // Export/import fallback via .mcworld container files
}

data class StorageCapability(
    val storageType: MinecraftStorageType,
    val authorizedTreeUri: Uri? = null,
    val isWorldDirectoryAccessible: Boolean = false,
    val isReadAvailable: Boolean = false,
    val isWriteAvailable: Boolean = false,
    val isPersistentUriGranted: Boolean = false,
    val isAutomaticReplacementSupported: Boolean = false,
    val isExportImportRequired: Boolean = false,
    val restrictionReason: String? = null,
    val detectedWorldsCount: Int = 0,
    val detectedWorldNames: List<String> = emptyList()
)
