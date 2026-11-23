package com.lefiru.music.data

import android.content.ContentUris
import android.net.Uri
import java.util.Locale
import androidx.compose.runtime.Immutable

/**
 * An audio track from the device's library.
 */
@Immutable
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val trackNumber: Int,
    val year: Int,
    val folder: String,
) {
    val artworkUri: Uri get() = albumArtworkUri(albumId)
}

/** Tracks grouped by album. */
@Immutable
data class AlbumGroup(
    val id: Long,
    val name: String,
    val artist: String,
    val artworkUri: Uri,
    val tracks: List<Track>,
)

/** Tracks grouped by artist. */
@Immutable
data class ArtistGroup(
    val name: String,
    val tracks: List<Track>,
    val artworkUri: Uri,
)

fun albumArtworkUri(albumId: Long): Uri =
    if (albumId <= 0L) {
        Uri.EMPTY
    } else {
        ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
    }

fun buildAlbums(tracks: List<Track>): List<AlbumGroup> =
    tracks
        .groupBy { "${it.albumId}|${it.album}" }
        .map { (_, list) ->
            val first = list.first()
            AlbumGroup(
                id = first.albumId,
                name = if (first.album.isBlank()) "Album desconocido" else first.album,
                artist = first.artist,
                artworkUri = first.artworkUri,
                tracks = list.sortedBy { it.trackNumber },
            )
        }
        .sortedBy { it.name.lowercase() }

fun buildArtists(tracks: List<Track>): List<ArtistGroup> =
    tracks
        .groupBy { it.artist }
        .map { (artist, list) ->
            ArtistGroup(
                name = if (artist.isBlank()) "Artista desconocido" else artist,
                tracks = list,
                artworkUri = list.first().artworkUri,
            )
        }
        .sortedBy { it.name.lowercase() }

fun formatDuration(ms: Long): String {
    if (ms <= 0L || ms == Long.MAX_VALUE) return "--:--"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
