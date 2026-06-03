package com.firmmy.dashcam.core.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class RemoteEventBus {
    private val mutableEvents = MutableSharedFlow<RemoteEvent>(
        replay = 1,
        extraBufferCapacity = 32,
    )

    val events: SharedFlow<RemoteEvent> = mutableEvents.asSharedFlow()

    fun tryEmit(event: RemoteEvent) {
        mutableEvents.tryEmit(event)
    }
}
