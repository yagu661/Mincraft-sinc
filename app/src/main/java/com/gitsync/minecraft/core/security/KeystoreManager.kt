package com.gitsync.minecraft.core.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.gitsync.minecraft.core.model.DeviceIdentity
import java.security.SecureRandom
import java.util.UUID

class KeystoreManager(private val context: Context) {

    companion object {
        private const val PREFS_FILE = "keystore_secrets"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DISPLAY_NAME = "device_display_name"
        private const val KEY_PAIRING_SECRET = "device_pairing_secret"
        private const val KEY_CREATED_EPOCH = "created_epoch"
        private const val KEY_PAIRED_DEVICE_ID = "paired_device_id"
        private const val KEY_PAIRED_DEVICE_NAME = "paired_device_name"
        private const val KEY_PAIRED_TOKEN = "paired_token"
        private const val KEY_SIGNALING_URL = "signaling_url"
        private const val DEFAULT_SIGNALING_URL = "wss://spring-paper-f60a.xmenlittlegamer.workers.dev/ws"
        private const val KEY_ROOM_ID = "room_id"
        private const val DEFAULT_ROOM_ID = "friendgroup123"
        private const val KEY_STORAGE_URL = "storage_url"
        private const val DEFAULT_STORAGE_URL = "https://spring-paper-f60a.xmenlittlegamer.workers.dev"
        private const val KEY_ACTIVE_SHARED_WORLD_ID = "active_shared_world_id"

        // Google OAuth & Shared Drive Folder preferences
        private const val KEY_GOOGLE_ACCOUNT_EMAIL = "google_account_email"
        private const val KEY_GOOGLE_ACCESS_TOKEN = "google_access_token"
        private const val KEY_GOOGLE_REFRESH_TOKEN = "google_refresh_token"
        private const val KEY_GOOGLE_TOKEN_EXPIRES_EPOCH = "google_token_expires_epoch"
        private const val KEY_DRIVE_SHARED_FOLDER_ID = "drive_shared_folder_id"
        private const val KEY_DRIVE_SHARED_FOLDER_NAME = "drive_shared_folder_name"
        private const val KEY_FRIEND_GOOGLE_EMAIL = "friend_google_email"
        private const val KEY_BACKUP_RETENTION_COUNT = "backup_retention_count"
        private const val DEFAULT_RETENTION_COUNT = 5
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }

