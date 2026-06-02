package com.firmmy.dashcam.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotControllerTest {
    @Test
    fun startMovesToStartedWhenStarterReturnsCredentials() {
        val starter = FakeLocalOnlyHotspotStarter()
        val controller = HotspotController(starter, logger = NoOpHotspotLogger)

        val result = controller.start()
        starter.callback?.onStarted(FakeLocalOnlyHotspotSession())

        assertTrue(result.isSuccess)
        assertEquals(
            HotspotState.Started(HotspotCredentials(ssid = "DashCam", password = "12345678")),
            controller.state.value,
        )
    }

    @Test
    fun startFailsWhenDeviceIsUnsupported() {
        val controller = HotspotController(
            starter = FakeLocalOnlyHotspotStarter(supported = false),
            logger = NoOpHotspotLogger,
        )

        val result = controller.start()

        assertTrue(!result.isSuccess)
        assertEquals(
            HotspotState.Failed(
                HotspotFailure.UNSUPPORTED,
                "LocalOnlyHotspot is unsupported on this device",
            ),
            controller.state.value,
        )
    }

    @Test
    fun startMapsStarterFailureToFailedState() {
        val starter = FakeLocalOnlyHotspotStarter()
        val controller = HotspotController(starter, logger = NoOpHotspotLogger)

        controller.start()
        starter.callback?.onFailed(HotspotFailure.TETHERING_DISALLOWED, "blocked")

        assertEquals(
            HotspotState.Failed(HotspotFailure.TETHERING_DISALLOWED, "blocked"),
            controller.state.value,
        )
    }

    @Test
    fun stopClosesSessionAndMovesToStopped() {
        val starter = FakeLocalOnlyHotspotStarter()
        val session = FakeLocalOnlyHotspotSession()
        val controller = HotspotController(starter, logger = NoOpHotspotLogger)

        controller.start()
        starter.callback?.onStarted(session)
        controller.stop()

        assertEquals(HotspotState.Stopped, controller.state.value)
        assertTrue(session.closed)
    }

    @Test
    fun repeatedStartWhileStartedDoesNotStartAgain() {
        val starter = FakeLocalOnlyHotspotStarter()
        val controller = HotspotController(starter, logger = NoOpHotspotLogger)

        controller.start()
        starter.callback?.onStarted(FakeLocalOnlyHotspotSession())
        controller.start()

        assertEquals(1, starter.startCount)
    }
}

private class FakeLocalOnlyHotspotStarter(
    private val supported: Boolean = true,
) : LocalOnlyHotspotStarter {
    var callback: LocalOnlyHotspotCallback? = null
    var startCount = 0

    override fun isSupported(): Boolean = supported

    override fun start(callback: LocalOnlyHotspotCallback) {
        startCount += 1
        this.callback = callback
    }
}

private class FakeLocalOnlyHotspotSession : LocalOnlyHotspotSession {
    var closed = false

    override val credentials: HotspotCredentials =
        HotspotCredentials(ssid = "DashCam", password = "12345678")

    override fun close() {
        closed = true
    }
}

private object NoOpHotspotLogger : HotspotLogger {
    override fun info(message: String) = Unit

    override fun warn(message: String, throwable: Throwable?) = Unit
}
