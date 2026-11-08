package com.gitsync.minecraft.core.model

data class SyncState(
    val selectedWorldId: String? = null,
    val selectedWorldName: String = "No World Selected",
    val isLocalOnline: Boolean = false,
    val isRemoteOnline: Boolean = false,
    val pairedDeviceId: String? = null,
    val pairedDeviceName: String? = null,
    val latestLocalCommit: Commit? = null,
    val latestRemoteCommitId: String? = null,
    val currentSyncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val activeTransferProgress: Float = 0f,
    val activeTransferSpeedBytesPerSec: Long = 0,
    val lastError: String? = null,
    val hasUnresolvedConflict: Boolean = false,
    val conflictingCommitA: Commit? = null,
    val conflictingCommitB: Commit? = null,
    val googleAccountEmail: String? = null,
    val googleDriveFolderId: String? = null,
    val isGoogleDriveConnected: Boolean = false,
    val googleDriveStorageUsedBytes: Long = 0L,
    val googleDriveStorageLimitBytes: Long = 15L * 1024L * 1024L * 1024L // 15 GB free quota
)
