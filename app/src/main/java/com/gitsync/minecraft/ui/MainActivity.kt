package com.gitsync.minecraft.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.gitsync.minecraft.ui.screens.*
import com.gitsync.minecraft.ui.theme.MinecraftGitSyncTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MinecraftGitSyncTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStorageCapabilities()
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val storageCapability by viewModel.storageCapability.collectAsState()
    val hasUsagePermission by viewModel.hasUsagePermission.collectAsState()
    val sessionState by viewModel.sessionMonitor.sessionState.collectAsState()
    val commits by viewModel.commitHistory.collectAsState()
    val latestCommit = commits.firstOrNull()?.toModel()

    val discoveredWorlds by viewModel.discoveredWorlds.collectAsState()
    val sharedWorlds by viewModel.sharedWorlds.collectAsState()
    val activeSharedWorld by viewModel.activeSharedWorld.collectAsState()

    when (currentScreen) {
        AppScreen.DASHBOARD -> {
            DashboardScreen(
                syncState = syncState,
                storageCapability = storageCapability,
                hasUsagePermission = hasUsagePermission,
                sessionState = sessionState,
                latestCommit = latestCommit,
                onNavigate = { viewModel.navigateTo(it) },
                onPushNow = { viewModel.pushNow() },
                onPullNow = { viewModel.pullNow() },
                onForceBackup = { viewModel.forceBackup() },
                onRetrySync = { viewModel.pullNow() }
            )
        }
        AppScreen.STORAGE_DIAGNOSTIC -> {
            val diagnostic by viewModel.storageDiagnostic.collectAsState()
            val worlds by viewModel.discoveredWorlds.collectAsState()
            val selectedWorldId by viewModel.selectedWorldId.collectAsState()
            StorageDiagnosticScreen(
                diagnostic = diagnostic,
                discoveredWorlds = worlds,
                selectedWorldId = selectedWorldId,
                onSelectWorld = { viewModel.selectSharedWorld(it) },
                onRunSafeTestBackup = { viewModel.runSafeTestBackup() },
                onRerunDiagnostic = { viewModel.refreshStorageCapabilities() },
                onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) }
            )
        }
        AppScreen.SYNC_GROUP -> {
            val syncGroup by viewModel.syncGroup.collectAsState()
            val deviceSwitchingResult by viewModel.deviceSwitchingResult.collectAsState()
            SyncGroupScreen(
                syncGroup = syncGroup,
                deviceSwitchingResult = deviceSwitchingResult,
                onAcquireLease = { viewModel.acquireWorldLease() },
                onReleaseLease = { viewModel.releaseWorldLease() },
                onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) }
            )
        }
        AppScreen.STORAGE_SETUP -> {
            StorageSetupScreen(
                storageCapability = storageCapability,
                hasUsagePermission = hasUsagePermission,
                onSaveSafUri = { viewModel.saveAuthorizedSafUri(it) },
                onRefresh = { viewModel.refreshStorageCapabilities() },
                onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) }
            )
        }
        AppScreen.PAIRING -> {
            PairingScreen(
                syncState = syncState,
                onPair = { id, name, token -> viewModel.pairWithFriend(id, name, token) },
                onUnpair = { viewModel.unpair() },
                onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) }
            )
        }
        AppScreen.WORLD_PAIRING -> {
            WorldPairingScreen(
                discoveredWorlds = discoveredWorlds,
                sharedWorlds = sharedWorlds,
                activeSharedWorld = activeSharedWorld,
                storageCapability = storageCapability,
                onCreateSharedWorld = { viewModel.createSharedWorldFromLocal(it) },
                onLinkExistingWorld = { id, world -> viewModel.linkExistingLocalWorldToShared(id, world) },
                onDownloadNewWorld = { id, name -> viewModel.createNewWorldFromSharedBackup(id, name) },
                onUnpairSharedWorld = { viewModel.unpairSharedWorld(it) },
                onChangeActiveWorld = { viewModel.changeActiveSharedWorld(it) },
                onUpdateMultiplayerMode = { viewModel.updateMultiplayerMode(it) },
                onSelectSafFolder = { viewModel.navigateTo(AppScreen.STORAGE_SETUP) },
                onRefreshWorlds = { viewModel.scanWorlds() },
                onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) }
            )
        }
        AppScreen.HISTORY -> {
            HistoryScreen(
                commits = commits,
                onRestoreCommit = { viewModel.restoreCommit(it) },
                onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) }
            )
        }
        AppScreen.CONFLICT -> {
            ConflictResolutionScreen(
                commitA = syncState.conflictingCommitA,
                commitB = syncState.conflictingCommitB,
                onChooseCommit = { viewModel.resolveConflict(it) },
                onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) }
            )
        }
        AppScreen.SETTINGS -> {
            SettingsScreen(
                onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) }
            )
        }
        AppScreen.TEST_MODE -> {
            TestModeScreen(
                onGenerateSampleWorld = { viewModel.createSampleWorldForTest() },
                onPushNow = { viewModel.pushNow() },
                onPullNow = { viewModel.pullNow() },
                onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) }
            )
        }
    }
}