    private val securePrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("KeystoreManager", "EncryptedSharedPreferences init failed, fallback to sandboxed mode", e)
            context.getSharedPreferences("${PREFS_FILE}_fallback", Context.MODE_PRIVATE)
        }
    }

    fun ensureDeviceIdentity(): DeviceIdentity {
        val existingId = securePrefs.getString(KEY_DEVICE_ID, null)
        if (existingId != null) {
            return DeviceIdentity(
                deviceId = existingId,
                displayName = securePrefs.getString(KEY_DISPLAY_NAME, "Player Device") ?: "Player Device",
                pairingSecret = securePrefs.getString(KEY_PAIRING_SECRET, "") ?: "",
                createdAtEpoch = securePrefs.getLong(KEY_CREATED_EPOCH, System.currentTimeMillis())
            )
        }

        val newId = UUID.randomUUID().toString()
        val defaultName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        val pairingSecret = PairingTokenGenerator.generateSecureToken()
        val created = System.currentTimeMillis()

        securePrefs.edit()
            .putString(KEY_DEVICE_ID, newId)
            .putString(KEY_DISPLAY_NAME, defaultName)
            .putString(KEY_PAIRING_SECRET, pairingSecret)
            .putLong(KEY_CREATED_EPOCH, created)
            .apply()

        return DeviceIdentity(newId, defaultName, pairingSecret, created)
    }

    fun getDeviceIdentity(): DeviceIdentity = ensureDeviceIdentity()

    fun updateDisplayName(name: String) {
        securePrefs.edit().putString(KEY_DISPLAY_NAME, name.trim()).apply()
    }

    fun savePairedDevice(remoteId: String, remoteName: String, sharedToken: String) {
        securePrefs.edit()
            .putString(KEY_PAIRED_DEVICE_ID, remoteId)
            .putString(KEY_PAIRED_DEVICE_NAME, remoteName)
            .putString(KEY_PAIRED_TOKEN, sharedToken)
            .apply()
    }

    fun getPairedDeviceId(): String? = securePrefs.getString(KEY_PAIRED_DEVICE_ID, null)
    fun getPairedDeviceName(): String? = securePrefs.getString(KEY_PAIRED_DEVICE_NAME, null)
    fun getPairedToken(): String? = securePrefs.getString(KEY_PAIRED_TOKEN, null)

    fun clearPairing() {
        securePrefs.edit()
            .remove(KEY_PAIRED_DEVICE_ID)
            .remove(KEY_PAIRED_DEVICE_NAME)
            .remove(KEY_PAIRED_TOKEN)
            .apply()
    }

    fun getSignalingServerUrl(): String =
        securePrefs.getString(KEY_SIGNALING_URL, DEFAULT_SIGNALING_URL) ?: DEFAULT_SIGNALING_URL

    fun setSignalingServerUrl(url: String) {
        securePrefs.edit().putString(KEY_SIGNALING_URL, url.trim()).apply()
    }

    fun getRoomId(): String =
        securePrefs.getString(KEY_ROOM_ID, DEFAULT_ROOM_ID) ?: DEFAULT_ROOM_ID

    fun setRoomId(roomId: String) {
        securePrefs.edit().putString(KEY_ROOM_ID, roomId.trim()).apply()
    }

    fun getStorageUrl(): String =
        securePrefs.getString(KEY_STORAGE_URL, DEFAULT_STORAGE_URL) ?: DEFAULT_STORAGE_URL

    fun setStorageUrl(url: String) {
        securePrefs.edit().putString(KEY_STORAGE_URL, url.trim()).apply()
    }

    fun getActiveSharedWorldId(): String? =
        securePrefs.getString(KEY_ACTIVE_SHARED_WORLD_ID, null)

    fun setActiveSharedWorldId(sharedWorldId: String?) {
        if (sharedWorldId == null) {
            securePrefs.edit().remove(KEY_ACTIVE_SHARED_WORLD_ID).apply()
        } else {
            securePrefs.edit().putString(KEY_ACTIVE_SHARED_WORLD_ID, sharedWorldId.trim()).apply()
        }
    }

    // Google OAuth & Account Management
    fun getGoogleAccountEmail(): String? = securePrefs.getString(KEY_GOOGLE_ACCOUNT_EMAIL, null)

    fun setGoogleAccount(email: String?, accessToken: String?, refreshToken: String? = null, expiresInSec: Long = 3600) {
        val editor = securePrefs.edit()
        if (email == null) {
            editor.remove(KEY_GOOGLE_ACCOUNT_EMAIL)
                .remove(KEY_GOOGLE_ACCESS_TOKEN)
                .remove(KEY_GOOGLE_REFRESH_TOKEN)
                .remove(KEY_GOOGLE_TOKEN_EXPIRES_EPOCH)
        } else {
            editor.putString(KEY_GOOGLE_ACCOUNT_EMAIL, email)
            if (accessToken != null) editor.putString(KEY_GOOGLE_ACCESS_TOKEN, accessToken)
            if (refreshToken != null) editor.putString(KEY_GOOGLE_REFRESH_TOKEN, refreshToken)
            editor.putLong(KEY_GOOGLE_TOKEN_EXPIRES_EPOCH, System.currentTimeMillis() + (expiresInSec * 1000L))
        }
        editor.apply()
    }

    fun getGoogleAccessToken(): String? = securePrefs.getString(KEY_GOOGLE_ACCESS_TOKEN, null)
    fun getGoogleRefreshToken(): String? = securePrefs.getString(KEY_GOOGLE_REFRESH_TOKEN, null)
    fun isGoogleTokenExpired(): Boolean =
        System.currentTimeMillis() >= securePrefs.getLong(KEY_GOOGLE_TOKEN_EXPIRES_EPOCH, 0L)

    // Shared Google Drive Folder Mapping
    fun getDriveSharedFolderId(): String? = securePrefs.getString(KEY_DRIVE_SHARED_FOLDER_ID, null)
    fun getDriveSharedFolderName(): String = securePrefs.getString(KEY_DRIVE_SHARED_FOLDER_NAME, "Minecraft_Bedrock_Sync") ?: "Minecraft_Bedrock_Sync"

    fun setDriveSharedFolder(folderId: String?, folderName: String = "Minecraft_Bedrock_Sync") {
        if (folderId == null) {
            securePrefs.edit().remove(KEY_DRIVE_SHARED_FOLDER_ID).remove(KEY_DRIVE_SHARED_FOLDER_NAME).apply()
        } else {
            securePrefs.edit()
                .putString(KEY_DRIVE_SHARED_FOLDER_ID, folderId.trim())
                .putString(KEY_DRIVE_SHARED_FOLDER_NAME, folderName.trim())
                .apply()
        }
    }

    // Friend's Google Account Email for Folder Sharing
    fun getFriendGoogleEmail(): String? = securePrefs.getString(KEY_FRIEND_GOOGLE_EMAIL, null)
    fun setFriendGoogleEmail(email: String?) {
        if (email == null) {
            securePrefs.edit().remove(KEY_FRIEND_GOOGLE_EMAIL).apply()
        } else {
            securePrefs.edit().putString(KEY_FRIEND_GOOGLE_EMAIL, email.trim().lowercase()).apply()
        }
    }

    // Configurable Backup Retention (Default 5)
    fun getBackupRetentionCount(): Int = securePrefs.getInt(KEY_BACKUP_RETENTION_COUNT, DEFAULT_RETENTION_COUNT)
    fun setBackupRetentionCount(count: Int) {
        securePrefs.edit().putInt(KEY_BACKUP_RETENTION_COUNT, count.coerceIn(2, 30)).apply()
    }

    // First Launch Onboarding Completed
    fun isOnboardingCompleted(): Boolean = securePrefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    fun setOnboardingCompleted(completed: Boolean) {
        securePrefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }
}
