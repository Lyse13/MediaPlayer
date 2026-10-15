package com.lyse.mediaplayer

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/**
 * Catalogue unique de médias, partagé par l'écran (MainActivity) et le service
 * (PlaybackService / MediaLibraryService). Un client externe (MediaBrowser,
 * Android Auto, Assistant...) navigue dans cet arbre : racine -> catégories -> médias.
 */
@UnstableApi
object MediaCatalog {

    const val ROOT_ID = "root"
    const val CATEGORY_VIDEO = "category_video"
    const val CATEGORY_AUDIO = "category_audio"
    const val CATEGORY_STREAMING = "category_streaming"

    const val DASH_URL = "https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd"
    const val HLS_VOD_URL = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    const val HLS_LIVE_URL = "https://live-hls-apps-aje-fa.getaj.net/AJE/index.m3u8"

    data class Category(val id: String, val title: String, val tracks: List<Track>)

    fun categories(context: Context): List<Category> = listOf(
        Category(
            CATEGORY_VIDEO, "Vidéos", listOf(
                Track("Big Buck Bunny (video)", "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"),
            )
        ),
        Category(
            CATEGORY_AUDIO, "Audio", listOf(
                Track("Jazz in Paris (audio)", "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3"),
                Track("Local sample (audio)", "android.resource://${context.packageName}/${R.raw.sample_audio}"),
            )
        ),
        Category(
            CATEGORY_STREAMING, "Streaming", listOf(
                Track("Streaming adaptatif DASH (vidéo)", DASH_URL, MimeTypes.APPLICATION_MPD),
                Track(
                    "DASH avec sous-titres (Elephants Dream)",
                    "https://dash.akamaized.net/akamai/test/caption_test/ElephantsDream/elephants_dream_480p_heaac5_1_https.mpd",
                    MimeTypes.APPLICATION_MPD
                ),
                Track("HLS adaptatif (vidéo à la demande)", HLS_VOD_URL, MimeTypes.APPLICATION_M3U8),
                Track("HLS en direct (test)", HLS_LIVE_URL, MimeTypes.APPLICATION_M3U8),
            )
        ),
    )

    /** Tous les médias, dans le même ordre qu'avant. */
    fun tracks(context: Context): List<Track> = categories(context).flatMap { it.tracks }

    /** Un média jouable : mediaId = url, ce qui permet de le retrouver avec [find]. */
    fun mediaItem(track: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId(track.url)
            .setUri(track.url)
            .setMimeType(track.mimeType)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()

    private fun folder(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()

    fun rootItem(): MediaItem = folder(ROOT_ID, "Ma médiathèque")

    /** Enfants d'un dossier, ou null si [parentId] n'existe pas. */
    fun children(context: Context, parentId: String): List<MediaItem>? {
        val categories = categories(context)
        return if (parentId == ROOT_ID) {
            categories.map { folder(it.id, it.title) }
        } else {
            categories.firstOrNull { it.id == parentId }?.tracks?.map(::mediaItem)
        }
    }

    /** Retrouve un média (ou un dossier) à partir de son mediaId. */
    fun find(context: Context, mediaId: String): MediaItem? {
        if (mediaId == ROOT_ID) return rootItem()
        val categories = categories(context)
        categories.firstOrNull { it.id == mediaId }?.let { return folder(it.id, it.title) }
        return categories.flatMap { it.tracks }.firstOrNull { it.url == mediaId }?.let(::mediaItem)
    }
}