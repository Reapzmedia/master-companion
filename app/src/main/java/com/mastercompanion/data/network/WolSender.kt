package com.mastercompanion.data.network

import com.mastercompanion.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WolSender @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    fun buildMagicPacket(macAddress: String): ByteArray {
        val macBytes = parseMacAddress(macAddress)
        // Magic packet: 6 bytes of 0xFF followed by 16 repetitions of the target MAC address (102 bytes total)
        val bytes = ByteArray(6 + 16 * macBytes.size)
        for (i in 0 until 6) {
            bytes[i] = 0xFF.toByte()
        }
        var dest = 6
        for (i in 0 until 16) {
            System.arraycopy(macBytes, 0, bytes, dest, macBytes.size)
            dest += macBytes.size
        }
        return bytes
    }

    /**
     * Sends a Wake-on-LAN Magic Packet to wake up a desktop PC on the local network (Layer 2 broadcast).
     * @param macAddress Format "AA:BB:CC:DD:EE:FF" or "AA-BB-CC-DD-EE-FF"
     * @param customBroadcastOrIp Optional user-specified broadcast IP or PC IP (e.g. "192.168.1.255", "192.168.1.254")
     */
    suspend fun sendMagicPacket(
        macAddress: String,
        customBroadcastOrIp: String? = null
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val bytes = buildMagicPacket(macAddress)

            // Gather all candidate broadcast and directed IP targets
            val targetAddresses = mutableSetOf<String>()

            // 1. Detect all active local network interface broadcast addresses (wlan0, eth0, etc.)
            targetAddresses.addAll(detectLocalBroadcastAddresses())

            // 2. Add user-specified IP and its calculated subnet broadcast
            if (!customBroadcastOrIp.isNullOrBlank()) {
                val clean = customBroadcastOrIp.trim()
                targetAddresses.add(clean)
                calculateSubnetBroadcast(clean)?.let { targetAddresses.add(it) }
            }

            // 3. Always include global broadcast & standard default subnet fallback
            targetAddresses.add("255.255.255.255")
            targetAddresses.add("192.168.1.255")

            Timber.i("Broadcasting WoL packet for MAC $macAddress to destinations: $targetAddresses")

            // Send to both port 9 and port 7 across all target IPs in a 3-burst transmission
            val ports = listOf(9, 7)
            var packetsSent = 0

            val socket = DatagramSocket()
            try {
                socket.broadcast = true

                // 3 burst repetitions with slight delay to wake modern NICs from PCIe low-power states
                for (burst in 1..3) {
                    for (targetIp in targetAddresses) {
                        try {
                            val address = InetAddress.getByName(targetIp)
                            for (port in ports) {
                                val packet = DatagramPacket(bytes, bytes.size, address, port)
                                socket.send(packet)
                                packetsSent++
                            }
                        } catch (e: Exception) {
                            Timber.w("Failed to send WoL packet to $targetIp: ${e.message}")
                        }
                    }
                    if (burst < 3) {
                        delay(35)
                    }
                }
            } finally {
                socket.close()
            }

            Timber.i("Successfully broadcasted $packetsSent WoL magic packets for MAC $macAddress")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send WoL Magic Packet to $macAddress")
            Result.failure(e)
        }
    }

    suspend fun sendWol(macAddress: String, customBroadcastOrIp: String? = null): Boolean {
        return sendMagicPacket(macAddress, customBroadcastOrIp).isSuccess
    }

    /**
     * Enumerates active network interfaces to find the true subnet broadcast address (e.g. 192.168.1.255).
     */
    fun detectLocalBroadcastAddresses(): List<String> {
        val broadcastList = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null) {
                        val hostAddress = broadcast.hostAddress
                        if (!hostAddress.isNullOrBlank() && !broadcastList.contains(hostAddress)) {
                            broadcastList.add(hostAddress)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error detecting network interface broadcast addresses")
        }
        return broadcastList
    }

    /**
     * Finds the active local IPv4 address of this Android device on the local Wi-Fi.
     */
    fun detectLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                for (inetAddress in networkInterface.inetAddresses) {
                    if (!inetAddress.isLoopbackAddress && inetAddress is java.net.Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error detecting local IP address")
        }
        return null
    }

    /**
     * Derives subnet broadcast address from an IPv4 address (e.g. 192.168.1.150 -> 192.168.1.255).
     */
    fun calculateSubnetBroadcast(ip: String): String? {
        val parts = ip.split(".")
        if (parts.size == 4) {
            return "${parts[0]}.${parts[1]}.${parts[2]}.255"
        }
        return null
    }

    private fun parseMacAddress(macStr: String): ByteArray {
        val clean = macStr.replace(":", "").replace("-", "").trim()
        require(clean.length == 12) { "Invalid MAC address: $macStr. Must be 12 hexadecimal characters." }
        val bytes = ByteArray(6)
        for (i in 0 until 6) {
            bytes[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return bytes
    }
}
