package com.firmmy.dashcam.core.database

import android.content.Context
import androidx.room.Room

object DashCamDatabaseProvider {
    @Volatile
    private var instance: DashCamDatabase? = null

    fun get(context: Context): DashCamDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                DashCamDatabase::class.java,
                "dashcam.db",
            )
                .addMigrations(*DashCamMigrations.ALL)
                .build()
                .also { instance = it }
        }
}
