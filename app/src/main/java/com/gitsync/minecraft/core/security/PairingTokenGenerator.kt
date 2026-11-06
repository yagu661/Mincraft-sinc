package com.gitsync.minecraft.core.security

import java.security.SecureRandom

object PairingTokenGenerator {
    private const val ALPHANUM = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" // Crockford base32 subset to avoid O/0, I/1 confusion
    private val random = SecureRandom()

    fun generateSecureToken(length: Int = 16): String {
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(ALPHANUM[random.nextInt(ALPHANUM.length)])
            if ((i + 1) % 4 == 0 && i < length - 1) {
                sb.append("-")
            }
        }
        return sb.toString()
    }

    fun cleanToken(raw: String): String {
        return raw.uppercase().replace("-", "").trim()
    }
}
