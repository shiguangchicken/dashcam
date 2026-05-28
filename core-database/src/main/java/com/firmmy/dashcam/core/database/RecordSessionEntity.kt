package com.firmmy.dashcam.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "record_session",
    indices = [
        Index(value = ["started_at"]),
        Index(value = ["mode"]),
    ],
)
data class RecordSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    @ColumnInfo(name = "mode") val mode: String,
    @ColumnInfo(name = "reason") val reason: String? = null,
)
