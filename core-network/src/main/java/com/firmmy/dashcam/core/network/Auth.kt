package com.firmmy.dashcam.core.network

import java.security.SecureRandom

data class PairingCredentials(
    val token: String,
    val pairingCode: String,
    val createdAtMillis: Long,
)

class PairingCredentialManager(
    private val random: SecureRandom = SecureRandom(),
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    fun generate(): PairingCredentials =
        PairingCredentials(
            token = randomToken(byteCount = TOKEN_BYTES),
            pairingCode = randomPairingCode(),
            createdAtMillis = clockMillis(),
        )

    fun ensure(token: String, pairingCode: String): PairingCredentials =
        if (token.isBlank() || pairingCode.isBlank()) {
            generate()
        } else {
            PairingCredentials(
                token = token,
                pairingCode = pairingCode,
                createdAtMillis = clockMillis(),
            )
        }

    private fun randomToken(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun randomPairingCode(): String =
        random.nextInt(PAIRING_CODE_BOUND).toString().padStart(PAIRING_CODE_LENGTH, '0')

    companion object {
        private const val TOKEN_BYTES = 32
        private const val PAIRING_CODE_LENGTH = 6
        private const val PAIRING_CODE_BOUND = 1_000_000
    }
}

sealed interface AuthResult {
    data object Authenticated : AuthResult
    data object NotConfigured : AuthResult
    data object MissingToken : AuthResult
    data object InvalidScheme : AuthResult
    data object InvalidToken : AuthResult
}

class BearerTokenAuthenticator(
    private val expectedTokenProvider: () -> String,
) {
    fun authenticate(authorizationHeader: String?): AuthResult {
        val expectedToken = expectedTokenProvider().trim()
        if (expectedToken.isBlank()) {
            return AuthResult.NotConfigured
        }
        if (authorizationHeader.isNullOrBlank()) {
            return AuthResult.MissingToken
        }

        val parts = authorizationHeader.trim().split(Regex("\\s+"), limit = 2)
        if (parts.size != 2 || !parts[0].equals("Bearer", ignoreCase = true)) {
            return AuthResult.InvalidScheme
        }

        return if (constantTimeEquals(parts[1], expectedToken)) {
            AuthResult.Authenticated
        } else {
            AuthResult.InvalidToken
        }
    }

    fun isWriteAuthorized(authorizationHeader: String?): Boolean =
        authenticate(authorizationHeader) == AuthResult.Authenticated

    private fun constantTimeEquals(actual: String, expected: String): Boolean {
        val actualBytes = actual.toByteArray()
        val expectedBytes = expected.toByteArray()
        var diff = actualBytes.size xor expectedBytes.size
        val maxSize = maxOf(actualBytes.size, expectedBytes.size)
        for (index in 0 until maxSize) {
            val actualByte = actualBytes.getOrNull(index)?.toInt() ?: 0
            val expectedByte = expectedBytes.getOrNull(index)?.toInt() ?: 0
            diff = diff or (actualByte xor expectedByte)
        }
        return diff == 0
    }
}
