package com.firmmy.dashcam.core.network

import com.firmmy.dashcam.core.common.DashCamCommand
import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus
import org.json.JSONArray
import org.json.JSONObject

object RemoteJson {
    fun status(value: RemoteStatus): String =
        JSONObject()
            .put("recordingStatus", value.recordingStatus.storedValue)
            .put("mode", value.mode.storedValue)
            .put("audioEnabled", value.audioEnabled)
            .put("hotspotEnabled", value.hotspotEnabled)
            .put("hotspotSsid", value.hotspotSsid)
            .put("freeSpaceBytes", value.freeSpaceBytes)
            .putNullable("batteryPercent", value.batteryPercent)
            .putNullable("temperatureCelsius", value.temperatureCelsius)
            .put("liveStreamAvailable", value.liveStreamAvailable)
            .put(
                "remoteViewers",
                JSONArray(
                    value.remoteViewers.map { viewer ->
                        JSONObject()
                            .put("id", viewer.id)
                            .put("name", viewer.name)
                            .put("lastSeenEpochMillis", viewer.lastSeenEpochMillis)
                    },
                ),
            )
            .toString()

    fun mediaList(items: List<RemoteMediaItem>): String =
        JSONObject()
            .put("items", JSONArray(items.map { JSONObject(media(it)) }))
            .toString()

    fun media(value: RemoteMediaItem): String =
        JSONObject()
            .put("id", value.id)
            .put("type", value.type.storedValue)
            .put("mode", value.mode.storedValue)
            .put("path", value.path)
            .putNullable("thumbnailPath", value.thumbnailPath)
            .put("createdAt", value.createdAt)
            .putNullable("durationMs", value.durationMs)
            .put("sizeBytes", value.sizeBytes)
            .putNullable("width", value.width)
            .putNullable("height", value.height)
            .putNullable("fps", value.fps)
            .putNullable("bitrate", value.bitrate)
            .put("hasAudio", value.hasAudio)
            .put("locked", value.locked)
            .toString()

    fun settings(value: RemoteSettings): String =
        JSONObject()
            .put("drivingResolution", value.drivingResolution)
            .put("drivingFps", value.drivingFps)
            .put("drivingBitrateKbps", value.drivingBitrateKbps)
            .put("parkingResolution", value.parkingResolution)
            .put("parkingFps", value.parkingFps)
            .put("parkingBitrateKbps", value.parkingBitrateKbps)
            .put("segmentDurationMinutes", value.segmentDurationMinutes)
            .put("maxStorageGb", value.maxStorageGb)
            .put("minFreeSpaceGb", value.minFreeSpaceGb)
            .put("audioEnabled", value.audioEnabled)
            .put("voiceWakeupEnabled", value.voiceWakeupEnabled)
            .put("wakeWord", value.wakeWord)
            .toString()

    fun response(ok: Boolean, message: String = ""): String =
        JSONObject()
            .put("ok", ok)
            .put("message", message)
            .toString()

    fun event(value: RemoteEvent): String {
        val json = JSONObject()
        when (value) {
            is RemoteEvent.CommandHandled -> json
                .put("type", "command")
                .put("command", value.command.wireValue())
                .put("ok", value.ok)

            is RemoteEvent.MediaCreated -> json
                .put("type", "media")
                .put("media", JSONObject(media(value.media)))

            is RemoteEvent.StatusChanged -> json
                .put("type", "status")
                .put("status", JSONObject(status(value.status)))
        }
        return json.toString()
    }

    fun parseStatus(body: String): RemoteStatus {
        val json = JSONObject(body)
        return RemoteStatus(
            recordingStatus = RecordingStatus.fromStoredValue(json.optString("recordingStatus"))
                ?: RecordingStatus.IDLE,
            mode = RecordingMode.fromStoredValue(json.optString("mode")) ?: RecordingMode.DRIVING,
            audioEnabled = json.optBoolean("audioEnabled", true),
            hotspotEnabled = json.optBoolean("hotspotEnabled", false),
            hotspotSsid = json.optString("hotspotSsid"),
            freeSpaceBytes = json.optLong("freeSpaceBytes", 0L),
            batteryPercent = json.nullableInt("batteryPercent"),
            temperatureCelsius = json.nullableDouble("temperatureCelsius")?.toFloat(),
            liveStreamAvailable = json.optBoolean("liveStreamAvailable", false),
            remoteViewers = json.optJSONArray("remoteViewers")?.let { array ->
                List(array.length()) { index ->
                    val viewer = array.getJSONObject(index)
                    RemoteViewerClientInfo(
                        id = viewer.getString("id"),
                        name = viewer.optString("name", "Remote viewer"),
                        lastSeenEpochMillis = viewer.optLong("lastSeenEpochMillis", 0L),
                    )
                }
            }.orEmpty(),
        )
    }

