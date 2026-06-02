package com.firmmy.dashcam.core.database

import com.firmmy.dashcam.core.common.DeviceRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsRepositoryTest {
    @Test
    fun emptySettingsReturnDefaults() = runBlocking {
        val repository = SettingsRepository(FakeAppSettingDao())

        val settings = repository.getSettings()

        assertNull(settings.deviceRole)
        assertEquals("1920x1080", settings.drivingResolution)
        assertEquals(30, settings.drivingFps)
        assertEquals(12000, settings.drivingBitrateKbps)
        assertEquals("1280x720", settings.parkingResolution)
        assertEquals(2, settings.parkingFps)
        assertEquals(1000, settings.parkingBitrateKbps)
        assertEquals(3, settings.segmentDurationMinutes)
        assertEquals(32, settings.maxStorageGb)
        assertEquals(2, settings.minFreeSpaceGb)
        assertEquals(true, settings.audioEnabled)
        assertEquals(false, settings.voiceWakeupEnabled)
        assertEquals("小行车", settings.wakeWord)
    }

    @Test
    fun invalidValuesFallBackToDefaults() = runBlocking {
        val dao = FakeAppSettingDao(
            AppSettingEntity(SettingKeys.DEVICE_ROLE, "bad"),
            AppSettingEntity(SettingKeys.DRIVING_RESOLUTION, "640x480"),
            AppSettingEntity(SettingKeys.DRIVING_FPS, "24"),
            AppSettingEntity(SettingKeys.DRIVING_BITRATE_KBPS, "abc"),
            AppSettingEntity(SettingKeys.PARKING_FPS, "30"),
            AppSettingEntity(SettingKeys.SEGMENT_DURATION_MINUTES, "2"),
            AppSettingEntity(SettingKeys.AUDIO_ENABLED, "yes"),
            AppSettingEntity(SettingKeys.WAKE_WORD, ""),
        )
        val repository = SettingsRepository(dao)

        val settings = repository.getSettings()

        assertNull(settings.deviceRole)
        assertEquals(SettingsDefaults.DRIVING_RESOLUTION, settings.drivingResolution)
        assertEquals(SettingsDefaults.DRIVING_FPS, settings.drivingFps)
        assertEquals(SettingsDefaults.DRIVING_BITRATE_KBPS, settings.drivingBitrateKbps)
        assertEquals(SettingsDefaults.PARKING_FPS, settings.parkingFps)
        assertEquals(SettingsDefaults.SEGMENT_DURATION_MINUTES, settings.segmentDurationMinutes)
        assertEquals(SettingsDefaults.AUDIO_ENABLED, settings.audioEnabled)
        assertEquals(SettingsDefaults.WAKE_WORD, settings.wakeWord)
    }

    @Test
    fun saveSettingsNormalizesBeforeWriting() = runBlocking {
        val dao = FakeAppSettingDao()
        val repository = SettingsRepository(dao)

        repository.saveSettings(
            DashCamSettings(
                deviceRole = DeviceRole.REMOTE,
                drivingResolution = "bad",
                drivingFps = 15,
                wakeWord = "",
                audioEnabled = false,
                pairingToken = "token-1",
                pairingCode = "123456",
            ),
        )

        val saved = repository.getSettings()
        assertEquals(DeviceRole.REMOTE, saved.deviceRole)
        assertEquals(SettingsDefaults.DRIVING_RESOLUTION, saved.drivingResolution)
        assertEquals(SettingsDefaults.DRIVING_FPS, saved.drivingFps)
        assertEquals(SettingsDefaults.WAKE_WORD, saved.wakeWord)
        assertEquals(false, saved.audioEnabled)
        assertEquals("token-1", saved.pairingToken)
        assertEquals("123456", saved.pairingCode)
    }

    @Test
    fun saveDeviceRoleWritesRoleOnly() = runBlocking {
        val repository = SettingsRepository(FakeAppSettingDao())

        repository.saveDeviceRole(DeviceRole.RECORDER)

        assertEquals(DeviceRole.RECORDER, repository.getDeviceRole())
    }
}

private class FakeAppSettingDao(
    vararg initialSettings: AppSettingEntity,
) : AppSettingDao {
    private val settings = initialSettings.associateBy { it.key }.toMutableMap()

    override suspend fun get(key: String): AppSettingEntity? = settings[key]

    override suspend fun getAll(): List<AppSettingEntity> = settings.values.toList()

    override fun observeAll(): Flow<List<AppSettingEntity>> = flowOf(settings.values.toList())

    override suspend fun upsert(entity: AppSettingEntity) {
        settings[entity.key] = entity
    }

    override suspend fun upsertAll(entities: List<AppSettingEntity>) {
        entities.forEach { settings[it.key] = it }
    }
}
