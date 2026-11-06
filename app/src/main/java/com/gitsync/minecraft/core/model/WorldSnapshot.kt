package com.gitsync.minecraft.core.model

import java.io.File

data class WorldSnapshot(
    val worldId: String,
    val worldName: String,
    val archiveFile: File,
    val fileSizeBytes: Long,
    val sha256: String,
    val timestamp: Long,
    val fileCount: Int
)

data class WorldFileItem(
    val relativePath: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val sha256: String
)
