package com.gitsync.minecraft.feature.monitoring

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.gitsync.minecraft.feature.sync.workers.AutoSnapshotWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MinecraftSessionMonitor(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "MinecraftSessionMonitor"
        const val PREFS_MONITOR = "monitor_preferences"
        const val KEY_GRACE_PERIOD_SEC = "grace_period_sec"
        const val DEFAULT_GRACE_PERIOD_SEC = 8
        const val WORK_NAME_AUTO_SNAPSHOT = "minecraft_auto_snapshot"
    }

    private var monitoringJob: Job? = null
    private var isCurrentlyInSession = false
    private var sessionStartTime = 0L

    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    enum class SessionState {
        IDLE,
        MINECRAFT_ACTIVE,
        GRACE_PERIOD_WAITING,
        TRIGGERING_SNAPSHOT
    }

    fun startMonitoring() {
        if (monitoringJob != null && monitoringJob?.isActive == true) return

        monitoringJob = scope.launch(Dispatchers.Default) {
            Log.i(TAG, "Starting Minecraft session monitoring poll loop")

            while (isActive) {
                val hasPermission = MinecraftUsageStatsHelper.hasUsageStatsPermission(context)
                if (!hasPermission) {
                    _sessionState.value = SessionState.IDLE
                    delay(5000)
                    continue
                }

                val inForeground = MinecraftUsageStatsHelper.isMinecraftInForeground(context)

                if (inForeground) {
                    if (!isCurrentlyInSession) {
                        isCurrentlyInSession = true
                        sessionStartTime = System.currentTimeMillis()
                        Log.i(TAG, "Minecraft session started at $sessionStartTime")
                    }
                    _sessionState.value = SessionState.MINECRAFT_ACTIVE
                } else {
                    if (isCurrentlyInSession) {
                        // Minecraft was active, but now left foreground
                        Log.i(TAG, "Minecraft left foreground. Entering grace period wait...")
                        _sessionState.value = SessionState.GRACE_PERIOD_WAITING

                        val graceSec = getGracePeriodSeconds()
                        delay(graceSec * 1000L)

                        // Recheck if Minecraft came back during grace period
                        val returned = MinecraftUsageStatsHelper.isMinecraftInForeground(context)
                        if (!returned) {
                            Log.i(TAG, "Grace period completed. Minecraft confirmed closed. Scheduling snapshot.")
                            isCurrentlyInSession = false
                            _sessionState.value = SessionState.TRIGGERING_SNAPSHOT
                            scheduleAutoSnapshot()
                        } else {
                            Log.i(TAG, "Player returned to Minecraft before grace period expired.")
                            _sessionState.value = SessionState.MINECRAFT_ACTIVE
                        }
                    } else {
                        _sessionState.value = SessionState.IDLE
                    }
                }

                delay(3000) // Efficient polling interval
            }
        }
    }

    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        _sessionState.value = SessionState.IDLE
    }

    fun scheduleAutoSnapshot() {
        val request = OneTimeWorkRequestBuilder<AutoSnapshotWorker>()
            .setInitialDelay(2, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME_AUTO_SNAPSHOT,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun getGracePeriodSeconds(): Int {
        return context.getSharedPreferences(PREFS_MONITOR, Context.MODE_PRIVATE)
            .getInt(KEY_GRACE_PERIOD_SEC, DEFAULT_GRACE_PERIOD_SEC)
    }

    fun setGracePeriodSeconds(seconds: Int) {
        context.getSharedPreferences(PREFS_MONITOR, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_GRACE_PERIOD_SEC, seconds.coerceIn(3, 60))
            .apply()
    }
}
