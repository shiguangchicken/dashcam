package com.firmmy.dashcam.core.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

interface RemoteServiceAdvertiser {
    fun register(port: Int = EmbeddedHttpServer.DEFAULT_PORT)

    fun unregister()
}

class AndroidNsdRemoteServiceAdvertiser(
    context: Context,
    private val serviceName: String = SERVICE_NAME,
) : RemoteServiceAdvertiser {
    private val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null

    override fun register(port: Int) {
        if (registrationListener != null) return
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                registrationListener = null
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        val serviceInfo = NsdServiceInfoFactory.create(
            NsdRemoteServiceDiscovery.SERVICE_TYPE,
            normalizedNsdServiceName(serviceName),
            port,
        )
        registrationListener = listener
        runCatching {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            registrationListener = null
        }
    }

    override fun unregister() {
        registrationListener?.let { listener ->
            runCatching { nsdManager.unregisterService(listener) }
        }
        registrationListener = null
    }

    companion object {
        const val SERVICE_NAME = "DashCam"
    }
}

internal fun normalizedNsdServiceName(name: String): String =
    name.trim().ifBlank { AndroidNsdRemoteServiceAdvertiser.SERVICE_NAME }
