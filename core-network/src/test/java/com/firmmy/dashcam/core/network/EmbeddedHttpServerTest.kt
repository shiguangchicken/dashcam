package com.firmmy.dashcam.core.network

import com.firmmy.dashcam.core.common.DashCamCommand
import com.firmmy.dashcam.core.common.DashCamResult
import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedHttpServerTest {
    private val client = HttpClient(CIO) {
        install(WebSockets)
    }
    private val dataSource = FakeRemoteDataSource()
    private val dispatcher = FakeRemoteCommandDispatcher()
    private val server = EmbeddedHttpServer(
        dataSource = dataSource,
        commandDispatcher = dispatcher,
        host = "127.0.0.1",
        port = 0,
    )

    @After
    fun tearDown() {
        server.stop()
        client.close()
    }

    @Test
    fun statusIsAvailableOnLocalNetwork() = runBlocking {
        server.start()

        val response = client.get(baseUrl() + "/api/status")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun servesStatusMediaRangeAndSettings() = runBlocking {
        server.start()

        val status = getPath("/api/status").bodyAsText()
        assertEquals(RecordingStatus.RECORDING_DRIVING, RemoteJson.parseStatus(status).recordingStatus)

        val media = getPath("/api/media?type=video").bodyAsText()
        assertEquals(1, RemoteJson.parseMediaList(media).size)

        val range = getPath("/api/media/1/stream") {
            header(HttpHeaders.Range, "bytes=1-3")
        }
        assertEquals(HttpStatusCode.PartialContent, range.status)
        assertEquals("bcd", range.bodyAsText())

        val settings = getPath("/api/settings").bodyAsText()
        assertEquals("1920x1080", RemoteJson.parseSettings(settings).drivingResolution)
    }

    @Test
    fun commandAndDeleteCallDependencies() = runBlocking {
        server.start()

        val command = client.post(baseUrl() + "/api/command") {
            setBody(RemoteJson.commandBody(DashCamCommand.TakePhoto))
        }
        val deleted = client.delete(baseUrl() + "/api/media/1")

        assertEquals(HttpStatusCode.OK, command.status)
        assertEquals(DashCamCommand.TakePhoto, dispatcher.commands.single())
        assertEquals(HttpStatusCode.OK, deleted.status)
        assertTrue(dataSource.deletedIds.contains(1L))
    }

    @Test
    fun websocketSendsInitialStatus() = runBlocking {
        server.start()

        client.webSocket(baseUrl().replace("http://", "ws://") + "/ws/events") {
            val frame = withTimeout(2_000L) { incoming.receive() } as Frame.Text
            assertTrue(frame.readText().contains("\"type\":\"status\""))
        }
    }

    @Test
    fun remoteClientMapsApiResponses() = runBlocking {
        server.start()
        val remoteClient = RemoteDashCamClient(
            baseUrl = baseUrl(),
            httpClient = client,
        )

        val status = remoteClient.status()
        val media = remoteClient.media(MediaType.VIDEO)
        val command = remoteClient.sendCommand(DashCamCommand.StartParkingMode)

        assertTrue(status is DashCamResult.Success)
        assertTrue(media is DashCamResult.Success)
        assertTrue(command is DashCamResult.Success)
        assertEquals(DashCamCommand.StartParkingMode, dispatcher.commands.last())
    }

    @Test
    fun remoteClientAssetUrlsUsePlainLocalPaths() = runBlocking {
        server.start()
        val remoteClient = RemoteDashCamClient(
            baseUrl = baseUrl(),
            httpClient = client,
        )

        assertTrue(remoteClient.streamUrl(1).endsWith("/api/media/1/stream"))
        assertTrue(remoteClient.thumbnailUrl(1).endsWith("/api/media/1/thumbnail"))
        assertTrue(remoteClient.downloadUrl(1).endsWith("/api/media/1/download"))
        assertTrue(remoteClient.liveStreamUrl().endsWith("/api/live.mjpeg"))
    }

    @Test
    fun livePreviewUnavailableReturnsServiceUnavailable() = runBlocking {
        server.start()

        val response = getPath("/api/live.mjpeg")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }

    @Test
    fun livePreviewStreamsMultipartJpegFrames() = runBlocking {
        dataSource.liveFrame = byteArrayOf(0x01, 0x02, 0x03)
        server.start()

        val response = getPath("/api/live.mjpeg")
        val body = withTimeout(2_000L) { response.readRawBytes() }.decodeToString()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("--dashcam-frame"))
        assertTrue(body.contains("Content-Type: image/jpeg"))
    }

    private suspend fun baseUrl(): String = "http://127.0.0.1:${server.resolvedPort()}"

    private suspend fun getPath(
        path: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ) = client.get(baseUrl() + path) {
        block()
    }
}

private class FakeRemoteCommandDispatcher : DashCamRemoteCommandDispatcher {
    val commands = mutableListOf<DashCamCommand>()

    override suspend fun dispatch(command: DashCamCommand): Boolean {
        commands += command
        return true
    }
}

private class FakeRemoteDataSource : DashCamRemoteDataSource {
    private val tempDir = Files.createTempDirectory("dashcam-remote-test").toFile()
    private val mediaFile = tempDir.resolve("video.mp4").also { it.writeText("abcde") }
    private val thumbnail = tempDir.resolve("thumb.jpg").also { it.writeText("jpg") }

    val deletedIds = mutableListOf<Long>()
    var liveFrame: ByteArray? = null

    override suspend fun status(): RemoteStatus =
        RemoteStatus(
            recordingStatus = RecordingStatus.RECORDING_DRIVING,
            mode = RecordingMode.DRIVING,
            audioEnabled = true,
            hotspotEnabled = true,
            hotspotSsid = "DashCam",
            freeSpaceBytes = 123L,
            liveStreamAvailable = liveFrame != null,
        )

    override suspend fun listMedia(
        type: MediaType?,
        date: String?,
    ): List<RemoteMediaItem> =
        listOf(
            RemoteMediaItem(
                id = 1L,
                type = MediaType.VIDEO,
                mode = RecordingMode.DRIVING,
                path = mediaFile.absolutePath,
                thumbnailPath = thumbnail.absolutePath,
                createdAt = 100L,
                sizeBytes = mediaFile.length(),
                hasAudio = true,
            ),
        ).filter { type == null || it.type == type }

    override suspend fun mediaThumbnail(id: Long): RemoteMediaAsset? =
        if (id == 1L) RemoteMediaAsset(thumbnail, "image/jpeg") else null

    override suspend fun mediaStream(id: Long): RemoteMediaAsset? =
        if (id == 1L) RemoteMediaAsset(mediaFile, "video/mp4") else null

    override suspend fun livePreviewFrame(): ByteArray? = liveFrame.also { liveFrame = null }

    override suspend fun deleteMedia(id: Long): Boolean {
        deletedIds += id
        return id == 1L
    }

    override suspend fun settings(): RemoteSettings =
        RemoteSettings(
            drivingResolution = "1920x1080",
            drivingFps = 30,
            drivingBitrateKbps = 12000,
            parkingResolution = "1280x720",
            parkingFps = 2,
            parkingBitrateKbps = 1000,
            segmentDurationMinutes = 3,
            maxStorageGb = 32,
            minFreeSpaceGb = 2,
            audioEnabled = true,
            voiceWakeupEnabled = false,
            wakeWord = "小行车",
        )

    override suspend fun saveSettings(settings: RemoteSettings): Boolean = true
}
