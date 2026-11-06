package com.gitsync.minecraft.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitsync.minecraft.core.model.Commit
import com.gitsync.minecraft.ui.theme.*

@Composable
fun ConflictResolutionScreen(
    commitA: Commit?,
    commitB: Commit?,
    onChooseCommit: (Commit) -> Unit,
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
                    text = "BRANCH CONFLICT RESOLUTION",
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
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, RedstoneError, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = RedstoneError.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CallSplit, contentDescription = null, tint = RedstoneError)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DIVERGENT TIMELINES DETECTED",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Both you and your friend independently modified the world from a shared parent snapshot. " +
                                    "Minecraft Bedrock stores terrain and chunk data inside proprietary LevelDB binary files—automatic merging cannot be safely performed without risking world corruption. " +
                                    "Choose which branch to keep as the primary world, or export the other as a backup.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Branch A Card (Your local version)
            if (commitA != null) {
                item {
                    BranchOptionCard(
                        branchName = "BRANCH A (YOUR DEVICE)",
                        commit = commitA,
                        isLocal = true,
                        onSelect = { onChooseCommit(commitA) }
                    )
                }
            }

            // Branch B Card (Friend's version)
            if (commitB != null) {
                item {
                    BranchOptionCard(
                        branchName = "BRANCH B (FRIEND'S DEVICE)",
                        commit = commitB,
                        isLocal = false,
                        onSelect = { onChooseCommit(commitB) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BranchOptionCard(
    branchName: String,
    commit: Commit,
    isLocal: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isLocal) EmeraldPrimary else LapisAccent, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = GraphiteCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = branchName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isLocal) EmeraldLight else LapisAccent,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = commit.commitMessage, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Author: ${commit.authorName} • ${commit.fileSizeBytes / 1024} KB", color = TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "SHA-256: ${commit.sha256Checksum}", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onSelect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLocal) EmeraldPrimary else LapisAccent,
                    contentColor = GraphiteBackground
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SELECT THIS VERSION AS PRIMARY", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
