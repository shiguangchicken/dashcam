package com.firmmy.dashcam.core.network

import com.firmmy.dashcam.core.common.MediaType
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.flow.collectLatest

class EmbeddedHttpServer(
    private val dataSource: DashCamRemoteDataSource,
    private val commandDispatcher: DashCamRemoteCommandDispatcher,
    private val tokenProvider: DashCamTokenProvider,
    private val eventBus: RemoteEventBus = RemoteEventBus(),
    private val host: String = DEFAULT_HOST,
    private val port: Int = DEFAULT_PORT,
) {
    private var engine: EmbeddedServer<*, *>? = null

    fun start(wait: Boolean = false) {
        if (engine != null) return
        engine = embeddedServer(CIO, host = host, port = port) {
            install(WebSockets)
            configureRoutes()
        }.start(wait = wait)
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 500L, timeoutMillis = 1_500L)
        engine = null
    }

    suspend fun resolvedPort(): Int? =
        engine?.engine?.resolvedConnectors()?.firstOrNull()?.port

    fun events(): RemoteEventBus = eventBus

    private fun Application.configureRoutes() {
        routing {
            get("/api/status") {
                if (!call.requireAuth()) return@get
                call.respondJson(RemoteJson.status(dataSource.status()))
            }

            post("/api/command") {
                if (!call.requireAuth()) return@post
                val command = runCatching { RemoteJson.parseCommand(call.receiveText()) }.getOrNull()
                    ?: return@post call.respondJson(
                        RemoteJson.response(ok = false, message = "Invalid command"),
                        HttpStatusCode.BadRequest,
                    )
                val ok = commandDispatcher.dispatch(command)
                eventBus.tryEmit(RemoteEvent.CommandHandled(command, ok))
                call.respondJson(RemoteJson.response(ok = ok, message = if (ok) "OK" else "Command failed"))
            }

            get("/api/media") {
                if (!call.requireAuth()) return@get
                val type = call.request.queryParameters["type"]?.let(MediaType::fromStoredValue)
                val date = call.request.queryParameters["date"]
                call.respondJson(RemoteJson.mediaList(dataSource.listMedia(type, date)))
            }

            get("/api/media/{id}/thumbnail") {
                if (!call.requireAuth()) return@get
                val id = call.mediaId() ?: return@get
                call.respondAsset(dataSource.mediaThumbnail(id), inline = true)
            }

            get("/api/media/{id}/stream") {
                if (!call.requireAuth()) return@get
                val id = call.mediaId() ?: return@get
                call.respondAsset(dataSource.mediaStream(id), inline = true, range = true)
            }

            get("/api/media/{id}/download") {
                if (!call.requireAuth()) return@get
                val id = call.mediaId() ?: return@get
                call.respondAsset(dataSource.mediaDownload(id), inline = false)
            }

            delete("/api/media/{id}") {
                if (!call.requireAuth()) return@delete
                val id = call.mediaId() ?: return@delete
                val ok = dataSource.deleteMedia(id)
                val status = if (ok) HttpStatusCode.OK else HttpStatusCode.NotFound
                call.respondJson(RemoteJson.response(ok, if (ok) "Deleted" else "Media not found"), status)
            }

            get("/api/settings") {
                if (!call.requireAuth()) return@get
                call.respondJson(RemoteJson.settings(dataSource.settings()))
            }

            put("/api/settings") {
                if (!call.requireAuth()) return@put
                val settings = runCatching { RemoteJson.parseSettings(call.receiveText()) }.getOrNull()
                    ?: return@put call.respondJson(
                        RemoteJson.response(ok = false, message = "Invalid settings"),
                        HttpStatusCode.BadRequest,
                    )
                val ok = dataSource.saveSettings(settings)
                call.respondJson(RemoteJson.response(ok, if (ok) "Saved" else "Settings rejected"))
            }

            webSocket("/ws/events") {
                if (!call.isAuthorized()) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                    return@webSocket
                }
                send(RemoteJson.event(RemoteEvent.StatusChanged(dataSource.status())))
                eventBus.events.collectLatest { event ->
                    send(RemoteJson.event(event))
                }
            }
        }
    }

    private suspend fun io.ktor.server.application.ApplicationCall.respondAsset(
        asset: RemoteMediaAsset?,
        inline: Boolean,
        range: Boolean = false,
    ) {
        if (asset == null || !asset.file.isFile) {
            respondJson(RemoteJson.response(ok = false, message = "Media not found"), HttpStatusCode.NotFound)
            return
        }
        if (!inline) {
            response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    asset.downloadName,
                ).toString(),
            )
        }
        if (range) {
            val handled = respondRange(asset)
            if (handled) return
        }
        respondFile(asset.file)
    }

    private suspend fun io.ktor.server.application.ApplicationCall.respondRange(asset: RemoteMediaAsset): Boolean {
        val rangeHeader = request.header(HttpHeaders.Range) ?: return false
        val range = parseSingleByteRange(rangeHeader, asset.file.length())
            ?: run {
                response.header(HttpHeaders.AcceptRanges, "bytes")
                response.header(HttpHeaders.ContentRange, "bytes */${asset.file.length()}")
                respondJson(RemoteJson.response(ok = false, message = "Invalid range"), HttpStatusCode.RequestedRangeNotSatisfiable)
                return true
            }
        val (start, endInclusive) = range
        val count = (endInclusive - start + 1).toInt()
        val bytes = ByteArray(count)
        asset.file.inputStream().use { input ->
            input.skip(start)
            var offset = 0
            while (offset < count) {
                val read = input.read(bytes, offset, count - offset)
                if (read < 0) break
                offset += read
            }
        }
        response.header(HttpHeaders.AcceptRanges, "bytes")
        response.header(HttpHeaders.ContentRange, "bytes $start-$endInclusive/${asset.file.length()}")
        respondBytes(
            bytes = bytes,
            contentType = ContentType.parse(asset.contentType),
            status = HttpStatusCode.PartialContent,
        )
        return true
    }

    private fun parseSingleByteRange(header: String, size: Long): Pair<Long, Long>? {
        if (!header.startsWith("bytes=") || size <= 0L) return null
        val parts = header.removePrefix("bytes=").split("-", limit = 2)
        if (parts.size != 2) return null
        val start = parts[0].toLongOrNull() ?: return null
        val requestedEnd = parts[1].toLongOrNull() ?: size - 1
        if (start < 0 || start >= size || requestedEnd < start) return null
        return start to requestedEnd.coerceAtMost(size - 1)
    }

    private fun io.ktor.server.application.ApplicationCall.isAuthorized(): Boolean {
        val header = request.header(HttpHeaders.Authorization)
        val queryToken = request.queryParameters["token"]?.let { "Bearer $it" }
        return BearerTokenAuthenticator(tokenProvider::currentToken)
            .authenticate(header ?: queryToken) == AuthResult.Authenticated
    }

    private suspend fun io.ktor.server.application.ApplicationCall.requireAuth(): Boolean {
        if (isAuthorized()) return true
        respondJson(RemoteJson.response(ok = false, message = "Unauthorized"), HttpStatusCode.Unauthorized)
        return false
    }

    private suspend fun io.ktor.server.application.ApplicationCall.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) {
        respondText(body, ContentType.Application.Json, status)
    }

    private suspend fun io.ktor.server.application.ApplicationCall.mediaId(): Long? {
        val id = parameters["id"]?.toLongOrNull()
        if (id == null) {
            respondJson(RemoteJson.response(ok = false, message = "Invalid media id"), HttpStatusCode.BadRequest)
        }
        return id
    }

    companion object {
        const val DEFAULT_HOST = "0.0.0.0"
        const val DEFAULT_PORT = 8080
    }
}
