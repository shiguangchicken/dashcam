package com.firmmy.dashcam.core.network

import org.json.JSONObject

data class RemoteConnectionPayload(
    val ssid: String,
    val password: String,
    val baseUrl: String,
    val port: Int = EmbeddedHttpServer.DEFAULT_PORT,
    val version: Int = VERSION,
) {
    fun toQrText(): String =
        JSONObject()
            .put("type", TYPE)
            .put("version", version)
            .put("ssid", ssid)
            .put("password", password)
            .put("baseUrl", baseUrl)
            .put("port", port)
            .toString()

    companion object {
        const val TYPE = "dashcam.remote"
        const val VERSION = 1

        fun parse(value: String): RemoteConnectionPayload? =
            runCatching {
                val json = JSONObject(value)
                if (json.optString("type") != TYPE) return null
                if (json.optInt("version", 0) != VERSION) return null
                val ssid = json.optString("ssid")
                val password = json.optString("password")
                val baseUrl = json.optString("baseUrl")
                val port = json.optInt("port", EmbeddedHttpServer.DEFAULT_PORT)
                if (ssid.isBlank() || password.isBlank() || baseUrl.isBlank()) return null
                RemoteConnectionPayload(
                    ssid = ssid,
                    password = password,
                    baseUrl = baseUrl,
                    port = port,
                    version = VERSION,
                )
            }.getOrNull()
    }
}
