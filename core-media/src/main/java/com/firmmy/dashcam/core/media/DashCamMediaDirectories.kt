package com.firmmy.dashcam.core.media

import android.content.Context
import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

data class DashCamMediaPaths(
    val root: File,
    val drivingVideos: File,
    val parkingVideos: File,
    val lockedVideos: File,
    val photos: File,
    val thumbnails: File,
    val logs: File,
)

class DashCamMediaDirectories(
    private val rootDirectory: File,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun ensureBaseDirectories(): DashCamMediaPaths {
        val paths = DashCamMediaPaths(
            root = rootDirectory,
            drivingVideos = rootDirectory.resolve("videos/driving"),
            parkingVideos = rootDirectory.resolve("videos/parking"),
            lockedVideos = rootDirectory.resolve("videos/locked"),
            photos = rootDirectory.resolve("photos"),
            thumbnails = rootDirectory.resolve("thumbnails"),
            logs = rootDirectory.resolve("logs"),
        )
        listOf(
            paths.drivingVideos,
            paths.parkingVideos,
            paths.lockedVideos,
            paths.photos,
            paths.thumbnails,
            paths.logs,
        ).forEach { it.mkdirs() }
        return paths
    }

    fun nextVideoFile(
        mode: RecordingMode,
        createdAt: Instant,
        locked: Boolean = false,
    ): File {
        val baseDirectory = videoBaseDirectory(mode = mode, locked = locked)
        val dateDirectory = datedDirectory(baseDirectory, createdAt)
        val sequence = nextSequence(dateDirectory)
        return dateDirectory.resolve(
            "${timestampFormatter.format(createdAt.atZone(zoneId))}_${sequence.formatSequence()}.mp4",
        )
    }

    fun photoFile(createdAt: Instant): File {
        val dateDirectory = datedDirectory(ensureBaseDirectories().photos, createdAt)
        return dateDirectory.resolve("${timestampFormatter.format(createdAt.atZone(zoneId))}.jpg")
    }

    fun thumbnailFile(
        mediaType: MediaType,
        sourceFile: File,
        createdAt: Instant,
    ): File {
        val dateDirectory = datedDirectory(ensureBaseDirectories().thumbnails, createdAt)
        val prefix = when (mediaType) {
            MediaType.VIDEO -> "video"
            MediaType.PHOTO -> "photo"
        }
        return dateDirectory.resolve("${prefix}_${sourceFile.nameWithoutExtension}.jpg")
    }

    private fun videoBaseDirectory(
        mode: RecordingMode,
        locked: Boolean,
    ): File {
        val paths = ensureBaseDirectories()
        return when {
            locked || mode == RecordingMode.EVENT -> paths.lockedVideos
            mode == RecordingMode.PARKING -> paths.parkingVideos
            else -> paths.drivingVideos
        }
    }

    private fun datedDirectory(
        baseDirectory: File,
        createdAt: Instant,
    ): File =
        baseDirectory
            .resolve(dateFormatter.format(createdAt.atZone(zoneId)))
            .also { it.mkdirs() }

    private fun nextSequence(dateDirectory: File): Int {
        val maxSequence = dateDirectory.listFiles()
            ?.asSequence()
            ?.mapNotNull { sequencePattern.find(it.nameWithoutExtension)?.groupValues?.get(1)?.toIntOrNull() }
            ?.maxOrNull()
            ?: 0
        return max(1, maxSequence + 1)
    }

    private fun Int.formatSequence(): String = String.format(Locale.US, "%03d", this)

    companion object {
        private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
        private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)
        private val sequencePattern = Regex("_(\\d{3})$")

        fun fromContext(context: Context): DashCamMediaDirectories {
            val externalRoot = context.getExternalFilesDir(null) ?: context.filesDir
            return DashCamMediaDirectories(externalRoot.resolve("DashCam"))
        }
    }
}
