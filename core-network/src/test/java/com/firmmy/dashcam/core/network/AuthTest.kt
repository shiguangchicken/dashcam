package com.firmmy.dashcam.core.network

import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthTest {
    @Test
    fun generateCreatesTokenAndPairingCode() {
        val manager = PairingCredentialManager(
            random = SecureRandom(byteArrayOf(1, 2, 3, 4)),
            clockMillis = { 123L },
        )

        val credentials = manager.generate()

        assertEquals(64, credentials.token.length)
        assertEquals(6, credentials.pairingCode.length)
        assertEquals(123L, credentials.createdAtMillis)
    }

    @Test
    fun ensureKeepsExistingCredentials() {
        val credentials = PairingCredentialManager(clockMillis = { 456L }).ensure(
            token = "token",
            pairingCode = "123456",
        )

        assertEquals("token", credentials.token)
        assertEquals("123456", credentials.pairingCode)
        assertEquals(456L, credentials.createdAtMillis)
    }

    @Test
    fun authenticateAcceptsMatchingBearerToken() {
        val authenticator = BearerTokenAuthenticator { "token" }

        assertEquals(AuthResult.Authenticated, authenticator.authenticate("Bearer token"))
        assertTrue(authenticator.isWriteAuthorized("Bearer token"))
    }

    @Test
    fun authenticateRejectsMissingToken() {
        val authenticator = BearerTokenAuthenticator { "token" }

        assertEquals(AuthResult.MissingToken, authenticator.authenticate(null))
        assertFalse(authenticator.isWriteAuthorized(null))
    }

    @Test
    fun authenticateRejectsInvalidScheme() {
        val authenticator = BearerTokenAuthenticator { "token" }

        assertEquals(AuthResult.InvalidScheme, authenticator.authenticate("Basic token"))
    }

    @Test
    fun authenticateRejectsInvalidToken() {
        val authenticator = BearerTokenAuthenticator { "token" }

        assertEquals(AuthResult.InvalidToken, authenticator.authenticate("Bearer wrong"))
    }

    @Test
    fun authenticateRejectsWhenTokenIsNotConfigured() {
        val authenticator = BearerTokenAuthenticator { "" }

        assertEquals(AuthResult.NotConfigured, authenticator.authenticate("Bearer token"))
    }
}
