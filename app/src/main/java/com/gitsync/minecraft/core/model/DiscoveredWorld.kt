package com.gitsync.minecraft.core.model

import java.io.File

data class DiscoveredWorld(
    val worldId: String,          // Stable identifier (e.g. folder name or unique hash)
    val folderName: String,       // Directory name in minecraftWorlds/
    val displayName: String,      // Read from levelname.txt
    val absolutePath: String,     // File path on storage
    val folderSizeBytes: Long,
    val lastModifiedEpoch: Long,
    val fileCount: Int,
    val hasLevelDat: Boolean,
    val hasDbDirectory: Boolean,
    val iconFile: File?,
    val isEligibleForSync: Boolean,
    val ineligibleReason: String? = null
)
