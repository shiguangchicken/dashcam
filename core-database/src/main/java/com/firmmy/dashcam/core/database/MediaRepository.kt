package com.firmmy.dashcam.core.database

import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import kotlinx.coroutines.flow.Flow

class MediaRepository(
    private val dao: MediaFileDao,
) {
    suspend fun addMediaFile(entity: MediaFileEntity): Long = dao.insert(entity)

    suspend fun getMediaFile(id: Long): MediaFileEntity? = dao.getById(id)

    fun observeMediaFiles(includeDeleted: Boolean = false): Flow<List<MediaFileEntity>> =
        dao.observeAll(includeDeleted)

    fun observeMediaByType(type: MediaType): Flow<List<MediaFileEntity>> =
        dao.observeByType(type.storedValue)

    fun observeMediaByMode(mode: RecordingMode): Flow<List<MediaFileEntity>> =
        dao.observeByMode(mode.storedValue)

    suspend fun markDeleted(id: Long): Boolean = dao.markDeleted(id) > 0

    suspend fun setLocked(id: Long, locked: Boolean): Boolean = dao.setLocked(id, locked) > 0

    suspend fun oldestDeletionCandidates(limit: Int): List<MediaFileEntity> =
        dao.oldestDeletionCandidates(limit)
}
