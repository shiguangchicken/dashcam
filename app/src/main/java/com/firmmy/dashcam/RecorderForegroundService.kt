package com.firmmy.dashcam

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.firmmy.dashcam.core.common.DashCamFormatters
import com.firmmy.dashcam.core.common.DashCamCommand
import com.firmmy.dashcam.core.common.DashCamResult
import com.firmmy.dashcam.core.common.RecordingMode
import com.firmmy.dashcam.core.common.RecordingStatus
import com.firmmy.dashcam.core.database.DashCamDatabaseProvider
import com.firmmy.dashcam.core.database.SettingsRepository
import com.firmmy.dashcam.core.database.MediaRepository
import com.firmmy.dashcam.core.database.RecordSessionRepository
import com.firmmy.dashcam.core.media.AndroidThumbnailGenerator
import com.firmmy.dashcam.core.media.CameraRecorderManager
import com.firmmy.dashcam.core.media.CameraXCameraFacade
import com.firmmy.dashcam.core.media.DashCamMediaDirectories
import com.firmmy.dashcam.core.media.DashCamMediaRepository
import com.firmmy.dashcam.core.media.RecordingProfiles
import com.firmmy.dashcam.core.media.SegmentRecordingController
import com.firmmy.dashcam.core.voice.VoiceCommandParseResult
import com.firmmy.dashcam.core.voice.VoiceCommandParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class RecorderForegroundService : Service(), LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var dependencies: RecorderServiceDependencies? = null
    private var currentMode: RecordingMode = RecordingMode.DRIVING
    private var currentStatus: RecordingStatus = RecordingStatus.IDLE
    private var audioEnabled: Boolean = true

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        publishRuntimeStatus()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        startForegroundCompat(buildNotification())
        serviceScope.launch {
            withCommandWakeLock {
                handleIntent(intent)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runBlocking {
            dependencies?.controller?.stopRecording(reason = "service_destroyed")
        }
        currentStatus = RecordingStatus.IDLE
        RecorderRuntimeState.clearLivePreviewFrame()
        publishRuntimeStatus()
        serviceScope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    private suspend fun handleIntent(intent: Intent?) {
        val action = intent?.action ?: ACTION_START_DRIVING
        when (action) {
            ACTION_START_DRIVING -> startOrSwitch(RecordingMode.DRIVING, intent)
            ACTION_START_PARKING -> startOrSwitch(RecordingMode.PARKING, intent)
            ACTION_STOP -> stopRecording()
            ACTION_TAKE_PHOTO -> dependencies().controller.takePhoto()
            ACTION_SWITCH_DRIVING -> startOrSwitch(RecordingMode.DRIVING, intent)
            ACTION_SWITCH_PARKING -> startOrSwitch(RecordingMode.PARKING, intent)
            ACTION_ENABLE_AUDIO -> setAudioEnabled(true)
            ACTION_DISABLE_AUDIO -> setAudioEnabled(false)
            ACTION_LOCK_CURRENT_CLIP -> Log.w(TAG, "Voice command LockCurrentClip is not supported by the recorder service yet")
            ACTION_VOICE_COMMAND_TEXT -> handleVoiceCommandText(intent)
        }
        if ((action == ACTION_ENABLE_AUDIO || action == ACTION_DISABLE_AUDIO) &&
            currentStatus == RecordingStatus.IDLE
        ) {
            stopForegroundCompat()
            stopSelf()
            return
        }
        if (action != ACTION_STOP) {
            publishRuntimeStatus()
            updateNotification()
        }
    }

    private suspend fun startOrSwitch(
        mode: RecordingMode,
        intent: Intent?,
    ) {
        val deps = dependencies()
        val settings = deps.settingsRepository.getSettings()
        val audioOverride = if (intent?.hasExtra(EXTRA_AUDIO_ENABLED) == true) {
            intent.getBooleanExtra(EXTRA_AUDIO_ENABLED, settings.audioEnabled)
        } else {
            settings.audioEnabled
        }
        val segmentDurationMinutes = if (intent?.hasExtra(EXTRA_SEGMENT_DURATION_MINUTES) == true) {
            intent.getIntExtra(EXTRA_SEGMENT_DURATION_MINUTES, settings.segmentDurationMinutes)
        } else {
            settings.segmentDurationMinutes
        }
        val profile = when (mode) {
            RecordingMode.PARKING -> RecordingProfiles.parking(settings).copy(audioEnabled = audioOverride)
            else -> RecordingProfiles.driving(settings).copy(audioEnabled = audioOverride)
        }
        audioEnabled = profile.audioEnabled
        val result = if (currentStatus == RecordingStatus.IDLE) {
            deps.controller.startRecording(profile, segmentDurationMinutes)
        } else {
            deps.controller.switchRecording(profile, segmentDurationMinutes)
        }
        if (result is DashCamResult.Success) {
            currentMode = profile.mode
            currentStatus = profile.mode.toRecordingStatus()
            publishRuntimeStatus()
        } else if (result is DashCamResult.Failure) {
            Log.w(TAG, "Recording command failed: ${result.error.message}")
        }
    }

    private suspend fun setAudioEnabled(enabled: Boolean) {
        val repository = dependencies().settingsRepository
        val settings = repository.getSettings()
        repository.saveSettings(settings.copy(audioEnabled = enabled))
        audioEnabled = enabled
        publishRuntimeStatus()
    }

    private suspend fun stopRecording() {
        val result = dependencies().controller.stopRecording()
        if (result is DashCamResult.Success || currentStatus != RecordingStatus.IDLE) {
            currentStatus = RecordingStatus.IDLE
        }
        RecorderRuntimeState.clearLivePreviewFrame()
        publishRuntimeStatus()
        if (result is DashCamResult.Failure) {
            Log.w(TAG, "Stop recording failed: ${result.error.message}")
        }
        stopForegroundCompat()
        stopSelf()
    }

    private suspend fun handleVoiceCommandText(intent: Intent?) {
        val text = intent?.getStringExtra(EXTRA_VOICE_COMMAND_TEXT).orEmpty()
        when (val parsed = VoiceCommandParser().parse(text)) {
            is VoiceCommandParseResult.Recognized -> {
                val action = actionForVoiceCommand(parsed.command)
                if (action == null) {
                    Log.w(TAG, "Voice command ${parsed.command} has no recorder service action")
                    return
                }
                handleIntent(Intent(this, RecorderForegroundService::class.java).setAction(action))
            }

            is VoiceCommandParseResult.Unrecognized -> {
                Log.w(TAG, "Voice command not recognized: ${parsed.reason}, input='${parsed.rawInput}'")
            }
        }
    }

    private fun dependencies(): RecorderServiceDependencies =
        dependencies ?: buildDependencies().also { dependencies = it }

    private fun buildDependencies(): RecorderServiceDependencies {
        val database = DashCamDatabaseProvider.get(this)
        val directories = DashCamMediaDirectories.fromContext(this)
        val dashCamMediaRepository = DashCamMediaRepository(
            mediaRepository = MediaRepository(database.mediaFileDao()),
            directories = directories,
            thumbnailGenerator = AndroidThumbnailGenerator(),
        )
        val recorderManager = CameraRecorderManager(
            directories = directories,
            mediaRepository = dashCamMediaRepository,
            cameraFacade = CameraXCameraFacade(
                context = applicationContext,
                lifecycleOwner = this,
            ),
        )
        return RecorderServiceDependencies(
            directories = directories,
            settingsRepository = SettingsRepository(database.appSettingDao()),
            controller = SegmentRecordingController(
                recorderManager = recorderManager,
                mediaRepository = dashCamMediaRepository,
                recordSessionRepository = RecordSessionRepository(database.recordSessionDao()),
                scope = serviceScope,
            ),
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "DashCam recording",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun publishRuntimeStatus() {
        val remaining = dependencies?.directories?.ensureBaseDirectories()?.root?.usableSpace ?: 0L
        RecorderRuntimeState.updateStatus(
            recordingStatus = currentStatus,
            mode = currentMode,
            audioEnabled = audioEnabled,
            freeSpaceBytes = remaining,
        )
    }

    private fun buildNotification(): Notification {
        val remaining = dependencies?.directories?.ensureBaseDirectories()?.root?.usableSpace ?: 0L
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            pendingIntentFlags(),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("DashCam ${currentStatus.label}")
            .setContentText(
                "${currentMode.label} | Audio ${if (audioEnabled) "on" else "off"} | " +
                    DashCamFormatters.formatFileSize(remaining),
            )
            .setOngoing(currentStatus != RecordingStatus.IDLE)
            .setContentIntent(contentIntent)
            .addAction(notificationAction(ACTION_STOP, "Stop", 1))
            .addAction(notificationAction(ACTION_TAKE_PHOTO, "Photo", 2))
            .addAction(notificationAction(ACTION_SWITCH_DRIVING, "Driving", 3))
            .addAction(notificationAction(ACTION_SWITCH_PARKING, "Parking", 4))
            .build()
    }

    private fun notificationAction(
        action: String,
        label: String,
        requestCode: Int,
    ): NotificationCompat.Action {
        val intent = Intent(this, RecorderForegroundService::class.java).setAction(action)
        val pendingIntent = PendingIntent.getService(this, requestCode, intent, pendingIntentFlags())
        return NotificationCompat.Action(R.drawable.ic_launcher, label, pendingIntent)
    }

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, foregroundServiceTypes())
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun foregroundServiceTypes(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

    private suspend fun withCommandWakeLock(block: suspend () -> Unit) {
        val wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DashCam:RecorderCommand")
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
        try {
            block()
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    private fun RecordingMode.toRecordingStatus(): RecordingStatus =
        when (this) {
            RecordingMode.PARKING -> RecordingStatus.RECORDING_PARKING
            RecordingMode.EVENT -> RecordingStatus.EVENT_BOOST_RECORDING
            else -> RecordingStatus.RECORDING_DRIVING
        }

    private data class RecorderServiceDependencies(
        val directories: DashCamMediaDirectories,
        val settingsRepository: SettingsRepository,
        val controller: SegmentRecordingController,
    )

    companion object {
        const val ACTION_START_DRIVING = "com.firmmy.dashcam.action.START_DRIVING"
        const val ACTION_START_PARKING = "com.firmmy.dashcam.action.START_PARKING"
        const val ACTION_STOP = "com.firmmy.dashcam.action.STOP"
        const val ACTION_TAKE_PHOTO = "com.firmmy.dashcam.action.TAKE_PHOTO"
        const val ACTION_SWITCH_DRIVING = "com.firmmy.dashcam.action.SWITCH_DRIVING"
        const val ACTION_SWITCH_PARKING = "com.firmmy.dashcam.action.SWITCH_PARKING"
        const val ACTION_ENABLE_AUDIO = "com.firmmy.dashcam.action.ENABLE_AUDIO"
        const val ACTION_DISABLE_AUDIO = "com.firmmy.dashcam.action.DISABLE_AUDIO"
        const val ACTION_LOCK_CURRENT_CLIP = "com.firmmy.dashcam.action.LOCK_CURRENT_CLIP"
        const val ACTION_VOICE_COMMAND_TEXT = "com.firmmy.dashcam.action.VOICE_COMMAND_TEXT"
        const val EXTRA_SEGMENT_DURATION_MINUTES = "com.firmmy.dashcam.extra.SEGMENT_DURATION_MINUTES"

        private const val CHANNEL_ID = "dashcam_recording"
        private const val NOTIFICATION_ID = 1001
        private const val WAKE_LOCK_TIMEOUT_MS = 15_000L
        private const val TAG = "RecorderForeground"

        private const val EXTRA_AUDIO_ENABLED = "com.firmmy.dashcam.extra.AUDIO_ENABLED"
        private const val EXTRA_VOICE_COMMAND_TEXT = "com.firmmy.dashcam.extra.VOICE_COMMAND_TEXT"

        fun commandIntent(
            context: Context,
            action: String,
            audioEnabled: Boolean? = null,
            segmentDurationMinutes: Int? = null,
        ): Intent =
            Intent(context, RecorderForegroundService::class.java)
                .setAction(action)
                .also { intent ->
                    if (audioEnabled != null) {
                        intent.putExtra(EXTRA_AUDIO_ENABLED, audioEnabled)
                    }
                    if (segmentDurationMinutes != null) {
                        intent.putExtra(EXTRA_SEGMENT_DURATION_MINUTES, segmentDurationMinutes)
                    }
                }

        fun actionForVoiceCommand(command: DashCamCommand): String? =
            when (command) {
                DashCamCommand.StartDrivingMode -> ACTION_SWITCH_DRIVING
                DashCamCommand.StartParkingMode -> ACTION_SWITCH_PARKING
                DashCamCommand.TakePhoto -> ACTION_TAKE_PHOTO
                DashCamCommand.EnableAudio -> ACTION_ENABLE_AUDIO
                DashCamCommand.DisableAudio -> ACTION_DISABLE_AUDIO
                DashCamCommand.LockCurrentClip -> ACTION_LOCK_CURRENT_CLIP
                DashCamCommand.StopRecording -> ACTION_STOP
                DashCamCommand.StartHotspot,
                DashCamCommand.StopHotspot,
                -> null
            }

        fun voiceCommandIntent(
            context: Context,
            text: String,
        ): Intent =
            Intent(context, RecorderForegroundService::class.java)
                .setAction(ACTION_VOICE_COMMAND_TEXT)
                .putExtra(EXTRA_VOICE_COMMAND_TEXT, text)
    }
}
