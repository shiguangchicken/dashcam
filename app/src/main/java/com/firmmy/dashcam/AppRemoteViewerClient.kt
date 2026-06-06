package com.firmmy.dashcam

import android.content.Context
import com.firmmy.dashcam.core.common.DashCamCommand
import com.firmmy.dashcam.core.common.DashCamResult
import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.network.CompositeRemoteServiceDiscovery
import com.firmmy.dashcam.core.network.EmbeddedHttpServer
import com.firmmy.dashcam.core.network.ManualRemoteServiceDiscovery
import com.firmmy.dashcam.core.network.NsdRemoteServiceDiscovery
import com.firmmy.dashcam.core.network.RemoteDashCamClient
import com.firmmy.dashcam.core.network.RemoteMediaItem
import com.firmmy.dashcam.core.network.RemoteServiceEndpoint
import com.firmmy.dashcam.core.network.RemoteSettings
import com.firmmy.dashcam.core.network.RemoteStatus
import com.firmmy.dashcam.core.network.WifiGatewayRemoteServiceDiscovery
import com.firmmy.dashcam.feature.remote.RemoteViewerClient
import java.net.URI

class AppRemoteViewerClient(
    context: Context,
    private val tokenProvider: () -> String,
) : RemoteViewerClient {
    private val applicationContext = context.applicationContext
    private var client: RemoteDashCamClient? = null

    override suspend fun connect(manualHost: String): Boolean {
        val discovery = CompositeRemoteServiceDiscovery(
            listOf(
                NsdRemoteServiceDiscovery(applicationContext),
                WifiGatewayRemoteServiceDiscovery(applicationContext),
                ManualRemoteServiceDiscovery(hostProvider = { manualHost }),
            ),
        )
        val endpoint = discovery.discover() ?: manualHost.manualEndpoint() ?: return false
        client = RemoteDashCamClient(
            baseUrl = endpoint.baseUrl(),
            tokenProvider = { tokenProvider() },
        )
        status()
        return true
    }

    override suspend fun status(): RemoteStatus =
        requireClient().status().successOrThrow()

    override suspend fun media(type: MediaType?): List<RemoteMediaItem> =
        requireClient().media(type).successOrThrow()

    override suspend fun send(command: DashCamCommand): Boolean =
        requireClient().sendCommand(command).successOrThrow().ok

    override suspend fun deleteMedia(id: Long): Boolean =
        requireClient().deleteMedia(id).successOrThrow().ok

    override suspend fun settings(): RemoteSettings =
        requireClient().settings().successOrThrow()

    override suspend fun saveSettings(settings: RemoteSettings): Boolean =
        requireClient().saveSettings(settings).successOrThrow().ok

    override fun streamUrl(id: Long): String =
        requireClient().streamUrl(id)

    private fun requireClient(): RemoteDashCamClient =
        client ?: error("Remote client is not connected")

    private fun String.manualEndpoint(): RemoteServiceEndpoint? {
        val endpoint = trim()
        if (endpoint.isBlank()) return null
        val uri = runCatching {
            URI(if ("://" in endpoint) endpoint else "http://$endpoint")
        }.getOrNull()
        val host = uri?.host?.takeIf { it.isNotBlank() }
            ?: endpoint.removePrefix("http://").removePrefix("https://").substringBefore(":")
        val port = uri?.port?.takeIf { it > 0 } ?: EmbeddedHttpServer.DEFAULT_PORT
        return RemoteServiceEndpoint(host = host, port = port)
    }

    private fun <T> DashCamResult<T>.successOrThrow(): T =
        when (this) {
            is DashCamResult.Success -> value
            is DashCamResult.Failure -> error(error.message)
        }
}
