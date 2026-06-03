package com.firmmy.dashcam.core.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.net.InetAddress
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class RemoteServiceEndpoint(
    val host: String,
    val port: Int = EmbeddedHttpServer.DEFAULT_PORT,
) {
    fun baseUrl(): String = "http://$host:$port"
}

fun interface RemoteServiceDiscovery {
    suspend fun discover(): RemoteServiceEndpoint?
}

class CompositeRemoteServiceDiscovery(
    private val strategies: List<RemoteServiceDiscovery>,
) : RemoteServiceDiscovery {
    override suspend fun discover(): RemoteServiceEndpoint? {
        strategies.forEach { strategy ->
            strategy.discover()?.let { return it }
        }
        return null
    }
}

class ManualRemoteServiceDiscovery(
    private val hostProvider: () -> String,
    private val portProvider: () -> Int = { EmbeddedHttpServer.DEFAULT_PORT },
) : RemoteServiceDiscovery {
    override suspend fun discover(): RemoteServiceEndpoint? {
        val host = hostProvider().trim().removePrefix("http://").removePrefix("https://").substringBefore(":")
        if (host.isBlank()) return null
        return RemoteServiceEndpoint(host = host, port = portProvider())
    }
}

class WifiGatewayRemoteServiceDiscovery(
    context: Context,
    private val port: Int = EmbeddedHttpServer.DEFAULT_PORT,
) : RemoteServiceDiscovery {
    private val applicationContext = context.applicationContext

    override suspend fun discover(): RemoteServiceEndpoint? =
        withContext(Dispatchers.IO) {
            val manager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return@withContext null
            @Suppress("DEPRECATION")
            val gateway = manager.dhcpInfo?.gateway ?: return@withContext null
            val host = gateway.toInetAddress().hostAddress ?: return@withContext null
            RemoteServiceEndpoint(host = host, port = port)
        }

    private fun Int.toInetAddress(): InetAddress {
        val value = if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            Integer.reverseBytes(this)
        } else {
            this
        }
        return InetAddress.getByAddress(
            byteArrayOf(
                (value ushr 24).toByte(),
                (value ushr 16).toByte(),
                (value ushr 8).toByte(),
                value.toByte(),
            ),
        )
    }
}

class NsdRemoteServiceDiscovery(
    context: Context,
    private val serviceType: String = SERVICE_TYPE,
    private val timeoutMillis: Long = 3_000L,
) : RemoteServiceDiscovery {
    private val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    override suspend fun discover(): RemoteServiceEndpoint? =
        suspendCancellableCoroutine { continuation ->
            var discoveryListener: NsdManager.DiscoveryListener? = null
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) = Unit

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (serviceInfo.serviceType != serviceType) return
                    nsdManager.resolveService(
                        serviceInfo,
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                val endpoint = RemoteServiceEndpoint(
                                    host = serviceInfo.host.hostAddress ?: return,
                                    port = serviceInfo.port,
                                )
                                discoveryListener?.let(nsdManager::stopServiceDiscovery)
                                if (continuation.isActive) {
                                    continuation.resume(endpoint)
                                }
                            }
                        },
                    )
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            }
            discoveryListener = listener
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            continuation.invokeOnCancellation {
                runCatching { nsdManager.stopServiceDiscovery(listener) }
            }
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch {
                delay(timeoutMillis)
                runCatching { nsdManager.stopServiceDiscovery(listener) }
                if (continuation.isActive) continuation.resume(null)
            }
        }

    companion object {
        const val SERVICE_TYPE = "_dashcam._tcp."
    }
}
