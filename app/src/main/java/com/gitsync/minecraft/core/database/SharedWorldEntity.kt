package com.gitsync.minecraft.core.database

import androidx.room.*
import com.gitsync.minecraft.core.model.MultiplayerSyncMode
import com.gitsync.minecraft.core.model.SharedWorldMapping
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "shared_world_mappings",
    indices = [
        Index(value = ["sharedWorldId"], unique = true),
        Index(value = ["localFolderId"])
    ]
)
data class SharedWorldEntity(
    @PrimaryKey
    val sharedWorldId: String,
    val localFolderId: String,
    val displayName: String,
    val localAbsolutePath: String,
    val pairingCode: String,
    val pairedDeviceId: String?,
    val pairedDeviceName: String?,
    val isHostDevice: Boolean,
    val createdAtEpoch: Long,
    val lastSyncEpoch: Long,
    val activeMultiplayerMode: String
) {
    fun toModel(): SharedWorldMapping = SharedWorldMapping(
        sharedWorldId = sharedWorldId,
        localFolderId = localFolderId,
        displayName = displayName,
        localAbsolutePath = localAbsolutePath,
        pairingCode = pairingCode,
        pairedDeviceId = pairedDeviceId,
        pairedDeviceName = pairedDeviceName,
        isHostDevice = isHostDevice,
        createdAtEpoch = createdAtEpoch,
        lastSyncEpoch = lastSyncEpoch,
        activeMultiplayerMode = runCatching {
            MultiplayerSyncMode.valueOf(activeMultiplayerMode)
        }.getOrDefault(MultiplayerSyncMode.MODE_1_HOST_READY)
    )

    companion object {
        fun fromModel(model: SharedWorldMapping): SharedWorldEntity = SharedWorldEntity(
            sharedWorldId = model.sharedWorldId,
            localFolderId = model.localFolderId,
            displayName = model.displayName,
            localAbsolutePath = model.localAbsolutePath,
            pairingCode = model.pairingCode,
            pairedDeviceId = model.pairedDeviceId,
            pairedDeviceName = model.pairedDeviceName,
            isHostDevice = model.isHostDevice,
            createdAtEpoch = model.createdAtEpoch,
            lastSyncEpoch = model.lastSyncEpoch,
            activeMultiplayerMode = model.activeMultiplayerMode.name
        )
    }
}

@Dao
interface SharedWorldDao {
    @Query("SELECT * FROM shared_world_mappings ORDER BY createdAtEpoch DESC")
    fun getAllSharedWorldsFlow(): Flow<List<SharedWorldEntity>>

    @Query("SELECT * FROM shared_world_mappings ORDER BY createdAtEpoch DESC")
    suspend fun getAllSharedWorlds(): List<SharedWorldEntity>

    @Query("SELECT * FROM shared_world_mappings WHERE sharedWorldId = :sharedWorldId LIMIT 1")
    suspend fun getMappingBySharedId(sharedWorldId: String): SharedWorldEntity?

    @Query("SELECT * FROM shared_world_mappings WHERE localFolderId = :localFolderId LIMIT 1")
    suspend fun getMappingByLocalFolder(localFolderId: String): SharedWorldEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMapping(entity: SharedWorldEntity)

    @Query("DELETE FROM shared_world_mappings WHERE sharedWorldId = :sharedWorldId")
    suspend fun deleteMapping(sharedWorldId: String)

    @Query("UPDATE shared_world_mappings SET pairedDeviceId = :deviceId, pairedDeviceName = :deviceName, lastSyncEpoch = :syncEpoch WHERE sharedWorldId = :sharedWorldId")
    suspend fun updatePairing(sharedWorldId: String, deviceId: String?, deviceName: String?, syncEpoch: Long)

    @Query("UPDATE shared_world_mappings SET activeMultiplayerMode = :mode WHERE sharedWorldId = :sharedWorldId")
    suspend fun updateMultiplayerMode(sharedWorldId: String, mode: String)
}
