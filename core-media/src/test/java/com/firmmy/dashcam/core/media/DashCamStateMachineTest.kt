package com.firmmy.dashcam.core.media

import com.firmmy.dashcam.core.common.DashCamCommand
import com.firmmy.dashcam.core.common.DashCamError
import com.firmmy.dashcam.core.common.DashCamResult
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashCamStateMachineTest {
    @Test
    fun idleStartsDrivingAndParkingRecording() {
        assertTransition(
            initialStatus = RecordingStatus.IDLE,
            command = DashCamCommand.StartDrivingMode,
            expectedStatus = RecordingStatus.RECORDING_DRIVING,
            expectedMode = RecordingMode.DRIVING,
        )
        assertTransition(
            initialStatus = RecordingStatus.IDLE,
            command = DashCamCommand.StartParkingMode,
            expectedStatus = RecordingStatus.RECORDING_PARKING,
            expectedMode = RecordingMode.PARKING,
        )
    }

    @Test
    fun drivingAndParkingCanSwitchModes() {
        assertTransition(
            initialStatus = RecordingStatus.RECORDING_DRIVING,
            initialMode = RecordingMode.DRIVING,
            command = DashCamCommand.StartParkingMode,
            expectedStatus = RecordingStatus.RECORDING_PARKING,
            expectedMode = RecordingMode.PARKING,
        )
        assertTransition(
            initialStatus = RecordingStatus.RECORDING_PARKING,
            initialMode = RecordingMode.PARKING,
            command = DashCamCommand.StartDrivingMode,
            expectedStatus = RecordingStatus.RECORDING_DRIVING,
            expectedMode = RecordingMode.DRIVING,
        )
    }

    @Test
    fun stopRecordingReturnsRecordingPausedAndErrorStatesToIdle() {
        listOf(
            RecordingStatus.RECORDING_DRIVING to RecordingMode.DRIVING,
            RecordingStatus.RECORDING_PARKING to RecordingMode.PARKING,
            RecordingStatus.PAUSED to RecordingMode.DRIVING,
            RecordingStatus.EVENT_BOOST_RECORDING to RecordingMode.EVENT,
            RecordingStatus.ERROR to null,
        ).forEach { (status, mode) ->
            assertTransition(
                initialStatus = status,
                initialMode = mode,
                command = DashCamCommand.StopRecording,
                expectedStatus = RecordingStatus.IDLE,
                expectedMode = null,
            )
        }
    }

    @Test
    fun parkingLockCurrentClipEntersEventBoostAndCanReturnToNormalRecording() {
        val stateMachine = DashCamStateMachine(
            DashCamRecorderState(
                status = RecordingStatus.RECORDING_PARKING,
                mode = RecordingMode.PARKING,
            ),
        )

        val locked = stateMachine.dispatch(DashCamCommand.LockCurrentClip).successValue()

        assertEquals(RecordingStatus.EVENT_BOOST_RECORDING, locked.status)
        assertEquals(RecordingMode.EVENT, locked.mode)
        assertTrue(locked.currentClipLocked)

        val parking = stateMachine.dispatch(DashCamCommand.StartParkingMode).successValue()
        assertEquals(RecordingStatus.RECORDING_PARKING, parking.status)
        assertEquals(RecordingMode.PARKING, parking.mode)

        stateMachine.dispatch(DashCamCommand.LockCurrentClip)
        val driving = stateMachine.dispatch(DashCamCommand.StartDrivingMode).successValue()
        assertEquals(RecordingStatus.RECORDING_DRIVING, driving.status)
        assertEquals(RecordingMode.DRIVING, driving.mode)
    }

    @Test
    fun recordingCommandsUpdateAttributesWithoutChangingMainState() {
        val stateMachine = DashCamStateMachine(
            DashCamRecorderState(
                status = RecordingStatus.RECORDING_DRIVING,
                mode = RecordingMode.DRIVING,
            ),
        )

        val muted = stateMachine.dispatch(DashCamCommand.DisableAudio).successValue()
        assertFalse(muted.audioEnabled)
        assertEquals(RecordingStatus.RECORDING_DRIVING, muted.status)

        val hotspot = stateMachine.dispatch(DashCamCommand.StartHotspot).successValue()
        assertTrue(hotspot.hotspotEnabled)
        assertEquals(RecordingStatus.RECORDING_DRIVING, hotspot.status)

        val photo = stateMachine.dispatch(DashCamCommand.TakePhoto).successValue()
        assertEquals(1, photo.photoCount)
        assertEquals(RecordingStatus.RECORDING_DRIVING, photo.status)

        val locked = stateMachine.dispatch(DashCamCommand.LockCurrentClip).successValue()
        assertTrue(locked.currentClipLocked)
        assertEquals(RecordingStatus.RECORDING_DRIVING, locked.status)
    }

    @Test
    fun illegalTransitionsReturnInvalidStateError() {
        val idle = DashCamStateMachine()
        val idleLock = idle.dispatch(DashCamCommand.LockCurrentClip)

        assertTrue(idleLock is DashCamResult.Failure)
        assertTrue((idleLock as DashCamResult.Failure).error is DashCamError.InvalidState)
        assertEquals(RecordingStatus.IDLE, idle.state.status)

        val error = DashCamStateMachine(DashCamRecorderState(status = RecordingStatus.ERROR))
        val startDriving = error.dispatch(DashCamCommand.StartDrivingMode)

        assertTrue(startDriving is DashCamResult.Failure)
        assertTrue((startDriving as DashCamResult.Failure).error is DashCamError.InvalidState)
        assertEquals(RecordingStatus.ERROR, error.state.status)
    }

    private fun assertTransition(
        initialStatus: RecordingStatus,
        initialMode: RecordingMode? = null,
        command: DashCamCommand,
        expectedStatus: RecordingStatus,
        expectedMode: RecordingMode?,
    ) {
        val stateMachine = DashCamStateMachine(
            DashCamRecorderState(
                status = initialStatus,
                mode = initialMode,
            ),
        )

        val nextState = stateMachine.dispatch(command).successValue()

        assertEquals(expectedStatus, nextState.status)
        assertEquals(expectedMode, nextState.mode)
    }

    private fun DashCamResult<DashCamRecorderState>.successValue(): DashCamRecorderState {
        assertTrue(this is DashCamResult.Success)
        return (this as DashCamResult.Success).value
    }
}
