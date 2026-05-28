package com.firmmy.dashcam.core.media

import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DashCamMediaDirectoriesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun createsRequiredAppSpecificDirectoryStructure() {
        val directories = DashCamMediaDirectories(temporaryFolder.root)

        val paths = directories.ensureBaseDirectories()

        assertTrue(paths.drivingVideos.isDirectory)
        assertTrue(paths.parkingVideos.isDirectory)
        assertTrue(paths.lockedVideos.isDirectory)
        assertTrue(paths.photos.isDirectory)
        assertTrue(paths.thumbnails.isDirectory)
        assertTrue(paths.logs.isDirectory)
    }

    @Test
    fun createsDatedMediaNamesWithMonotonicVideoSequence() {
        val directories = DashCamMediaDirectories(temporaryFolder.root, ZoneId.of("UTC"))
        val createdAt = Instant.parse("2026-05-28T10:15:30Z")

        val firstVideo = directories.nextVideoFile(RecordingMode.DRIVING, createdAt)
        firstVideo.writeBytes(byteArrayOf(1))
        val secondVideo = directories.nextVideoFile(RecordingMode.DRIVING, createdAt)
        val parkingVideo = directories.nextVideoFile(RecordingMode.PARKING, createdAt)
        val lockedVideo = directories.nextVideoFile(RecordingMode.EVENT, createdAt, locked = true)
        val photo = directories.photoFile(createdAt)
        val thumbnail = directories.thumbnailFile(MediaType.VIDEO, firstVideo, createdAt)

        assertEquals(
            "videos/driving/2026-05-28/20260528_101530_001.mp4",
            firstVideo.relativeTo(temporaryFolder.root).invariantSeparatorsPath,
        )
        assertEquals(
            "videos/driving/2026-05-28/20260528_101530_002.mp4",
            secondVideo.relativeTo(temporaryFolder.root).invariantSeparatorsPath,
        )
        assertEquals(
            "videos/parking/2026-05-28/20260528_101530_001.mp4",
            parkingVideo.relativeTo(temporaryFolder.root).invariantSeparatorsPath,
        )
        assertEquals(
            "videos/locked/2026-05-28/20260528_101530_001.mp4",
            lockedVideo.relativeTo(temporaryFolder.root).invariantSeparatorsPath,
        )
        assertEquals(
            "photos/2026-05-28/20260528_101530.jpg",
            photo.relativeTo(temporaryFolder.root).invariantSeparatorsPath,
        )
        assertEquals(
            "thumbnails/2026-05-28/video_20260528_101530_001.jpg",
            thumbnail.relativeTo(temporaryFolder.root).invariantSeparatorsPath,
        )
    }
}
