package com.firmmy.dashcam

import android.content.Context
import com.firmmy.dashcam.core.common.DashCamCommand
import com.firmmy.dashcam.core.database.DashCamDatabaseProvider
import com.firmmy.dashcam.core.database.MediaRepository
import com.firmmy.dashcam.core.database.SettingsRepository
import com.firmmy.dashcam.core.media.AndroidThumbnailGenerator
import com.firmmy.dashcam.core.media.DashCamMediaDirectories
import com.firmmy.dashcam.core.media.DashCamMediaRepository
import com.firmmy.dashcam.core.network.AndroidNsdRemoteServiceAdvertiser
import com.firmmy.dashcam.core.network.EmbeddedHttpServer
import com.firmmy.dashcam.core.network.RemoteViewerClientInfo

class AppRemoteServerController(
    context: Context,
    private val onHotspotCommand: (DashCamCommand) -> Boolean,
) {
    private val applicationContext = context.applicationContext
    private var server: EmbeddedHttpServer? = null
    private val advertiser = AndroidNsdRemoteServiceAdvertiser(applicationContext)

    fun start() {
        if (server != null) return
        val database = DashCamDatabaseProvider.get(applicationContext)
        val settingsRepository = SettingsRepository(database.appSettingDao())
        val mediaRepository = MediaRepository(database.mediaFileDao())
        val directories = DashCamMediaDirectories.fromContext(applicationContext)
        val dashCamMediaRepository = DashCamMediaRepository(
            mediaRepository = mediaRepository,
            directories = directories,
            thumbnailGenerator = AndroidThumbnailGenerator(),
        )
        val created = EmbeddedHttpServer(
            dataSource = AppRemoteDataSource(
                settingsRepository = settingsRepository,
                mediaRepository = mediaRepository,
                dashCamMediaRepository = dashCamMediaRepository,
                directories = directories,
            ),
            commandDispatcher = AppRemoteCommandDispatcher(
                context = applicationContext,
                onHotspotCommand = onHotspotCommand,
            ),
        )
        created.start()
        advertiser.register()
        server = created
    }

    fun stop() {
        advertiser.unregister()
        server?.stop()
        server = null
    }

    fun activeRemoteViewers(): List<RemoteViewerClientInfo> =
        server?.activeRemoteViewers().orEmpty()
}
