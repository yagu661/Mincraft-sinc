package com.gitsync.minecraft.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitsync.minecraft.core.model.Commit
import com.gitsync.minecraft.core.model.StorageCapability
import com.gitsync.minecraft.core.model.SyncState
import com.gitsync.minecraft.core.model.SyncStatus
import com.gitsync.minecraft.feature.monitoring.MinecraftSessionMonitor
import com.gitsync.minecraft.ui.AppScreen
import com.gitsync.minecraft.ui.theme.*

@Composable
fun DashboardScreen(
    syncState: SyncState,
    storageCapability: StorageCapability,
    hasUsagePermission: Boolean,
    sessionState: MinecraftSessionMonitor.SessionState,
    latestCommit: Commit?,
    onNavigate: (AppScreen) -> Unit,
    onPushNow: () -> Unit,
    onPullNow: () -> Unit,
    onForceBackup: () -> Unit,
    onRetrySync: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GraphiteBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Title & Tagline
        item {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(EmeraldPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = GraphiteBackground,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "MINECRAFT GITSYNC",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }

                    IconButton(onClick = { onNavigate(AppScreen.SETTINGS) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Automatic Bedrock P2P World Synchronization",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Prominent Sync & Connection Status Header
        item {
            StatusHeaderCard(
                syncState = syncState,
                storageCapability = storageCapability,
                sessionState = sessionState,
                latestCommit = latestCommit
            )
        }

        // Live Monitoring & Permission Alert if missing
        if (!hasUsagePermission) {
            item {
                PermissionWarningCard(
                    title = "Usage Access Required",
                    description = "GitSync needs Usage Access to automatically detect when you close Minecraft without running battery-draining services.",
                    onClick = { onNavigate(AppScreen.STORAGE_SETUP) }
                )
            }
        }

        // Transfer Progress Bar (when pushing or receiving)
        if (syncState.currentSyncStatus == SyncStatus.PUSHING || syncState.currentSyncStatus == SyncStatus.RECEIVING) {
            item {
                TransferProgressCard(syncState = syncState)
            }
        }

        // Emergency Manual Action Controls (Section 10)
        item {
            ManualActionsCard(
                syncState = syncState,
                onPushNow = onPushNow,
                onPullNow = onPullNow,
                onForceBackup = onForceBackup,
                onRetrySync = onRetrySync
            )
        }

        // Main Dashboard Feature Cards
        item {
            AutomaticSyncCard(
                sessionState = sessionState,
                syncState = syncState,
                onClick = { onNavigate(AppScreen.SETTINGS) }
            )
        }

        item {
            WorldInfoCard(
                storageCapability = storageCapability,
                onClick = { onNavigate(AppScreen.STORAGE_SETUP) }
            )
        }

        item {
            FriendConnectionCard(
                syncState = syncState,
                onClick = { onNavigate(AppScreen.PAIRING) }
            )
        }

        item {
            VersionHistoryCard(
                latestCommit = latestCommit,
                onClick = { onNavigate(AppScreen.HISTORY) }
            )
        }

        item {
            TestHarnessCard(
                onClick = { onNavigate(AppScreen.TEST_MODE) }
            )
        }
    }
}

@Composable
private fun StatusHeaderCard(
    syncState: SyncState,
    storageCapability: StorageCapability,
    sessionState: MinecraftSessionMonitor.SessionState,
    latestCommit: Commit?
) {
    val syncBadgeColor by animateColorAsState(
        when (syncState.currentSyncStatus) {
            SyncStatus.SYNCED -> StatusSynced
            SyncStatus.PUSHING, SyncStatus.RECEIVING -> StatusTransferring
            SyncStatus.PENDING_PUSH, SyncStatus.AWAITING_IMPORT -> StatusPending
            SyncStatus.CONFLICT -> StatusConflict
            SyncStatus.FAILED -> RedstoneError
            else -> StatusOffline
        },
        label = "statusColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GraphiteCardBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = GraphiteCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SELECTED WORLD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = storageCapability.detectedWorldNames.firstOrNull() ?: "No World Selected",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                // Sync status chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(syncBadgeColor.copy(alpha = 0.2f))
                        .border(1.dp, syncBadgeColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = syncState.currentSyncStatus.name.replace("_", " "),
                        color = syncBadgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = GraphiteCardBorder)
            Spacer(modifier = Modifier.height(14.dp))

            // Dual Presence Indicator: You & Friend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PresenceIndicator(
                    label = "Your Device",
                    isOnline = syncState.isLocalOnline,
                    detail = if (sessionState == MinecraftSessionMonitor.SessionState.MINECRAFT_ACTIVE) "In Game" else "Monitoring"
                )

                PresenceIndicator(
                    label = syncState.pairedDeviceName ?: "Friend Device",
                    isOnline = syncState.isRemoteOnline,
                    detail = if (syncState.pairedDeviceId == null) "Not Paired" else if (syncState.isRemoteOnline) "Connected P2P" else "Offline"
                )
            }

            if (latestCommit != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Commit,
                        contentDescription = null,
                        tint = EmeraldLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${latestCommit.commitMessage} (${latestCommit.commitId.take(8)})",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun PresenceIndicator(
    label: String,
    isOnline: Boolean,
    detail: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isOnline) StatusSynced else StatusOffline)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = detail,
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun TransferProgressCard(syncState: SyncState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StatusTransferring, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = GraphiteCardElevated),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { syncState.activeTransferProgress },
                        modifier = Modifier.size(20.dp),
                        color = EmeraldPrimary,
                        strokeWidth = 2.dp,
                        trackColor = GraphiteCardBorder
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (syncState.currentSyncStatus == SyncStatus.PUSHING) "Transferring Snapshot to Friend…" else "Receiving World Snapshot…",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${(syncState.activeTransferProgress * 100).toInt()}%",
                    color = EmeraldLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { syncState.activeTransferProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = EmeraldPrimary,
                trackColor = GraphiteCardBorder
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "WebRTC DataChannel • 32KB binary chunking with flow backpressure",
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun ManualActionsCard(
    syncState: SyncState,
    onPushNow: () -> Unit,
    onPullNow: () -> Unit,
    onForceBackup: () -> Unit,
    onRetrySync: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GraphiteCardBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = GraphiteCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "EMERGENCY MANUAL CONTROLS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPushNow,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = GraphiteBackground),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PUSH NOW", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onPullNow,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = GraphiteCardElevated, contentColor = TextPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PULL NOW", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onForceBackup,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Text("FORCE BACKUP", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onRetrySync,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Text("RETRY SYNC", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AutomaticSyncCard(
    sessionState: MinecraftSessionMonitor.SessionState,
    syncState: SyncState,
    onClick: () -> Unit
) {
    DashboardNavCard(
        icon = Icons.Default.Autorenew,
        title = "Automatic Sync Engine",
        subtitle = when (sessionState) {
            MinecraftSessionMonitor.SessionState.MINECRAFT_ACTIVE -> "🎮 Minecraft session actively detected! Monitoring…"
            MinecraftSessionMonitor.SessionState.GRACE_PERIOD_WAITING -> "⏳ Minecraft closed. Waiting for 8s grace period…"
            MinecraftSessionMonitor.SessionState.TRIGGERING_SNAPSHOT -> "⚡ Creating snapshot and preparing WebRTC push…"
            MinecraftSessionMonitor.SessionState.IDLE -> "🟢 Active. GitSync will push when you exit Minecraft."
        },
        onClick = onClick
    )
}

@Composable
private fun WorldInfoCard(
    storageCapability: StorageCapability,
    onClick: () -> Unit
) {
    val sub = if (storageCapability.isWorldDirectoryAccessible) {
        "Authorized (${storageCapability.detectedWorldsCount} worlds detected) • Type: ${storageCapability.storageType.name}"
    } else {
        "⚠️ Setup needed: Android Scoped Storage requires folder permission"
    }

    DashboardNavCard(
        icon = Icons.Default.Folder,
        title = "Minecraft Storage Setup",
        subtitle = sub,
        onClick = onClick
    )
}

@Composable
private fun FriendConnectionCard(
    syncState: SyncState,
    onClick: () -> Unit
) {
    val sub = if (syncState.pairedDeviceId != null) {
        "Paired with ${syncState.pairedDeviceName} (${if (syncState.isRemoteOnline) "Online" else "Offline"})"
    } else {
        "No friend paired yet. Tap to generate pairing token."
    }

    DashboardNavCard(
        icon = Icons.Default.People,
        title = "Friend Pairing & WebRTC",
        subtitle = sub,
        onClick = onClick
    )
}

@Composable
private fun VersionHistoryCard(
    latestCommit: Commit?,
    onClick: () -> Unit
) {
    val sub = if (latestCommit != null) {
        "Latest: ${latestCommit.commitMessage} • Tap to view all commits or rollback"
    } else {
        "No commits yet. Backups will appear here automatically."
    }

    DashboardNavCard(
        icon = Icons.Default.History,
        title = "Version History & Commits",
        subtitle = sub,
        onClick = onClick
    )
}

@Composable
private fun TestHarnessCard(
    onClick: () -> Unit
) {
    DashboardNavCard(
        icon = Icons.Default.Science,
        title = "Test Mode & Simulator",
        subtitle = "Test world synchronization and SHA-256 verification without Minecraft",
        onClick = onClick
    )
}

@Composable
private fun PermissionWarningCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, GoldWarning, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = GoldWarning.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = GoldWarning)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(text = description, color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun DashboardNavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, GraphiteCardBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = GraphiteCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(GraphiteCardElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = EmeraldLight)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 12.sp, color = TextSecondary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
        }
    }
}
