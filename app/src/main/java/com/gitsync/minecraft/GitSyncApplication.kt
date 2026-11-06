package com.gitsync.minecraft

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.gitsync.minecraft.core.database.AppDatabase
import com.gitsync.minecraft.core.security.KeystoreManager
import com.gitsync.minecraft.feature.sync.NotificationHelper

class GitSyncApplication : Application(), Configuration.Provider {

    companion object {
        const val TAG = "MinecraftGitSync"
        lateinit var instance: GitSyncApplication
            private set
    }

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    val keystoreManager: KeystoreManager by lazy {
        KeystoreManager(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "Initializing Minecraft GitSync Application")

        // Create notification channels for background transfer progress and alerts
        NotificationHelper.createNotificationChannels(this)

        // Ensure device identity is provisioned in Keystore on first run
        keystoreManager.ensureDeviceIdentity()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .build()
}
