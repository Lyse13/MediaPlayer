package com.lyse.mediaplayer

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

@UnstableApi
object OfflineDownloads {
    private const val CACHE_DIRECTORY = "media3_offline_downloads"

    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var downloadCache: SimpleCache? = null
    private var downloadManager: DownloadManager? = null
    private val downloadExecutor = Executors.newFixedThreadPool(3)

    @Synchronized
    private fun databaseProvider(context: Context): StandaloneDatabaseProvider =
        databaseProvider ?: StandaloneDatabaseProvider(context.applicationContext).also {
            databaseProvider = it
        }

    @Synchronized
    fun getCache(context: Context): SimpleCache = downloadCache ?: SimpleCache(
        File(context.applicationContext.filesDir, CACHE_DIRECTORY),
        NoOpCacheEvictor(),
        databaseProvider(context)
    ).also { downloadCache = it }

    @Synchronized
    fun getManager(context: Context): DownloadManager = downloadManager ?: run {
        val appContext = context.applicationContext
        DownloadManager(
            appContext,
            databaseProvider(appContext),
            getCache(appContext),
            DefaultHttpDataSource.Factory(),
            downloadExecutor
        ).apply {
            maxParallelDownloads = 2
            downloadManager = this
        }
    }

    fun createPlaybackDataSourceFactory(
        context: Context,
        playbackCacheDataSourceFactory: DataSource.Factory
    ): DataSource.Factory {
        val cache = getCache(context)
        val offlineReadFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context.applicationContext))
            .setCacheWriteDataSinkFactory(null)
        return DownloadAwareDataSourceFactory(
            cache,
            offlineReadFactory,
            playbackCacheDataSourceFactory
        )
    }

    private class DownloadAwareDataSourceFactory(
        private val downloadCache: SimpleCache,
        private val offlineReadFactory: DataSource.Factory,
        private val streamingFactory: DataSource.Factory
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = DownloadAwareDataSource(
            downloadCache,
            offlineReadFactory,
            streamingFactory
        )
    }

    private class DownloadAwareDataSource(
        private val downloadCache: SimpleCache,
        private val offlineReadFactory: DataSource.Factory,
        private val streamingFactory: DataSource.Factory
    ) : DataSource {
        private val transferListeners = mutableListOf<TransferListener>()
        private var activeDataSource: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            transferListeners += transferListener
            activeDataSource?.addTransferListener(transferListener)
        }

        @Throws(IOException::class)
        override fun open(dataSpec: DataSpec): Long {
            val cacheKey = dataSpec.key ?: dataSpec.uri.toString()
            val requestedLength = if (dataSpec.length.toInt() == C.LENGTH_UNSET) {
                Long.MAX_VALUE
            } else {
                dataSpec.length
            }
            val hasDownloadedBytes = downloadCache.getCachedLength(
                cacheKey,
                dataSpec.position,
                requestedLength
            ) > 0L
            val dataSource = (if (hasDownloadedBytes) offlineReadFactory else streamingFactory)
                .createDataSource()
            transferListeners.forEach(dataSource::addTransferListener)
            activeDataSource = dataSource
            return dataSource.open(dataSpec)
        }

        @Throws(IOException::class)
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            activeDataSource?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

        override fun getUri(): Uri? = activeDataSource?.uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            activeDataSource?.responseHeaders ?: emptyMap()

        @Throws(IOException::class)
        override fun close() {
            activeDataSource?.close()
            activeDataSource = null
        }
    }
}
