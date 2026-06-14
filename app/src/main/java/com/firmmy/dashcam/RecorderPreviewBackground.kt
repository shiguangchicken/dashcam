package com.firmmy.dashcam

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun RecorderPreviewBackground(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .testTag("recorder_preview_surface"),
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var surface: Surface? = null

                    override fun onSurfaceTextureAvailable(
                        texture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        texture.setDefaultBufferSize(width.coerceAtLeast(1), height.coerceAtLeast(1))
                        surface = Surface(texture).also(RecorderPreviewSurfaceRegistry::attach)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        texture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        texture.setDefaultBufferSize(width.coerceAtLeast(1), height.coerceAtLeast(1))
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        surface?.let {
                            RecorderPreviewSurfaceRegistry.detach(it)
                            it.release()
                        }
                        surface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
    )
}
