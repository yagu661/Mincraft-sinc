package com.gitsync.minecraft.feature.sync.workers

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gitsync.minecraft.GitSyncApplication
import com.gitsync.minecraft.feature.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit

class QueuedSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "QueuedSyncWorker"
        const val PERIODIC_WORK_NAME = "minecraft_queued_sync_worker"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<QueuedSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "QueuedSyncWorker checking pending sync queue...")

        val app = applicationContext as GitSyncApplication
        val pendingQueue = app.database.syncQueueDao().getPendingQueue()
        if (pendingQueue.isEmpty()) {
            return Result.success()
        }

        val workerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val syncEngine = SyncEngine(app, app.database, app.keystoreManager, workerScope)
        syncEngine.start()

        syncEngine.triggerPendingPushSync()
        syncEngine.stop()

        return Result.success()
    }
}
