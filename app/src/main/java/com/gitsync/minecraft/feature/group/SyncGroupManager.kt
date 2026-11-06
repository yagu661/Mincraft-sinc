package com.gitsync.minecraft.feature.group

import android.content.Context
import android.util.Log
import com.gitsync.minecraft.core.database.AppDatabase
import com.gitsync.minecraft.core.database.SyncQueueEntity
import com.gitsync.minecraft.core.model.*
import com.gitsync.minecraft.core.security.KeystoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SyncGroupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val keystoreManager: KeystoreManager,
    val leaseManager: WorldEditingLeaseManager
) {
    companion object {
        private const val TAG = "SyncGroupManager"
        const val DEFAULT_GROUP_ID = "group_bedrock_survival_3device"
    }

    private val _syncGroup = MutableStateFlow(createDefaultThreeDeviceGroup())
    val syncGroup: StateFlow<SyncGroup> = _syncGroup.asStateFlow()

    private fun createDefaultThreeDeviceGroup(): SyncGroup {
        val devA = GroupDevice(
            deviceId = "dev_a_owner_m30s",
            displayName = "Owner (Galaxy M30s)",
            role = DeviceRole.OWNER_DEVICE_A,
            androidVersion = 11,
            isOnline = true,
            currentCommitId = null,
            lastSeenEpoch = System.currentTimeMillis()
        )

        val devB = GroupDevice(
            deviceId = "dev_b_friend_phone1",
            displayName = "Friend Phone 1 (Android 11)",
            role = DeviceRole.FRIEND_DEVICE_B,
            androidVersion = 11,
            isOnline = false,
            currentCommitId = null,
            lastSeenEpoch = System.currentTimeMillis()
        )

        val devC = GroupDevice(
            deviceId = "dev_c_friend_phone2",
            displayName = "Friend Phone 2 (Android 15)",
            role = DeviceRole.FRIEND_DEVICE_C,
            androidVersion = 15,
            isOnline = false,
            currentCommitId = null,
            lastSeenEpoch = System.currentTimeMillis()
        )

        return SyncGroup(
            groupId = DEFAULT_GROUP_ID,
            sharedWorldId = "SurvivalWorld",
            sharedWorldName = "Survival World",
            devices = listOf(devA, devB, devC),
            activeLeaseHolderDeviceId = null,
            leaseAcquiredEpoch = 0L,
            leaseExpiresEpoch = 0L,
            lastCommonCommitId = null,
            pendingSyncDeviceIds = emptyList()
        )
    }

    /**
     * Checks if current device is up to date before launching Minecraft (Device Switching Check).
     */
    suspend fun verifyDeviceReadinessBeforePlay(worldId: String): DeviceSwitchingCheckResult {
        val currentHeadCommit = database.commitDao().getLatestCommit(worldId)?.toModel()
        val latestKnownGroupCommit = _syncGroup.value.devices
            .mapNotNull { it.currentCommitId }
            .firstOrNull()

        // 1. Check editing lease
        val leaseCheck = leaseManager.checkCanStartSession(worldId)
        if (leaseCheck is LeaseCheckResult.DENIED_ANOTHER_DEVICE_ACTIVE) {
            return DeviceSwitchingCheckResult.BLOCKED_BY_ACTIVE_LEASE(
                holderName = leaseCheck.holderName,
                remainingSec = leaseCheck.expiresRemainingSec
            )
        }

        // 2. Check if local copy is outdated compared to other group devices
        if (latestKnownGroupCommit != null && currentHeadCommit?.commitId != latestKnownGroupCommit) {
            // Check if we already have this commit in local history
            val commitInHistory = database.commitDao().getCommitById(latestKnownGroupCommit)
            if (commitInHistory == null) {
                return DeviceSwitchingCheckResult.OUTDATED_NEEDS_SYNC(
                    localCommit = currentHeadCommit?.commitId ?: "none",
                    newestRemoteCommit = latestKnownGroupCommit
                )
            }
        }

        return DeviceSwitchingCheckResult.READY_TO_PLAY
    }

    /**
     * Multi-Device Fan-out: Registers a new snapshot and schedules delivery to all other devices in the group.
     */
    suspend fun fanoutSnapshotToGroup(commit: Commit, onlineDeviceIds: Set<String>) {
        val localId = keystoreManager.getDeviceIdentity().deviceId
        val group = _syncGroup.value

        val otherDevices = group.devices.filter { it.deviceId != localId }
        val pendingList = mutableListOf<String>()

        for (target in otherDevices) {
            val isTargetOnline = onlineDeviceIds.contains(target.deviceId)

            // Queue in Room persistent database for guaranteed delivery
            database.syncQueueDao().enqueue(
                SyncQueueEntity(
                    commitId = commit.commitId,
                    targetDeviceId = target.deviceId,
                    worldId = commit.worldId,
                    addedTimestamp = System.currentTimeMillis()
                )
            )

            if (!isTargetOnline) {
                pendingList.add(target.deviceId)
                Log.i(TAG, "Device ${target.displayName} (${target.role}) is offline. Update queued.")
            } else {
                Log.i(TAG, "Device ${target.displayName} (${target.role}) is online. Dispatched over WebRTC.")
            }
        }

        _syncGroup.update {
            it.copy(
                lastCommonCommitId = commit.parentCommitId ?: commit.commitId,
                pendingSyncDeviceIds = pendingList
            )
        }
    }

    fun updateDeviceOnlineStatus(deviceId: String, isOnline: Boolean) {
        _syncGroup.update { current ->
            val updatedDevices = current.devices.map { dev ->
                if (dev.deviceId == deviceId) {
                    dev.copy(isOnline = isOnline, lastSeenEpoch = System.currentTimeMillis())
                } else {
                    dev
                }
            }
            current.copy(devices = updatedDevices)
        }
    }

    fun updateDeviceCommit(deviceId: String, commitId: String) {
        _syncGroup.update { current ->
            val updatedDevices = current.devices.map { dev ->
                if (dev.deviceId == deviceId) dev.copy(currentCommitId = commitId) else dev
            }
            current.copy(devices = updatedDevices)
        }
    }
}

sealed class DeviceSwitchingCheckResult {
    data object READY_TO_PLAY : DeviceSwitchingCheckResult()
    data class OUTDATED_NEEDS_SYNC(val localCommit: String, val newestRemoteCommit: String) : DeviceSwitchingCheckResult()
    data class BLOCKED_BY_ACTIVE_LEASE(val holderName: String, val remainingSec: Long) : DeviceSwitchingCheckResult()
}
