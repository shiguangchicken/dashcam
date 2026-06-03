package com.firmmy.dashcam.core.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteServiceDiscoveryTest {
    @Test
    fun manualDiscoveryParsesHostAndPort() = runBlocking {
        val discovery = ManualRemoteServiceDiscovery(
            hostProvider = { "http://192.168.43.1:8080" },
            portProvider = { 8080 },
        )

        val endpoint = discovery.discover()

        assertEquals(RemoteServiceEndpoint("192.168.43.1", 8080), endpoint)
        assertEquals("http://192.168.43.1:8080", endpoint?.baseUrl())
    }

    @Test
    fun compositeUsesFirstAvailableEndpoint() = runBlocking {
        val discovery = CompositeRemoteServiceDiscovery(
            listOf(
                RemoteServiceDiscovery { null },
                ManualRemoteServiceDiscovery(hostProvider = { "dashcam.local" }),
            ),
        )

        assertEquals(RemoteServiceEndpoint("dashcam.local", 8080), discovery.discover())
    }

    @Test
    fun manualDiscoveryReturnsNullForBlankHost() = runBlocking {
        assertNull(ManualRemoteServiceDiscovery(hostProvider = { " " }).discover())
    }

    @Test
    fun nsdServiceNameFallsBackWhenBlank() {
        assertEquals(AndroidNsdRemoteServiceAdvertiser.SERVICE_NAME, normalizedNsdServiceName("  "))
        assertEquals("DashCam Living Room", normalizedNsdServiceName("  DashCam Living Room  "))
    }
}
