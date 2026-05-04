package com.pg_axis.ytcnv.services

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object MusicAxsClient {

    fun isMusicAxsInstalled(context: Context): Boolean {
        return try {
            val info = context.packageManager.getPackageInfo("com.pg_axis.musicaxs", 0)
            val versionCode = info.longVersionCode
            versionCode >= MusicAxsContract.MIN_MUSICAXS_VERSION
        } catch (_: PackageManager.NameNotFoundException) { false }
    }

    data class PlaylistInfo(val id: Long, val name: String, val songCount: Int)

    fun getPlaylists(context: Context): List<PlaylistInfo> {
        return try {
            context.contentResolver.query(
                MusicAxsContract.Playlists.URI,
                null, null, null, null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MusicAxsContract.Playlists.ID)
                val nameCol = cursor.getColumnIndexOrThrow(MusicAxsContract.Playlists.NAME)
                val countCol = cursor.getColumnIndexOrThrow(MusicAxsContract.Playlists.SONG_COUNT)
                buildList {
                    while (cursor.moveToNext()) {
                        add(PlaylistInfo(
                            cursor.getLong(idCol),
                            cursor.getString(nameCol),
                            cursor.getInt(countCol)
                        ))
                    }
                }
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun addSongToPlaylist(context: Context, songUri: Uri, playlistId: Long): Boolean {
        val path = resolveSystemPath(context, songUri) ?: return false

        return suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, scannedUri ->

                val songId = scannedUri?.let { ContentUris.parseId(it) }

                if (songId == null) {
                    cont.resume(false); return@scanFile
                }

                val values = ContentValues().apply {
                    put(MusicAxsContract.Songs.PLAYLIST_ID, playlistId)
                    put(MusicAxsContract.Songs.SONG_ID, songId)
                }
                val insertResult = context.contentResolver.insert(MusicAxsContract.Songs.URI, values)
                cont.resume(insertResult != null)
            }
        }
    }

    private fun resolveSystemPath(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri,
            arrayOf(MediaStore.Audio.Media.DATA), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0)?.also { p -> return p } }

        when (uri.authority) {
            "com.android.externalstorage.documents" -> {
                val docId = DocumentsContract.getDocumentId(uri)
                val parts = docId.split(":", limit = 2)
                if (parts.size == 2) {
                    val root = if (parts[0] == "primary")
                        Environment.getExternalStorageDirectory().absolutePath
                    else
                        "/storage/${parts[0]}"
                    return "$root/${parts[1]}"
                }
            }

            "com.android.providers.downloads.documents" -> {
                val docId = DocumentsContract.getDocumentId(uri)
                // Newer Android: may be "raw:/storage/emulated/0/Download/foo.mp3"
                if (docId.startsWith("raw:")) {
                    return docId.removePrefix("raw:")
                }
                // Older Android: numeric ID, resolve via Downloads ContentProvider
                val downloadsUri = ContentUris.withAppendedId(
                    "content://downloads/public_downloads".toUri(),
                    docId.toLongOrNull() ?: return null
                )
                context.contentResolver.query(downloadsUri,
                    arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                    ?.use { if (it.moveToFirst()) return it.getString(0) }
            }
        }

        return null
    }
}