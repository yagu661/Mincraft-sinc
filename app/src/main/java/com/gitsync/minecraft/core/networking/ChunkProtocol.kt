package com.gitsync.minecraft.core.networking

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object ChunkProtocol {

    const val CHUNK_PAYLOAD_SIZE = 32 * 1024 // 32 KB per chunk for optimal WebRTC DataChannel throughput
    const val MAX_SUPPORTED_WORLD_SIZE_BYTES = 1024L * 1024L * 1024L // 1 GB safety limit

    const val TYPE_METADATA: Byte = 0x01
    const val TYPE_CHUNK: Byte = 0x02
    const val TYPE_CHUNK_ACK: Byte = 0x03
    const val TYPE_COMPLETE_VERIFY: Byte = 0x04
    const val TYPE_STORAGE_ACK: Byte = 0x05
    const val TYPE_TRANSFER_ERROR: Byte = 0x06

    data class TransferMetadata(
        val transferId: String,
        val commitId: String,
        val worldId: String,
        val parentCommitId: String?,
        val totalSizeBytes: Long,
        val totalChunks: Int,
        val sha256Checksum: String,
        val commitMessage: String,
        val senderDeviceId: String,
        val senderDisplayName: String,
        val timestamp: Long
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("transferId", transferId)
            put("commitId", commitId)
            put("worldId", worldId)
            put("parentCommitId", parentCommitId ?: JSONObject.NULL)
            put("totalSizeBytes", totalSizeBytes)
            put("totalChunks", totalChunks)
            put("sha256Checksum", sha256Checksum)
            put("commitMessage", commitMessage)
            put("senderDeviceId", senderDeviceId)
            put("senderDisplayName", senderDisplayName)
            put("timestamp", timestamp)
        }

        companion object {
            fun fromJson(json: JSONObject): TransferMetadata = TransferMetadata(
                transferId = json.getString("transferId"),
                commitId = json.getString("commitId"),
                worldId = json.getString("worldId"),
                parentCommitId = if (json.isNull("parentCommitId")) null else json.getString("parentCommitId"),
                totalSizeBytes = json.getLong("totalSizeBytes"),
                totalChunks = json.getInt("totalChunks"),
                sha256Checksum = json.getString("sha256Checksum"),
                commitMessage = json.getString("commitMessage"),
                senderDeviceId = json.getString("senderDeviceId"),
                senderDisplayName = json.getString("senderDisplayName"),
                timestamp = json.getLong("timestamp")
            )
        }
    }

    /**
     * Packs metadata into a binary packet: [1 byte TYPE] [4 bytes JSON len] [JSON bytes]
     */
    fun packMetadata(metadata: TransferMetadata): ByteArray {
        val jsonBytes = metadata.toJson().toString().toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(1 + 4 + jsonBytes.size)
        buffer.put(TYPE_METADATA)
        buffer.putInt(jsonBytes.size)
        buffer.put(jsonBytes)
        return buffer.array()
    }

    /**
     * Packs a binary data chunk: [1 byte TYPE] [4 bytes chunkIndex] [payload bytes]
     */
    fun packChunk(chunkIndex: Int, payload: ByteArray, offset: Int, length: Int): ByteArray {
        val buffer = ByteBuffer.allocate(1 + 4 + length)
        buffer.put(TYPE_CHUNK)
        buffer.putInt(chunkIndex)
        buffer.put(payload, offset, length)
        return buffer.array()
    }

    /**
     * Packs a chunk ACK: [1 byte TYPE] [4 bytes chunkIndex]
     */
    fun packChunkAck(chunkIndex: Int): ByteArray {
        val buffer = ByteBuffer.allocate(1 + 4)
        buffer.put(TYPE_CHUNK_ACK)
        buffer.putInt(chunkIndex)
        return buffer.array()
    }

    /**
     * Packs a storage verification ACK: [1 byte TYPE] [1 byte successFlag 1/0] [4 bytes commitId len] [commitId bytes]
     */
    fun packStorageAck(commitId: String, success: Boolean): ByteArray {
        val commitBytes = commitId.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(1 + 1 + 4 + commitBytes.size)
        buffer.put(TYPE_STORAGE_ACK)
        buffer.put(if (success) 1.toByte() else 0.toByte())
        buffer.putInt(commitBytes.size)
        buffer.put(commitBytes)
        return buffer.array()
    }
}
