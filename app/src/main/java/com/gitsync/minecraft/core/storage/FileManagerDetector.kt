package com.gitsync.minecraft.core.storage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

data class AppChooserItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isKnownFileManager: Boolean,
    val intent: Intent
)

object FileManagerDetector {

    /**
     * Common Android file managers including ZArchiver, ZArchiver Pro, Samsung My Files,
     * Total Commander, Solid Explorer, MiXplorer, Files by Google, and system DocumentsUI.
     */
    val KNOWN_FILE_MANAGERS = mapOf(
        "ru.zdevs.zarchiver" to "ZArchiver",
        "ru.zdevs.zarchiver.pro" to "ZArchiver Pro",
        "com.google.android.documentsui" to "Android System Files (SAF)",
        "com.google.android.apps.nbu.files" to "Files by Google",
        "com.sec.android.app.myfiles" to "Samsung My Files",
        "pl.solidexplorer2" to "Solid Explorer",
        "com.ghisler.android.TotalCommander" to "Total Commander",
        "com.mixplorer" to "MiXplorer",
        "com.speedsoftware.rootexplorer" to "Root Explorer",
        "com.alphainventor.filemanager" to "File Manager (Flash)"
    )

    /**
     * Resolves all installed apps on the device that can browse or pick directories/files,
     * prioritizing ZArchiver and specialized file managers, plus the system document provider.
     */
    fun getInstalledFileApps(context: Context): List<AppChooserItem> {
        val pm = context.packageManager
        val items = mutableListOf<AppChooserItem>()
        val seenPackages = mutableSetOf<String>()

        // 1. Check for specific known file managers directly first (like ZArchiver)
        for ((pkg, label) in KNOWN_FILE_MANAGERS) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val appName = pm.getApplicationLabel(appInfo).toString().ifBlank { label }
                val icon = pm.getApplicationIcon(appInfo)

                // Try OPEN_DOCUMENT_TREE intent first for SAF compatibility, falling back to launch intent
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    setPackage(pkg)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }

                // If OPEN_DOCUMENT_TREE has no resolve in this package, use launch/get content
                val resolved = pm.queryIntentActivities(intent, 0)
                val finalIntent = if (resolved.isNotEmpty()) {
                    intent
                } else {
                    pm.getLaunchIntentForPackage(pkg) ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        setPackage(pkg)
                        type = "*/*"
                    }
                }

                items.add(
                    AppChooserItem(
                        packageName = pkg,
                        appName = appName,
                        icon = icon,
                        isKnownFileManager = true,
                        intent = finalIntent
                    )
                )
                seenPackages.add(pkg)
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed
            }
        }

        // 2. Discover any additional apps that respond to ACTION_OPEN_DOCUMENT_TREE
        val treeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        val treeResolvers = pm.queryIntentActivities(treeIntent, 0)
        for (ri in treeResolvers) {
            val pkg = ri.activityInfo.packageName
            if (!seenPackages.contains(pkg)) {
                val name = ri.loadLabel(pm).toString()
                val icon = ri.loadIcon(pm)
                val appSpecificIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    setPackage(pkg)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                items.add(
                    AppChooserItem(
                        packageName = pkg,
                        appName = name,
                        icon = icon,
                        isKnownFileManager = KNOWN_FILE_MANAGERS.containsKey(pkg),
                        intent = appSpecificIntent
                    )
                )
                seenPackages.add(pkg)
            }
        }

        // 3. Fallback generic system DocumentsUI option (default picker)
        val genericSystemIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        if (items.none { it.packageName == "com.google.android.documentsui" && it.intent.getPackage() == null }) {
            items.add(
                AppChooserItem(
                    packageName = "android.system.default",
                    appName = "Android Default Folder Picker (SAF)",
                    icon = null,
                    isKnownFileManager = true,
                    intent = genericSystemIntent
                )
            )
        }

        // Sort so ZArchiver and recognized file managers appear at the very top
        return items.sortedWith(
            compareByDescending<AppChooserItem> { it.packageName.contains("zarchiver", ignoreCase = true) }
                .thenByDescending { it.isKnownFileManager }
                .thenBy { it.appName }
        )
    }
}
