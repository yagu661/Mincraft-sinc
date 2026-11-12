package com.gitsync.minecraft.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitsync.minecraft.GitSyncApplication
import com.gitsync.minecraft.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as GitSyncApplication
    val coroutineScope = rememberCoroutineScope()
    var gracePeriodSec by remember { mutableStateOf(8f) }
    var signalingUrl by remember { mutableStateOf(app.keystoreManager.getSignalingServerUrl()) }
    var roomId by remember { mutableStateOf(app.keystoreManager.getRoomId()) }
    var storageUrl by remember { mutableStateOf(app.keystoreManager.getStorageUrl()) }
    var saveStatus by remember { mutableStateOf<String?>(null) }

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
                    text = "SETTINGS & SYSTEM",
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
                            text = "SESSION GRACE PERIOD",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Wait ${gracePeriodSec.toInt()} seconds after leaving Minecraft before creating snapshot. " +
                                    "This ensures Minecraft has fully flushed LevelDB chunks and prevents capturing while switching apps briefly.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Slider(
                            value = gracePeriodSec,
                            onValueChange = { gracePeriodSec = it },
                            valueRange = 3f..30f,
                            steps = 26,
                            colors = SliderDefaults.colors(
                                thumbColor = EmeraldPrimary,
                                activeTrackColor = EmeraldPrimary,
                                inactiveTrackColor = GraphiteCardBorder
                            )
                        )
                    }
                }
            }

            // MINECRAFT STORAGE & ZARCHIVER INTEGRATION
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
                            Icon(Icons.Default.FolderZip, contentDescription = null, tint = EmeraldLight)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MINECRAFT STORAGE & ZARCHIVER",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Minecraft Bedrock External storage directory on Android:\n" +
                                    "/storage/emulated/0/Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds/",
                            fontSize = 11.sp,
                            color = EmeraldLight,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "To access this folder directly on Android 11 or Android 15, use ZArchiver to view world folders or export .mcworld backups. You can share .mcworld files directly to GitSync.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val pm = context.packageManager
                                val zPackages = listOf("ru.zdevs.zarchiver.pro", "ru.zdevs.zarchiver")
                                var launched = false
                                for (pkg in zPackages) {
                                    val launchIntent = pm.getLaunchIntentForPackage(pkg)
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(launchIntent)
                                        launched = true
                                        break
                                    }
                                }
                                if (!launched) {
                                    try {
                                        val marketIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=ru.zdevs.zarchiver")).apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(marketIntent)
                                    } catch (_: Exception) {
                                        val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=ru.zdevs.zarchiver")).apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(webIntent)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = GraphiteBackground),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("OPEN MINECRAFT STORAGE WITH ZARCHIVER", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            // GOOGLE DRIVE (SAF - NO BILLING / NO CONSOLE)
            item {
                val driveFolderName = app.keystoreManager.getSafDriveFolderName()
                val isConnected = app.keystoreManager.isSafDriveFolderConnected()
                var driveStatusMsg by remember { mutableStateOf<String?>(null) }
                val safManager = remember { com.gitsync.minecraft.core.storage.SafDriveFolderManager(context) { app.keystoreManager.getSafDriveFolderUri() } }
                val coroutineScope = rememberCoroutineScope()

                val driveFolderPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
                ) { uri ->
                    if (uri != null) {
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        } catch (e: Exception) {
                            android.util.Log.w("SettingsScreen", "Failed to take persistable URI permission", e)
                        }
                        val folderDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                        val folderName = folderDoc?.name ?: "Minecraft Sync"
                        app.keystoreManager.setSafDriveFolderUri(uri, folderName)
                        coroutineScope.launch {
                            val res = safManager.verifyFolderReadWriteAccess()
                            driveStatusMsg = if (res.isSuccessful) {
                                "Connected & Verified: '$folderName' (Read/Write OK)"
                            } else {
                                "Warning: ${res.errorMessage}"
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GraphiteCardBorder, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = GraphiteCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = EmeraldLight)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SHARED GOOGLE DRIVE FOLDER (SAF)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Connect your shared 'Minecraft Sync' Google Drive folder using Android's Storage Access Framework. " +
                                    "No Google Cloud Console billing, OAuth client IDs, or API keys required. " +
                                    "Both you and your friend independently select the shared folder using your own Google accounts.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (isConnected && driveFolderName != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(GraphiteCardElevated, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSynced, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Connected Folder: $driveFolderName",
                                    color = EmeraldLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { driveFolderPicker.launch(null) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = GraphiteBackground),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isConnected) "CHANGE FOLDER" else "CONNECT FOLDER", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            if (isConnected) {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val res = safManager.verifyFolderReadWriteAccess()
                                            driveStatusMsg = if (res.isSuccessful) {
                                                "Verification Passed: Read, Write & Delete operations confirmed!"
                                            } else {
                                                "Verification Failed: ${res.errorMessage}"
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("TEST READ/WRITE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (driveStatusMsg != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = driveStatusMsg!!,
                                color = EmeraldLight,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GraphiteCardBorder, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = GraphiteCardElevated),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = EmeraldLight)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Android Background Restrictions",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Android restricts background network activity during Doze mode. " +
                                    "GitSync uses standard WorkManager and foreground dataSync notifications to reliably complete long file transfers. " +
                                    "If transfers are delayed when your phone is asleep, verify in Android Settings that GitSync is allowed unrestricted battery usage.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

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
                            text = "CLOUDFLARE BACKEND CONFIGURATION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = signalingUrl,
                            onValueChange = { signalingUrl = it },
                            label = { Text("Cloudflare WebSocket URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = roomId,
                            onValueChange = { roomId = it },
                            label = { Text("Sync Room ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = storageUrl,
                            onValueChange = { storageUrl = it },
                            label = { Text("Cloudflare Storage (R2) HTTP URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                app.keystoreManager.setSignalingServerUrl(signalingUrl)
                                app.keystoreManager.setRoomId(roomId)
                                app.keystoreManager.setStorageUrl(storageUrl)
                                saveStatus = "Cloudflare settings saved! Reconnecting room..."
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = GraphiteBackground),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("SAVE & RECONNECT ROOM", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        if (saveStatus != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = saveStatus!!,
                                color = EmeraldLight,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
