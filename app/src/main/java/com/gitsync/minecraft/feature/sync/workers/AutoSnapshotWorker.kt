package com.gitsync.minecraft.feature.sync.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gitsync.minecraft.GitSyncApplication
import com.gitsync.minecraft.core.model.SyncStatus
import com.gitsync.minecraft.core.storage.MinecraftStorageDetector
import com.gitsync.minecraft.feature.backup.BackupEngine

class AutoSnapshotWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "AutoSnapshotWorker"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "AutoSnapshotWorker started execution.")

        val app = applicationContext as GitSyncApplication
        val backupEngine = BackupEngine(app, app.database, app.keystoreManager)
        val detector = MinecraftStorageDetector(app)
        val capability = detector.detectCapabilities()

        val worldId = capability.detectedWorldNames.firstOrNull() ?: "DefaultWorld"
        Log.i(TAG, "Creating automatic snapshot for detected world: $worldId")

        val result = backupEngine.createSnapshotForWorld(worldId)

        return if (result.isSuccess) {
            val commit = result.getOrThrow()
            Log.i(TAG, "Automatic snapshot successfully generated: ${commit.commitId}")

            // If a paired friend or connected Google Drive SAF folder exists, enqueue DirectPushWorker automatically
            if (app.keystoreManager.getPairedDeviceId() != null || app.keystoreManager.isSafDriveFolderConnected()) {
                val pushRequest = OneTimeWorkRequestBuilder<DirectPushWorker>().build()
                WorkManager.getInstance(applicationContext).enqueue(pushRequest)
            }
            Result.success()
        } else {
            val err = result.exceptionOrNull()
            Log.e(TAG, "AutoSnapshot failed: ${err?.message}")
            if (err is IllegalStateException && err.message?.contains("still being modified") == true) {
                // Minecraft is still flushing, retry with exponential backoff
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
