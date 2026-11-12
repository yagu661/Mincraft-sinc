package com.gitsync.minecraft.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitsync.minecraft.core.model.StorageCapability
import com.gitsync.minecraft.core.storage.StorageAccessHelper
import com.gitsync.minecraft.feature.monitoring.MinecraftUsageStatsHelper
import com.gitsync.minecraft.ui.AppScreen
import com.gitsync.minecraft.ui.theme.*

@Composable
fun StorageSetupScreen(
    storageCapability: StorageCapability,
    hasUsagePermission: Boolean,
    onSaveSafUri: (Uri) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showFileManagerChooser by remember { mutableStateOf(false) }

    val treeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            StorageAccessHelper.persistUriPermission(
                context,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            onSaveSafUri(uri)
        }
    }

    val customAppLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            StorageAccessHelper.persistUriPermission(
                context,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            onSaveSafUri(uri)
        }
    }

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
                    text = "STORAGE & CAPABILITIES",
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
            // Storage Capability Status Box
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GraphiteCardBorder, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = GraphiteCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "MINECRAFT STORAGE CAPABILITY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        CapabilityRow(
                            label = "Storage Access Type",
                            value = storageCapability.storageType.name,
                            isOk = storageCapability.isWorldDirectoryAccessible
                        )
                        CapabilityRow(
                            label = "Read Access",
                            value = if (storageCapability.isReadAvailable) "Available" else "Restricted",
                            isOk = storageCapability.isReadAvailable
                        )
                        CapabilityRow(
                            label = "Automatic Replacement",
                            value = if (storageCapability.isAutomaticReplacementSupported) "Supported" else "Requires .mcworld import",
                            isOk = storageCapability.isAutomaticReplacementSupported
                        )
                        CapabilityRow(
                            label = "Persistent SAF URI",
                            value = if (storageCapability.isPersistentUriGranted) "Granted" else "Not Authorized",
                            isOk = storageCapability.isPersistentUriGranted
                        )
                    }
                }
            }

            // Android Scoped Storage Explanation (Section 2)
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
                            text = "ANDROID SCOPED STORAGE RULES",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldLight,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Minecraft Bedrock Android stores worlds in either 'Application' (private sandbox) or 'External' storage.\n\n" +
                                    "On modern Android (11+), Android blocks direct app access to Android/data/. To sync automatically, authorize your Minecraft folder using Android's system folder picker below. " +
                                    "If direct write access is restricted by your device manufacturer, GitSync automatically provides a safe .mcworld export/import fallback.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { showFileManagerChooser = true },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = GraphiteBackground),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SELECT FOLDER (CHOOSE ZARCHIVER / FILES APP)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Usage Access Permission Card (Section 3)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (hasUsagePermission) EmeraldContainer else GoldWarning, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = GraphiteCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasUsagePermission) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (hasUsagePermission) EmeraldLight else GoldWarning
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Minecraft Session Detection",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (hasUsagePermission) {
                                "Usage Access granted. GitSync will automatically detect when you open and close Minecraft."
                            } else {
                                "Usage Access permission is needed so GitSync can detect when com.mojang.minecraftpe exits the foreground."
                            },
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        if (!hasUsagePermission) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { context.startActivity(MinecraftUsageStatsHelper.openUsageAccessSettingsIntent()) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                            ) {
                                Text("GRANT USAGE ACCESS PERMISSION", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Detected Worlds
            item {
                Text(
                    text = "DETECTED WORLDS (${storageCapability.detectedWorldsCount})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (storageCapability.detectedWorldNames.isEmpty()) {
                item {
                    Text(
                        text = "No worlds detected yet. Authorize folder above or create a test world in Test Mode.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            } else {
                items(storageCapability.detectedWorldNames) { worldName ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GraphiteCardBorder, RoundedCornerShape(8.dp)),
                        colors = CardDefaults.cardColors(containerColor = GraphiteCardElevated),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = EmeraldLight)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = worldName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    if (showFileManagerChooser) {
        com.gitsync.minecraft.ui.components.FileManagerChooserDialog(
            onDismissRequest = { showFileManagerChooser = false },
            onAppSelected = { chosenApp ->
                showFileManagerChooser = false
                try {
                    if (chosenApp.packageName == "android.system.default") {
                        treeLauncher.launch(null)
                    } else {
                        customAppLauncher.launch(chosenApp.intent)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("StorageSetupScreen", "Direct package launch failed, falling back to tree picker", e)
                    treeLauncher.launch(null)
                }
            }
        )
    }
}

@Composable
private fun CapabilityRow(label: String, value: String, isOk: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
        Text(
            text = value,
            color = if (isOk) EmeraldLight else GoldWarning,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
