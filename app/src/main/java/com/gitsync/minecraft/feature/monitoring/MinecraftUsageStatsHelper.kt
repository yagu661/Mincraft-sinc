package com.gitsync.minecraft.feature.monitoring

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log

object MinecraftUsageStatsHelper {

    private const val TAG = "UsageStatsHelper"
    const val MINECRAFT_PACKAGE = "com.mojang.minecraftpe"

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Determines whether Minecraft is currently the foreground application.
     */
    fun isMinecraftInForeground(context: Context): Boolean {
        if (!hasUsageStatsPermission(context)) return false

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 30_000, now)
        val event = UsageEvents.Event()

        var lastEventPackage: String? = null
        var lastEventType: Int = -1

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                event.eventType == UsageEvents.Event.ACTIVITY_STOPPED
            ) {
                lastEventPackage = event.packageName
                lastEventType = event.eventType
            }
        }

        return lastEventPackage == MINECRAFT_PACKAGE && lastEventType == UsageEvents.Event.ACTIVITY_RESUMED
    }

    /**
     * Finds the epoch timestamp of the last time Minecraft left the foreground.
     */
    fun getLastMinecraftExitTimestamp(context: Context): Long {
        if (!hasUsageStatsPermission(context)) return 0L

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0L
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 24 * 3600_000, now) // Look up to last 24h
        val event = UsageEvents.Event()

        var lastExit = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == MINECRAFT_PACKAGE &&
                (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED || event.eventType == UsageEvents.Event.ACTIVITY_STOPPED)
            ) {
                lastExit = event.timeStamp
            }
        }
        return lastExit
    }
}
