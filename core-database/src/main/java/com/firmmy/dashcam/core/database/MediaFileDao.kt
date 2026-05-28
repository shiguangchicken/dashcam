package com.firmmy.dashcam.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaFileDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MediaFileEntity): Long

    @Query("SELECT * FROM media_file WHERE id = :id")
    suspend fun getById(id: Long): MediaFileEntity?

    @Query(
        """
        SELECT * FROM media_file
        WHERE deleted = :includeDeleted OR deleted = 0
        ORDER BY created_at DESC
        """,
    )
    fun observeAll(includeDeleted: Boolean = false): Flow<List<MediaFileEntity>>

    @Query(
        """
        SELECT * FROM media_file
        WHERE type = :type AND (deleted = :includeDeleted OR deleted = 0)
        ORDER BY created_at DESC
        """,
    )
    fun observeByType(type: String, includeDeleted: Boolean = false): Flow<List<MediaFileEntity>>

    @Query(
        """
        SELECT * FROM media_file
        WHERE mode = :mode AND (deleted = :includeDeleted OR deleted = 0)
        ORDER BY created_at DESC
        """,
    )
    fun observeByMode(mode: String, includeDeleted: Boolean = false): Flow<List<MediaFileEntity>>

    @Query("UPDATE media_file SET deleted = 1 WHERE id = :id")
    suspend fun markDeleted(id: Long): Int

    @Query("UPDATE media_file SET locked = :locked WHERE id = :id")
    suspend fun setLocked(id: Long, locked: Boolean): Int

    @Query(
        """
        SELECT * FROM media_file
        WHERE locked = 0 AND deleted = 0
        ORDER BY created_at ASC
        LIMIT :limit
        """,
    )
    suspend fun oldestDeletionCandidates(limit: Int): List<MediaFileEntity>
}
