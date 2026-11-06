package com.gitsync.minecraft.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gitsync.minecraft.core.model.Commit
import com.gitsync.minecraft.core.model.SyncStatus

@Entity(
    tableName = "commits",
    indices = [
        Index(value = ["worldId"]),
        Index(value = ["parentCommitId"]),
        Index(value = ["sha256Checksum"], unique = false)
    ]
)
data class CommitEntity(
    @PrimaryKey
    val commitId: String,
    val worldId: String,
    val parentCommitId: String?,
    val timestamp: Long,
    val deviceId: String,
    val authorName: String,
    val commitMessage: String,
    val fileSizeBytes: Long,
    val sha256Checksum: String,
    val snapshotFilePath: String,
    val syncStatus: String
) {
    fun toModel(): Commit = Commit(
        commitId = commitId,
        worldId = worldId,
        parentCommitId = parentCommitId,
        timestamp = timestamp,
        deviceId = deviceId,
        authorName = authorName,
        commitMessage = commitMessage,
        fileSizeBytes = fileSizeBytes,
        sha256Checksum = sha256Checksum,
        snapshotFilePath = snapshotFilePath,
        syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }.getOrDefault(SyncStatus.LOCAL_ONLY)
    )

    companion object {
        fun fromModel(model: Commit): CommitEntity = CommitEntity(
            commitId = model.commitId,
            worldId = model.worldId,
            parentCommitId = model.parentCommitId,
            timestamp = model.timestamp,
            deviceId = model.deviceId,
            authorName = model.authorName,
            commitMessage = model.commitMessage,
            fileSizeBytes = model.fileSizeBytes,
            sha256Checksum = model.sha256Checksum,
            snapshotFilePath = model.snapshotFilePath,
            syncStatus = model.syncStatus.name
        )
    }
}
