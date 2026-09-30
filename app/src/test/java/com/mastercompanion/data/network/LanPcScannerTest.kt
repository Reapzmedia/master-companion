package com.mastercompanion.data.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanPcScannerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var scanner: LanPcScanner

    @Before
    fun setup() {
        scanner = LanPcScanner(testDispatcher)
    }

    @Test
    fun testBuildNetBiosNodeStatusQuery_lengthAndHeader() {
        val packet = scanner.buildNetBiosNodeStatusQuery()
        assertEquals("Packet length must be exactly 50 bytes", 50, packet.size)
        // Transaction ID: 0x1337
        assertEquals(0x13.toByte(), packet[0])
        assertEquals(0x37.toByte(), packet[1])
        // Flags: 0x0000
        assertEquals(0x00.toByte(), packet[2])
        assertEquals(0x00.toByte(), packet[3])
        // Questions: 1
        assertEquals(0x01.toByte(), packet[5])
        // Name length: 32 bytes (0x20)
        assertEquals(0x20.toByte(), packet[12])
        // Wildcard 'CK'
        assertEquals('C'.code.toByte(), packet[13])
        assertEquals('K'.code.toByte(), packet[14])
        // Question Type: 0x0021 (NBSTAT)
        assertEquals(0x00.toByte(), packet[46])
        assertEquals(0x21.toByte(), packet[47])
    }

    @Test
    fun testParseNetBiosResponse_extractsNameAndMac() {
        // Construct mock NetBIOS Node Status response
        // Header (12 bytes) + Question (38 bytes) + Answer RR Header (6 bytes) = 56 bytes offset
        val data = ByteArray(120)
        // 56th byte: Number of names = 1
        data[56] = 1

        // Name 1: "MY-GAMING-RIG  " (15 bytes) + Type 0x00 (Workstation) + 2 bytes flags = 18 bytes
        val nameBytes = "MY-GAMING-RIG  ".toByteArray(Charsets.US_ASCII)
        System.arraycopy(nameBytes, 0, data, 57, 15)
        data[57 + 15] = 0x00 // Type: Workstation

        // Statistics block follows names table: offset = 57 + 18 = 75
        // First 6 bytes of stats block is MAC Address: D8:BB:C1:2A:9F:44
        val macBytes = byteArrayOf(
            0xD8.toByte(), 0xBB.toByte(), 0xC1.toByte(),
            0x2A.toByte(), 0x9F.toByte(), 0x44.toByte()
        )
        System.arraycopy(macBytes, 0, data, 75, 6)

        val result = scanner.parseNetBiosResponse(data, 120, "192.168.1.105")
        assertNotNull(result)
        assertEquals("MY-GAMING-RIG", result?.name)
        assertEquals("D8:BB:C1:2A:9F:44", result?.macAddress)
        assertEquals("192.168.1.105", result?.ipAddress)
    }

    @Test
    fun testParseNetBiosResponse_invalidLengthReturnsNull() {
        val shortData = ByteArray(30)
        val result = scanner.parseNetBiosResponse(shortData, 30, "192.168.1.100")
        assertNull(result)
    }
}
