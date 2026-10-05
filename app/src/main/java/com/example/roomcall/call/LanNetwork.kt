package com.example.roomcall.call

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/** Only a directly attached Wi-Fi IPv4 subnet is eligible; no cellular, VPN or WAN routing. */
data class LanNetwork(val network: Network, val link: LinkAddress) {
    val address: InetAddress get() = link.address
    fun contains(peer: InetAddress): Boolean = sameSubnet(address.address, peer.address, link.prefixLength)
    fun socket(peer: InetAddress, port: Int): Socket {
        require(contains(peer)) { "같은 Wi-Fi의 Receiver IP를 입력하세요." }
        return network.socketFactory.createSocket().apply {
            try {
                bind(InetSocketAddress(address, 0))
                connect(InetSocketAddress(peer, port), 3000)
                tcpNoDelay = true
                soTimeout = 6000
            } catch (e: Exception) { close(); throw e }
        }
    }
    companion object {
        fun find(context: Context): LanNetwork? {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            return manager.allNetworks.firstNotNullOfOrNull { network ->
                val caps = manager.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@firstNotNullOfOrNull null
                val link = manager.getLinkProperties(network)?.linkAddresses?.firstOrNull {
                    it.address is Inet4Address && !it.address.isLoopbackAddress && !it.address.isLinkLocalAddress
                } ?: return@firstNotNullOfOrNull null
                LanNetwork(network, link)
            }
        }
        internal fun sameSubnet(local: ByteArray, peer: ByteArray, prefix: Int): Boolean {
            if (local.size != 4 || peer.size != 4 || prefix !in 1..32) return false
            // Exclude multicast, broadcast, unspecified and loopback peers.
            val first = peer[0].toInt() and 255
            if (first == 0 || first == 127 || first >= 224 || local.contentEquals(peer)) return false
            for (bit in 0 until prefix) {
                val mask = 1 shl (7 - bit % 8)
                if ((local[bit / 8].toInt() and mask) != (peer[bit / 8].toInt() and mask)) return false
            }
            if (prefix < 31 && (prefix until 32).all { bit ->
                (peer[bit / 8].toInt() and (1 shl (7 - bit % 8))) != 0
            }) return false
            return true
        }
    }
}