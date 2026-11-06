package com.gitsync.minecraft.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CommitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommit(commit: CommitEntity)

    @Update
    suspend fun updateCommit(commit: CommitEntity)

    @Query("SELECT * FROM commits WHERE commitId = :commitId LIMIT 1")
    suspend fun getCommitById(commitId: String): CommitEntity?

    @Query("SELECT * FROM commits WHERE worldId = :worldId ORDER BY timestamp DESC")
    fun getCommitsForWorldFlow(worldId: String): Flow<List<CommitEntity>>

    @Query("SELECT * FROM commits WHERE worldId = :worldId ORDER BY timestamp DESC")
    suspend fun getCommitsForWorld(worldId: String): List<CommitEntity>

    @Query("SELECT * FROM commits WHERE worldId = :worldId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestCommit(worldId: String): CommitEntity?

    @Query("SELECT * FROM commits WHERE sha256Checksum = :sha256 LIMIT 1")
    suspend fun getCommitByChecksum(sha256: String): CommitEntity?

    @Query("UPDATE commits SET syncStatus = :newStatus WHERE commitId = :commitId")
    suspend fun updateSyncStatus(commitId: String, newStatus: String)

    @Query("SELECT * FROM commits WHERE syncStatus = 'PENDING_PUSH' ORDER BY timestamp ASC")
    suspend fun getPendingPushes(): List<CommitEntity>

    @Query("DELETE FROM commits WHERE commitId = :commitId")
    suspend fun deleteCommit(commitId: String)
}
