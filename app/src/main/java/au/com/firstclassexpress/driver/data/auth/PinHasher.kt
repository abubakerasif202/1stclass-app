package au.com.firstclassexpress.driver.data.auth

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 hashing for driver PINs.
 *
 * PINs are never written to Room in plain text: only the salt and the derived hash are stored, and
 * verification re-derives the hash rather than comparing secrets directly.
 */
class PinHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val random: SecureRandom = SecureRandom()
) {
    fun newSalt(): String = ByteArray(SALT_BYTES).also(random::nextBytes).toHex()

    fun hash(pin: String, saltHex: String): String {
        require(pin.isNotEmpty()) { "PIN is required" }
        require(saltHex.isNotEmpty()) { "Salt is required" }
        val spec = PBEKeySpec(pin.toCharArray(), saltHex.fromHex(), iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded.toHex()
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(pin: String, saltHex: String, expectedHashHex: String): Boolean {
        if (pin.isEmpty() || saltHex.isEmpty() || expectedHashHex.isEmpty()) return false
        return constantTimeEquals(hash(pin, saltHex), expectedHashHex)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray {
        require(length % 2 == 0) { "Salt must be hex encoded" }
        return ByteArray(length / 2) { i ->
            substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val DEFAULT_ITERATIONS = 20_000
        const val KEY_BITS = 256
        const val SALT_BYTES = 16
    }
}
