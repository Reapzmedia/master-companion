package com.mastercompanion.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WolSenderTest {

    private lateinit var wolSender: WolSender

    @Before
    fun setUp() {
        wolSender = WolSender(Dispatchers.Unconfined)
    }

    @Test
    fun buildMagicPacket_createsExact102BytePacket() {
        val mac = "E8:9C:25:3B:36:8A"
        val packet = wolSender.buildMagicPacket(mac)

        assertEquals("Magic packet must be exactly 102 bytes", 102, packet.size)

        // First 6 bytes must be 0xFF
        for (i in 0 until 6) {
            assertEquals("Byte $i must be 0xFF", 0xFF.toByte(), packet[i])
        }

        // Expected 6 bytes of MAC
        val expectedMacBytes = byteArrayOf(
            0xE8.toByte(), 0x9C.toByte(), 0x25.toByte(),
            0x3B.toByte(), 0x36.toByte(), 0x8A.toByte()
        )

        // Next 16 blocks must repeat the exact MAC bytes
        for (repetition in 0 until 16) {
            val offset = 6 + (repetition * 6)
            val chunk = packet.copyOfRange(offset, offset + 6)
            assertArrayEquals("Repetition $repetition must match MAC bytes", expectedMacBytes, chunk)
        }
    }

    @Test
    fun buildMagicPacket_handlesHyphenSeparatedMac() {
        val mac = "E8-9C-25-3B-36-8A"
        val packet = wolSender.buildMagicPacket(mac)
        assertEquals(102, packet.size)
        assertEquals(0xE8.toByte(), packet[6])
        assertEquals(0x8A.toByte(), packet[11])
    }

    @Test
    fun buildMagicPacket_handlesLowerCaseMac() {
        val mac = "e8:9c:25:3b:36:8a"
        val packet = wolSender.buildMagicPacket(mac)
        assertEquals(102, packet.size)
        assertEquals(0xE8.toByte(), packet[6])
        assertEquals(0x8A.toByte(), packet[11])
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildMagicPacket_throwsOnInvalidMacLength() {
        wolSender.buildMagicPacket("E8:9C:25:3B:36")
    }

    @Test
    fun calculateSubnetBroadcast_derivesBroadcastAddress() {
        val bcast = wolSender.calculateSubnetBroadcast("192.168.1.64")
        assertNotNull(bcast)
        assertEquals("192.168.1.255", bcast)

        val bcast10 = wolSender.calculateSubnetBroadcast("10.0.0.15")
        assertEquals("10.0.0.255", bcast10)
    }
}
