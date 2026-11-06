package com.gitsync.minecraft.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val commitId: String,
    val targetDeviceId: String,
    val worldId: String,
    val addedTimestamp: Long,
    val attemptCount: Int = 0,
    val lastAttemptTimestamp: Long = 0,
    val lastErrorMessage: String? = null,
    val isPending: Boolean = true
)
