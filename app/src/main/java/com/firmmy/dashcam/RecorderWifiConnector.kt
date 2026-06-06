package com.firmmy.dashcam

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import com.firmmy.dashcam.core.network.RemoteConnectionPayload
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class RecorderWifiConnector(context: Context) {
    private val applicationContext = context.applicationContext
    private val connectivityManager =
        applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var callback: ConnectivityManager.NetworkCallback? = null

    suspend fun connect(payload: RemoteConnectionPayload): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWithSpecifier(payload)
        } else {
            connectLegacy(payload)
        }

    fun release() {
        callback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        callback = null
        runCatching { connectivityManager.bindProcessToNetwork(null) }
    }

    private suspend fun connectWithSpecifier(payload: RemoteConnectionPayload): Boolean {
        release()
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(payload.ssid)
            .setWpa2Passphrase(payload.password)
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build()
        return withTimeoutOrNull(WIFI_CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        connectivityManager.bindProcessToNetwork(network)
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onUnavailable() {
                        if (continuation.isActive) continuation.resume(false)
                    }

                    override fun onLost(network: Network) {
                        connectivityManager.bindProcessToNetwork(null)
                    }
                }
                callback = networkCallback
                continuation.invokeOnCancellation {
                    runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
                }
                runCatching {
                    connectivityManager.requestNetwork(request, networkCallback)
                }.onFailure {
                    callback = null
                    runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        } ?: false
    }

    @Suppress("DEPRECATION")
    private fun connectLegacy(payload: RemoteConnectionPayload): Boolean {
        val config = WifiConfiguration().apply {
            SSID = payload.ssid.quoted()
            preSharedKey = payload.password.quoted()
        }
        val networkId = wifiManager.addNetwork(config)
        if (networkId == -1) return false
        return wifiManager.enableNetwork(networkId, true) && wifiManager.reconnect()
    }

    private fun String.quoted(): String = "\"$this\""

    companion object {
        private const val WIFI_CONNECT_TIMEOUT_MS = 30_000L
    }
}
