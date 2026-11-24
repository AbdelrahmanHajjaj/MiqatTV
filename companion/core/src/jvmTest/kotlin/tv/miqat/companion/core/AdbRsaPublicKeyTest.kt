package tv.miqat.companion.core

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AdbRsaPublicKeyTest {
    @Test
    fun legacyAdbKeyEncodesActualModulusAndMontgomeryValues() {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public as RSAPublicKey
        val random = Random(574)
        val moduli = listOf(key.modulus) + List(4) {
            val bytes = random.nextBytes(256)
            bytes[0] = (bytes[0].toInt() or 128).toByte()
            bytes[255] = (bytes[255].toInt() or 1).toByte()
            BigInteger(1, bytes)
        }
        for (modulus in moduli) {
            val input = pkcs1(modulus)
            val encoded = assertNotNull(AdbRsaPublicKey.encode(input))
            assertEquals(524, encoded.size)
            assertEquals(BigInteger.valueOf(64), littleEndian(encoded, 0, 4))
            assertEquals(modulus, littleEndian(encoded, 8, 264))
            assertEquals(BigInteger.ONE.shiftLeft(4096).mod(modulus), littleEndian(encoded, 264, 520))
            assertEquals(BigInteger.valueOf(65537), littleEndian(encoded, 520, 524))
            val base = BigInteger.ONE.shiftLeft(32)
            assertEquals(base - BigInteger.ONE, (modulus * littleEndian(encoded, 4, 8)).mod(base))
        }
    }

    @Test
    fun rejectsTruncatedDerAndNonCanonicalKeyParameters() {
        val modulus = BigInteger.ONE.shiftLeft(2047).add(BigInteger.ONE)
        val key = pkcs1(modulus)
        assertNull(AdbRsaPublicKey.encode(key.copyOf(key.size - 1)))
        assertNull(AdbRsaPublicKey.encode(key.copyOf().also { it[264] = 0 }))
        assertNull(AdbRsaPublicKey.encode(key.copyOf().also { it[269] = 3 }))
    }

    private fun pkcs1(modulus: BigInteger) =
        byteArrayOf(0x30, 0x82.toByte(), 1, 10, 2, 0x82.toByte(), 1, 1) +
            modulus.toByteArray() + byteArrayOf(2, 3, 1, 0, 1)

    private fun littleEndian(bytes: ByteArray, start: Int, end: Int) =
        BigInteger(1, bytes.copyOfRange(start, end).reversedArray())
}
