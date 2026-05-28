package com.firmmy.dashcam.core.common

import android.util.Log

object DashCamLog {
    private const val PREFIX = "DashCam"

    fun debug(tag: String, message: String) {
        Log.d("$PREFIX:$tag", message)
    }

    fun info(tag: String, message: String) {
        Log.i("$PREFIX:$tag", message)
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        Log.w("$PREFIX:$tag", message, throwable)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("$PREFIX:$tag", message, throwable)
    }
}
