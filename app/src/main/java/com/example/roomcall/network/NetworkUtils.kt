package com.example.roomcall.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    fun getLocalIpAddress(context: Context): String {
        return try {
            val connectivityManager =
                context.getSystemService(ConnectivityManager::class.java)
            val activeNetwork = connectivityManager.activeNetwork
            val activeAddress = activeNetwork
                ?.let(connectivityManager::getLinkProperties)
                ?.linkAddresses
                ?.firstNotNullOfOrNull(::usableIpv4Address)

            activeAddress ?: fallbackIpv4Address() ?: "IP 확인 불가"
        } catch (e: Exception) {
            fallbackIpv4Address() ?: "IP 확인 불가"
        }
    }

    private fun fallbackIpv4Address(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val addresses = networkInterface.inetAddresses

                for (address in addresses) {
                    if (address is Inet4Address &&
                        !address.isLoopbackAddress &&
                        !address.isLinkLocalAddress
                    ) {
                        return address.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun usableIpv4Address(linkAddress: LinkAddress): String? {
        val address = linkAddress.address
        return if (address is Inet4Address &&
            !address.isLoopbackAddress &&
            !address.isLinkLocalAddress
        ) address.hostAddress else null
    }
}

