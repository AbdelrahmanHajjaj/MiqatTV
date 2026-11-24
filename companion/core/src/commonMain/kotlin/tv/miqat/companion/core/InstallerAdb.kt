package tv.miqat.companion.core

enum class AdbCommand(val wire: Int) {
    CONNECT(0x4e584e43),
    AUTH(0x48545541),
    START_TLS(0x534c5453),
    OPEN(0x4e45504f),
    READY(0x59414b4f),
    WRITE(0x45545257),
    CLOSE(0x45534c43),
}

class AdbHeader internal constructor(
    val command: AdbCommand,
    val first: Int,
    val second: Int,
    val payloadSize: Int,
    val checksum: Int,
)

class AdbPacket(val command: AdbCommand, val first: Int, val second: Int, val payload: ByteArray)

object InstallerAdb {
    const val headerSize = 24
    const val maximumPayload = 256 * 1024
    const val legacyVersion = 0x01000000
    const val currentVersion = 0x01000001

    fun decodeHeader(bytes: ByteArray, payloadLimit: Int): AdbHeader? {
        if (bytes.size != headerSize || payloadLimit !in 0..maximumPayload) return null
        val tag = readWord(bytes, 0)
        if (readWord(bytes, 20) != tag.inv()) return null
        val command = AdbCommand.entries.firstOrNull { it.wire == tag } ?: return null
        val count = readWord(bytes, 12)
        if (count !in 0..payloadLimit) return null
        return AdbHeader(command, readWord(bytes, 4), readWord(bytes, 8), count, readWord(bytes, 16))
    }

    fun decodePayload(header: AdbHeader, payload: ByteArray, version: Int): AdbPacket? {
        if (header.payloadSize != payload.size || version !in legacyVersion..currentVersion) return null
        if (version == legacyVersion && header.checksum != checksum(payload)) return null
        return AdbPacket(header.command, header.first, header.second, payload)
    }

    fun decodeHandshakePayload(header: AdbHeader, payload: ByteArray): AdbPacket? {
        if (header.command != AdbCommand.CONNECT && header.command != AdbCommand.AUTH &&
            header.command != AdbCommand.START_TLS
        ) return null
        // New peers can omit the checksum before the host sees their CONNECT version.
        val version = if (header.checksum == 0) currentVersion else legacyVersion
        if (header.command == AdbCommand.CONNECT && header.first == legacyVersion) {
            return decodePayload(header, payload, legacyVersion)
        }
        return decodePayload(header, payload, version)
    }

    fun encodeHeader(packet: AdbPacket, version: Int): ByteArray? {
        if (packet.payload.size > maximumPayload || version !in legacyVersion..currentVersion) return null
        val header = ByteArray(headerSize)
        writeWord(header, 0, packet.command.wire)
        writeWord(header, 4, packet.first)
        writeWord(header, 8, packet.second)
        writeWord(header, 12, packet.payload.size)
        writeWord(header, 16, if (version == legacyVersion) checksum(packet.payload) else 0)
        writeWord(header, 20, packet.command.wire.inv())
        return header
    }

    private fun checksum(bytes: ByteArray): Int = bytes.sumOf { it.toInt() and 255 }

    private fun readWord(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 255) or
            ((bytes[offset + 1].toInt() and 255) shl 8) or
            ((bytes[offset + 2].toInt() and 255) shl 16) or
            ((bytes[offset + 3].toInt() and 255) shl 24)

    private fun writeWord(bytes: ByteArray, offset: Int, word: Int) {
        for (index in 0..3) bytes[offset + index] = (word ushr (8 * index)).toByte()
    }
}

enum class AdbHandshakeAction { SIGN, OFFER_KEY, CONNECTED, WIRELESS_REQUIRED, REJECT }

class ClassicAdbHandshake {
    private enum class Phase { HELLO, SIGNED, OFFERED, FINISHED }

    private var phase = Phase.HELLO
    var version = InstallerAdb.legacyVersion
        private set
    var payloadLimit = 4096
        private set

    fun hello(): AdbPacket = AdbPacket(
        AdbCommand.CONNECT, InstallerAdb.currentVersion, InstallerAdb.maximumPayload,
        "host::\u0000".encodeToByteArray(),
    )

