package com.firmmy.dashcam.core.database

import com.firmmy.dashcam.core.common.RecordingMode

class RecordSessionRepository(
    private val dao: RecordSessionDao,
) {
    suspend fun startSession(startedAt: Long, mode: RecordingMode): Long =
        dao.insert(
            RecordSessionEntity(
                startedAt = startedAt,
                mode = mode.storedValue,
            ),
        )

    suspend fun getSession(id: Long): RecordSessionEntity? = dao.getById(id)

    suspend fun finishSession(id: Long, endedAt: Long, reason: String? = null): Boolean =
        dao.finishSession(id, endedAt, reason) > 0
}
