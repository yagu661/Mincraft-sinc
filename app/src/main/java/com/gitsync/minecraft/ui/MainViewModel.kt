package com.gitsync.minecraft.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gitsync.minecraft.GitSyncApplication
import com.gitsync.minecraft.core.database.CommitEntity
import com.gitsync.minecraft.core.model.*
import com.gitsync.minecraft.core.storage.*
import com.gitsync.minecraft.feature.backup.BackupEngine
import com.gitsync.minecraft.feature.group.DeviceSwitchingCheckResult
import com.gitsync.minecraft.feature.group.SyncGroupManager
import com.gitsync.minecraft.feature.group.WorldEditingLeaseManager
import com.gitsync.minecraft.feature.monitoring.MinecraftSessionMonitor
import com.gitsync.minecraft.feature.monitoring.MinecraftUsageStatsHelper
import com.gitsync.minecraft.feature.sync.SyncEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class AppScreen {
    DASHBOARD,
    STORAGE_SETUP,
    STORAGE_DIAGNOSTIC,
    SYNC_GROUP,
    PAIRING,
    HISTORY,
    CONFLICT,
    SETTINGS,
    TEST_MODE
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GitSyncApplication
    private val detector = MinecraftStorageDetector(app)
    private val diagnosticHelper = StorageDiagnosticHelper(app)
    private val worldDiscovery = WorldDiscovery(app)
    private val backupEngine = BackupEngine(app, app.database, app.keystoreManager)
    val snapshotManager = WorldSnapshotManager(app, app.database, app.keystoreManager)
    val safeInstaller = SafeWorldInstaller(app)

    val leaseManager = WorldEditingLeaseManager(app, app.keystoreManager)
    val syncGroupManager = SyncGroupManager(app, app.database, app.keystoreManager, leaseManager)

    val syncEngine = SyncEngine(app, app.database, app.keystoreManager, viewModelScope)
    val sessionMonitor = MinecraftSessionMonitor(app, viewModelScope)

    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _storageCapability = MutableStateFlow(detector.detectCapabilities())
    val storageCapability: StateFlow<StorageCapability> = _storageCapability.asStateFlow()

    private val _storageDiagnostic = MutableStateFlow(diagnosticHelper.runFullDiagnostic())
    val storageDiagnostic: StateFlow<StorageDiagnostic> = _storageDiagnostic.asStateFlow()

    private val _discoveredWorlds = MutableStateFlow<List<DiscoveredWorld>>(emptyList())
    val discoveredWorlds: StateFlow<List<DiscoveredWorld>> = _discoveredWorlds.asStateFlow()

    private val _selectedWorldId = MutableStateFlow<String?>("SurvivalWorld")
    val selectedWorldId: StateFlow<String?> = _selectedWorldId.asStateFlow()

    private val _deviceSwitchingResult = MutableStateFlow<DeviceSwitchingCheckResult>(DeviceSwitchingCheckResult.READY_TO_PLAY)
    val deviceSwitchingResult: StateFlow<DeviceSwitchingCheckResult> = _deviceSwitchingResult.asStateFlow()

    private val _hasUsagePermission = MutableStateFlow(MinecraftUsageStatsHelper.hasUsageStatsPermission(app))
    val hasUsagePermission: StateFlow<Boolean> = _hasUsagePermission.asStateFlow()

    val syncState: StateFlow<SyncState> = syncEngine.syncState
    val syncGroup: StateFlow<SyncGroup> = syncGroupManager.syncGroup

    val commitHistory: StateFlow<List<CommitEntity>> = app.database.commitDao()
        .getCommitsForWorldFlow(selectedWorldId.value ?: "SurvivalWorld")
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        syncEngine.start()
        sessionMonitor.startMonitoring()
        refreshStorageCapabilities()
        scanWorlds()
        verifyDeviceReadiness()
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun refreshStorageCapabilities() {
        _storageCapability.value = detector.detectCapabilities()
        _storageDiagnostic.value = diagnosticHelper.runFullDiagnostic()
        _hasUsagePermission.value = MinecraftUsageStatsHelper.hasUsageStatsPermission(app)
        scanWorlds()
    }

    fun scanWorlds() {
        viewModelScope.launch {
            val primaryDir = File(StorageDiagnosticHelper.PRIMARY_MINECRAFT_WORLDS_PATH)
            val legacyDir = File(StorageDiagnosticHelper.LEGACY_MINECRAFT_WORLDS_PATH)
            val targetDir = if (primaryDir.exists()) primaryDir else legacyDir

            val worlds = worldDiscovery.scanLocalMinecraftWorlds(targetDir)
            _discoveredWorlds.value = worlds
            if (worlds.isNotEmpty() && _selectedWorldId.value == null) {
                _selectedWorldId.value = worlds.first().worldId
            }
        }
    }

    fun selectSharedWorld(world: DiscoveredWorld) {
        _selectedWorldId.value = world.worldId
        verifyDeviceReadiness()
    }

    fun verifyDeviceReadiness() {
        viewModelScope.launch {
            val worldId = _selectedWorldId.value ?: "SurvivalWorld"
            _deviceSwitchingResult.value = syncGroupManager.verifyDeviceReadinessBeforePlay(worldId)
        }
    }

    fun acquireWorldLease() {
        val worldId = _selectedWorldId.value ?: "SurvivalWorld"
        val headCommit = commitHistory.value.firstOrNull()?.commitId ?: "c_initial"
        leaseManager.acquireLease(worldId, headCommit)
        verifyDeviceReadiness()
    }

    fun releaseWorldLease() {
        leaseManager.releaseLease()
        verifyDeviceReadiness()
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

    // Manual Recovery Controls (Section 10 & Section H)
    fun runSafeTestBackup() {
        viewModelScope.launch {
            val stagingDir = StorageAccessHelper.getTempDirectory(app)
            val testWorldFolder = File(stagingDir, "safe_test_world")
            testWorldFolder.mkdirs()
            File(testWorldFolder, "levelname.txt").writeText("Test World")
            val db = File(testWorldFolder, "db").apply { mkdirs() }
            File(testWorldFolder, "level.dat").writeBytes(ByteArray(512))
            File(db, "CURRENT").writeText("MANIFEST-000001\n")
            File(db, "MANIFEST-000001").writeBytes(ByteArray(1024))

            snapshotManager.createValidatedSnapshot(testWorldFolder, "safe_test_world", "Diagnostic Test World", "Non-destructive diagnostic backup")
            refreshStorageCapabilities()
        }
    }

    fun pushNow() {
        viewModelScope.launch {
            val worldId = _selectedWorldId.value ?: "SurvivalWorld"
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
            val worldId = _selectedWorldId.value ?: "SurvivalWorld"
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
            backupEngine.restoreSnapshot(chosenCommit)
            _currentScreen.value = AppScreen.DASHBOARD
        }
    }

    fun createSampleWorldForTest(): Result<String> {
        return runCatching {
            val stagingDir = StorageAccessHelper.getTempDirectory(app)
            val sampleFolder = File(stagingDir, "sample_bedrock_world")
            sampleFolder.mkdirs()

            File(sampleFolder, "levelname.txt").writeText("GitSync Survival Test")
            File(sampleFolder, "world_icon.jpeg").writeBytes(ByteArray(1024))
            val dbDir = File(sampleFolder, "db").apply { mkdirs() }
            File(dbDir, "CURRENT").writeText("MANIFEST-000001\n")
            File(dbDir, "MANIFEST-000001").writeBytes(ByteArray(2048) { 0x42 })
            File(dbDir, "000002.ldb").writeBytes(ByteArray(10240) { 0x1A })

            val archiver = com.gitsync.minecraft.core.storage.WorldArchiver(app)
            val snapshot = archiver.archiveLocalWorld(sampleFolder, "sample_bedrock_world", "GitSync Survival Test")
            kotlinx.coroutines.runBlocking {
                backupEngine.createSnapshotForWorld("sample_bedrock_world", "Genesis commit for test mode")
            }

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
