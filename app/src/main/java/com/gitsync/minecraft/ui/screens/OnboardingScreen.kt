package com.gitsync.minecraft.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitsync.minecraft.GitSyncApplication
import com.gitsync.minecraft.core.model.StorageCapability
import com.gitsync.minecraft.core.storage.StorageAccessHelper
import com.gitsync.minecraft.feature.monitoring.MinecraftUsageStatsHelper
import com.gitsync.minecraft.ui.theme.*

/**
 * Guided First-Launch Permission & Cloud Drive Onboarding Screen.
 *
 * Sequence:
 * Step 1: Minecraft World Storage (Legacy / SAF Folder Access).
 * Step 2: Minecraft Foreground Session Detection (Usage Stats).
 * Step 3: Google Account & Shared Google Drive Folder Setup.
 * Step 4: Friend Google Account Email Sharing.
 * Step 5: Android Notifications & Background Sync readiness.
 */
@Composable
fun OnboardingScreen(
    storageCapability: StorageCapability,
    hasUsagePermission: Boolean,
    onSaveSafUri: (Uri) -> Unit,
    onConnectGoogle: (email: String, token: String) -> Unit,
    onSetFriendEmail: (String) -> Unit,
    onFinishOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as GitSyncApplication
    var currentStep by remember { mutableStateOf(1) }

    var inputGoogleEmail by remember { mutableStateOf(app.keystoreManager.getGoogleAccountEmail() ?: "") }
    var inputGoogleToken by remember { mutableStateOf(app.keystoreManager.getGoogleAccessToken() ?: "") }
    var inputFriendEmail by remember { mutableStateOf(app.keystoreManager.getFriendGoogleEmail() ?: "") }

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmeraldPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = GraphiteBackground, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "GITSYNC SETUP WIZARD",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                        Text(
                            text = "Step $currentStep of 4: Setup Minecraft Bedrock & Drive Sync",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { (currentStep.toFloat() / 4f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = EmeraldLight,
                    trackColor = GraphiteCard
                )
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
            // STEP 1: MINECRAFT STORAGE ACCESS
            if (currentStep == 1) {
                item {
                    OnboardingStepCard(
                        stepNumber = "1",
                        icon = Icons.Default.FolderSpecial,
                        title = "Minecraft Bedrock Storage Access",
                        subtitle = "Required to discover and synchronize your Bedrock worlds.",
                        statusText = if (storageCapability.isWorldDirectoryAccessible) "STORAGE ACCESSIBLE" else "PERMISSION NEEDED",
                        isComplete = storageCapability.isWorldDirectoryAccessible
                    ) {
                        Column {
                            Text(
                                text = "Minecraft Bedrock must have storage location set to 'External' in Minecraft Settings -> Storage.\n\n" +
                                        if (Build.VERSION.SDK_INT >= 30) {
                                            "On Android 11+ and Android 15, direct access to app folders is restricted by the OS. Authorize the Minecraft world backup folder using the official Storage Access Framework (SAF) folder picker."
                                        } else {
                                            "On Android 10 and below, legacy external storage is used (/sdcard/games/com.mojang/minecraftWorlds)."
                                        },
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            if (!storageCapability.isWorldDirectoryAccessible) {
                                Button(
                                    onClick = { showFileManagerChooser = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = GraphiteBackground),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("SELECT FOLDER (CHOOSE ZARCHIVER / FILES APP)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(GraphiteCardElevated, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSynced, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Minecraft worlds directory is ready (${storageCapability.detectedWorldsCount} worlds detected)", color = EmeraldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // STEP 2: USAGE STATS PERMISSION
            if (currentStep == 2) {
                item {
                    OnboardingStepCard(
                        stepNumber = "2",
                        icon = Icons.Default.Bolt,
                        title = "Minecraft Session Detection",
                        subtitle = "Detects when you close Minecraft so backups can sync automatically.",
                        statusText = if (hasUsagePermission) "USAGE ACCESS GRANTED" else "USAGE ACCESS NEEDED",
                        isComplete = hasUsagePermission
                    ) {
                        Column {
                            Text(
                                text = "GitSync uses Usage Access to notice when Minecraft leaves the screen, ensuring worlds are never uploaded while Minecraft is actively writing LevelDB chunks.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            if (!hasUsagePermission) {
                                Button(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = GraphiteBackground),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("OPEN USAGE ACCESS SETTINGS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(GraphiteCardElevated, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSynced, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Usage Access is enabled! Automatic post-game backups are ready.", color = EmeraldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // STEP 3: GOOGLE DRIVE SHARED FOLDER CONFIGURATION
            if (currentStep == 3) {
                item {
                    OnboardingStepCard(
                        stepNumber = "3",
                        icon = Icons.Default.CloudUpload,
                        title = "Google Drive Shared Folder",
                        subtitle = "Transfers actual Minecraft .mcworld files across your Google accounts.",
                        statusText = if (inputGoogleEmail.isNotBlank()) "ACCOUNT CONFIGURED" else "ENTER ACCOUNT",
                        isComplete = inputGoogleEmail.isNotBlank()
                    ) {
                        Column {
                            Text(
                                text = "Your world files are stored in a dedicated Google Drive folder ('Minecraft_Bedrock_Sync') and shared with your friend. Each account keeps its own 15 GB quota.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = inputGoogleEmail,
                                onValueChange = { inputGoogleEmail = it },
                                label = { Text("Your Google Account Email") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = inputGoogleToken,
                                onValueChange = { inputGoogleToken = it },
                                label = { Text("OAuth Access Token / Bearer Token") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    if (inputGoogleEmail.isNotBlank()) {
                                        onConnectGoogle(inputGoogleEmail.trim(), inputGoogleToken.trim())
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = GraphiteBackground),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("SAVE & INITIALIZE DRIVE SYNC FOLDER", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // STEP 4: FRIEND'S GOOGLE ACCOUNT SHARING & COMPLETION
            if (currentStep == 4) {
                item {
                    OnboardingStepCard(
                        stepNumber = "4",
                        icon = Icons.Default.GroupAdd,
                        title = "Friend Sharing & Notifications",
                        subtitle = "Share the Drive folder with your friend's Google email address.",
                        statusText = "FINAL STEP",
                        isComplete = inputFriendEmail.isNotBlank()
                    ) {
                        Column {
                            Text(
                                text = "Enter your friend's Google Account email. The sync folder will be automatically shared with them with 'writer' permissions so their phone can sync world backups.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = inputFriendEmail,
                                onValueChange = { inputFriendEmail = it },
                                label = { Text("Friend's Google Email Address") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    if (inputFriendEmail.isNotBlank()) {
                                        onSetFriendEmail(inputFriendEmail.trim())
                                    }
                                    onFinishOnboarding()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = GraphiteBackground),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("COMPLETE SETUP & ENTER DASHBOARD", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Navigation buttons (Next / Skip / Back)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 1) {
                        TextButton(onClick = { currentStep-- }) {
                            Text("BACK", color = TextSecondary)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (currentStep < 4) {
                        Button(
                            onClick = { currentStep++ },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldLight, contentColor = GraphiteBackground),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("CONTINUE", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        TextButton(onClick = onFinishOnboarding) {
                            Text("SKIP TO DASHBOARD", color = TextMuted)
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
                    android.util.Log.w("OnboardingScreen", "Direct package launch failed, falling back to tree picker", e)
                    treeLauncher.launch(null)
                }
            }
        )
    }
}

@Composable
private fun OnboardingStepCard(
    stepNumber: String,
    icon: ImageVector,
    title: String,
    subtitle: String,
    statusText: String,
    isComplete: Boolean,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isComplete) EmeraldPrimary.copy(alpha = 0.5f) else GraphiteCardBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = GraphiteCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isComplete) StatusSynced else GraphiteBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stepNumber,
                            color = if (isComplete) GraphiteBackground else EmeraldLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = statusText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isComplete) StatusSynced else GoldWarning,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, fontSize = 11.sp, color = TextMuted)
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}
