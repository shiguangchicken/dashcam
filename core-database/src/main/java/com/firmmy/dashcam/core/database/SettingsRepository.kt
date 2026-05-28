package com.firmmy.dashcam.core.database

import com.firmmy.dashcam.core.common.DeviceRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class DashCamSettings(
    val deviceRole: DeviceRole? = null,
    val drivingResolution: String = SettingsDefaults.DRIVING_RESOLUTION,
    val drivingFps: Int = SettingsDefaults.DRIVING_FPS,
    val drivingBitrateKbps: Int = SettingsDefaults.DRIVING_BITRATE_KBPS,
    val parkingResolution: String = SettingsDefaults.PARKING_RESOLUTION,
    val parkingFps: Int = SettingsDefaults.PARKING_FPS,
    val parkingBitrateKbps: Int = SettingsDefaults.PARKING_BITRATE_KBPS,
    val segmentDurationMinutes: Int = SettingsDefaults.SEGMENT_DURATION_MINUTES,
    val maxStorageGb: Int = SettingsDefaults.MAX_STORAGE_GB,
    val minFreeSpaceGb: Int = SettingsDefaults.MIN_FREE_SPACE_GB,
    val audioEnabled: Boolean = SettingsDefaults.AUDIO_ENABLED,
    val voiceWakeupEnabled: Boolean = SettingsDefaults.VOICE_WAKEUP_ENABLED,
    val wakeWord: String = SettingsDefaults.WAKE_WORD,
    val hotspotSsid: String = "",
    val hotspotPassword: String = "",
    val pairingToken: String = "",
)

object SettingsDefaults {
    const val DRIVING_RESOLUTION = "1920x1080"
    const val DRIVING_FPS = 30
    const val DRIVING_BITRATE_KBPS = 12000
    const val PARKING_RESOLUTION = "1280x720"
    const val PARKING_FPS = 2
    const val PARKING_BITRATE_KBPS = 1000
    const val SEGMENT_DURATION_MINUTES = 3
    const val MAX_STORAGE_GB = 32
    const val MIN_FREE_SPACE_GB = 2
    const val AUDIO_ENABLED = true
    const val VOICE_WAKEUP_ENABLED = false
    const val WAKE_WORD = "小行车"

    val allowedResolutions = setOf("1920x1080", "1280x720")
    val allowedDrivingFps = setOf(30, 60)
    val allowedParkingFps = setOf(1, 2, 5)
    val allowedSegmentDurations = setOf(1, 3, 5, 10)
    val allowedStorageGb = setOf(8, 16, 32, 64, 128)
    val allowedMinFreeSpaceGb = setOf(1, 2, 4, 8)
    val allowedDrivingBitrates = setOf(8000, 12000, 16000)
    val allowedParkingBitrates = setOf(500, 1000, 2000)
}

object SettingKeys {
    const val DEVICE_ROLE = "device_role"
    const val DRIVING_RESOLUTION = "driving_resolution"
    const val DRIVING_FPS = "driving_fps"
    const val DRIVING_BITRATE_KBPS = "driving_bitrate_kbps"
    const val PARKING_RESOLUTION = "parking_resolution"
    const val PARKING_FPS = "parking_fps"
    const val PARKING_BITRATE_KBPS = "parking_bitrate_kbps"
    const val SEGMENT_DURATION_MINUTES = "segment_duration_minutes"
    const val MAX_STORAGE_GB = "max_storage_gb"
    const val MIN_FREE_SPACE_GB = "min_free_space_gb"
    const val AUDIO_ENABLED = "audio_enabled"
    const val VOICE_WAKEUP_ENABLED = "voice_wakeup_enabled"
    const val WAKE_WORD = "wake_word"
    const val HOTSPOT_SSID = "hotspot_ssid"
    const val HOTSPOT_PASSWORD = "hotspot_password"
    const val PAIRING_TOKEN = "pairing_token"
}

