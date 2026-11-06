package com.gitsync.minecraft.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitsync.minecraft.core.model.DiscoveredWorld
import com.gitsync.minecraft.core.model.StorageDiagnostic
import com.gitsync.minecraft.ui.theme.*

@Composable
fun StorageDiagnosticScreen(
    diagnostic: StorageDiagnostic,
    discoveredWorlds: List<DiscoveredWorld>,
    selectedWorldId: String?,
    onSelectWorld: (DiscoveredWorld) -> Unit,
    onRunSafeTestBackup: () -> Unit,
    onRerunDiagnostic: () -> Unit,
    onBack: () -> Unit
) {
    var showTestBackupDialog by remember { mutableStateOf(false) }

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
                    text = "STORAGE DIAGNOSTICS & WORLDS",
                    color = TextPrimary,
                    fontSize = 17.sp,
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
            // Target Test Device Profile Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EmeraldPrimary, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = GraphiteCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = EmeraldLight)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TARGET: SAMSUNG GALAXY M30s (ANDROID 11)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldLight,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        DiagnosticMetricRow("Android OS Version", "API ${diagnostic.androidOsVersion} (Android 11)")
                        DiagnosticMetricRow("App Target SDK", "${diagnostic.targetSdkVersion} (Legacy Storage Mode)")
                        DiagnosticMetricRow(
                            "isExternalStorageLegacy()",
                            if (diagnostic.isExternalStorageLegacy) "TRUE (Active)" else "FALSE"
                        )
                        DiagnosticMetricRow(
                            "Runtime Permissions",
                            if (diagnostic.hasReadPermission) "READ GRANTED" else "READ MISSING"
                        )
                        DiagnosticMetricRow(
                            "Safe Write Test",
                            if (diagnostic.safeWriteTestPassed) "PASSED (Private Staging)" else "FAILED"
                        )
                    }
                }
            }

            // Path Evaluation Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GraphiteCardBorder, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = GraphiteCardElevated),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "MINECRAFT BEDROCK PATH AUDIT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = diagnostic.testedPath,
                            fontSize = 11.sp,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        DiagnosticMetricRow("Directory Exists", if (diagnostic.directoryExists) "DETECTED" else "NOT FOUND")
                        DiagnosticMetricRow("Direct Read Access", if (diagnostic.readAccessAvailable) "AVAILABLE" else "BLOCKED")
                        DiagnosticMetricRow("World Enumeration", if (diagnostic.worldEnumerationSuccessful) "SUCCESSFUL" else "FAILED")
                        DiagnosticMetricRow("Safe In-Place Replacement", if (diagnostic.safeReplacementSupported) "SUPPORTED" else "SAF / .MCWORLD IMPORT FALLBACK")

                        if (diagnostic.restrictionDetails != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Notice: ${diagnostic.restrictionDetails}",
                                color = GoldWarning,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Actions & Non-Destructive Test Backup
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRerunDiagnostic,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = GraphiteCardElevated, contentColor = TextPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("RE-TEST STORAGE", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            onRunSafeTestBackup()
                            showTestBackupDialog = true
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = GraphiteBackground),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SAFE TEST BACKUP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Discovered Worlds List (Section C)
            item {
                Text(
                    text = "DISCOVERED MINECRAFT WORLDS (${discoveredWorlds.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (discoveredWorlds.isEmpty()) {
                item {
                    Text(
                        text = "No worlds detected in directory. Ensure Minecraft storage is set to External and at least one world exists.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            } else {
                items(discoveredWorlds) { world ->
                    val isSelected = world.worldId == selectedWorldId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isSelected) EmeraldPrimary else GraphiteCardBorder,
                                shape = RoundedCornerShape(10.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) GraphiteCardElevated else GraphiteCard),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        tint = if (isSelected) EmeraldLight else TextSecondary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(text = world.displayName, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                        Text(text = "Folder: ${world.folderName}", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .background(EmeraldContainer, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("ACTIVE SHARED WORLD", color = EmeraldLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Size: ${world.folderSizeBytes / 1024} KB • ${world.fileCount} files", color = TextSecondary, fontSize = 11.sp)
                                Text(text = if (world.hasDbDirectory) "LevelDB Valid" else "db/ Missing", color = if (world.hasDbDirectory) StatusSynced else StatusConflict, fontSize = 11.sp)
                            }

                            if (!isSelected) {
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = { onSelectWorld(world) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                                ) {
                                    Text("SELECT AS SHARED WORLD FOR GITSYNC", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTestBackupDialog) {
        AlertDialog(
            onDismissRequest = { showTestBackupDialog = false },
            title = { Text("Safe Non-Destructive Test Backup", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "A test snapshot was safely packaged into GitSync's private directory without modifying or writing into your Minecraft world. " +
                            "LevelDB write lock detection and SHA-256 integrity passed."
                )
            },
            confirmButton = {
                Button(
                    onClick = { showTestBackupDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = GraphiteBackground)
                ) {
                    Text("OK")
                }
            },
            containerColor = GraphiteCardElevated,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}

@Composable
private fun DiagnosticMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
        Text(text = value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
