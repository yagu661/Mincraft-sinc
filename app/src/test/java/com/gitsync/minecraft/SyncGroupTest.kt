package com.gitsync.minecraft

import com.gitsync.minecraft.core.model.DeviceRole
import com.gitsync.minecraft.core.model.GroupDevice
import com.gitsync.minecraft.core.model.SyncGroup
import org.junit.Assert.*
import org.junit.Test

class SyncGroupTest {

    @Test
    fun testThreeDeviceGroupConfiguration() {
        val devA = GroupDevice("dev_a", "Owner M30s", DeviceRole.OWNER_DEVICE_A, 11, true, "c_v1", 1000L)
        val devB = GroupDevice("dev_b", "Friend Phone 1", DeviceRole.FRIEND_DEVICE_B, 11, true, "c_v1", 1000L)
        val devC = GroupDevice("dev_c", "Friend Phone 2", DeviceRole.FRIEND_DEVICE_C, 15, false, "c_v1", 1000L)

        val group = SyncGroup(
            groupId = "group_bedrock_survival",
            sharedWorldId = "SurvivalWorld",
            sharedWorldName = "Survival World",
            devices = listOf(devA, devB, devC),
            activeLeaseHolderDeviceId = null,
            leaseAcquiredEpoch = 0L,
            leaseExpiresEpoch = 0L,
            lastCommonCommitId = "c_v1",
            pendingSyncDeviceIds = emptyList()
        )

        assertEquals(3, group.devices.size)
        assertFalse(group.isLeaseActive)
        assertEquals(DeviceRole.OWNER_DEVICE_A, group.devices[0].role)
        assertEquals(DeviceRole.FRIEND_DEVICE_B, group.devices[1].role)
        assertEquals(DeviceRole.FRIEND_DEVICE_C, group.devices[2].role)
        assertEquals(15, group.devices[2].androidVersion)
    }

    @Test
    fun testEditingLeaseActivationAndExpiration() {
        val devA = GroupDevice("dev_a", "Owner M30s", DeviceRole.OWNER_DEVICE_A, 11, true, "c_v1", 1000L)
        val now = System.currentTimeMillis()

        val groupWithActiveLease = SyncGroup(
            groupId = "group_bedrock_survival",
            sharedWorldId = "SurvivalWorld",
            sharedWorldName = "Survival World",
            devices = listOf(devA),
            activeLeaseHolderDeviceId = "dev_a",
            leaseAcquiredEpoch = now,
            leaseExpiresEpoch = now + 60000L, // 60s future
            lastCommonCommitId = "c_v1",
            pendingSyncDeviceIds = emptyList()
        )

        assertTrue(groupWithActiveLease.isLeaseActive)

        val expiredGroup = groupWithActiveLease.copy(
            leaseExpiresEpoch = now - 1000L // Expired
        )
        assertFalse(expiredGroup.isLeaseActive)
    }
}
