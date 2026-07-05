package com.example.roomcall.network

import java.net.NetworkInterface

object NetworkUtils {

    fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()

            for (networkInterface in interfaces) {
                val addresses = networkInterface.inetAddresses

                for (address in addresses) {
                    val hostAddress = address.hostAddress ?: continue

                    if (!address.isLoopbackAddress &&
                        hostAddress.contains(".") &&
                        !hostAddress.startsWith("169.254")
                    ) {
                        return hostAddress
                    }
                }
            }

            "IP 확인 불가"
        } catch (e: Exception) {
            "IP 확인 불가"
        }
    }
}

