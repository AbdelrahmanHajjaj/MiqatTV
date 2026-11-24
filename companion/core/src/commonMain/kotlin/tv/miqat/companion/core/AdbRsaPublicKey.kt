package tv.miqat.companion.core

object AdbRsaPublicKey {
    private const val modulusBytes = 256
    private val prefix = byteArrayOf(0x30, 0x82.toByte(), 1, 10, 2, 0x82.toByte(), 1, 1, 0)
    private val exponent = byteArrayOf(2, 3, 1, 0, 1)

    // The iOS installer generates 2048-bit RSA keys with exponent 65537.
    fun encode(pkcs1: ByteArray): ByteArray? {
        if (pkcs1.size != prefix.size + modulusBytes + exponent.size ||
            !prefix.indices.all { pkcs1[it] == prefix[it] } ||
            !exponent.indices.all { pkcs1[prefix.size + modulusBytes + it] == exponent[it] } ||
            pkcs1[prefix.size] >= 0
        ) return null

        val modulus = pkcs1.copyOfRange(prefix.size, prefix.size + modulusBytes).reversedArray()
        val leastWord = (0..3).fold(0) { word, index -> word or ((modulus[index].toInt() and 255) shl (8 * index)) }
        if (leastWord and 1 == 0) return null

        var inverse = 1
        repeat(5) { inverse *= 2 - leastWord * inverse }
        val rr = ByteArray(modulusBytes)
        rr[0] = 1
        repeat(modulusBytes * 16) { doubleModulo(rr, modulus) }

        val result = ByteArray(8 + 2 * modulusBytes + 4)
        result[0] = (modulusBytes / 4).toByte()
        val negativeInverse = -inverse
        for (index in 0..3) result[4 + index] = (negativeInverse ushr (8 * index)).toByte()
        modulus.copyInto(result, 8)
        rr.copyInto(result, 8 + modulusBytes)
        result[result.size - 4] = 1
        result[result.size - 2] = 1
        return result
    }

    private fun doubleModulo(value: ByteArray, modulus: ByteArray) {
        var carry = 0
        for (index in value.indices) {
            val next = (value[index].toInt() and 255) * 2 + carry
            value[index] = next.toByte()
            carry = next ushr 8
        }
        if (carry == 0) {
            for (index in value.indices.reversed()) {
                val digit = value[index].toInt() and 255
                val bound = modulus[index].toInt() and 255
                if (digit < bound) return
                if (digit > bound) break
            }
        }
        var borrow = 0
        for (index in value.indices) {
            val difference = (value[index].toInt() and 255) - (modulus[index].toInt() and 255) - borrow
            value[index] = difference.toByte()
            borrow = if (difference < 0) 1 else 0
        }
    }
}
