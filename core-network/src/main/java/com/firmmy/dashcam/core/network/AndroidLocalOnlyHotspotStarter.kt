package com.firmmy.dashcam.core.network

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper

class AndroidLocalOnlyHotspotStarter(
    context: Context,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : LocalOnlyHotspotStarter {
    private val applicationContext = context.applicationContext
    private val wifiManager = applicationContext.getSystemService(WifiManager::class.java)

    override fun isSupported(): Boolean =
        applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI) && wifiManager != null

    override fun start(callback: LocalOnlyHotspotCallback) {
        val manager = requireNotNull(wifiManager) { "WifiManager is unavailable" }
        manager.startLocalOnlyHotspot(
            object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    callback.onStarted(AndroidLocalOnlyHotspotSession(reservation))
                }

                override fun onStopped() {
                    callback.onStopped()
                }

                override fun onFailed(reason: Int) {
                    val failure = reason.toHotspotFailure()
                    callback.onFailed(failure, failure.message)
                }
            },
            handler,
        )
    }
}

private class AndroidLocalOnlyHotspotSession(
    private val reservation: WifiManager.LocalOnlyHotspotReservation,
) : LocalOnlyHotspotSession {
    override val credentials: HotspotCredentials
        get() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val config = reservation.softApConfiguration
                return HotspotCredentials(
                    ssid = config.ssid.orEmpty(),
                    password = config.passphrase.orEmpty(),
                )
            }
            @Suppress("DEPRECATION")
            val config = reservation.wifiConfiguration
            @Suppress("DEPRECATION")
            return HotspotCredentials(
                ssid = config?.SSID?.trim('"').orEmpty(),
                password = config?.preSharedKey?.trim('"').orEmpty(),
            )
        }

    override fun close() {
        reservation.close()
    }
}

private fun Int.toHotspotFailure(): HotspotFailure =
    when (this) {
        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> HotspotFailure.INCOMPATIBLE_MODE
        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> HotspotFailure.NO_CHANNEL
        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED -> HotspotFailure.TETHERING_DISALLOWED
        else -> HotspotFailure.SYSTEM_ERROR
    }

private val HotspotFailure.message: String
    get() = when (this) {
        HotspotFailure.UNSUPPORTED -> "LocalOnlyHotspot is unsupported"
        HotspotFailure.PERMISSION_DENIED -> "LocalOnlyHotspot permission denied"
        HotspotFailure.INCOMPATIBLE_MODE -> "Wi-Fi is in an incompatible mode"
        HotspotFailure.NO_CHANNEL -> "No Wi-Fi hotspot channel is available"
        HotspotFailure.TETHERING_DISALLOWED -> "LocalOnlyHotspot is disallowed by the system"
        HotspotFailure.SYSTEM_ERROR -> "LocalOnlyHotspot failed"
    }
