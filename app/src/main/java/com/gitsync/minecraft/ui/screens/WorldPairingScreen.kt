package com.gitsync.minecraft.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitsync.minecraft.core.database.SharedWorldEntity
import com.gitsync.minecraft.core.model.DiscoveredWorld
import com.gitsync.minecraft.core.model.MultiplayerSyncMode
import com.gitsync.minecraft.core.model.SharedWorldMapping
import com.gitsync.minecraft.core.model.StorageCapability
import com.gitsync.minecraft.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WorldPairingScreen(
    discoveredWorlds: List<DiscoveredWorld>,
    sharedWorlds: List<SharedWorldEntity>,
    activeSharedWorld: SharedWorldMapping?,
    storageCapability: StorageCapability,
    onCreateSharedWorld: (DiscoveredWorld) -> SharedWorldMapping,
    onLinkExistingWorld: (sharedWorldId: String, localWorld: DiscoveredWorld) -> Unit,
    onDownloadNewWorld: (sharedWorldId: String, displayName: String) -> Unit,
    onUnpairSharedWorld: (sharedWorldId: String) -> Unit,
    onChangeActiveWorld: (SharedWorldMapping) -> Unit,
    onUpdateMultiplayerMode: (MultiplayerSyncMode) -> Unit,
    onSelectSafFolder: () -> Unit,
    onRefreshWorlds: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = Active & Host, 1 = Join Friend World, 2 = Local Worlds Scan
    var selectedLocalWorldForSharing by remember { mutableStateOf<DiscoveredWorld?>(null) }
    var joinPairingCodeInput by remember { mutableStateOf("") }
    var joinDisplayNameInput by remember { mutableStateOf("") }
    var selectedExistingWorldToLink by remember { mutableStateOf<DiscoveredWorld?>(null) }
    var showLinkConfirmationDialog by remember { mutableStateOf(false) }
    var showUnpairDialog by remember { mutableStateOf<String?>(null) }

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
                Column {
                    Text(
                        text = "WORLD PAIRING & MULTIPLAYER",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "One shared world • Online multiplayer & offline continuation",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Mode Architecture Indicator Card
            item {
                MultiplayerArchitectureCard(
                    activeSharedWorld = activeSharedWorld,
                    onUpdateMultiplayerMode = onUpdateMultiplayerMode
                )
            }

            // Tab bar for switching views
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = GraphiteCard,
                    contentColor = EmeraldLight
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("HOST WORLD", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("JOIN FRIEND", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("ALL WORLDS (${discoveredWorlds.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // TAB 0: HOST / ACTIVE SHARED WORLD
            if (selectedTab == 0) {
                if (activeSharedWorld != null) {
                    item {
                        ActiveSharedWorldCard(
                            mapping = activeSharedWorld,
                            onCopyPairingCode = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Minecraft World Pairing Code", activeSharedWorld.pairingCode)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Pairing code copied! Send to your friend.", Toast.LENGTH_SHORT).show()
                            },
                            onUnpair = { showUnpairDialog = activeSharedWorld.sharedWorldId }
                        )
                    }
                } else {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, TextMuted.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = GraphiteCard),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "SELECT WORLD TO HOST & SYNCHRONIZE",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldLight,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Choose exactly one Minecraft Bedrock world from your device below. A unique persistent Shared World ID will be generated to keep all devices in sync.",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Storage permission check / warning if external storage blocked
                    if (!storageCapability.isWorldDirectoryAccessible) {
                        item {
                            StoragePermissionNoticeCard(
                                restrictionReason = storageCapability.restrictionReason,
                                onSelectSafFolder = onSelectSafFolder
                            )
                        }
                    }

                    // List accessible worlds for Host selection
                    items(discoveredWorlds) { world ->
                        WorldSelectionCard(
                            world = world,
                            isSelected = selectedLocalWorldForSharing?.folderName == world.folderName,
                            onClick = { selectedLocalWorldForSharing = world }
                        )
                    }

                    if (selectedLocalWorldForSharing != null) {
                        item {
                            Button(
                                onClick = {
                                    val mapping = onCreateSharedWorld(selectedLocalWorldForSharing!!)
                                    Toast.makeText(context, "Shared World created! Shared ID: ${mapping.sharedWorldId}", Toast.LENGTH_LONG).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = GraphiteBackground),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "HOST & GENERATE PAIRING CODE FOR \"${selectedLocalWorldForSharing!!.displayName.uppercase()}\"",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Other configured shared worlds if any
                if (sharedWorlds.size > 1) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "OTHER INDEPENDENT SHARED WORLDS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    items(sharedWorlds.filter { it.sharedWorldId != activeSharedWorld?.sharedWorldId }) { sw ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, TextMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                            colors = CardDefaults.cardColors(containerColor = GraphiteCardElevated),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sw.displayName, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text("Shared ID: ${sw.sharedWorldId}", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                                }
                                Button(
                                    onClick = { onChangeActiveWorld(sw.toModel()) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldLight, contentColor = GraphiteBackground),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("SWITCH", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // TAB 1: JOIN FRIEND'S WORLD (Two choices: Link Existing or Download New)
            if (selectedTab == 1) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, EmeraldLight.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = GraphiteCard),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.GroupAdd, contentDescription = null, tint = EmeraldLight)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "JOIN SHARED MINECRAFT WORLD",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldLight,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Paste the pairing code given by the world host (format: MWPAIR:<sharedId>:<token>). Both devices will be linked to the same logically shared world.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = joinPairingCodeInput,
                                onValueChange = { joinPairingCodeInput = it },
                                label = { Text("Pairing Code (e.g. MWPAIR:sw_abc123:...)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }

                // If code is entered, present the Two Options
                val parsedCode = parsePairingCode(joinPairingCodeInput)
                if (parsedCode != null) {
                    item {
                        Text(
                            text = "CHOOSE HOW TO JOIN ON THIS PHONE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // CHOICE A: Link Existing Local Minecraft World
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, LapisAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = GraphiteCardElevated),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Link, contentDescription = null, tint = LapisAccent)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "CHOICE A: LINK EXISTING LOCAL WORLD",
                                        fontWeight = FontWeight.Bold,
                                        color = LapisAccent,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "If you already have a copy of this world on your device, link it. You will see a confirmation dialog before any replacement occurs.",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                if (selectedExistingWorldToLink != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(GraphiteBackground, RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSynced, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(selectedExistingWorldToLink!!.displayName, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 12.sp)
                                            Text("Folder: ${selectedExistingWorldToLink!!.folderName}", fontSize = 10.sp, color = TextMuted)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                Button(
                                    onClick = { showLinkConfirmationDialog = true },
                                    enabled = selectedExistingWorldToLink != null,
                                    colors = ButtonDefaults.buttonColors(containerColor = LapisAccent, contentColor = TextPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("CONFIRM & LINK SELECTED LOCAL WORLD", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // World picker list for Choice A
                    items(discoveredWorlds) { world ->
                        WorldSelectionCard(
                            world = world,
                            isSelected = selectedExistingWorldToLink?.folderName == world.folderName,
                            onClick = { selectedExistingWorldToLink = world }
                        )
                    }

                    // CHOICE B: Download & Create New World
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, EmeraldPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = GraphiteCardElevated),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = EmeraldLight)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "CHOICE B: DOWNLOAD & CREATE NEW WORLD",
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldLight,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "If you don't have this world on your phone yet, create a fresh folder and download the latest synchronized world from Cloudflare R2.",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = joinDisplayNameInput,
                                    onValueChange = { joinDisplayNameInput = it },
                                    label = { Text("Local World Name (e.g. Shared Bedrock Survival)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        val name = if (joinDisplayNameInput.isNotBlank()) joinDisplayNameInput else "Shared World"
                                        onDownloadNewWorld(parsedCode.first, name)
                                        Toast.makeText(context, "Initiated download for $name!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = GraphiteBackground),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("DOWNLOAD & CREATE LOCAL COPY", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // TAB 2: DETAILED LOCAL WORLDS AUDIT
            if (selectedTab == 2) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACCESSIBLE BEDROCK WORLDS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                        IconButton(onClick = onRefreshWorlds) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = EmeraldLight)
                        }
                    }
                }

                if (!storageCapability.isWorldDirectoryAccessible) {
                    item {
                        StoragePermissionNoticeCard(
                            restrictionReason = storageCapability.restrictionReason,
                            onSelectSafFolder = onSelectSafFolder
                        )
                    }
                }

                items(discoveredWorlds) { world ->
                    WorldDetailedItemCard(world = world)
                }
            }
        }
    }

    // Confirmation dialog before replacing or associating existing world
    if (showLinkConfirmationDialog && selectedExistingWorldToLink != null && parsePairingCode(joinPairingCodeInput) != null) {
        val parsed = parsePairingCode(joinPairingCodeInput)!!
        AlertDialog(
            onDismissRequest = { showLinkConfirmationDialog = false },
            title = {
                Text("Confirm Linking Local World", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column {
                    Text(
                        text = "Are you sure you want to associate your local world \"${selectedExistingWorldToLink!!.displayName}\" (${selectedExistingWorldToLink!!.folderName}) with Shared World ID:",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = parsed.first,
                        color = EmeraldLight,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• A rollback safety snapshot will be preserved automatically before any sync replacement.\n" +
                                "• The app will never overwrite unrelated Minecraft worlds.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onLinkExistingWorld(parsed.first, selectedExistingWorldToLink!!)
                        showLinkConfirmationDialog = false
                        Toast.makeText(context, "World linked successfully!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = GraphiteBackground)
                ) {
                    Text("YES, LINK WORLD")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLinkConfirmationDialog = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = GraphiteCardElevated
        )
    }

    // Confirmation dialog for unpairing
    if (showUnpairDialog != null) {
        val targetId = showUnpairDialog!!
        AlertDialog(
            onDismissRequest = { showUnpairDialog = null },
            title = { Text("Unpair Shared World?", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Text(
                    text = "This will disconnect this world from automatic Cloudflare synchronization. Your local world files and snapshot history will remain safe on your device.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUnpairSharedWorld(targetId)
                        showUnpairDialog = null
                        Toast.makeText(context, "World unpaired.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedstoneError, contentColor = TextPrimary)
                ) {
                    Text("UNPAIR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnpairDialog = null }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = GraphiteCardElevated
        )
    }
}

@Composable
private fun MultiplayerArchitectureCard(
    activeSharedWorld: SharedWorldMapping?,
    onUpdateMultiplayerMode: (MultiplayerSyncMode) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GoldWarning.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = GraphiteCardElevated),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Public, contentDescription = null, tint = GoldWarning)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ONE LOGICALLY SHARED WORLD",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldWarning,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Both devices maintain local copies of the same shared Bedrock world. The app does not replace native Minecraft multiplayer; it keeps files synchronized when playing together or offline.",
                color = TextSecondary,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 3 Mode Selector / Status
            val currentMode = activeSharedWorld?.activeMultiplayerMode ?: MultiplayerSyncMode.MODE_1_HOST_READY
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ModeChip(
                    title = "MODE 1",
                    subtitle = "Online Co-op",
                    isSelected = currentMode == MultiplayerSyncMode.MODE_1_HOST_READY || currentMode == MultiplayerSyncMode.MODE_1_JOINED_LIVE,
                    modifier = Modifier.weight(1f),
                    onClick = { onUpdateMultiplayerMode(MultiplayerSyncMode.MODE_1_HOST_READY) }
                )
                ModeChip(
                    title = "MODE 2",
                    subtitle = "Host Offline",
                    isSelected = currentMode == MultiplayerSyncMode.MODE_2_HOST_OFFLINE,
                    modifier = Modifier.weight(1f),
                    onClick = { onUpdateMultiplayerMode(MultiplayerSyncMode.MODE_2_HOST_OFFLINE) }
                )
                ModeChip(
                    title = "MODE 3",
                    subtitle = "Friend Offline",
                    isSelected = currentMode == MultiplayerSyncMode.MODE_3_FRIEND_OFFLINE,
                    modifier = Modifier.weight(1f),
                    onClick = { onUpdateMultiplayerMode(MultiplayerSyncMode.MODE_3_FRIEND_OFFLINE) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            val modeExplanation = when (currentMode) {
                MultiplayerSyncMode.MODE_1_HOST_READY, MultiplayerSyncMode.MODE_1_JOINED_LIVE ->
                    "Mode 1: Galaxy M30s hosts original world. Friend joins live Minecraft multiplayer. When session ends, updated world snapshot uploads and syncs to both devices."
                MultiplayerSyncMode.MODE_2_HOST_OFFLINE ->
                    "Mode 2: Host phone is offline. Friend opens local copy and plays independently. Changes auto-backup to Cloudflare R2 and sync back to host when reconnected."
                MultiplayerSyncMode.MODE_3_FRIEND_OFFLINE ->
                    "Mode 3: Friend is offline. Host plays local world. Changes auto-backup and sync to friend upon reconnection."
            }
            Text(
                text = modeExplanation,
                color = EmeraldLight,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun ModeChip(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) EmeraldPrimary.copy(alpha = 0.2f) else GraphiteCard)
            .border(1.dp, if (isSelected) EmeraldPrimary else TextMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (isSelected) EmeraldLight else TextSecondary)
            Text(subtitle, fontSize = 9.sp, color = if (isSelected) TextPrimary else TextMuted)
        }
    }
}

@Composable
private fun ActiveSharedWorldCard(
    mapping: SharedWorldMapping,
    onCopyPairingCode: () -> Unit,
    onUnpair: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, EmeraldPrimary, RoundedCornerShape(12.dp)),
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
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSynced)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ACTIVE SHARED WORLD",
                        fontWeight = FontWeight.Bold,
                        color = StatusSynced,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = if (mapping.isHostDevice) "HOST (ORIGIN)" else "SYNCHRONIZED CLIENT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldLight,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(mapping.displayName, fontSize = 18.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Text("Local Folder: ${mapping.localFolderId}", fontSize = 11.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            Text("Shared World ID: ${mapping.sharedWorldId}", fontSize = 11.sp, color = EmeraldLight, fontFamily = FontFamily.Monospace)

            Spacer(modifier = Modifier.height(12.dp))
            Text("PAIRING INVITE CODE FOR FRIEND", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Text(
                text = mapping.pairingCode,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = EmeraldLight,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCopyPairingCode,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("COPY CODE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onUnpair,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedstoneError),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("UNPAIR WORLD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WorldSelectionCard(
    world: DiscoveredWorld,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) EmeraldPrimary else TextMuted.copy(alpha = 0.25f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) GraphiteCardElevated else GraphiteCard
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WorldThumbnail(iconFile = world.iconFile)
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = world.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Folder: ${world.folderName}",
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Size: ${formatBytes(world.folderSizeBytes)} • Modified: ${formatDate(world.lastModifiedEpoch)}",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrimary)
            )
        }
    }
}

@Composable
private fun WorldDetailedItemCard(world: DiscoveredWorld) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TextMuted.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = GraphiteCard),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WorldThumbnail(iconFile = world.iconFile)
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(world.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                Text("Directory: ${world.folderName}", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                Text("Path: ${world.absolutePath}", fontSize = 9.sp, color = TextMuted)
                Text(
                    text = "${formatBytes(world.folderSizeBytes)} • ${world.fileCount} files • ${formatDate(world.lastModifiedEpoch)}",
                    fontSize = 10.sp,
                    color = TextSecondary
                )

                if (!world.isEligibleForSync) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ineligible: ${world.ineligibleReason}",
                        color = RedstoneError,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun WorldThumbnail(iconFile: File?) {
    val bitmap = remember(iconFile) {
        if (iconFile != null && iconFile.exists()) {
            runCatching { BitmapFactory.decodeFile(iconFile.absolutePath) }.getOrNull()
        } else null
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "World Icon",
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, TextMuted.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(EmeraldPrimary.copy(alpha = 0.2f))
                .border(1.dp, EmeraldPrimary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Terrain, contentDescription = null, tint = EmeraldLight, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun StoragePermissionNoticeCard(
    restrictionReason: String?,
    onSelectSafFolder: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GoldWarning, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = GraphiteCardElevated),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = GoldWarning)
                Spacer(modifier = Modifier.width(8.dp))
                Text("STORAGE ACCESS LIMITATION", fontWeight = FontWeight.Bold, color = GoldWarning, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = restrictionReason ?: "Direct Minecraft storage access is restricted. Use the folder picker below to authorize the Minecraft folder via Storage Access Framework.",
                color = TextSecondary,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onSelectSafFolder,
                colors = ButtonDefaults.buttonColors(containerColor = GoldWarning, contentColor = GraphiteBackground),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("AUTHORIZE MINECRAFT WORLDS FOLDER (SAF)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun parsePairingCode(input: String): Pair<String, String>? {
    val trimmed = input.trim()
    if (!trimmed.startsWith("MWPAIR:")) return null
    val parts = trimmed.split(":")
    if (parts.size < 3) return null
    return Pair(parts[1], parts[2])
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatDate(epoch: Long): String {
    if (epoch <= 0) return "Unknown"
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(epoch))
}
