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
import com.gitsync.minecraft.core.database.CommitEntity
import com.gitsync.minecraft.core.model.Commit
import com.gitsync.minecraft.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    commits: List<CommitEntity>,
    onRestoreCommit: (Commit) -> Unit,
    onBack: () -> Unit
) {
    var selectedCommitForRestore by remember { mutableStateOf<Commit?>(null) }

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
                    text = "VERSION HISTORY & COMMITS",
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "IMMUTABLE SNAPSHOT LOG (${commits.size} COMMITS)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (commits.isEmpty()) {
                item {
                    Text(
                        text = "No snapshots recorded yet. Commits will appear automatically when you close Minecraft.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                items(commits) { entity ->
                    val commit = entity.toModel()
                    CommitItemCard(
                        commit = commit,
                        onRestoreClick = { selectedCommitForRestore = commit }
                    )
                }
            }
        }
    }

    selectedCommitForRestore?.let { commit ->
        AlertDialog(
            onDismissRequest = { selectedCommitForRestore = null },
            title = { Text("Restore Snapshot?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to restore '${commit.commitMessage}'? " +
                            "A safety backup of your current world will be taken before restoring."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreCommit(commit)
                        selectedCommitForRestore = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = GraphiteBackground)
                ) {
                    Text("RESTORE SNAPSHOT")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { selectedCommitForRestore = null }) {
                    Text("CANCEL")
                }
            },
            containerColor = GraphiteCardElevated,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}

@Composable
private fun CommitItemCard(
    commit: Commit,
    onRestoreClick: () -> Unit
) {
    val dateStr = remember(commit.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        sdf.format(Date(commit.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GraphiteCardBorder, RoundedCornerShape(10.dp)),
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
                    Icon(Icons.Default.Commit, contentDescription = null, tint = EmeraldLight, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = commit.commitId.take(10),
                        color = EmeraldLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = "${commit.fileSizeBytes / 1024} KB",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = commit.commitMessage, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))

            Text(text = "Author: ${commit.authorName} • $dateStr", color = TextSecondary, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "SHA-256: ${commit.sha256Checksum}",
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = onRestoreClick,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RESTORE THIS VERSION", fontSize = 11.sp)
                }
            }
        }
    }
}
