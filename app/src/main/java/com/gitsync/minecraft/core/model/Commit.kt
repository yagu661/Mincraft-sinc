package com.gitsync.minecraft.core.model

/**
 * Git-style commit representing an immutable snapshot of a Minecraft Bedrock world.
 */
data class Commit(
    val commitId: String,          // e.g., "c_a1b2c3d4..."
    val worldId: String,           // Target world identifier or folder name
    val parentCommitId: String?,   // Parent commit ID (null for genesis commit)
    val timestamp: Long,           // Epoch milliseconds when snapshot was taken
    val deviceId: String,          // Device identifier that authored this commit
    val authorName: String,        // Player/Device display name
    val commitMessage: String,     // e.g. "v14 - Nether portal completed"
    val fileSizeBytes: Long,       // Total size of compressed snapshot
    val sha256Checksum: String,    // Cryptographic hash of the .mcworld / tar payload
    val snapshotFilePath: String,  // Local file path where archive is preserved
    val syncStatus: SyncStatus     // SYNCED, PENDING_UPLOAD, RECEIVING, CONFLICT, FAILED
)

enum class SyncStatus {
    LOCAL_ONLY,
    PENDING_PUSH,
    PUSHING,
    SYNCED,
    RECEIVING,
    AWAITING_IMPORT,
    CONFLICT,
    FAILED
}
