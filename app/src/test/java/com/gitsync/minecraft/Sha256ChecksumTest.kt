package com.gitsync.minecraft

import com.gitsync.minecraft.core.storage.Sha256Checksum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class Sha256ChecksumTest {

    @Test
    fun testEmptyStringSha256() {
        val input = "".toByteArray(StandardCharsets.UTF_8)
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val actual = Sha256Checksum.calculate(input)
        assertEquals(expected, actual)
    }

    @Test
    fun testKnownStringSha256() {
        val input = "MinecraftGitSyncBedrockWorld".toByteArray(StandardCharsets.UTF_8)
        val stream = ByteArrayInputStream(input)
        val hash = Sha256Checksum.calculate(stream)
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("^[0-9a-f]{64}$")))
    }
}
