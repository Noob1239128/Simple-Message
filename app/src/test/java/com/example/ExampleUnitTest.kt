package com.example

import com.example.data.firestore.CryptoUtil
import com.example.data.github.ObtainiumIntegration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun nicknameValidationAndConversationId_isDeterministic() {
        assertTrue(CryptoUtil.isValidNickname("Max_99"))
        assertFalse(CryptoUtil.isValidNickname("ab"))
        assertEquals("anna__max", CryptoUtil.conversationIdFor("Max", "anna"))
        assertEquals("anna__max", CryptoUtil.conversationIdFor("Anna", "max"))
    }

    @Test
    fun passwordHashing_verifiesCorrectly() {
        val salt = CryptoUtil.generateSalt()
        val hash = CryptoUtil.hashPassword("geheim123", salt)
        assertTrue(CryptoUtil.verifyPassword("geheim123", salt, hash))
        assertFalse(CryptoUtil.verifyPassword("falsch", salt, hash))
    }

    @Test
    fun obtainiumIntegration_parsesGithubRepoAndBuildsConfig() {
        val parsed = ObtainiumIntegration.parseOwnerRepo("https://github.com/octocat/PulseChat/")
        assertNotNull(parsed)
        assertEquals("octocat" to "PulseChat", parsed)

        val json = ObtainiumIntegration.buildObtainiumJson("octocat/PulseChat")
        assertTrue(json.contains("https://github.com/octocat/PulseChat"))
        assertTrue(json.contains("com.aistudio.pulsechat.qmzvkt"))

        val deepLink = ObtainiumIntegration.buildObtainiumDeepLink("octocat/PulseChat")
        assertTrue(deepLink.startsWith("obtainium://add/"))
    }
}
