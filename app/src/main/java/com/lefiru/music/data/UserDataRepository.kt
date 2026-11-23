package com.lefiru.music.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.userDataStore by preferencesDataStore(name = "lefiru_user_data")

/**
 * Stores favorites, playlists, and user settings.
 */
class UserDataRepository(context: Context) {

    private val dataStore = context.applicationContext.userDataStore
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val FAVORITES = stringSetPreferencesKey("favorites")
        val FAVORITE_ORDER = stringPreferencesKey("favorite_order")
        val TRACK_ORDER = stringPreferencesKey("track_order")
        val PLAYLISTS = stringPreferencesKey("playlists")
        val SPEED = stringPreferencesKey("playback_speed")
        val EQ_ENABLED = stringPreferencesKey("eq_enabled")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
        val EQ_BASS = stringPreferencesKey("eq_bass")
        val EQ_VOLUME_BOOST = stringPreferencesKey("eq_volume_boost")
    }

    val favorites: Flow<Set<Long>> = dataStore.data.map { prefs ->
        prefs[Keys.FAVORITES].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    /** Manual order of favorite songs (track IDs). */
    val favoriteOrder: Flow<List<Long>> = dataStore.data.map { prefs ->
        decodeIds(prefs[Keys.FAVORITE_ORDER])
    }

    /** Manual order of the full library (track IDs). */
    val trackOrder: Flow<List<Long>> = dataStore.data.map { prefs ->
        decodeIds(prefs[Keys.TRACK_ORDER])
    }

    val playlists: Flow<List<Playlist>> = dataStore.data.map { prefs ->
        decodePlaylists(prefs[Keys.PLAYLISTS])
    }

    val playbackSpeed: Flow<Float> = dataStore.data.map { prefs ->
        prefs[Keys.SPEED]?.toFloatOrNull() ?: 1f
    }

    val equalizerSettings: Flow<EqualizerSettings> = dataStore.data.map { prefs ->
        EqualizerSettings(
            enabled = prefs[Keys.EQ_ENABLED]?.toBooleanStrictOrNull() ?: false,
            bands = prefs[Keys.EQ_BANDS].orEmpty()
                .split(',')
                .mapNotNull { entry ->
                    val parts = entry.split(':')
                    if (parts.size == 2) {
                        val index = parts[0].toIntOrNull()
                        val level = parts[1].toIntOrNull()
                        if (index != null && level != null) index to level else null
                    } else {
                        null
                    }
                }
                .toMap(),
            bassBoost = prefs[Keys.EQ_BASS]?.toIntOrNull() ?: 0,
            volumeBoostPercent = (prefs[Keys.EQ_VOLUME_BOOST]?.toIntOrNull() ?: 100)
                .coerceIn(100, 200),
        )
    }

    suspend fun toggleFavorite(trackId: Long) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITES].orEmpty()
            val id = trackId.toString()
            val adding = id !in current
            prefs[Keys.FAVORITES] = if (adding) current + id else current - id

            val order = decodeIds(prefs[Keys.FAVORITE_ORDER]).toMutableList()
            order.remove(trackId)
            if (adding) order.add(trackId)
            prefs[Keys.FAVORITE_ORDER] = encodeIds(order)
        }
    }

    suspend fun saveTrackOrder(ids: List<Long>) {
        dataStore.edit { it[Keys.TRACK_ORDER] = encodeIds(ids) }
    }

    suspend fun saveFavoriteOrder(ids: List<Long>) {
        dataStore.edit { it[Keys.FAVORITE_ORDER] = encodeIds(ids) }
    }

    suspend fun setPlaylistTrackOrder(playlistId: String, trackIds: List<Long>) {
        updatePlaylists { list ->
            list.map { playlist ->
                if (playlist.id == playlistId) playlist.copy(trackIds = trackIds) else playlist
            }
        }
    }

    suspend fun savePlaybackSpeed(speed: Float) {
        dataStore.edit { it[Keys.SPEED] = speed.toString() }
    }

    suspend fun saveEqualizerSettings(settings: EqualizerSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.EQ_ENABLED] = settings.enabled.toString()
            prefs[Keys.EQ_BANDS] = settings.bands.entries.joinToString(",") { "${it.key}:${it.value}" }
            prefs[Keys.EQ_BASS] = settings.bassBoost.toString()
            prefs[Keys.EQ_VOLUME_BOOST] = settings.volumeBoostPercent.toString()
        }
    }

    suspend fun createPlaylist(name: String, trackIds: List<Long> = emptyList()): Playlist {
        val playlist = Playlist(id = UUID.randomUUID().toString(), name = name, trackIds = trackIds)
        updatePlaylists { it + playlist }
        return playlist
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) {
        updatePlaylists { list ->
            list.map { if (it.id == playlistId) it.copy(name = newName) else it }
        }
    }

    suspend fun deletePlaylist(playlistId: String) {
        updatePlaylists { list -> list.filterNot { it.id == playlistId } }
    }

    suspend fun addTrackToPlaylist(playlistId: String, trackId: Long) {
        updatePlaylists { list ->
            list.map { playlist ->
                if (playlist.id == playlistId && trackId !in playlist.trackIds) {
                    playlist.copy(trackIds = playlist.trackIds + trackId)
                } else {
                    playlist
                }
            }
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: Long) {
        updatePlaylists { list ->
            list.map { playlist ->
                if (playlist.id == playlistId) {
                    playlist.copy(trackIds = playlist.trackIds - trackId)
                } else {
                    playlist
                }
            }
        }
    }

    private suspend fun updatePlaylists(transform: (List<Playlist>) -> List<Playlist>) {
        dataStore.edit { prefs ->
            val updated = transform(decodePlaylists(prefs[Keys.PLAYLISTS]))
            prefs[Keys.PLAYLISTS] = json.encodeToString(updated)
        }
    }

    private fun decodePlaylists(raw: String?): List<Playlist> =
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<Playlist>>(raw) }.getOrDefault(emptyList())
        }

    private fun encodeIds(ids: List<Long>): String =
        ids.joinToString(separator = ",")

    private fun decodeIds(raw: String?): List<Long> =
        raw?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?: emptyList()

    suspend fun currentPlaylists(): List<Playlist> = playlists.first()
}

data class EqualizerSettings(
    val enabled: Boolean = false,
    val bands: Map<Int, Int> = emptyMap(),
    val bassBoost: Int = 0,
    /** 100 = original volume, 200 = maximum boost. */
    val volumeBoostPercent: Int = 100,
)
