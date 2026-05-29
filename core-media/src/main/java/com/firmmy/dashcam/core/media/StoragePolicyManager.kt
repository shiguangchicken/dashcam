package com.firmmy.dashcam.core.media

import com.firmmy.dashcam.core.common.DashCamError
import com.firmmy.dashcam.core.common.DashCamResult
import java.io.File
import java.time.Instant

data class StoragePolicyConfig(
    val maxStorageBytes: Long,
    val minFreeBytes: Long,
    val allowDeleteParkingVideos: Boolean = true,
)

data class StoragePolicyResult(
    val deletedCount: Int,
    val freedBytes: Long,
    val dashCamBytes: Long,
    val freeBytes: Long,
    val exhaustedCandidates: Boolean,
)

class StoragePolicyManager(
    private val mediaRepository: DashCamMediaRepository,
    private val directories: DashCamMediaDirectories,
    private val freeSpaceProvider: (File) -> Long = { it.usableSpace },
    private val now: () -> Instant = Instant::now,
) {
    suspend fun enforce(config: StoragePolicyConfig): DashCamResult<StoragePolicyResult> =
        runCatching {
            val paths = directories.ensureBaseDirectories()
            var dashCamBytes = paths.root.directorySize()
            var freeBytes = freeSpaceProvider(paths.root)
            var deletedCount = 0
            var freedBytes = 0L
            var exhausted = false

            while (dashCamBytes > config.maxStorageBytes || freeBytes < config.minFreeBytes) {
                val candidate = mediaRepository
                    .deletionCandidates(limit = 1, allowParking = config.allowDeleteParkingVideos)
                    .firstOrNull()
                if (candidate == null) {
                    exhausted = true
                    break
                }
                when (val deleted = mediaRepository.deleteMedia(candidate.id)) {
                    is DashCamResult.Success -> {
                        deletedCount++
                        freedBytes += deleted.value.bytesFreed
                        writeLog("deleted id=${candidate.id} path=${candidate.path} bytes=${deleted.value.bytesFreed}")
                    }

                    is DashCamResult.Failure -> {
                        writeLog("failed id=${candidate.id} path=${candidate.path} reason=${deleted.error.message}")
                        return DashCamResult.Failure(deleted.error)
                    }
                }
                dashCamBytes = paths.root.directorySize()
                freeBytes = freeSpaceProvider(paths.root)
            }

            StoragePolicyResult(
                deletedCount = deletedCount,
                freedBytes = freedBytes,
                dashCamBytes = dashCamBytes,
                freeBytes = freeBytes,
                exhaustedCandidates = exhausted,
            )
        }.fold(
            onSuccess = { DashCamResult.Success(it) },
            onFailure = { DashCamResult.Failure(DashCamError.Unknown(it.message ?: "Storage policy failed")) },
        )

    private fun writeLog(message: String) {
        val logFile = directories.ensureBaseDirectories().logs.resolve("storage_policy.log")
        logFile.appendText("${now().toEpochMilli()} $message\n")
    }

    private fun File.directorySize(): Long =
        if (!exists()) {
            0L
        } else {
            walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }
        }
}
