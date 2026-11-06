package com.gitsync.minecraft.core.storage

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

object Sha256Checksum {

    private const val BUFFER_SIZE = 64 * 1024 // 64 KB buffer for streaming

    fun calculate(file: File): String {
        require(file.exists() && file.isFile) { "File does not exist or is not a regular file: ${file.absolutePath}" }
        return FileInputStream(file).use { input ->
            calculate(input)
        }
    }

    fun calculate(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        val hashBytes = digest.digest()
        return bytesToHex(hashBytes)
    }

    fun calculate(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return bytesToHex(hashBytes)
    }

    fun verify(file: File, expectedSha256: String): Boolean {
        if (!file.exists()) return false
        val actual = calculate(file)
        return actual.equals(expectedSha256.trim(), ignoreCase = true)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val hexArray = "0123456789abcdef".toCharArray()
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }
}
