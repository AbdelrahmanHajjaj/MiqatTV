package tv.miqat.companion.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdbPairingWireTest {
    @Test
    fun validatesFirstFrameBeforeReadingTheBoundedPayload() {
        val challenge = byteArrayOf(1, 0, 0, 0, 0, 32)
        val peer = byteArrayOf(1, 1, 0, 0, 0x20, 0x10)
        assertContentEquals(challenge, AdbPairingWire.header(AdbPairingFrame.CHALLENGE))
        assertContentEquals(peer, AdbPairingWire.header(AdbPairingFrame.PEER))
        assertTrue(AdbPairingWire.matchesHeader(peer, AdbPairingFrame.PEER))
        assertFalse(AdbPairingWire.matchesHeader(peer, AdbPairingFrame.CHALLENGE))
        assertFalse(AdbPairingWire.matchesHeader(peer.copyOf(5), AdbPairingFrame.PEER))
        assertFalse(AdbPairingWire.matchesHeader(peer.copyOf().also { it[0] = 2 }, AdbPairingFrame.PEER))
        assertFalse(AdbPairingWire.matchesHeader(peer.copyOf().also { it[2] = 1 }, AdbPairingFrame.PEER))
    }

    @Test
    fun preservesLeadingZeroAndBindsTheCodeToThisTlsSession() {
        val exporter = ByteArray(64) { (it + 128).toByte() }
        val password = assertNotNull(AdbPairingWire.password("٠١٢٣٤٥", exporter))
        assertContentEquals("012345".encodeToByteArray() + exporter, password)
        assertNull(AdbPairingWire.password("01234x", exporter))
        assertNull(AdbPairingWire.password(" 012345", exporter))
        assertNull(AdbPairingWire.password("012345", exporter.copyOf(63)))
    }

    @Test
    fun checksThePeerTypeAndTheGuidTerminator() {
        val offered = assertNotNull(AdbPairingWire.hostPeer("fixture Miqat-iPhone\u0000".encodeToByteArray()))
        assertEquals(8192, offered.size)
        assertEquals(0.toByte(), offered[0])
        assertNull(AdbPairingWire.deviceGuid(offered))
        assertNull(AdbPairingWire.hostPeer("fixture".encodeToByteArray()))
        assertNull(AdbPairingWire.hostPeer("fixture\u0000injected\u0000".encodeToByteArray()))

        val peer = ByteArray(8192)
        peer[0] = 1
        "adb-fixture-1".encodeToByteArray().copyInto(peer, 1)
        assertEquals("adb-fixture-1", AdbPairingWire.deviceGuid(peer))
        assertNull(AdbPairingWire.deviceGuid(peer.copyOf(32)))
        assertNull(AdbPairingWire.deviceGuid(ByteArray(8192) { if (it == 0) 1 else 65 }))
    }
}
