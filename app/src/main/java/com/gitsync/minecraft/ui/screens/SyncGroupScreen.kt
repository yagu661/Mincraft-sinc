package com.gitsync.minecraft.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitsync.minecraft.core.model.DeviceRole
import com.gitsync.minecraft.core.model.GroupDevice
import com.gitsync.minecraft.core.model.SyncGroup
import com.gitsync.minecraft.feature.group.DeviceSwitchingCheckResult
import com.gitsync.minecraft.ui.theme.*

@Composable
fun SyncGroupScreen(
    syncGroup: SyncGroup,
    deviceSwitchingResult: DeviceSwitchingCheckResult,
    onAcquireLease: () -> Unit,
    onReleaseLease: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = GraphiteBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "3-DEVICE SYNC GROUP",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Group Header Info
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EmeraldPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = GraphiteCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = EmeraldLight)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "BEDROCK SHARED SYNC GROUP",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldLight,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "Shared World: ${syncGroup.sharedWorldName}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(text = "Group ID: ${syncGroup.groupId}", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Synchronizes 3 devices across 2 players: Owner (Device A) and Friend (Device B & C).",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Single Active Player Editing Lease Card (Requirement 4)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (syncGroup.isLeaseActive) StatusPending else EmeraldPrimary, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = GraphiteCardElevated),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (syncGroup.isLeaseActive) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (syncGroup.isLeaseActive) StatusPending else EmeraldLight
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SHARED-WORLD EDITING LEASE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (syncGroup.isLeaseActive) StatusPending else EmeraldLight,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        if (syncGroup.isLeaseActive) {
                            val remainingSec = ((syncGroup.leaseExpiresEpoch - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                            Text(
                                text = "Active Lease Holder: ${syncGroup.activeLeaseHolderDeviceId} ($remainingSec s remaining)",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Single Active Player Lock protects LevelDB chunks from concurrent corruption. " +
                                        "Other devices are locked until session ends and snapshot synchronizes.",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onReleaseLease,
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                            ) {
                                Text("RELEASE LEASE AFTER BACKUP", fontSize = 11.sp)
                            }
                        } else {
                            Text(
                                text = "World Lease is currently FREE. Any authorized group device can start a session.",
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onAcquireLease,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = GraphiteBackground),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("ACQUIRE EDITING LEASE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Device Switching Verification Status Card (Requirement 3)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            when (deviceSwitchingResult) {
                                is DeviceSwitchingCheckResult.READY_TO_PLAY -> StatusSynced
                                is DeviceSwitchingCheckResult.OUTDATED_NEEDS_SYNC -> GoldWarning
                                is DeviceSwitchingCheckResult.BLOCKED_BY_ACTIVE_LEASE -> RedstoneError
                            },
                            RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = GraphiteCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "DEVICE SWITCHING READINESS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        when (deviceSwitchingResult) {
                            is DeviceSwitchingCheckResult.READY_TO_PLAY -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSynced)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("This device has the latest confirmed commit. Ready to launch Minecraft.", color = TextPrimary, fontSize = 12.sp)
                                }
                            }
                            is DeviceSwitchingCheckResult.OUTDATED_NEEDS_SYNC -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = GoldWarning)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Outdated snapshot! A newer commit (${deviceSwitchingResult.newestRemoteCommit.take(8)}) was created on another device. Sync required before playing.",
                                        color = TextPrimary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            is DeviceSwitchingCheckResult.BLOCKED_BY_ACTIVE_LEASE -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = RedstoneError)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Session currently active on ${deviceSwitchingResult.holderName}. Please wait for session completion to avoid divergence.",
                                        color = TextPrimary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3 Registered Devices List
            item {
                Text(
                    text = "REGISTERED GROUP DEVICES (${syncGroup.devices.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }

            items(syncGroup.devices) { device ->
                GroupDeviceCard(device = device, isLeaseHolder = device.deviceId == syncGroup.activeLeaseHolderDeviceId)
            }
        }
    }
}

@Composable
private fun GroupDeviceCard(device: GroupDevice, isLeaseHolder: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isLeaseHolder) StatusPending else GraphiteCardBorder, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = GraphiteCard),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (device.isOnline) StatusSynced else StatusOffline)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = device.displayName, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                        Text(
                            text = when (device.role) {
                                DeviceRole.OWNER_DEVICE_A -> "Owner Device • Android ${device.androidVersion} (External Storage)"
                                DeviceRole.FRIEND_DEVICE_B -> "Friend Device 1 • Android ${device.androidVersion} (External Storage)"
                                DeviceRole.FRIEND_DEVICE_C -> "Friend Device 2 • Android ${device.androidVersion} (Scoped Storage SAF)"
                            },
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                if (isLeaseHolder) {
                    Box(
                        modifier = Modifier
                            .background(StatusPending.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("ACTIVE SESSION", color = StatusPending, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "ID: ${device.deviceId}", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text(
                    text = "Commit: ${device.currentCommitId?.take(8) ?: "initial"}",
                    color = EmeraldLight,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
