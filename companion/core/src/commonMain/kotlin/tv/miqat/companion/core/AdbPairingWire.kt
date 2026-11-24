package tv.miqat.companion.core

enum class AdbPairingFrame(val wire: Byte, val payloadSize: Int) {
    CHALLENGE(0, 32),
    PEER(1, 8192 + 16),
}

object AdbPairingWire {
    const val headerSize = 6
    const val exporterSize = 64
    const val peerSize = 8192

    fun header(frame: AdbPairingFrame): ByteArray {
        val size = frame.payloadSize
        return byteArrayOf(1, frame.wire, (size ushr 24).toByte(), (size ushr 16).toByte(),
            (size ushr 8).toByte(), size.toByte())
    }

    fun matchesHeader(bytes: ByteArray, frame: AdbPairingFrame): Boolean =
        bytes.size == headerSize && bytes.contentEquals(header(frame))

    fun password(code: String, exporter: ByteArray): ByteArray? {
        if (code.length != 6 || exporter.size != exporterSize) return null
        val input = ByteArray(6)
        for (i in code.indices) {
            val digit = code[i].digitToIntOrNull() ?: return null
            input[i] = ('0'.code + digit).toByte()
        }
        return input + exporter
    }

    fun hostPeer(publicKey: ByteArray): ByteArray? {
        if (publicKey.size !in 2 until peerSize || publicKey.last() != 0.toByte() ||
            (0 until publicKey.lastIndex).any { publicKey[it].toInt() !in 32..126 }
        ) return null
        return ByteArray(peerSize).also { publicKey.copyInto(it, destinationOffset = 1) }
    }

    fun deviceGuid(plaintext: ByteArray): String? {
        if (plaintext.size != peerSize || plaintext[0] != 1.toByte()) return null
        var end = 1
        while (end < plaintext.size && plaintext[end] != 0.toByte()) {
            if (plaintext[end].toInt() !in 33..126 || end > 255) return null
            end++
        }
        if (end == 1 || end == plaintext.size) return null
        return plaintext.decodeToString(startIndex = 1, endIndex = end)
    }
}