    fun receive(packet: AdbPacket): AdbHandshakeAction {
        if (phase == Phase.FINISHED) return AdbHandshakeAction.REJECT
        when (packet.command) {
            AdbCommand.AUTH -> {
                if (packet.first == 1 && packet.second == 0 && packet.payload.size == 20) {
                    when (phase) {
                        Phase.HELLO -> {
                            phase = Phase.SIGNED
                            return AdbHandshakeAction.SIGN
                        }
                        Phase.SIGNED -> {
                            phase = Phase.OFFERED
                            return AdbHandshakeAction.OFFER_KEY
                        }
                        else -> Unit
                    }
                }
            }
            AdbCommand.CONNECT -> {
                val banner = packet.payload.decodeToString()
                if (phase != Phase.HELLO && packet.first >= InstallerAdb.legacyVersion && packet.second >= 4096 &&
                    packet.payload.size in 9..4096 && banner.startsWith("device:") && banner.indexOf(':', 7) >= 7
                ) {
                    phase = Phase.FINISHED
                    version = minOf(packet.first, InstallerAdb.currentVersion)
                    payloadLimit = minOf(packet.second, InstallerAdb.maximumPayload)
                    return AdbHandshakeAction.CONNECTED
                }
            }
            AdbCommand.START_TLS -> {
                if (packet.first == InstallerAdb.legacyVersion && packet.second == 0 && packet.payload.isEmpty()) {
                    phase = Phase.FINISHED
                    return AdbHandshakeAction.WIRELESS_REQUIRED
                }
            }
            else -> Unit
        }
        phase = Phase.FINISHED
        return AdbHandshakeAction.REJECT
    }
}

enum class AdbStreamAction { OPENED, ACKNOWLEDGED, OUTPUT, CLOSED, REFUSED, REJECT }

class ClassicAdbStream(private val localId: Int, private val payloadLimit: Int) {
    private enum class Phase { NEW, OPENING, ACTIVE, CLOSED }

    private var phase = Phase.NEW
    private var remoteId = 0
    private var awaitingWrite = false
    private var awaitingReadAck = false

    fun open(destination: String): AdbPacket? {
        if (phase != Phase.NEW || localId == 0 || payloadLimit !in 4096..InstallerAdb.maximumPayload ||
            destination.isEmpty() || destination.length >= 4096 || '\u0000' in destination
        ) return null
        val service = (destination + '\u0000').encodeToByteArray()
        if (service.size > 4096) return null
        phase = Phase.OPENING
        return AdbPacket(AdbCommand.OPEN, localId, 0, service)
    }

    fun write(bytes: ByteArray): AdbPacket? {
        if (phase != Phase.ACTIVE || awaitingWrite || bytes.size !in 1..payloadLimit) return null
        awaitingWrite = true
        return AdbPacket(AdbCommand.WRITE, localId, remoteId, bytes)
    }

    fun acknowledgeOutput(): AdbPacket? {
        if (phase != Phase.ACTIVE || !awaitingReadAck) return null
        awaitingReadAck = false
        return AdbPacket(AdbCommand.READY, localId, remoteId, ByteArray(0))
    }

    fun close(): AdbPacket? {
        if (remoteId == 0) return null
        val packet = AdbPacket(AdbCommand.CLOSE, localId, remoteId, ByteArray(0))
        remoteId = 0
        phase = Phase.CLOSED
        return packet
    }

    fun receive(packet: AdbPacket): AdbStreamAction {
        if (packet.second != localId || (phase == Phase.ACTIVE && packet.first != remoteId)) return reject()
        if (phase == Phase.OPENING && packet.payload.isEmpty()) {
            if (packet.command == AdbCommand.READY && packet.first != 0) {
                phase = Phase.ACTIVE
                remoteId = packet.first
                return AdbStreamAction.OPENED
            }
            if (packet.command == AdbCommand.CLOSE && packet.first == 0) {
                phase = Phase.CLOSED
                return AdbStreamAction.REFUSED
            }
        }
        if (phase != Phase.ACTIVE) return reject()
        return when {
            packet.command == AdbCommand.READY && awaitingWrite && packet.payload.isEmpty() -> {
                awaitingWrite = false
                AdbStreamAction.ACKNOWLEDGED
            }
            packet.command == AdbCommand.WRITE && !awaitingReadAck && packet.payload.size <= payloadLimit -> {
                awaitingReadAck = true
                AdbStreamAction.OUTPUT
            }
            packet.command == AdbCommand.CLOSE && packet.payload.isEmpty() -> {
                phase = Phase.CLOSED
                AdbStreamAction.CLOSED
            }
            else -> reject()
        }
    }

    private fun reject(): AdbStreamAction {
        phase = Phase.CLOSED
        remoteId = 0
        return AdbStreamAction.REJECT
    }
}
