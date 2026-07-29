package com.pg_axis.ytcnv.services

import android.net.Uri
import androidx.core.net.toUri

object MusicAxsContract {
    const val AUTHORITY = "dev.pgaxis.musicaxs.provider"
    const val MIN_MUSICAXS_VERSION = 30L
    const val MIN_YTCNV_VERSION = 69L

    object Playlists {
        val URI: Uri = "content://$AUTHORITY/playlists".toUri()
        const val ID = "id"
        const val NAME = "name"
        const val SONG_COUNT = "song_count"
    }

    object Songs {
        val URI: Uri = "content://$AUTHORITY/songs".toUri()
        const val PLAYLIST_ID = "playlist_id"
        const val SONG_URI = "song_uri"
    }
}