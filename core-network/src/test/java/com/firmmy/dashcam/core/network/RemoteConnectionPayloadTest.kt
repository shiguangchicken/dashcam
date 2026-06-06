package com.firmmy.dashcam.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteConnectionPayloadTest {
    @Test
    fun roundTripsQrPayload() {
        val payload = RemoteConnectionPayload(
            ssid = "DIRECT-abc",
            password = "password123",
            baseUrl = "http://192.168.1.1:8080",
        )

        val parsed = RemoteConnectionPayload.parse(payload.toQrText())

        assertEquals(payload, parsed)
    }

    @Test
    fun rejectsNonDashcamQrText() {
        assertNull(RemoteConnectionPayload.parse("not-json"))
        assertNull(RemoteConnectionPayload.parse("""{"type":"other","version":1}"""))
        assertNull(RemoteConnectionPayload.parse("""{"type":"dashcam.remote","version":2}"""))
    }
}
