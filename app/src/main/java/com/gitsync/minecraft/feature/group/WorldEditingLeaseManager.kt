package com.gitsync.minecraft.feature.group

import android.content.Context
import android.util.Log
import com.gitsync.minecraft.core.model.WorldEditingLease
import com.gitsync.minecraft.core.security.KeystoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class WorldEditingLeaseManager(
    private val context: Context,
    private val keystoreManager: KeystoreManager
) {
    companion object {
        private const val TAG = "LeaseManager"
        const val DEFAULT_LEASE_DURATION_MS = 15 * 60 * 1000L // 15 minute lease window
    }

    private val _activeLease = MutableStateFlow<WorldEditingLease?>(null)
    val activeLease: StateFlow<WorldEditingLease?> = _activeLease.asStateFlow()

    private val _isLocalLeaseHolder = MutableStateFlow(false)
    val isLocalLeaseHolder: StateFlow<Boolean> = _isLocalLeaseHolder.asStateFlow()

    /**
     * Checks if a device is currently permitted to launch Minecraft without conflict.
     */
    fun checkCanStartSession(worldId: String): LeaseCheckResult {
        val lease = _activeLease.value
        val localIdentity = keystoreManager.getDeviceIdentity()

        if (lease == null || System.currentTimeMillis() > lease.expiresEpoch) {
            return LeaseCheckResult.ALLOWED_NO_ACTIVE_LEASE
        }

        if (lease.holderDeviceId == localIdentity.deviceId) {
            return LeaseCheckResult.ALLOWED_ALREADY_HOLDING_LEASE
        }

        return LeaseCheckResult.DENIED_ANOTHER_DEVICE_ACTIVE(
            holderName = lease.holderDisplayName,
            holderId = lease.holderDeviceId,
            expiresRemainingSec = ((lease.expiresEpoch - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        )
    }

    /**
     * Acquires single active player lock for this device.
     */
    fun acquireLease(worldId: String, currentHeadCommitId: String): WorldEditingLease {
        val identity = keystoreManager.getDeviceIdentity()
        val now = System.currentTimeMillis()
        val expires = now + DEFAULT_LEASE_DURATION_MS

        val lease = WorldEditingLease(
            groupId = "group_minecraft_bedrock_sync",
            worldId = worldId,
            holderDeviceId = identity.deviceId,
            holderDisplayName = identity.displayName,
            baseParentCommitId = currentHeadCommitId,
            acquiredEpoch = now,
            expiresEpoch = expires,
            leaseToken = UUID.randomUUID().toString()
        )

        _activeLease.value = lease
        _isLocalLeaseHolder.value = true
        Log.i(TAG, "Acquired editing lease for world $worldId on device ${identity.displayName} until $expires")
        return lease
    }

    /**
     * Heartbeat renewal while player continues playing.
     */
    fun renewHeartbeat() {
        val current = _activeLease.value ?: return
        val identity = keystoreManager.getDeviceIdentity()
        if (current.holderDeviceId == identity.deviceId) {
            val renewed = current.copy(expiresEpoch = System.currentTimeMillis() + DEFAULT_LEASE_DURATION_MS)
            _activeLease.value = renewed
            Log.d(TAG, "Renewed lease heartbeat for ${current.worldId}")
        }
    }

    /**
     * Releases lock only after snapshot is created and verified.
     */
    fun releaseLease(): Boolean {
        val current = _activeLease.value ?: return true
        val identity = keystoreManager.getDeviceIdentity()
        if (current.holderDeviceId == identity.deviceId) {
            _activeLease.value = null
            _isLocalLeaseHolder.value = false
            Log.i(TAG, "Released editing lease for world ${current.worldId}")
            return true
        }
        return false
    }

    fun updateRemoteLease(remoteLease: WorldEditingLease?) {
        val identity = keystoreManager.getDeviceIdentity()
        _activeLease.value = remoteLease
        _isLocalLeaseHolder.value = (remoteLease?.holderDeviceId == identity.deviceId)
    }
}

sealed class LeaseCheckResult {
    data object ALLOWED_NO_ACTIVE_LEASE : LeaseCheckResult()
    data object ALLOWED_ALREADY_HOLDING_LEASE : LeaseCheckResult()
    data class DENIED_ANOTHER_DEVICE_ACTIVE(
        val holderName: String,
        val holderId: String,
        val expiresRemainingSec: Long
    ) : LeaseCheckResult()
}
