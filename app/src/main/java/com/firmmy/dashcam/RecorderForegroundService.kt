package com.firmmy.dashcam

import android.app.Service
import android.content.Intent
import android.os.IBinder

class RecorderForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
