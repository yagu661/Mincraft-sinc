package com.gitsync.minecraft.core.networking

import android.content.Context
import android.util.Log
import com.gitsync.minecraft.core.storage.Sha256Checksum
import com.gitsync.minecraft.core.storage.StorageAccessHelper
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.json.JSONObject

class FileTransferSession(
    private val context: Context,
    val onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit,
    val onComplete: (archiveFile: File, metadata: ChunkProtocol.TransferMetadata) -> Unit,
    val onError: (error: String) -> Unit
) {
    companion object {
        private const val TAG = "FileTransferSession"
    }

    private var currentMetadata: ChunkProtocol.TransferMetadata? = null
    private var stagingFile: File? = null
    private var randomAccessFile: RandomAccessFile? = null
    private var totalBytesReceived = 0L
    private val receivedChunksBitSet = mutableSetOf<Int>()

    fun handleIncomingBuffer(buffer: ByteBuffer): ByteArray? {
        if (!buffer.hasRemaining()) return null

        val packetType = buffer.get()
        when (packetType) {
            ChunkProtocol.TYPE_METADATA -> {
                val jsonLength = buffer.getInt()
                val jsonBytes = ByteArray(jsonLength)
                buffer.get(jsonBytes)
                val jsonString = String(jsonBytes, StandardCharsets.UTF_8)
                val metadata = ChunkProtocol.TransferMetadata.fromJson(JSONObject(jsonString))
                initializeSession(metadata)
                return null
            }
            ChunkProtocol.TYPE_CHUNK -> {
                val chunkIndex = buffer.getInt()
                val payloadSize = buffer.remaining()
                val payload = ByteArray(payloadSize)
                buffer.get(payload)
                processChunk(chunkIndex, payload)
                return null
            }
            ChunkProtocol.TYPE_STORAGE_ACK -> {
                // Sender received remote storage confirmation
                val success = buffer.get() == 1.toByte()
                val commitLen = buffer.getInt()
                val commitBytes = ByteArray(commitLen)
                buffer.get(commitBytes)
                val commitId = String(commitBytes, StandardCharsets.UTF_8)
                Log.i(TAG, "Storage ACK received for commit: $commitId, success=$success")
                return null
            }
            else -> {
                Log.w(TAG, "Unknown packet type: $packetType")
                return null
            }
        }
    }

    private fun initializeSession(metadata: ChunkProtocol.TransferMetadata) {
        currentMetadata = metadata
        totalBytesReceived = 0L
        receivedChunksBitSet.clear()

        if (metadata.totalSizeBytes > ChunkProtocol.MAX_SUPPORTED_WORLD_SIZE_BYTES) {
            onError("World size (${metadata.totalSizeBytes / (1024 * 1024)}MB) exceeds maximum allowed limit.")
            return
        }

        val stagingDir = StorageAccessHelper.getTempDirectory(context)
        val file = File(stagingDir, "incoming_${metadata.transferId}_${metadata.commitId}.tmp")
        if (file.exists()) file.delete()

        stagingFile = file
        randomAccessFile = RandomAccessFile(file, "rw")
        randomAccessFile?.setLength(metadata.totalSizeBytes) // Pre-allocate for fast streaming writes

        Log.i(TAG, "Transfer session initialized for: ${metadata.worldId}, size: ${metadata.totalSizeBytes}B, chunks: ${metadata.totalChunks}")
    }

    private fun processChunk(chunkIndex: Int, payload: ByteArray) {
        val meta = currentMetadata ?: return
        val raf = randomAccessFile ?: return

        try {
            val offset = chunkIndex.toLong() * ChunkProtocol.CHUNK_PAYLOAD_SIZE
            synchronized(raf) {
                raf.seek(offset)
                raf.write(payload)
            }

            if (receivedChunksBitSet.add(chunkIndex)) {
                totalBytesReceived += payload.size
                onProgress(totalBytesReceived, meta.totalSizeBytes)
            }

            // Check if all chunks received
            if (receivedChunksBitSet.size == meta.totalChunks || totalBytesReceived >= meta.totalSizeBytes) {
                finalizeSession(meta)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing chunk $chunkIndex", e)
            onError("Disk write error: ${e.message}")
        }
    }

    private fun finalizeSession(metadata: ChunkProtocol.TransferMetadata) {
        try {
            randomAccessFile?.close()
            randomAccessFile = null
        } catch (_: Exception) {}

        val file = stagingFile ?: return
        Log.i(TAG, "All chunks received. Verifying SHA-256 checksum...")

        val calculatedSha = Sha256Checksum.calculate(file)
        if (!calculatedSha.equals(metadata.sha256Checksum, ignoreCase = true)) {
            Log.e(TAG, "SHA-256 verification failed! Expected: ${metadata.sha256Checksum}, Actual: $calculatedSha")
            file.delete()
            onError("SHA-256 checksum verification failed. Archive is corrupt.")
            return
        }

        Log.i(TAG, "SHA-256 verification passed ($calculatedSha). Snapshot integrity confirmed.")
        val snapshotsDir = StorageAccessHelper.getSnapshotsDirectory(context)
        val finalFile = File(snapshotsDir, "received_${metadata.worldId}_${metadata.commitId}.mcworld")

        if (!file.renameTo(finalFile)) {
            file.copyTo(finalFile, overwrite = true)
            file.delete()
        }

        onComplete(finalFile, metadata)
    }

    fun abort() {
        try {
            randomAccessFile?.close()
            randomAccessFile = null
            stagingFile?.delete()
            stagingFile = null
        } catch (e: Exception) {
            Log.w(TAG, "Error during session abort", e)
        }
    }
}
