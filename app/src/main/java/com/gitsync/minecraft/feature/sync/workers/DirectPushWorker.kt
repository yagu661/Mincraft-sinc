package com.gitsync.minecraft.feature.sync.workers

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.gitsync.minecraft.GitSyncApplication
import com.gitsync.minecraft.feature.sync.NotificationHelper
import com.gitsync.minecraft.feature.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DirectPushWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "DirectPushWorker"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "DirectPushWorker executing...")

        val app = applicationContext as GitSyncApplication
        val pendingPushes = app.database.commitDao().getPendingPushes()
        if (pendingPushes.isEmpty()) {
            Log.i(TAG, "No pending pushes in queue.")
            return Result.success()
        }

        val commitEntity = pendingPushes.first()
        val commit = commitEntity.toModel()

        // Set foreground service notification for long-running transfer
        setForeground(createForegroundInfo(commit.worldId, 0))

        val workerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val syncEngine = SyncEngine(app, app.database, app.keystoreManager, workerScope)
        syncEngine.start()

        val pushResult = syncEngine.pushCommit(commit)
        syncEngine.stop()

        return if (pushResult.isSuccess) {
            Log.i(TAG, "DirectPushWorker successfully transferred commit ${commit.commitId}")
            Result.success()
        } else {
            Log.e(TAG, "DirectPushWorker failed: ${pushResult.exceptionOrNull()?.message}")
            Result.retry()
        }
    }

    private fun createForegroundInfo(worldName: String, progress: Int): ForegroundInfo {
        val notification = NotificationHelper.buildTransferNotification(
            context = applicationContext,
            worldName = worldName,
            progressPercent = progress,
            isUploading = true
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationHelper.NOTIF_ID_TRANSFER,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NotificationHelper.NOTIF_ID_TRANSFER, notification)
        }
    }
}