class SettingsRepository(
    private val dao: AppSettingDao,
) {
    fun observeSettings(): Flow<DashCamSettings> =
        dao.observeAll().map { entities -> entities.toSettings() }

    suspend fun getSettings(): DashCamSettings = dao.getAll().toSettings()

    suspend fun getDeviceRole(): DeviceRole? =
        dao.get(SettingKeys.DEVICE_ROLE)?.value?.let(DeviceRole::fromStoredValue)

    suspend fun saveDeviceRole(role: DeviceRole) {
        dao.upsert(AppSettingEntity(SettingKeys.DEVICE_ROLE, role.storedValue))
    }

    suspend fun saveSettings(settings: DashCamSettings) {
        dao.upsertAll(settings.normalized().toEntities())
    }

    private fun List<AppSettingEntity>.toSettings(): DashCamSettings {
        val values = associate { it.key to it.value }
        return DashCamSettings(
            deviceRole = values[SettingKeys.DEVICE_ROLE]?.let(DeviceRole::fromStoredValue),
            drivingResolution = values.stringChoice(
                SettingKeys.DRIVING_RESOLUTION,
                SettingsDefaults.allowedResolutions,
                SettingsDefaults.DRIVING_RESOLUTION,
            ),
            drivingFps = values.intChoice(
                SettingKeys.DRIVING_FPS,
                SettingsDefaults.allowedDrivingFps,
                SettingsDefaults.DRIVING_FPS,
            ),
            drivingBitrateKbps = values.intChoice(
                SettingKeys.DRIVING_BITRATE_KBPS,
                SettingsDefaults.allowedDrivingBitrates,
                SettingsDefaults.DRIVING_BITRATE_KBPS,
            ),
            parkingResolution = values.stringChoice(
                SettingKeys.PARKING_RESOLUTION,
                SettingsDefaults.allowedResolutions,
                SettingsDefaults.PARKING_RESOLUTION,
            ),
            parkingFps = values.intChoice(
                SettingKeys.PARKING_FPS,
                SettingsDefaults.allowedParkingFps,
                SettingsDefaults.PARKING_FPS,
            ),
            parkingBitrateKbps = values.intChoice(
                SettingKeys.PARKING_BITRATE_KBPS,
                SettingsDefaults.allowedParkingBitrates,
                SettingsDefaults.PARKING_BITRATE_KBPS,
            ),
            segmentDurationMinutes = values.intChoice(
                SettingKeys.SEGMENT_DURATION_MINUTES,
                SettingsDefaults.allowedSegmentDurations,
                SettingsDefaults.SEGMENT_DURATION_MINUTES,
            ),
            maxStorageGb = values.intChoice(
                SettingKeys.MAX_STORAGE_GB,
                SettingsDefaults.allowedStorageGb,
                SettingsDefaults.MAX_STORAGE_GB,
            ),
            minFreeSpaceGb = values.intChoice(
                SettingKeys.MIN_FREE_SPACE_GB,
                SettingsDefaults.allowedMinFreeSpaceGb,
                SettingsDefaults.MIN_FREE_SPACE_GB,
            ),
            audioEnabled = values.booleanValue(SettingKeys.AUDIO_ENABLED, SettingsDefaults.AUDIO_ENABLED),
            voiceWakeupEnabled = values.booleanValue(
                SettingKeys.VOICE_WAKEUP_ENABLED,
                SettingsDefaults.VOICE_WAKEUP_ENABLED,
            ),
            wakeWord = values[SettingKeys.WAKE_WORD].nonBlankOr(SettingsDefaults.WAKE_WORD),
            hotspotSsid = values[SettingKeys.HOTSPOT_SSID].orEmpty(),
            hotspotPassword = values[SettingKeys.HOTSPOT_PASSWORD].orEmpty(),
            pairingToken = values[SettingKeys.PAIRING_TOKEN].orEmpty(),
        )
    }

    private fun DashCamSettings.normalized(): DashCamSettings =
        listOfNotNull(
            deviceRole?.let { AppSettingEntity(SettingKeys.DEVICE_ROLE, it.storedValue) },
            AppSettingEntity(SettingKeys.DRIVING_RESOLUTION, drivingResolution),
            AppSettingEntity(SettingKeys.DRIVING_FPS, drivingFps.toString()),
            AppSettingEntity(SettingKeys.DRIVING_BITRATE_KBPS, drivingBitrateKbps.toString()),
            AppSettingEntity(SettingKeys.PARKING_RESOLUTION, parkingResolution),
            AppSettingEntity(SettingKeys.PARKING_FPS, parkingFps.toString()),
            AppSettingEntity(SettingKeys.PARKING_BITRATE_KBPS, parkingBitrateKbps.toString()),
            AppSettingEntity(SettingKeys.SEGMENT_DURATION_MINUTES, segmentDurationMinutes.toString()),
            AppSettingEntity(SettingKeys.MAX_STORAGE_GB, maxStorageGb.toString()),
            AppSettingEntity(SettingKeys.MIN_FREE_SPACE_GB, minFreeSpaceGb.toString()),
            AppSettingEntity(SettingKeys.AUDIO_ENABLED, audioEnabled.toString()),
            AppSettingEntity(SettingKeys.VOICE_WAKEUP_ENABLED, voiceWakeupEnabled.toString()),
            AppSettingEntity(SettingKeys.WAKE_WORD, wakeWord),
            AppSettingEntity(SettingKeys.HOTSPOT_SSID, hotspotSsid),
            AppSettingEntity(SettingKeys.HOTSPOT_PASSWORD, hotspotPassword),
            AppSettingEntity(SettingKeys.PAIRING_TOKEN, pairingToken),
        ).toSettings()

    private fun DashCamSettings.toEntities(): List<AppSettingEntity> =
        listOfNotNull(
            deviceRole?.let { AppSettingEntity(SettingKeys.DEVICE_ROLE, it.storedValue) },
            AppSettingEntity(SettingKeys.DRIVING_RESOLUTION, drivingResolution),
            AppSettingEntity(SettingKeys.DRIVING_FPS, drivingFps.toString()),
            AppSettingEntity(SettingKeys.DRIVING_BITRATE_KBPS, drivingBitrateKbps.toString()),
            AppSettingEntity(SettingKeys.PARKING_RESOLUTION, parkingResolution),
            AppSettingEntity(SettingKeys.PARKING_FPS, parkingFps.toString()),
            AppSettingEntity(SettingKeys.PARKING_BITRATE_KBPS, parkingBitrateKbps.toString()),
            AppSettingEntity(SettingKeys.SEGMENT_DURATION_MINUTES, segmentDurationMinutes.toString()),
            AppSettingEntity(SettingKeys.MAX_STORAGE_GB, maxStorageGb.toString()),
            AppSettingEntity(SettingKeys.MIN_FREE_SPACE_GB, minFreeSpaceGb.toString()),
            AppSettingEntity(SettingKeys.AUDIO_ENABLED, audioEnabled.toString()),
            AppSettingEntity(SettingKeys.VOICE_WAKEUP_ENABLED, voiceWakeupEnabled.toString()),
            AppSettingEntity(SettingKeys.WAKE_WORD, wakeWord),
            AppSettingEntity(SettingKeys.HOTSPOT_SSID, hotspotSsid),
            AppSettingEntity(SettingKeys.HOTSPOT_PASSWORD, hotspotPassword),
            AppSettingEntity(SettingKeys.PAIRING_TOKEN, pairingToken),
        )

    private fun Map<String, String>.intChoice(key: String, allowed: Set<Int>, fallback: Int): Int =
        get(key)?.toIntOrNull()?.takeIf { it in allowed } ?: fallback

    private fun Map<String, String>.stringChoice(key: String, allowed: Set<String>, fallback: String): String =
        get(key)?.takeIf { it in allowed } ?: fallback

    private fun Map<String, String>.booleanValue(key: String, fallback: Boolean): Boolean =
        when (get(key)) {
            "true" -> true
            "false" -> false
            else -> fallback
        }

    private fun String?.nonBlankOr(fallback: String): String = this?.takeIf { it.isNotBlank() } ?: fallback
}