    fun parseMediaList(body: String): List<RemoteMediaItem> {
        val array = JSONObject(body).optJSONArray("items") ?: return emptyList()
        return List(array.length()) { index -> parseMedia(array.getJSONObject(index)) }
    }

    fun parseSettings(body: String): RemoteSettings {
        val json = JSONObject(body)
        return RemoteSettings(
            drivingResolution = json.getString("drivingResolution"),
            drivingFps = json.getInt("drivingFps"),
            drivingBitrateKbps = json.getInt("drivingBitrateKbps"),
            parkingResolution = json.getString("parkingResolution"),
            parkingFps = json.getInt("parkingFps"),
            parkingBitrateKbps = json.getInt("parkingBitrateKbps"),
            segmentDurationMinutes = json.getInt("segmentDurationMinutes"),
            maxStorageGb = json.getInt("maxStorageGb"),
            minFreeSpaceGb = json.getInt("minFreeSpaceGb"),
            audioEnabled = json.getBoolean("audioEnabled"),
            voiceWakeupEnabled = json.getBoolean("voiceWakeupEnabled"),
            wakeWord = json.getString("wakeWord"),
        )
    }

    fun parseResponse(body: String): RemoteApiResponse {
        val json = JSONObject(body)
        return RemoteApiResponse(
            ok = json.optBoolean("ok", false),
            message = json.optString("message"),
        )
    }

    fun parseCommand(body: String): DashCamCommand? =
        JSONObject(body).optString("command").toDashCamCommand()

    fun commandBody(command: DashCamCommand): String =
        JSONObject().put("command", command.wireValue()).toString()

    fun DashCamCommand.wireValue(): String =
        when (this) {
            DashCamCommand.StartDrivingMode -> "driving"
            DashCamCommand.StartParkingMode -> "parking"
            DashCamCommand.TakePhoto -> "photo"
            DashCamCommand.EnableAudio -> "audio_on"
            DashCamCommand.DisableAudio -> "audio_off"
            DashCamCommand.StartHotspot -> "hotspot_on"
            DashCamCommand.StopHotspot -> "hotspot_off"
            DashCamCommand.LockCurrentClip -> "lock_current"
            DashCamCommand.StopRecording -> "stop"
        }

    private fun String.toDashCamCommand(): DashCamCommand? =
        when (trim()) {
            "driving" -> DashCamCommand.StartDrivingMode
            "parking" -> DashCamCommand.StartParkingMode
            "photo" -> DashCamCommand.TakePhoto
            "audio_on" -> DashCamCommand.EnableAudio
            "audio_off" -> DashCamCommand.DisableAudio
            "hotspot_on" -> DashCamCommand.StartHotspot
            "hotspot_off" -> DashCamCommand.StopHotspot
            "lock_current" -> DashCamCommand.LockCurrentClip
            "stop" -> DashCamCommand.StopRecording
            else -> null
        }

    private fun parseMedia(json: JSONObject): RemoteMediaItem =
        RemoteMediaItem(
            id = json.getLong("id"),
            type = MediaType.fromStoredValue(json.optString("type")) ?: MediaType.VIDEO,
            mode = RecordingMode.fromStoredValue(json.optString("mode")) ?: RecordingMode.MANUAL,
            path = json.optString("path"),
            thumbnailPath = json.nullableString("thumbnailPath"),
            createdAt = json.getLong("createdAt"),
            durationMs = json.nullableLong("durationMs"),
            sizeBytes = json.getLong("sizeBytes"),
            width = json.nullableInt("width"),
            height = json.nullableInt("height"),
            fps = json.nullableDouble("fps"),
            bitrate = json.nullableInt("bitrate"),
            hasAudio = json.optBoolean("hasAudio", false),
            locked = json.optBoolean("locked", false),
        )

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject {
        put(key, value ?: JSONObject.NULL)
        return this
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.nullableInt(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun JSONObject.nullableLong(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (has(key) && !isNull(key)) getDouble(key) else null
}
