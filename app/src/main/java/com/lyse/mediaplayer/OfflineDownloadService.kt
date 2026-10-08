package com.lyse.mediaplayer

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler

private const val DOWNLOAD_NOTIFICATION_ID = 4102
private const val DOWNLOAD_CHANNEL_ID = "offline_media_downloads"

@UnstableApi
class OfflineDownloadService : DownloadService(
    DOWNLOAD_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DOWNLOAD_CHANNEL_ID,
    R.string.download_notification_channel,
    R.string.download_notification_channel_description
) {
    private val notificationHelper by lazy {
        DownloadNotificationHelper(this, DOWNLOAD_CHANNEL_ID)
    }

    override fun getDownloadManager(): DownloadManager = OfflineDownloads.getManager(this)

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int
    ): Notification = notificationHelper.buildProgressNotification(
        this,
        R.mipmap.ic_launcher,
        null,
        null,
        downloads,
        notMetRequirements
    )
}
