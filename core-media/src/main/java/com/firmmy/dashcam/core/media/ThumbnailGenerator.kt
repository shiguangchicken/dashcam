package com.firmmy.dashcam.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.firmmy.dashcam.core.common.DashCamError
import com.firmmy.dashcam.core.common.DashCamResult
import java.io.File
import kotlin.math.max

interface ThumbnailGenerator {
    suspend fun createImageThumbnail(
        source: File,
        destination: File,
        maxSizePx: Int = DEFAULT_THUMBNAIL_SIZE_PX,
    ): DashCamResult<File>

    suspend fun createVideoThumbnail(
        source: File,
        destination: File,
        maxSizePx: Int = DEFAULT_THUMBNAIL_SIZE_PX,
    ): DashCamResult<File> =
        DashCamResult.Failure(DashCamError.Unknown("Video thumbnail generation is not available"))

    companion object {
        const val DEFAULT_THUMBNAIL_SIZE_PX = 512
    }
}

class AndroidThumbnailGenerator : ThumbnailGenerator {
    override suspend fun createImageThumbnail(
        source: File,
        destination: File,
        maxSizePx: Int,
    ): DashCamResult<File> =
        runCatching {
            val bitmap = BitmapFactory.decodeFile(source.absolutePath)
                ?: return DashCamResult.Failure(DashCamError.Unknown("Could not decode image thumbnail"))
            writeScaledThumbnail(bitmap, destination, maxSizePx)
        }.fold(
            onSuccess = { DashCamResult.Success(destination) },
            onFailure = { DashCamResult.Failure(DashCamError.Unknown(it.message ?: "Image thumbnail failed")) },
        )

    override suspend fun createVideoThumbnail(
        source: File,
        destination: File,
        maxSizePx: Int,
    ): DashCamResult<File> =
        runCatching {
            val retriever = MediaMetadataRetriever()
            val bitmap = try {
                retriever.setDataSource(source.absolutePath)
                retriever.frameAtTime
                    ?: return DashCamResult.Failure(DashCamError.Unknown("Could not decode video thumbnail"))
            } finally {
                retriever.release()
            }
            writeScaledThumbnail(bitmap, destination, maxSizePx)
        }.fold(
            onSuccess = { DashCamResult.Success(destination) },
            onFailure = { DashCamResult.Failure(DashCamError.Unknown(it.message ?: "Video thumbnail failed")) },
        )

    private fun writeScaledThumbnail(
        bitmap: Bitmap,
        destination: File,
        maxSizePx: Int,
    ) {
        destination.parentFile?.mkdirs()
        val largestSide = max(bitmap.width, bitmap.height).coerceAtLeast(1)
        val scale = maxSizePx.toFloat() / largestSide
        val width = max(1, (bitmap.width * scale).toInt())
        val height = max(1, (bitmap.height * scale).toInt())
        val thumbnail = Bitmap.createScaledBitmap(bitmap, width, height, true)
        destination.outputStream().use { output ->
            thumbnail.compress(Bitmap.CompressFormat.JPEG, 82, output)
        }
        if (thumbnail !== bitmap) {
            thumbnail.recycle()
        }
        bitmap.recycle()
    }
}
