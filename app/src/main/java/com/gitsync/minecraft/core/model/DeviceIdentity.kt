package com.gitsync.minecraft.core.model

data class DeviceIdentity(
    val deviceId: String,          // Unique UUID for this device
    val displayName: String,       // Human-readable nickname
    val pairingSecret: String,     // High-entropy token for mutual authentication
    val createdAtEpoch: Long
)

data class PairingInvite(
    val deviceId: String,
    val displayName: String,
    val token: String,
    val signalingServerUrl: String
)
