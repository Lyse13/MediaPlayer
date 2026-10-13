package com.lyse.mediaplayer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun saveVideoToMovies(context: Context, source: File): Uri =
    withContext(Dispatchers.IO) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "MediaStore export requires Android 10 or newer"
        }

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "MediaPlayer-${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/MediaPlayer"
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val destination = resolver.insert(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values
        ) ?: throw IOException("Impossible de créer le fichier vidéo dans Films.")

        try {
            copyIntoUri(context, source, destination)
            val published = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            resolver.update(destination, published, null, null)
            destination
        } catch (exception: Exception) {
            resolver.delete(destination, null, null)
            throw exception
        }
    }

internal suspend fun copyVideoToDocument(context: Context, source: File, destination: Uri) =
    withContext(Dispatchers.IO) {
        copyIntoUri(context, source, destination)
    }

private fun copyIntoUri(context: Context, source: File, destination: Uri) {
    val output = context.contentResolver.openOutputStream(destination, "w")
        ?: throw IOException("Impossible d’ouvrir l’emplacement choisi.")
    source.inputStream().use { input ->
        output.use { input.copyTo(it) }
    }
}
