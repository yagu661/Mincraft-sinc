package com.gitsync.minecraft.core.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object StorageAccessHelper {

    private const val TAG = "StorageAccessHelper"

    fun persistUriPermission(context: Context, uri: Uri, intentFlags: Int) {
        val takeFlags = intentFlags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            Log.i(TAG, "Successfully persisted SAF URI permission: $uri")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to persist URI permission for $uri", e)
        }
    }

    fun readTextContent(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText().trim()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading text from URI: $uri", e)
            null
        }
    }

    fun getAvailableStorageBytes(dir: File): Long {
        return try {
            val stat = StatFs(dir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }

    fun getSnapshotsDirectory(context: Context): File {
        val dir = File(context.filesDir, "snapshots")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getTempDirectory(context: Context): File {
        val dir = File(context.cacheDir, "staging")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun cleanTempDirectory(context: Context) {
        try {
            val dir = File(context.cacheDir, "staging")
            if (dir.exists()) {
                dir.deleteRecursively()
                dir.mkdirs()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean staging temp dir", e)
        }
    }
}
