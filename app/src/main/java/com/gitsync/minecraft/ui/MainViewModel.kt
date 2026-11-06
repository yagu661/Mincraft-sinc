package com.gitsync.minecraft.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gitsync.minecraft.GitSyncApplication
import com.gitsync.minecraft.core.database.CommitEntity
import com.gitsync.minecraft.core.model.Commit
import com.gitsync.minecraft.core.model.StorageCapability
import com.gitsync.minecraft.core.model.SyncState
import com.gitsync.minecraft.core.model.SyncStatus
import com.gitsync.minecraft.core.storage.MinecraftStorageDetector
import com.gitsync.minecraft.core.storage.StorageAccessHelper
import com.gitsync.minecraft.feature.backup.BackupEngine
import com.gitsync.minecraft.feature.monitoring.MinecraftSessionMonitor
import com.gitsync.minecraft.feature.monitoring.MinecraftUsageStatsHelper
import com.gitsync.minecraft.feature.sync.SyncEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

enum class AppScreen {
    DASHBOARD,
    STORAGE_SETUP,
    PAIRING,
    HISTORY,
    CONFLICT,
    SETTINGS,
    TEST_MODE
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GitSyncApplication
    private val detector = MinecraftStorageDetector(app)
    private val backupEngine = BackupEngine(app, app.database, app.keystoreManager)
    val syncEngine = SyncEngine(app, app.database, app.keystoreManager, viewModelScope)
    val sessionMonitor = MinecraftSessionMonitor(app, viewModelScope)

    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _storageCapability = MutableStateFlow(detector.detectCapabilities())
    val storageCapability: StateFlow<StorageCapability> = _storageCapability.asStateFlow()

    private val _hasUsagePermission = MutableStateFlow(MinecraftUsageStatsHelper.hasUsageStatsPermission(app))
    val hasUsagePermission: StateFlow<Boolean> = _hasUsagePermission.asStateFlow()

    val syncState: StateFlow<SyncState> = syncEngine.syncState

    val commitHistory: StateFlow<List<CommitEntity>> = app.database.commitDao()
        .getCommitsForWorldFlow(storageCapability.value.detectedWorldNames.firstOrNull() ?: "DefaultWorld")
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        syncEngine.start()
        sessionMonitor.startMonitoring()
        refreshStorageCapabilities()
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun refreshStorageCapabilities() {
        _storageCapability.value = detector.detectCapabilities()
        _hasUsagePermission.value = MinecraftUsageStatsHelper.hasUsageStatsPermission(app)
    }

    fun saveAuthorizedSafUri(uri: Uri) {
        detector.saveAuthorizedUri(uri)
        refreshStorageCapabilities()
    }

    fun pairWithFriend(remoteId: String, remoteName: String, token: String) {
        app.keystoreManager.savePairedDevice(remoteId, remoteName, token)
        syncEngine.start()
    }

    fun unpair() {
        app.keystoreManager.clearPairing()
        syncEngine.start()
    }

    // Manual Recovery Controls (Section 10)
    fun pushNow() {
        viewModelScope.launch {
            val worldId = storageCapability.value.detectedWorldNames.firstOrNull() ?: "DefaultWorld"
            val snapshotResult = backupEngine.createSnapshotForWorld(worldId, "Manual PUSH by player")
            if (snapshotResult.isSuccess) {
                syncEngine.pushCommit(snapshotResult.getOrThrow())
            }
        }
    }

    fun pullNow() {
        viewModelScope.launch {
            syncEngine.triggerPendingPushSync()
        }
    }

    fun forceBackup() {
        viewModelScope.launch {
            val worldId = storageCapability.value.detectedWorldNames.firstOrNull() ?: "DefaultWorld"
            backupEngine.createSnapshotForWorld(worldId, "Emergency manual backup")
        }
    }

    fun restoreCommit(commit: Commit) {
        viewModelScope.launch {
            backupEngine.restoreSnapshot(commit)
        }
    }

    fun resolveConflict(chosenCommit: Commit) {
        viewModelScope.launch {
            app.database.commitDao().updateSyncStatus(chosenCommit.commitId, SyncStatus.SYNCED.name)
            // Restore chosen branch
            backupEngine.restoreSnapshot(chosenCommit)
            _currentScreen.value = AppScreen.DASHBOARD
        }
    }

    /**
     * Section 15: Creates a synthetic Minecraft Bedrock test world for validation without Minecraft installed.
     */
    fun createSampleWorldForTest(): Result<String> {
        return runCatching {
            val stagingDir = StorageAccessHelper.getTempDirectory(app)
            val sampleFolder = File(stagingDir, "sample_bedrock_world")
            sampleFolder.mkdirs()

            // Minecraft Bedrock signature files:
            File(sampleFolder, "levelname.txt").writeText("GitSync Survival Test")
            File(sampleFolder, "world_icon.jpeg").writeBytes(ByteArray(1024))
            val dbDir = File(sampleFolder, "db").apply { mkdirs() }
            File(dbDir, "CURRENT").writeText("MANIFEST-000001\n")
            File(dbDir, "MANIFEST-000001").writeBytes(ByteArray(2048) { 0x42 })
            File(dbDir, "000002.ldb").writeBytes(ByteArray(10240) { 0x1A })

            // Create snapshot archive
            val archiver = com.gitsync.minecraft.core.storage.WorldArchiver(app)
            val snapshot = archiver.archiveLocalWorld(sampleFolder, "sample_bedrock_world", "GitSync Survival Test")
            val commit = backupEngine.createSnapshotForWorld("sample_bedrock_world", "Genesis commit for test mode")

            refreshStorageCapabilities()
            "Sample test world created successfully with SHA-256: ${snapshot.getOrNull()?.sha256?.take(8)}"
        }
    }

    override fun onCleared() {
        super.onCleared()
        syncEngine.stop()
        sessionMonitor.stopMonitoring()
    }
}
