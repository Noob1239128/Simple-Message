package com.example.data.firestore

import java.security.MessageDigest
import java.security.SecureRandom

object CryptoUtil {
    private val ID_REGEX = Regex("^[a-z0-9_\\-]{3,24}$")
    private val random = SecureRandom()

    fun normalizeNickname(raw: String): String =
        raw.trim().lowercase().replace(" ", "_")

    fun isValidNickname(raw: String): Boolean {
        val norm = normalizeNickname(raw)
        return ID_REGEX.matches(norm)
    }

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$password".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPassword(password: String, salt: String, expectedHash: String): Boolean =
        hashPassword(password, salt) == expectedHash

    fun conversationIdFor(nickA: String, nickB: String): String {
        val a = normalizeNickname(nickA)
        val b = normalizeNickname(nickB)
        return listOf(a, b).sorted().joinToString("__")
    }
}
