package com.firmmy.dashcam

import android.content.Context
import com.firmmy.dashcam.core.common.DeviceRole

class RoleStore(context: Context) {
    private val preferences = context.getSharedPreferences("dashcam_settings", Context.MODE_PRIVATE)

    fun currentRole(): DeviceRole? = preferences
        .getString(KEY_DEVICE_ROLE, null)
        ?.let(DeviceRole::fromStoredValue)

    fun saveRole(role: DeviceRole) {
        preferences.edit()
            .putString(KEY_DEVICE_ROLE, role.storedValue)
            .apply()
    }

    private companion object {
        const val KEY_DEVICE_ROLE = "device_role"
    }
}
