package com.gitsync.minecraft.core.model

enum class DeviceRole {
    OWNER_DEVICE_A,      // Owner's phone (Android 11, External Storage)
    FRIEND_DEVICE_B,     // Friend's primary phone (Android 11, External Storage)
    FRIEND_DEVICE_C      // Friend's secondary phone (Android 15, Scoped Storage / SAF)
}

data class GroupDevice(
    val deviceId: String,
    val displayName: String,
    val role: DeviceRole,
    val androidVersion: Int,
    val isOnline: Boolean,
    val currentCommitId: String?,
    val lastSeenEpoch: Long
)

data class SyncGroup(
    val groupId: String,
    val sharedWorldId: String,
    val sharedWorldName: String,
    val devices: List<GroupDevice>,
    val activeLeaseHolderDeviceId: String?,
    val leaseAcquiredEpoch: Long,
    val leaseExpiresEpoch: Long,
    val lastCommonCommitId: String?,
    val pendingSyncDeviceIds: List<String>
) {
    val isLeaseActive: Boolean
        get() = activeLeaseHolderDeviceId != null && System.currentTimeMillis() < leaseExpiresEpoch
}

data class WorldEditingLease(
    val groupId: String,
    val worldId: String,
    val holderDeviceId: String,
    val holderDisplayName: String,
    val baseParentCommitId: String,
    val acquiredEpoch: Long,
    val expiresEpoch: Long,
    val leaseToken: String
)
