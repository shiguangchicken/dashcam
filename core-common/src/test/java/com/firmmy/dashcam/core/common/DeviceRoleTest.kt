package com.firmmy.dashcam.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceRoleTest {
    @Test
    fun fromStoredValueReturnsKnownRole() {
        assertEquals(DeviceRole.RECORDER, DeviceRole.fromStoredValue("recorder"))
        assertEquals(DeviceRole.REMOTE, DeviceRole.fromStoredValue("remote"))
    }

    @Test
    fun fromStoredValueReturnsNullForUnknownRole() {
        assertNull(DeviceRole.fromStoredValue("unknown"))
    }
}
