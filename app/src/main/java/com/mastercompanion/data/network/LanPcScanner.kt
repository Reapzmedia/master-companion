package com.mastercompanion.data.network

import android.os.Build
import com.mastercompanion.data.remote.model.DiscoveredPc
import com.mastercompanion.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanPcScanner @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /**
     * Scans the active local network subnet (e.g. 192.168.1.1..254) for computers.
     * Uses NetBIOS Node Status queries (UDP port 137) to discover PC hostname and MAC address
     * without requiring any software on the target PC or root on Android.
     */
    fun scanSubnet(): Flow<DiscoveredPc> = channelFlow {
        withContext(ioDispatcher) {
            val localIps = getCandidateSubnetIps()
            if (localIps.isEmpty()) {
                Timber.w("No active local subnet detected for LAN scan")
                return@withContext
            }

            Timber.i("Starting LAN scan across ${localIps.size} candidate IPs")

            // On Android 9, attempt to pre-populate from /proc/net/arp if readable
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                val arpEntries = readArpTable()
                for (entry in arpEntries) {
                    send(entry)
                }
            }

            // Probe subnet in chunks of 24 concurrent requests to prevent Wi-Fi saturation
            val chunkSize = 24
            for (chunk in localIps.chunked(chunkSize)) {
                val deferreds = chunk.map { ip ->
                    async {
                        queryNetBios(ip)
                    }
                }
                val results = deferreds.awaitAll()
                for (pc in results.filterNotNull()) {
                    send(pc)
                }
            }
        }
    }

    /**
     * Probes an IP address with a standard NetBIOS Node Status query on UDP port 137.
     * Returns DiscoveredPc containing Hostname and hardware MAC address if target responds.
     */
    suspend fun queryNetBios(ip: String, timeoutMs: Int = 450): DiscoveredPc? = withContext(ioDispatcher) {
        var socket: DatagramSocket? = null
        try {
            val targetAddr = InetAddress.getByName(ip)
            val packetData = buildNetBiosNodeStatusQuery()
            val requestPacket = DatagramPacket(packetData, packetData.size, targetAddr, 137)

            socket = DatagramSocket()
            socket.soTimeout = timeoutMs
            socket.send(requestPacket)

            val buffer = ByteArray(1024)
            val responsePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(responsePacket)

            return@withContext parseNetBiosResponse(responsePacket.data, responsePacket.length, ip)
        } catch (_: Exception) {
            // Socket timeout or host offline - expected for unused IPs
            null
        } finally {
            socket?.close()
        }
    }

    /**
     * Tests if target PC is powered on and network-ready by probing TCP port 445 (Microsoft SMB)
     * and fallback port 135 (RPC Endpoint Mapper).
     * Returns true within milliseconds once Windows has booted.
     */
    suspend fun verifyPcOnline(ip: String, timeoutMs: Int = 750): Boolean = withContext(ioDispatcher) {
        if (ip.isBlank()) return@withContext false
        
        // 1. Probe Port 445 (SMB)
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, 445), timeoutMs)
                return@withContext true
            }
        } catch (_: Exception) {
            // Port 445 did not respond, test port 135 fallback
        }

        // 2. Probe Port 135 (RPC)
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, 135), timeoutMs)
                return@withContext true
            }
        } catch (_: Exception) {
            // Both ports unreachable
            false
        }
    }

    /**
     * Builds RFC 1002 compliant 50-byte NetBIOS Node Status Query packet.
     */
    fun buildNetBiosNodeStatusQuery(): ByteArray {
        val bytes = ByteArray(50)
        // Transaction ID: 0x1337
        bytes[0] = 0x13
        bytes[1] = 0x37
        // Flags: 0x0000 (Standard Query)
        bytes[2] = 0x00
        bytes[3] = 0x00
        // Questions: 1
        bytes[4] = 0x00
        bytes[5] = 0x01
        // Answer, Authority, Additional RRs: 0
        bytes[6] = 0x00; bytes[7] = 0x00
        bytes[8] = 0x00; bytes[9] = 0x00
        bytes[10] = 0x00; bytes[11] = 0x00

        // Question Name length: 32 bytes (0x20)
        bytes[12] = 0x20
        // Wildcard '*' (0x2A -> 'C', 'K')
        bytes[13] = 'C'.code.toByte()
        bytes[14] = 'K'.code.toByte()
        // 15 spaces (0x20 -> 'C', 'A')
        for (i in 0 until 15) {
            bytes[15 + (i * 2)] = 'C'.code.toByte()
            bytes[16 + (i * 2)] = 'A'.code.toByte()
        }
        // Zero-length terminator for root
        bytes[45] = 0x00
        // Question Type: 0x0021 (NBSTAT: Node Status)
        bytes[46] = 0x00
        bytes[47] = 0x21
        // Question Class: 0x0001 (IN: Internet)
        bytes[48] = 0x00
        bytes[49] = 0x01

        return bytes
    }

    /**
     * Parses RFC 1002 NetBIOS Node Status Response to extract PC Hostname and physical MAC address.
     */
    fun parseNetBiosResponse(data: ByteArray, length: Int, ip: String): DiscoveredPc? {
        if (length < 56) return null

        try {
            // Offset 56 is typically where the Answer Record begins after Header + Question
            // Find the number of names (1 byte)
            val numNamesOffset = 56
            if (numNamesOffset >= length) return null

            val numNames = data[numNamesOffset].toInt() and 0xFF
            if (numNames <= 0 || numNames > 50) return null

            var pcName = ""
            val namesTableStart = numNamesOffset + 1

            for (i in 0 until numNames) {
                val offset = namesTableStart + (i * 18)
                if (offset + 18 > length) break

                val rawName = String(data, offset, 15, Charsets.US_ASCII).trim()
                val nameType = data[offset + 15].toInt() and 0xFF

                // Name type 0x00 = Workstation/Computer Name, 0x20 = Server
                if (nameType == 0x00 || nameType == 0x20) {
                    if (pcName.isBlank() && rawName.isNotBlank() && !rawName.startsWith("__MSBROWSE__")) {
                        pcName = rawName
                    }
                }
            }

            // Immediately following the names table is the STATISTICS block
            // The first 6 bytes of the statistics block is the UNIT_ID (Physical MAC Address)
            val macOffset = namesTableStart + (numNames * 18)
            if (macOffset + 6 > length) return null

            val macBytes = ByteArray(6)
            System.arraycopy(data, macOffset, macBytes, 0, 6)

            val macAddress = macBytes.joinToString(":") { "%02X".format(it) }

            // Validate MAC address isn't all zeros or broadcast
            if (macAddress == "00:00:00:00:00:00" || macAddress == "FF:FF:FF:FF:FF:FF") {
                return null
            }

            val displayName = pcName.ifBlank { "PC ($ip)" }
            return DiscoveredPc(
                name = displayName,
                ipAddress = ip,
                macAddress = macAddress,
                isOnline = true
            )
        } catch (e: Exception) {
            Timber.w("Error parsing NetBIOS response from $ip: ${e.message}")
            return null
        }
    }

    /**
     * Enumerates candidate IPv4 addresses on the current active Wi-Fi or Ethernet subnet.
     */
    private fun getCandidateSubnetIps(): List<String> {
        val candidates = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                for (addr in iface.interfaceAddresses) {
                    val ip = addr.address
                    if (ip is java.net.Inet4Address && !ip.isLoopbackAddress) {
                        val host = ip.hostAddress ?: continue
                        val parts = host.split(".")
                        if (parts.size == 4) {
                            val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
                            val myLastOctet = parts[3].toIntOrNull() ?: 0
                            // Scan .1 through .254 excluding self
                            for (octet in 1..254) {
                                if (octet != myLastOctet) {
                                    candidates.add("$prefix.$octet")
                                }
                            }
                            return candidates // Found primary subnet
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error calculating subnet candidates")
        }
        return candidates
    }

    /**
     * Reads /proc/net/arp on Android 9 and older devices.
     */
    private fun readArpTable(): List<DiscoveredPc> {
        val list = mutableListOf<DiscoveredPc>()
        val arp = File("/proc/net/arp")
        if (!arp.exists() || !arp.canRead()) return list

        try {
            BufferedReader(FileReader(arp)).use { reader ->
                reader.readLine() // Skip header
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val tokens = line!!.split("\\s+".toRegex())
                    if (tokens.size >= 4) {
                        val ip = tokens[0]
                        val mac = tokens[3].uppercase()
                        if (mac.length == 17 && mac != "00:00:00:00:00:00") {
                            list.add(
                                DiscoveredPc(
                                    name = "Device ($ip)",
                                    ipAddress = ip,
                                    macAddress = mac,
                                    isOnline = true
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d("Could not read /proc/net/arp: ${e.message}")
        }
        return list
    }
}
