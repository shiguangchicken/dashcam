package com.firmmy.dashcam.core.database

import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryTest {
    @Test
    fun mediaRepositoryInsertsAndUpdatesMedia() = runBlocking {
        val repository = MediaRepository(FakeMediaFileDao())
        val id = repository.addMediaFile(testMedia(createdAt = 100L))

        assertEquals(id, repository.getMediaFile(id)?.id)
        assertTrue(repository.setLocked(id, true))
        assertEquals(true, repository.getMediaFile(id)?.locked)
        assertTrue(repository.markDeleted(id))
        assertEquals(true, repository.getMediaFile(id)?.deleted)
    }

    @Test
    fun mediaRepositoryReturnsOldestUnlockedCandidates() = runBlocking {
        val dao = FakeMediaFileDao()
        val repository = MediaRepository(dao)
        repository.addMediaFile(testMedia(createdAt = 300L))
        repository.addMediaFile(testMedia(createdAt = 100L))
        repository.addMediaFile(testMedia(createdAt = 200L, locked = true))

        val candidates = repository.oldestDeletionCandidates(limit = 2)

        assertEquals(listOf(100L, 300L), candidates.map { it.createdAt })
    }

    @Test
    fun recordSessionRepositoryStartsAndFinishesSession() = runBlocking {
        val repository = RecordSessionRepository(FakeRecordSessionDao())

        val id = repository.startSession(100L, RecordingMode.DRIVING)

        assertEquals(RecordingMode.DRIVING.storedValue, repository.getSession(id)?.mode)
        assertTrue(repository.finishSession(id, 200L, "stopped"))
        val finished = repository.getSession(id)
        assertEquals(200L, finished?.endedAt)
        assertEquals("stopped", finished?.reason)
        assertFalse(repository.finishSession(999L, 300L, null))
    }

    private fun testMedia(
        createdAt: Long,
        locked: Boolean = false,
    ): MediaFileEntity =
        MediaFileEntity(
            type = MediaType.VIDEO.storedValue,
            mode = RecordingMode.DRIVING.storedValue,
            path = "/tmp/$createdAt.mp4",
            createdAt = createdAt,
            sizeBytes = 1024L,
            hasAudio = true,
            locked = locked,
        )
}

private class FakeMediaFileDao : MediaFileDao {
    private val media = mutableMapOf<Long, MediaFileEntity>()
    private var nextId = 1L

    override suspend fun insert(entity: MediaFileEntity): Long {
        val id = nextId++
        media[id] = entity.copy(id = id)
        return id
    }

    override suspend fun getById(id: Long): MediaFileEntity? = media[id]

    override fun observeAll(includeDeleted: Boolean): Flow<List<MediaFileEntity>> =
        flowOf(media.values.filter { includeDeleted || !it.deleted })

    override fun observeByType(type: String, includeDeleted: Boolean): Flow<List<MediaFileEntity>> =
        flowOf(media.values.filter { it.type == type && (includeDeleted || !it.deleted) })

    override fun observeByMode(mode: String, includeDeleted: Boolean): Flow<List<MediaFileEntity>> =
        flowOf(media.values.filter { it.mode == mode && (includeDeleted || !it.deleted) })

    override suspend fun markDeleted(id: Long): Int {
        val entity = media[id] ?: return 0
        media[id] = entity.copy(deleted = true)
        return 1
    }

    override suspend fun setLocked(id: Long, locked: Boolean): Int {
        val entity = media[id] ?: return 0
        media[id] = entity.copy(locked = locked)
        return 1
    }

    override suspend fun oldestDeletionCandidates(limit: Int): List<MediaFileEntity> =
        media.values
            .filter { !it.locked && !it.deleted }
            .sortedBy { it.createdAt }
            .take(limit)
}

private class FakeRecordSessionDao : RecordSessionDao {
    private val sessions = mutableMapOf<Long, RecordSessionEntity>()
    private var nextId = 1L

    override suspend fun insert(entity: RecordSessionEntity): Long {
        val id = nextId++
        sessions[id] = entity.copy(id = id)
        return id
    }

    override suspend fun getById(id: Long): RecordSessionEntity? = sessions[id]

    override suspend fun finishSession(id: Long, endedAt: Long, reason: String?): Int {
        val entity = sessions[id] ?: return 0
        sessions[id] = entity.copy(endedAt = endedAt, reason = reason)
        return 1
    }
}
