package com.firmmy.dashcam.core.network

import com.firmmy.dashcam.core.common.DashCamCommand
import com.firmmy.dashcam.core.common.DashCamError
import com.firmmy.dashcam.core.common.DashCamResult
import com.firmmy.dashcam.core.common.MediaType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.appendPathSegments

class RemoteDashCamClient(
    private val baseUrl: String,
    private val httpClient: HttpClient = defaultHttpClient(),
) {
    suspend fun status(): DashCamResult<RemoteStatus> =
        request {
            val response = httpClient.get(apiUrl("status"))
            response.ensureSuccess()
            RemoteJson.parseStatus(response.body())
        }

    suspend fun sendCommand(command: DashCamCommand): DashCamResult<RemoteApiResponse> =
        request {
            val response = httpClient.post(apiUrl("command")) {
                header(HttpHeaders.ContentType, "application/json")
                setBody(RemoteJson.commandBody(command))
            }
            response.ensureSuccess()
            RemoteJson.parseResponse(response.body())
        }

    suspend fun media(
        type: MediaType? = null,
        date: String? = null,
    ): DashCamResult<List<RemoteMediaItem>> =
        request {
            val response = httpClient.get(apiUrl("media")) {
                type?.let { parameter("type", it.storedValue) }
                date?.let { parameter("date", it) }
            }
            response.ensureSuccess()
            RemoteJson.parseMediaList(response.body())
        }

    suspend fun settings(): DashCamResult<RemoteSettings> =
        request {
            val response = httpClient.get(apiUrl("settings"))
            response.ensureSuccess()
            RemoteJson.parseSettings(response.body())
        }

    suspend fun saveSettings(settings: RemoteSettings): DashCamResult<RemoteApiResponse> =
        request {
            val response = httpClient.put(apiUrl("settings")) {
                header(HttpHeaders.ContentType, "application/json")
                setBody(RemoteJson.settings(settings))
            }
            response.ensureSuccess()
            RemoteJson.parseResponse(response.body())
        }

    suspend fun deleteMedia(id: Long): DashCamResult<RemoteApiResponse> =
        request {
            val response = httpClient.delete(apiUrl("media", id.toString()))
            response.ensureSuccess()
            RemoteJson.parseResponse(response.body())
        }

    fun thumbnailUrl(id: Long): String = apiUrl("media", id.toString(), "thumbnail")

    fun streamUrl(id: Long): String = apiUrl("media", id.toString(), "stream")

    fun downloadUrl(id: Long): String = apiUrl("media", id.toString(), "download")

    fun liveStreamUrl(): String = apiUrl("live.mjpeg")

    fun liveH264WebSocketUrl(): String =
        rawUrl("ws", "live", "h264")
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://")

    private fun apiUrl(vararg segments: String): String {
        return rawUrl("api", *segments)
    }

    private fun rawUrl(vararg segments: String): String {
        val url = Url(baseUrl.trimEnd('/'))
        return io.ktor.http.URLBuilder(url)
            .appendPathSegments(segments.toList())
            .buildString()
    }

    private suspend fun HttpResponse.ensureSuccess() {
        when (status) {
            HttpStatusCode.OK,
            HttpStatusCode.PartialContent,
            -> return

            HttpStatusCode.Unauthorized -> throw RemoteClientException("Unauthorized")
            HttpStatusCode.NotFound -> throw RemoteClientException("Not found")
            else -> throw RemoteClientException("HTTP ${status.value}")
        }
    }

    private suspend fun <T> request(block: suspend () -> T): DashCamResult<T> =
        runCatching { block() }.fold(
            onSuccess = { DashCamResult.Success(it) },
            onFailure = { DashCamResult.Failure(DashCamError.Unknown(it.message ?: "Remote request failed")) },
        )

    companion object {
        fun defaultHttpClient(): HttpClient =
            HttpClient(OkHttp) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 5_000L
                    connectTimeoutMillis = 3_000L
                    socketTimeoutMillis = 5_000L
                }
            }
    }
}

private class RemoteClientException(message: String) : RuntimeException(message)
