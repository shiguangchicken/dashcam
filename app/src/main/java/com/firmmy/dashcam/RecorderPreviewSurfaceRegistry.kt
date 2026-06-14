package com.firmmy.dashcam

import android.view.Surface
import java.util.concurrent.atomic.AtomicReference

object RecorderPreviewSurfaceRegistry {
    private val surface = AtomicReference<Surface?>(null)

    fun attach(previewSurface: Surface) {
        surface.set(previewSurface)
    }

    fun detach(previewSurface: Surface) {
        surface.compareAndSet(previewSurface, null)
    }

    fun surface(): Surface? = surface.get()?.takeIf { it.isValid }
}
