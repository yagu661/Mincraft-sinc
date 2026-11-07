package com.gitsync.minecraft.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gitsync.minecraft.GitSyncApplication
import com.gitsync.minecraft.core.database.CommitEntity
import com.gitsync.minecraft.core.database.SharedWorldEntity
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
    WORLD_PAIRING,
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

    val sharedWorlds: StateFlow<List<SharedWorldEntity>> = app.database.sharedWorldDao()
        .getAllSharedWorldsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeSharedWorld: StateFlow<SharedWorldMapping?> = MutableStateFlow<SharedWorldMapping?>(null)

    init {
        syncEngine.start()
        sessionMonitor.startMonitoring()
        refreshStorageCapabilities()
        scanWorlds()
        loadActiveSharedWorld()
        verifyDeviceReadiness()
    }

    private fun loadActiveSharedWorld() {
        viewModelScope.launch {
            val activeId = app.keystoreManager.getActiveSharedWorldId()
            if (activeId != null) {
                val entity = app.database.sharedWorldDao().getMappingBySharedId(activeId)
                if (entity != null) {
                    _selectedWorldId.value = entity.localFolderId
                    (activeSharedWorld as MutableStateFlow).value = entity.toModel()
                }
            }
        }
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

            val worlds = mutableListOf<DiscoveredWorld>()
            if (targetDir.exists()) {
                worlds.addAll(worldDiscovery.scanLocalMinecraftWorlds(targetDir))
            }

            // Also check SAF tree if authorized
            val cap = detector.detectCapabilities()
            if (cap.authorizedTreeUri != null) {
                val safWorlds = worldDiscovery.scanSafMinecraftWorlds(cap.authorizedTreeUri)
                // Filter out any duplicates
                safWorlds.forEach { sw ->
                    if (worlds.none { it.folderName == sw.folderName }) {
                        worlds.add(sw)
                    }
                }
            }

            _discoveredWorlds.value = worlds
            val currentActive = app.keystoreManager.getActiveSharedWorldId()
            if (currentActive == null && worlds.isNotEmpty() && _selectedWorldId.value == null) {
                // Do not auto-pair; user must explicitly select world in World Pairing screen
            }
        }
    }

    fun selectSharedWorld(world: DiscoveredWorld) {
        _selectedWorldId.value = world.folderName
        verifyDeviceReadiness()
    }

    /**
     * Mode 1 Host: Generate a persistent unique shared World ID, link the selected local world,
     * and generate a secure pairing code for friend.
     */
    fun createSharedWorldFromLocal(world: DiscoveredWorld): SharedWorldMapping {
        val uniqueSharedId = "sw_${java.util.UUID.randomUUID().toString().replace("-", "").take(12)}"
        val token = com.gitsync.minecraft.core.security.PairingTokenGenerator.generateSecureToken()
        val pairingCode = "MWPAIR:$uniqueSharedId:$token"
        val mapping = SharedWorldMapping(
            sharedWorldId = uniqueSharedId,
            localFolderId = world.folderName,
            displayName = world.displayName,
            localAbsolutePath = world.absolutePath,
            pairingCode = pairingCode,
            isHostDevice = true,
            activeMultiplayerMode = MultiplayerSyncMode.MODE_1_HOST_READY
        )

        viewModelScope.launch {
            app.database.sharedWorldDao().insertOrUpdateMapping(SharedWorldEntity.fromModel(mapping))
            app.keystoreManager.setActiveSharedWorldId(uniqueSharedId)
            _selectedWorldId.value = world.folderName
            (activeSharedWorld as MutableStateFlow).value = mapping
        }
        return mapping
    }

    /**
     * Mode 2 / Friend Option A: Link an existing local Minecraft world to the shared World ID.
     */
    fun linkExistingLocalWorldToShared(
        sharedWorldId: String,
        localWorld: DiscoveredWorld,
        friendDeviceId: String? = null,
        friendDeviceName: String? = null
    ) {
        val mapping = SharedWorldMapping(
            sharedWorldId = sharedWorldId,
            localFolderId = localWorld.folderName,
            displayName = localWorld.displayName,
            localAbsolutePath = localWorld.absolutePath,
            pairingCode = "LINKED",
            pairedDeviceId = friendDeviceId,
            pairedDeviceName = friendDeviceName,
            isHostDevice = false,
            activeMultiplayerMode = MultiplayerSyncMode.MODE_2_HOST_OFFLINE
        )

        viewModelScope.launch {
            app.database.sharedWorldDao().insertOrUpdateMapping(SharedWorldEntity.fromModel(mapping))
            app.keystoreManager.setActiveSharedWorldId(sharedWorldId)
            _selectedWorldId.value = localWorld.folderName
            (activeSharedWorld as MutableStateFlow).value = mapping
            verifyDeviceReadiness()
        }
    }

    /**
     * Mode 2 / Friend Option B: Download and create a new world from the shared backup.
     */
    fun createNewWorldFromSharedBackup(
        sharedWorldId: String,
        targetDisplayName: String
    ) {
        viewModelScope.launch {
            val safeFolderName = targetDisplayName.replace("[^a-zA-Z0-9_-]".toRegex(), "_") + "_" + System.currentTimeMillis().toString().takeLast(4)
            val worldsDir = if (MinecraftStorageDetector.LEGACY_WORLDS_DIR.exists() && MinecraftStorageDetector.LEGACY_WORLDS_DIR.canWrite()) {
                MinecraftStorageDetector.LEGACY_WORLDS_DIR
            } else {
                MinecraftStorageDetector.SCOPED_WORLDS_DIR
            }
            val targetFolder = File(worldsDir, safeFolderName)
            targetFolder.mkdirs()

            val mapping = SharedWorldMapping(
                sharedWorldId = sharedWorldId,
                localFolderId = safeFolderName,
                displayName = targetDisplayName,
                localAbsolutePath = targetFolder.absolutePath,
                pairingCode = "DOWNLOAD_PENDING",
                isHostDevice = false,
                activeMultiplayerMode = MultiplayerSyncMode.MODE_2_HOST_OFFLINE
            )
            app.database.sharedWorldDao().insertOrUpdateMapping(SharedWorldEntity.fromModel(mapping))
            app.keystoreManager.setActiveSharedWorldId(sharedWorldId)
            _selectedWorldId.value = safeFolderName
            (activeSharedWorld as MutableStateFlow).value = mapping

            // Trigger immediate pull from Cloudflare R2
            syncEngine.pullLatest(sharedWorldId)
            scanWorlds()
        }
    }

    fun unpairSharedWorld(sharedWorldId: String) {
        viewModelScope.launch {
            app.database.sharedWorldDao().deleteMapping(sharedWorldId)
            if (app.keystoreManager.getActiveSharedWorldId() == sharedWorldId) {
                app.keystoreManager.setActiveSharedWorldId(null)
                _selectedWorldId.value = null
                (activeSharedWorld as MutableStateFlow).value = null
            }
        }
    }

    fun changeActiveSharedWorld(mapping: SharedWorldMapping) {
        viewModelScope.launch {
            app.keystoreManager.setActiveSharedWorldId(mapping.sharedWorldId)
            _selectedWorldId.value = mapping.localFolderId
            (activeSharedWorld as MutableStateFlow).value = mapping
            verifyDeviceReadiness()
        }
    }

    fun updateMultiplayerMode(mode: MultiplayerSyncMode) {
        val current = activeSharedWorld.value ?: return
        viewModelScope.launch {
            val updated = current.copy(activeMultiplayerMode = mode)
            app.database.sharedWorldDao().updateMultiplayerMode(current.sharedWorldId, mode.name)
            (activeSharedWorld as MutableStateFlow).value = updated
        }
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
            val worldId = _selectedWorldId.value ?: "SurvivalWorld"
            syncEngine.pullLatest(worldId)
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
