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
import io.ktor.server.request.userAgent
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import java.util.concurrent.ConcurrentHashMap

class EmbeddedHttpServer(
    private val dataSource: DashCamRemoteDataSource,
    private val commandDispatcher: DashCamRemoteCommandDispatcher,
    private val eventBus: RemoteEventBus = RemoteEventBus(),
    private val host: String = DEFAULT_HOST,
    private val port: Int = DEFAULT_PORT,
) {
    private var engine: EmbeddedServer<*, *>? = null
    private val viewerTracker = RemoteViewerTracker()

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

    fun activeRemoteViewers(): List<RemoteViewerClientInfo> = viewerTracker.activeViewers()

    private fun Application.configureRoutes() {
        routing {
            get("/api/status") {
                call.trackRemoteViewer()
                call.respondJson(RemoteJson.status(dataSource.status().withActiveViewers()))
            }

            post("/api/command") {
                call.trackRemoteViewer()
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
                call.trackRemoteViewer()
                val type = call.request.queryParameters["type"]?.let(MediaType::fromStoredValue)
                val date = call.request.queryParameters["date"]
                call.respondJson(RemoteJson.mediaList(dataSource.listMedia(type, date)))
            }

            get("/api/media/{id}/thumbnail") {
                call.trackRemoteViewer()
                val id = call.mediaId() ?: return@get
                call.respondAsset(dataSource.mediaThumbnail(id), inline = true)
            }

            get("/api/media/{id}/stream") {
                call.trackRemoteViewer()
                val id = call.mediaId() ?: return@get
                call.respondAsset(dataSource.mediaStream(id), inline = true, range = true)
            }

            get("/api/media/{id}/download") {
                call.trackRemoteViewer()
                val id = call.mediaId() ?: return@get
                call.respondAsset(dataSource.mediaDownload(id), inline = false)
            }

            get("/api/live.mjpeg") {
                call.trackRemoteViewer()
                val firstFrame = dataSource.livePreviewFrame()
                    ?: return@get call.respondJson(
                        RemoteJson.response(ok = false, message = "Live preview unavailable"),
                        HttpStatusCode.ServiceUnavailable,
                    )
                call.respondMjpeg(firstFrame)
            }

            delete("/api/media/{id}") {
                call.trackRemoteViewer()
                val id = call.mediaId() ?: return@delete
                val ok = dataSource.deleteMedia(id)
                val status = if (ok) HttpStatusCode.OK else HttpStatusCode.NotFound
                call.respondJson(RemoteJson.response(ok, if (ok) "Deleted" else "Media not found"), status)
            }

            get("/api/settings") {
                call.trackRemoteViewer()
                call.respondJson(RemoteJson.settings(dataSource.settings()))
            }

            put("/api/settings") {
                call.trackRemoteViewer()
                val settings = runCatching { RemoteJson.parseSettings(call.receiveText()) }.getOrNull()
                    ?: return@put call.respondJson(
                        RemoteJson.response(ok = false, message = "Invalid settings"),
                        HttpStatusCode.BadRequest,
                    )
                val ok = dataSource.saveSettings(settings)
                call.respondJson(RemoteJson.response(ok, if (ok) "Saved" else "Settings rejected"))
            }

            webSocket("/ws/events") {
                call.trackRemoteViewer()
                send(RemoteJson.event(RemoteEvent.StatusChanged(dataSource.status().withActiveViewers())))
                eventBus.events.collectLatest { event ->
                    send(RemoteJson.event(event))
                }
            }

            webSocket("/ws/live/h264") {
                call.trackRemoteViewer()
                val stream = dataSource.liveH264Stream()
                    ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "H.264 live stream unavailable"))
                var configSent = false
                stream.subscribe().collect { event ->
                    when (event) {
                        is H264LiveStreamEvent.Config -> {
                            send(event.value.toJson())
                            configSent = true
                        }

                        is H264LiveStreamEvent.Frame -> {
                            if (!configSent && !event.value.isKeyframe) return@collect
                            if (!configSent) {
                                stream.currentConfig()?.let { config ->
                                    send(config.toJson())
                                    configSent = true
                                } ?: return@collect
                            }
                            outgoing.send(Frame.Binary(fin = true, data = event.value.toWebSocketPayload()))
                        }
                    }
                }
            }
        }
    }

    private fun io.ktor.server.application.ApplicationCall.trackRemoteViewer() {
        viewerTracker.markSeen(
            remoteHost = request.local.remoteHost,
            userAgent = request.userAgent(),
        )
    }

    private fun RemoteStatus.withActiveViewers(): RemoteStatus =
        copy(remoteViewers = viewerTracker.activeViewers())

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

    private suspend fun io.ktor.server.application.ApplicationCall.respondMjpeg(firstFrame: ByteArray) {
        respondOutputStream(
            contentType = ContentType.parse("multipart/x-mixed-replace; boundary=$MJPEG_BOUNDARY"),
            status = HttpStatusCode.OK,
        ) {
            var frame = firstFrame
            while (kotlin.coroutines.coroutineContext.isActive) {
                write("--$MJPEG_BOUNDARY\r\n".toByteArray())
                write("Content-Type: image/jpeg\r\n".toByteArray())
                write("Content-Length: ${frame.size}\r\n\r\n".toByteArray())
                write(frame)
                write("\r\n".toByteArray())
                flush()
                delay(MJPEG_FRAME_INTERVAL_MS)
                frame = dataSource.livePreviewFrame() ?: break
            }
        }
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
        private const val MJPEG_BOUNDARY = "dashcam-frame"
        private const val MJPEG_FRAME_INTERVAL_MS = 200L
    }
}

private class RemoteViewerTracker(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val viewers = ConcurrentHashMap<String, RemoteViewerClientInfo>()

    fun markSeen(
        remoteHost: String,
        userAgent: String?,
    ) {
        val now = clock()
        expire(now)
        viewers[remoteHost] = RemoteViewerClientInfo(
            id = remoteHost,
            name = userAgent?.takeIf { it.isNotBlank() }?.substringBefore(" ") ?: "Remote viewer",
            lastSeenEpochMillis = now,
        )
    }

    fun activeViewers(): List<RemoteViewerClientInfo> {
        val now = clock()
        expire(now)
        return viewers.values.sortedByDescending { it.lastSeenEpochMillis }
    }

    private fun expire(now: Long) {
        viewers.entries.removeIf { (_, viewer) ->
            now - viewer.lastSeenEpochMillis > VIEWER_TTL_MS
        }
    }

    companion object {
        private const val VIEWER_TTL_MS = 30_000L
    }
}
