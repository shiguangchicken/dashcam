package com.firmmy.dashcam

import com.firmmy.dashcam.core.network.EmbeddedHttpServer
import java.net.Inet4Address
import java.net.NetworkInterface

object HotspotEndpointResolver {
    fun privateIpv4Addresses(): Set<String> =
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { networkInterface ->
                networkInterface.inetAddresses.asSequence()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
                    .map { it.hostAddress }
            }
            .toSet()

    fun resolveBaseUrl(
        addressesBeforeHotspot: Set<String>,
        port: Int = EmbeddedHttpServer.DEFAULT_PORT,
    ): String {
        val current = privateIpv4Addresses()
        val hotspotAddress = (current - addressesBeforeHotspot).firstOrNull()
            ?: current.firstOrNull()
            ?: return ""
        return "http://$hotspotAddress:$port"
    }
}
