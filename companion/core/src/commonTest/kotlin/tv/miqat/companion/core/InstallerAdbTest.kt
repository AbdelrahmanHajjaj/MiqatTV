package tv.miqat.companion.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InstallerAdbTest {
    @Test
    fun oldPeerUsesUnsignedByteSumAndRejectsChangedPayload() {
        val bytes = byteArrayOf(0xff.toByte(), 0x80.toByte(), 0)
        val packet = AdbPacket(AdbCommand.WRITE, 7, 10, bytes)
        val frame = assertNotNull(InstallerAdb.encodeHeader(packet, InstallerAdb.legacyVersion))
        assertContentEquals(
            byteArrayOf(
                0x57, 0x52, 0x54, 0x45, 7, 0, 0, 0, 10, 0, 0, 0,
                3, 0, 0, 0, 0x7f, 1, 0, 0, 0xa8.toByte(), 0xad.toByte(), 0xab.toByte(), 0xba.toByte(),
            ),
            frame,
        )
        val header = assertNotNull(InstallerAdb.decodeHeader(frame, 4096))
        assertEquals(AdbCommand.WRITE, header.command)
        assertEquals(7, header.first)
        assertEquals(10, header.second)
        assertContentEquals(bytes, assertNotNull(InstallerAdb.decodePayload(header, bytes, InstallerAdb.legacyVersion)).payload)
        assertNull(InstallerAdb.decodePayload(header, byteArrayOf(0, 0, 0), InstallerAdb.legacyVersion))
        assertNull(InstallerAdb.decodePayload(header, byteArrayOf(0), InstallerAdb.currentVersion))
    }

    @Test
    fun badHeaderIsRejectedBeforePayloadAllocation() {
        val packet = AdbPacket(AdbCommand.WRITE, 1, 2, ByteArray(4097))
        val frame = assertNotNull(InstallerAdb.encodeHeader(packet, InstallerAdb.currentVersion))
        assertNull(InstallerAdb.decodeHeader(frame, 4096))
        val allowed = assertNotNull(InstallerAdb.decodeHeader(frame, 8192))
        assertEquals(0, allowed.checksum)
        assertNotNull(InstallerAdb.decodePayload(allowed, ByteArray(4097) { 1 }, InstallerAdb.currentVersion))
        assertNull(InstallerAdb.decodeHeader(frame.copyOf(22), 8192))
        val corrupted = frame.copyOf().also { it[20] = 0 }
        assertNull(InstallerAdb.decodeHeader(corrupted, 8192))
        val negative = frame.copyOf().also { it[15] = -1 }
        assertNull(InstallerAdb.decodeHeader(negative, 8192))
    }

    @Test
    fun registeredKeySignsAndNewKeyIsOfferedOnlyAfterSecondChallenge() {
        val handshake = ClassicAdbHandshake()
        assertEquals(AdbCommand.CONNECT, handshake.hello().command)
        assertEquals(AdbHandshakeAction.SIGN, handshake.receive(challenge()))
        assertEquals(AdbHandshakeAction.OFFER_KEY, handshake.receive(challenge()))
        assertEquals(AdbHandshakeAction.CONNECTED, handshake.receive(peer(InstallerAdb.currentVersion, 1024 * 1024)))
        assertEquals(InstallerAdb.currentVersion, handshake.version)
        assertEquals(InstallerAdb.maximumPayload, handshake.payloadLimit)
        assertEquals(AdbHandshakeAction.REJECT, handshake.receive(challenge()))

        val old = ClassicAdbHandshake()
        assertEquals(AdbHandshakeAction.SIGN, old.receive(challenge()))
        assertEquals(AdbHandshakeAction.CONNECTED, old.receive(peer(InstallerAdb.legacyVersion, 4096)))
        assertEquals(InstallerAdb.legacyVersion, old.version)
        assertEquals(4096, old.payloadLimit)
    }

    @Test
    fun unapprovedConnectionAndUnexpectedProtocolStopTheHandshake() {
        val unsolicited = ClassicAdbHandshake()
        assertEquals(AdbHandshakeAction.REJECT, unsolicited.receive(peer(InstallerAdb.currentVersion, 4096)))
        assertEquals(AdbHandshakeAction.REJECT, unsolicited.receive(challenge()))

        val shortToken = ClassicAdbHandshake()
        assertEquals(AdbHandshakeAction.REJECT, shortToken.receive(AdbPacket(AdbCommand.AUTH, 1, 0, ByteArray(19))))

        val repeatedToken = ClassicAdbHandshake()
        assertEquals(AdbHandshakeAction.SIGN, repeatedToken.receive(challenge()))
        assertEquals(AdbHandshakeAction.OFFER_KEY, repeatedToken.receive(challenge()))
        assertEquals(AdbHandshakeAction.REJECT, repeatedToken.receive(challenge()))

        val wireless = ClassicAdbHandshake()
        assertEquals(AdbHandshakeAction.WIRELESS_REQUIRED, wireless.receive(
            AdbPacket(AdbCommand.START_TLS, InstallerAdb.legacyVersion, 0, byteArrayOf()),
        ))
        assertEquals(AdbHandshakeAction.REJECT, wireless.receive(challenge()))
    }

    @Test
    fun wirelessAcceptsConnectOnlyAfterStartTls() {
        val upgrade = AdbPacket(AdbCommand.START_TLS, InstallerAdb.legacyVersion, 0, byteArrayOf())
        val connected = peer(InstallerAdb.currentVersion, 8192)
        val wireless = ClassicAdbHandshake()
        assertEquals(AdbHandshakeAction.WIRELESS_REQUIRED, wireless.receive(upgrade))
        assertEquals(AdbHandshakeAction.CONNECTED, wireless.receiveOverTls(connected))
        assertEquals(8192, wireless.payloadLimit)
        val plaintext = ClassicAdbHandshake()
        assertEquals(AdbHandshakeAction.WIRELESS_REQUIRED, plaintext.receive(upgrade))
        assertEquals(AdbHandshakeAction.REJECT, plaintext.receive(connected))
        assertEquals(AdbHandshakeAction.REJECT, plaintext.receiveOverTls(connected))
        assertEquals(AdbHandshakeAction.REJECT, ClassicAdbHandshake().receiveOverTls(connected))
    }


    @Test
    fun newPeerCanOmitHandshakeChecksumAndLegacyPeerCannotOmitBannerChecksum() {
        val auth = AdbPacket(AdbCommand.AUTH, 1, 0, ByteArray(20) { 4 })
        val modern = assertNotNull(InstallerAdb.encodeHeader(auth, InstallerAdb.currentVersion))
        val header = assertNotNull(InstallerAdb.decodeHeader(modern, 4096))
        assertNotNull(InstallerAdb.decodeHandshakePayload(header, auth.payload))

        val old = peer(InstallerAdb.legacyVersion, 4096)
        val skipped = assertNotNull(InstallerAdb.encodeHeader(old, InstallerAdb.currentVersion))
        assertNull(InstallerAdb.decodeHandshakePayload(
            assertNotNull(InstallerAdb.decodeHeader(skipped, 4096)), old.payload,
        ))
        val checked = assertNotNull(InstallerAdb.encodeHeader(old, InstallerAdb.legacyVersion))
        assertNotNull(InstallerAdb.decodeHandshakePayload(
            assertNotNull(InstallerAdb.decodeHeader(checked, 4096)), old.payload,
        ))
    }

    @Test
    fun uploadWaitsForReadyAndReturnsThePeersOutputAndClose() {
        val stream = ClassicAdbStream(5, 4096)
        val open = assertNotNull(stream.open("exec:example"))
        assertContentEquals("exec:example\u0000".encodeToByteArray(), open.payload)
        assertNull(stream.write(byteArrayOf(1)))
        fun reply(command: AdbCommand, payload: ByteArray = byteArrayOf()) = AdbPacket(command, 7, 5, payload)
        assertEquals(AdbStreamAction.OPENED, stream.receive(reply(AdbCommand.READY)))

        val sent = assertNotNull(stream.write(byteArrayOf(1, 2)))
        assertEquals(7, sent.second)
        assertNull(stream.write(byteArrayOf(3)))
        assertEquals(AdbStreamAction.OUTPUT, stream.receive(reply(AdbCommand.WRITE, byteArrayOf(10))))
        assertEquals(AdbCommand.READY, assertNotNull(stream.acknowledgeOutput()).command)
        assertNull(stream.write(byteArrayOf(3)))
        assertEquals(AdbStreamAction.ACKNOWLEDGED, stream.receive(reply(AdbCommand.READY)))
        assertNotNull(stream.write(byteArrayOf(3)))
        assertEquals(AdbStreamAction.ACKNOWLEDGED, stream.receive(reply(AdbCommand.READY)))
        assertEquals(AdbStreamAction.CLOSED, stream.receive(reply(AdbCommand.CLOSE)))
        assertEquals(7, assertNotNull(stream.close()).second)
        assertNull(stream.close())
    }

    @Test
    fun refusesUnsolicitedOrCrossStreamReplies() {
        val rejected = ClassicAdbStream(5, 4096)
        assertNotNull(rejected.open("exec:example"))
        assertEquals(AdbStreamAction.REFUSED, rejected.receive(AdbPacket(AdbCommand.CLOSE, 0, 5, byteArrayOf())))
        assertNull(rejected.write(byteArrayOf(1)))

        val wrongStream = ClassicAdbStream(5, 4096)
        assertNotNull(wrongStream.open("exec:example"))
        assertEquals(AdbStreamAction.OPENED, wrongStream.receive(AdbPacket(AdbCommand.READY, 7, 5, byteArrayOf())))
        assertEquals(AdbStreamAction.REJECT, wrongStream.receive(AdbPacket(AdbCommand.WRITE, 8, 5, byteArrayOf(1))))
        assertNull(wrongStream.write(byteArrayOf(1)))
    }

    private fun challenge() = AdbPacket(AdbCommand.AUTH, 1, 0, ByteArray(20))
    private fun peer(version: Int, limit: Int) =
        AdbPacket(AdbCommand.CONNECT, version, limit, "device::ro.product.device=tv;\u0000".encodeToByteArray())
}
