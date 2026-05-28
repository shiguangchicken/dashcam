package com.firmmy.dashcam

import android.content.Context
import com.firmmy.dashcam.core.common.DeviceRole
import com.firmmy.dashcam.core.database.DashCamDatabaseProvider
import com.firmmy.dashcam.core.database.DashCamSettings
import com.firmmy.dashcam.core.database.SettingsRepository
import kotlinx.coroutines.runBlocking

class RoleStore(context: Context) {
    private val settingsRepository = SettingsRepository(
        DashCamDatabaseProvider.get(context).appSettingDao(),
    )

    fun currentRole(): DeviceRole? = runBlocking {
        settingsRepository.getDeviceRole()
    }

    fun currentSettings(): DashCamSettings = runBlocking {
        settingsRepository.getSettings()
    }

    fun saveRole(role: DeviceRole) {
        runBlocking {
            settingsRepository.saveDeviceRole(role)
        }
    }

    fun saveSettings(settings: DashCamSettings) {
        runBlocking {
            settingsRepository.saveSettings(settings)
        }
    }
}
