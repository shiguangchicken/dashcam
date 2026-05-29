package com.firmmy.dashcam

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class RecorderCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.takeIf { it in supportedActions } ?: return
        val serviceIntent = RecorderForegroundService.commandIntent(context, action).apply {
            intent.extras?.let(::putExtras)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    private companion object {
        val supportedActions = setOf(
            RecorderForegroundService.ACTION_START_DRIVING,
            RecorderForegroundService.ACTION_START_PARKING,
            RecorderForegroundService.ACTION_STOP,
            RecorderForegroundService.ACTION_TAKE_PHOTO,
            RecorderForegroundService.ACTION_SWITCH_DRIVING,
            RecorderForegroundService.ACTION_SWITCH_PARKING,
        )
    }
}
