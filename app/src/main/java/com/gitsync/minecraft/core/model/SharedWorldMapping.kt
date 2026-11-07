package com.gitsync.minecraft.core.model

import java.io.File

/**
 * Mapping between a local Minecraft Bedrock world folder and the globally unique Shared World ID.
 * Supports multiple independent shared worlds and tracks pairing status.
 */
data class SharedWorldMapping(
    val sharedWorldId: String,          // Persistent unique shared World ID (e.g., "shared_world_a1b2c3d4...")
    val localFolderId: String,          // Local directory name in minecraftWorlds/
    val displayName: String,            // World name read from levelname.txt
    val localAbsolutePath: String,      // Full path on device storage
    val pairingCode: String,            // Secure pairing invite code for friend: "MWPAIR:<sharedWorldId>:<token>"
    val pairedDeviceId: String? = null, // Remote partner device ID
    val pairedDeviceName: String? = null,
    val isHostDevice: Boolean = true,   // True if this device originated the shared world (Mode 1 Host)
    val createdAtEpoch: Long = System.currentTimeMillis(),
    val lastSyncEpoch: Long = 0L,
    val activeMultiplayerMode: MultiplayerSyncMode = MultiplayerSyncMode.MODE_1_HOST_READY
)

enum class MultiplayerSyncMode {
    MODE_1_HOST_READY,   // Host is online or hosting live multiplayer. Friends should join host's live game.
    MODE_1_JOINED_LIVE,  // Friend connected directly to host's live Bedrock game session.
    MODE_2_HOST_OFFLINE, // Host is offline. Friend plays local synchronized copy independently; auto-backs up when done.
    MODE_3_FRIEND_OFFLINE // Friend is offline. Host plays local synchronized copy independently; auto-backs up when done.
}
