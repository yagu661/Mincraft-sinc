package com.gitsync.minecraft.core.networking

import android.util.Log
import com.gitsync.minecraft.core.model.Commit
import com.gitsync.minecraft.core.storage.Sha256Checksum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class CloudflareStorageClient(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "CloudflareStorage"
        private val MEDIA_TYPE_OCTET = "application/octet-stream".toMediaType()
    }

    suspend fun uploadSnapshot(
        worldId: String,
        commit: Commit,
        snapshotFile: File,
        onProgress: (bytesSent: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!snapshotFile.exists() || snapshotFile.length() == 0L) {
                error("Snapshot file does not exist or is empty: ${snapshotFile.absolutePath}")
            }

            val totalBytes = snapshotFile.length()
            val cleanBaseUrl = baseUrl.trimEnd('/')
            val uploadUrl = "$cleanBaseUrl/api/snapshots/$worldId/${commit.commitId}"

            Log.i(TAG, "Uploading world snapshot to Cloudflare R2: $uploadUrl ($totalBytes bytes)")

            val countingBody = object : RequestBody() {
                override fun contentType(): MediaType = MEDIA_TYPE_OCTET
                override fun contentLength(): Long = totalBytes

                override fun writeTo(sink: BufferedSink) {
                    val buffer = ByteArray(64 * 1024) // 64 KB buffer
                    var uploaded = 0L
                    snapshotFile.inputStream().use { input ->
                        var read = input.read(buffer)
                        while (read != -1) {
                            sink.write(buffer, 0, read)
                            uploaded += read
                            onProgress(uploaded, totalBytes)
                            read = input.read(buffer)
                        }
                    }
                }
            }

            val request = Request.Builder()
                .url(uploadUrl)
                .put(countingBody)
                .addHeader("X-Device-Id", commit.deviceId)
                .addHeader("X-World-Id", worldId)
                .addHeader("X-Commit-Id", commit.commitId)
                .addHeader("X-Parent-Commit-Id", commit.parentCommitId ?: "")
                .addHeader("X-SHA256-Checksum", commit.sha256Checksum)
                .addHeader("X-Author-Name", commit.authorName)
                .addHeader("X-Commit-Message", commit.commitMessage)
                .addHeader("X-Timestamp", commit.timestamp.toString())
                .addHeader("X-File-Size", totalBytes.toString())
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: response.message
                error("Upload to Cloudflare R2 failed with code ${response.code}: $errBody")
            }

            Log.i(TAG, "Snapshot uploaded successfully to Cloudflare R2 (${commit.commitId})")
            uploadUrl
        }
    }

    suspend fun downloadSnapshot(
        worldId: String,
        commitId: String,
        expectedSha256: String,
        targetFile: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBaseUrl = baseUrl.trimEnd('/')
            val downloadUrl = "$cleanBaseUrl/api/snapshots/$worldId/$commitId"
            Log.i(TAG, "Downloading world snapshot from Cloudflare R2: $downloadUrl")

            val request = Request.Builder()
                .url(downloadUrl)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                error("Download from Cloudflare R2 failed with code ${response.code}: ${response.message}")
            }

            val body = response.body ?: error("Empty response body from Cloudflare R2")
            val totalBytes = body.contentLength()
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.downloading")
            tempFile.parentFile?.mkdirs()

            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            var bytesRead = 0L

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    var count = input.read(buffer)
                    while (count != -1) {
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        bytesRead += count
                        onProgress(bytesRead, totalBytes)
                        count = input.read(buffer)
                    }
                    output.flush()
                }
            }

            val calculatedHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (expectedSha256.isNotBlank() && !calculatedHash.equals(expectedSha256, ignoreCase = true)) {
                tempFile.delete()
                error("Checksum verification failed! Expected: $expectedSha256, Calculated: $calculatedHash")
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                // Fallback copy if rename fails across mounts
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            Log.i(TAG, "Snapshot downloaded & checksum verified successfully: ${targetFile.absolutePath} ($bytesRead bytes)")
            targetFile
        }
    }

    suspend fun getLatestRemoteSnapshot(worldId: String): Result<JSONObject?> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBaseUrl = baseUrl.trimEnd('/')
            val url = "$cleanBaseUrl/api/snapshots/$worldId/latest"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (response.code == 404) {
                return@runCatching null
            }
            if (!response.isSuccessful) {
                error("Failed getting latest snapshot: code ${response.code}")
            }
            val text = response.body?.string() ?: return@runCatching null
            JSONObject(text)
        }
    }
}
