package com.firmmy.dashcam.core.network

import com.firmmy.dashcam.core.common.DashCamError
import com.firmmy.dashcam.core.common.DashCamLog
import com.firmmy.dashcam.core.common.DashCamResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HotspotCredentials(
    val ssid: String,
    val password: String,
)

sealed interface HotspotState {
    data object Stopped : HotspotState
    data object Starting : HotspotState
    data class Started(val credentials: HotspotCredentials) : HotspotState
    data class Failed(val reason: HotspotFailure, val message: String) : HotspotState
}

enum class HotspotFailure {
    UNSUPPORTED,
    PERMISSION_DENIED,
    INCOMPATIBLE_MODE,
    NO_CHANNEL,
    TETHERING_DISALLOWED,
    SYSTEM_ERROR,
}

interface LocalOnlyHotspotStarter {
    fun isSupported(): Boolean
    fun start(callback: LocalOnlyHotspotCallback)
}

interface LocalOnlyHotspotCallback {
    fun onStarted(session: LocalOnlyHotspotSession)
    fun onStopped()
    fun onFailed(failure: HotspotFailure, message: String)
}

interface LocalOnlyHotspotSession {
    val credentials: HotspotCredentials
    fun close()
}

class HotspotController(
    private val starter: LocalOnlyHotspotStarter,
    private val logger: HotspotLogger = AndroidHotspotLogger,
) {
    private var session: LocalOnlyHotspotSession? = null
    private val mutableState = MutableStateFlow<HotspotState>(HotspotState.Stopped)

    val state: StateFlow<HotspotState> = mutableState.asStateFlow()

    fun start(): DashCamResult<Unit> {
        if (!starter.isSupported()) {
            val message = "LocalOnlyHotspot is unsupported on this device"
            mutableState.value = HotspotState.Failed(HotspotFailure.UNSUPPORTED, message)
            logger.warn(message)
            return DashCamResult.Failure(DashCamError.Unknown(message))
        }

        if (mutableState.value is HotspotState.Started || mutableState.value is HotspotState.Starting) {
            return DashCamResult.Success(Unit)
        }

        mutableState.value = HotspotState.Starting
        logger.info("Starting LocalOnlyHotspot")
        return try {
            starter.start(
                object : LocalOnlyHotspotCallback {
                    override fun onStarted(session: LocalOnlyHotspotSession) {
                        this@HotspotController.session = session
                        mutableState.value = HotspotState.Started(session.credentials)
                        logger.info("LocalOnlyHotspot started: ssid=${session.credentials.ssid}")
                    }

                    override fun onStopped() {
                        this@HotspotController.session = null
                        mutableState.value = HotspotState.Stopped
                        logger.info("LocalOnlyHotspot stopped by system")
                    }

                    override fun onFailed(failure: HotspotFailure, message: String) {
                        this@HotspotController.session = null
                        mutableState.value = HotspotState.Failed(failure, message)
                        logger.warn("LocalOnlyHotspot failed: $message")
                    }
                },
            )
            DashCamResult.Success(Unit)
        } catch (exception: SecurityException) {
            val message = exception.message ?: "LocalOnlyHotspot permission denied"
            mutableState.value = HotspotState.Failed(HotspotFailure.PERMISSION_DENIED, message)
            logger.warn(message, exception)
            DashCamResult.Failure(DashCamError.PermissionDenied("CHANGE_WIFI_STATE", message))
        } catch (exception: RuntimeException) {
            val message = exception.message ?: "LocalOnlyHotspot system error"
            mutableState.value = HotspotState.Failed(HotspotFailure.SYSTEM_ERROR, message)
            logger.warn(message, exception)
            DashCamResult.Failure(DashCamError.Unknown(message))
        }
    }

    fun stop() {
        logger.info("Stopping LocalOnlyHotspot")
        session?.close()
        session = null
        mutableState.value = HotspotState.Stopped
    }
}

interface HotspotLogger {
    fun info(message: String)
    fun warn(message: String, throwable: Throwable? = null)
}

object AndroidHotspotLogger : HotspotLogger {
    override fun info(message: String) {
        DashCamLog.info(TAG, message)
    }

    override fun warn(message: String, throwable: Throwable?) {
        DashCamLog.warn(TAG, message, throwable)
    }

    private const val TAG = "Hotspot"
}
