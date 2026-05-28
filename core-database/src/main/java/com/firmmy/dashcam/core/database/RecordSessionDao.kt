package com.firmmy.dashcam.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecordSessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: RecordSessionEntity): Long

    @Query("SELECT * FROM record_session WHERE id = :id")
    suspend fun getById(id: Long): RecordSessionEntity?

    @Query("UPDATE record_session SET ended_at = :endedAt, reason = :reason WHERE id = :id")
    suspend fun finishSession(id: Long, endedAt: Long, reason: String?): Int
}
