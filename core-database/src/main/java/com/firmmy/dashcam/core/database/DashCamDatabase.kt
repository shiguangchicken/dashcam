package com.firmmy.dashcam.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaFileEntity::class,
        RecordSessionEntity::class,
        AppSettingEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class DashCamDatabase : RoomDatabase() {
    abstract fun mediaFileDao(): MediaFileDao
    abstract fun recordSessionDao(): RecordSessionDao
    abstract fun appSettingDao(): AppSettingDao
}
