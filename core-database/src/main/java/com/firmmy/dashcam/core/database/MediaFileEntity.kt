package com.firmmy.dashcam.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_file",
    indices = [
        Index(value = ["type"]),
        Index(value = ["mode"]),
        Index(value = ["created_at"]),
        Index(value = ["deleted"]),
    ],
)
data class MediaFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "mode") val mode: String,
    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "width") val width: Int? = null,
    @ColumnInfo(name = "height") val height: Int? = null,
    @ColumnInfo(name = "fps") val fps: Double? = null,
    @ColumnInfo(name = "bitrate") val bitrate: Int? = null,
    @ColumnInfo(name = "has_audio") val hasAudio: Boolean,
    @ColumnInfo(name = "locked", defaultValue = "0") val locked: Boolean = false,
    @ColumnInfo(name = "checksum") val checksum: String? = null,
    @ColumnInfo(name = "deleted", defaultValue = "0") val deleted: Boolean = false,
)
