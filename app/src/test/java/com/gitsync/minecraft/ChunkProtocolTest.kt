package com.gitsync.minecraft

import com.gitsync.minecraft.core.networking.ChunkProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.ByteBuffer

class ChunkProtocolTest {

    @Test
    fun testMetadataPackingAndUnpacking() {
        val meta = ChunkProtocol.TransferMetadata(
            transferId = "t-12345",
            commitId = "c-9999",
            worldId = "SurvivalWorld",
            parentCommitId = "c-8888",
            totalSizeBytes = 1048576L,
            totalChunks = 32,
            sha256Checksum = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
            commitMessage = "v4 - Built iron farm",
            senderDeviceId = "dev-a",
            senderDisplayName = "Steve",
            timestamp = 1700000000000L
        )

        val packed = ChunkProtocol.packMetadata(meta)
        assertNotNull(packed)

        val buffer = ByteBuffer.wrap(packed)
        val type = buffer.get()
        assertEquals(ChunkProtocol.TYPE_METADATA, type)

        val len = buffer.getInt()
        val jsonBytes = ByteArray(len)
        buffer.get(jsonBytes)
        val json = org.json.JSONObject(String(jsonBytes))

        val unpacked = ChunkProtocol.TransferMetadata.fromJson(json)
        assertEquals(meta.transferId, unpacked.transferId)
        assertEquals(meta.commitId, unpacked.commitId)
        assertEquals(meta.worldId, unpacked.worldId)
        assertEquals(meta.totalSizeBytes, unpacked.totalSizeBytes)
        assertEquals(meta.sha256Checksum, unpacked.sha256Checksum)
    }

    @Test
    fun testChunkPacking() {
        val payload = "Bedrock LevelDB Chunk Binary Payload Data".toByteArray()
        val chunkPacket = ChunkProtocol.packChunk(5, payload, 0, payload.size)

        val buffer = ByteBuffer.wrap(chunkPacket)
        assertEquals(ChunkProtocol.TYPE_CHUNK, buffer.get())
        assertEquals(5, buffer.getInt())
        assertEquals(payload.size, buffer.remaining())
    }
}
