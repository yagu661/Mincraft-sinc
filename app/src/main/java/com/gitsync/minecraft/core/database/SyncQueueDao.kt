package com.gitsync.minecraft.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Update
    suspend fun update(item: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE isPending = 1 ORDER BY addedTimestamp ASC")
    fun getPendingQueueFlow(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE isPending = 1 ORDER BY addedTimestamp ASC")
    suspend fun getPendingQueue(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE commitId = :commitId AND targetDeviceId = :targetDeviceId LIMIT 1")
    suspend fun findByCommitAndTarget(commitId: String, targetDeviceId: String): SyncQueueEntity?

    @Query("UPDATE sync_queue SET isPending = 0 WHERE commitId = :commitId")
    suspend fun markComplete(commitId: String)

    @Query("DELETE FROM sync_queue WHERE commitId = :commitId")
    suspend fun deleteForCommit(commitId: String)
}
