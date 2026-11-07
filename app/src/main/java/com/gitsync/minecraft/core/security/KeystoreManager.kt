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
}
