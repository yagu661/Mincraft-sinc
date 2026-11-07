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
                                app.syncEngine.start()
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
