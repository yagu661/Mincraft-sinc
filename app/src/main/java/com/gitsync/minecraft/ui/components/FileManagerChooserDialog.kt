package com.gitsync.minecraft.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.gitsync.minecraft.core.storage.AppChooserItem
import com.gitsync.minecraft.core.storage.FileManagerDetector
import com.gitsync.minecraft.ui.theme.*

/**
 * Interactive popup dialog that lists all installed file manager apps on the phone
 * (including ZArchiver, ZArchiver Pro, Samsung My Files, Total Commander, Solid Explorer,
 * and Android Default Picker) and lets the user choose their preferred app to select
 * the Minecraft Bedrock world folder.
 */
@Composable
fun FileManagerChooserDialog(
    onDismissRequest: () -> Unit,
    onAppSelected: (AppChooserItem) -> Unit
) {
    val context = LocalContext.current
    val installedApps = remember { FileManagerDetector.getInstalledFileApps(context) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(EmeraldPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = GraphiteBackground,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "CHOOSE FILE MANAGER",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                    Text(
                        text = "Select ZArchiver or another file manager",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Pick the file manager you want to use to browse or authorize your Minecraft worlds folder. ZArchiver and recognized tools appear first:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(installedApps) { appItem ->
                        AppChooserCard(
                            appItem = appItem,
                            onClick = {
                                onAppSelected(appItem)
                                onDismissRequest()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("CANCEL", color = TextSecondary, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = GraphiteCardElevated,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun AppChooserCard(
    appItem: AppChooserItem,
    onClick: () -> Unit
) {
    val isZArchiver = appItem.packageName.contains("zarchiver", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isZArchiver) 1.5.dp else 1.dp,
                color = if (isZArchiver) EmeraldPrimary else GraphiteCardBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isZArchiver) GraphiteCard else GraphiteBackground
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawableAppIcon(drawable = appItem.icon, isZArchiver = isZArchiver)
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = appItem.appName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isZArchiver) EmeraldLight else TextPrimary
                    )
                    if (isZArchiver) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(EmeraldPrimary.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "RECOMMENDED",
                                color = EmeraldLight,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                Text(
                    text = appItem.packageName,
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isZArchiver) EmeraldLight else TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DrawableAppIcon(drawable: Drawable?, isZArchiver: Boolean) {
    val bitmap = remember(drawable) {
        drawable?.let { d ->
            if (d is BitmapDrawable && d.bitmap != null) {
                d.bitmap
            } else {
                val width = if (d.intrinsicWidth > 0) d.intrinsicWidth else 96
                val height = if (d.intrinsicHeight > 0) d.intrinsicHeight else 96
                val bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bm)
                d.setBounds(0, 0, canvas.width, canvas.height)
                d.draw(canvas)
                bm
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isZArchiver) EmeraldPrimary.copy(alpha = 0.2f) else GraphiteCardElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isZArchiver) Icons.Default.Archive else Icons.Default.Folder,
                contentDescription = null,
                tint = if (isZArchiver) EmeraldLight else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
