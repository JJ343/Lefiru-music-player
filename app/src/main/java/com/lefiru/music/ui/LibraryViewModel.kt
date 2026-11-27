package com.lefiru.music.ui

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lefiru.music.data.AlbumGroup
import com.lefiru.music.data.ArtistGroup
import com.lefiru.music.data.MediaScanner
import com.lefiru.music.data.Playlist
import com.lefiru.music.data.Track
import com.lefiru.music.data.UserDataRepository
import com.lefiru.music.data.buildAlbums
import com.lefiru.music.data.buildArtists
import com.lefiru.music.playback.EqualizerController
import com.lefiru.music.playback.PlaybackConnection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class LibraryState(
    val tracks: List<Track> = emptyList(),
    val albums: List<AlbumGroup> = emptyList(),
    val artists: List<ArtistGroup> = emptyList(),
    val tracksById: Map<Long, Track> = emptyMap(),
    val loading: Boolean = false,
    val scanned: Boolean = false,
    val error: String? = null,
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UserDataRepository(application)
    private val connection = PlaybackConnection(application)
    private val appContentResolver: android.content.ContentResolver = application.contentResolver

    private val _library = MutableStateFlow(LibraryState())
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    /** Identifies the latest library scan so older scans cannot overwrite it. */
    private var refreshGeneration = 0

    val playback = connection.state
    /** Isolated playback position: the UI collects it separately from the rest of the state. */
    val playbackPosition = connection.positionMs
    val equalizerState = EqualizerController.state

    /** Remaining sleep-timer milliseconds, or null when it is off. */
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()
    private var sleepTimerJob: Job? = null

    // ---- Stopwatch ----

    private val _stopwatchElapsedMs = MutableStateFlow(0L)
    val stopwatchElapsedMs: StateFlow<Long> = _stopwatchElapsedMs.asStateFlow()

    private val _stopwatchRunning = MutableStateFlow(false)
    val stopwatchRunning: StateFlow<Boolean> = _stopwatchRunning.asStateFlow()

    /** Accumulated laps (total elapsed time at each lap). */
    private val _stopwatchLaps = MutableStateFlow<List<Long>>(emptyList())
    val stopwatchLaps: StateFlow<List<Long>> = _stopwatchLaps.asStateFlow()

    private var stopwatchJob: Job? = null
    private var stopwatchAccumulatedMs = 0L
    private var stopwatchStartedAt = 0L

    val favorites: StateFlow<Set<Long>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _favoriteOrder = MutableStateFlow<List<Long>>(emptyList())

    /** Favorite tracks in the order chosen by the user. */
    val favoriteTracks: StateFlow<List<Track>> = combine(
        _library,
        favorites,
        _favoriteOrder,
    ) { library, favoriteIds, order ->
        val position = order.withIndex().associate { (index, id) -> id to index }
        val libraryPosition = library.tracks.withIndex().associate { (index, track) -> track.id to index }
        library.tracks
            .filter { it.id in favoriteIds }
            .sortedWith(
                compareBy(
                    { position[it.id] ?: Int.MAX_VALUE },
                    { libraryPosition[it.id] ?: Int.MAX_VALUE },
                ),
            )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        connection.connect()
        viewModelScope.launch {
            repository.favoriteOrder.collect { _favoriteOrder.value = it }
        }
        viewModelScope.launch {
            runCatching {
                EqualizerController.restore(repository.equalizerSettings.first())
            }
        }
        viewModelScope.launch {
            runCatching {
                val speed = repository.playbackSpeed.first()
                if (speed != 1f) connection.setSpeed(speed)
            }
        }
        viewModelScope.launch {
            runCatching {
                connection.setVolume(repository.playbackVolume.first())
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            refresh()
        } else {
            _library.update { it.copy(loading = false) }
        }
    }

    fun refresh(force: Boolean = false) {
        if (_library.value.loading && !force) return
        // Only the most recent scan may update the library. This prevents an
        // older scan (started before a delete) from bringing deleted songs back.
        val generation = ++refreshGeneration
        viewModelScope.launch {
            _library.update { it.copy(loading = true, error = null) }
            val result = runCatching { MediaScanner.scan(getApplication()) }
            if (generation != refreshGeneration) return@launch
            val scanned = result.getOrElse { error ->
                _library.update {
                    it.copy(loading = false, error = error.message ?: "Error leyendo la musica")
                }
                return@launch
            }
            val savedOrder = runCatching { repository.trackOrder.first() }.getOrDefault(emptyList())
            if (generation != refreshGeneration) return@launch
            val tracks = applyTrackOrder(scanned, savedOrder)
            _library.update {
                it.copy(
                    tracks = tracks,
                    albums = buildAlbums(tracks),
                    artists = buildArtists(tracks),
                    tracksById = tracks.associateBy { track -> track.id },
                    loading = false,
                    scanned = true,
                    error = null,
                )
            }
        }
    }

    // ---- Playback ----

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        connection.playQueue(tracks, startIndex)
    }

    fun playAlbum(album: AlbumGroup) = playTracks(album.tracks, 0)

    fun playArtist(artist: ArtistGroup) = playTracks(artist.tracks, 0)

    fun playPlaylist(playlist: Playlist) {
        val tracks = playlist.trackIds.mapNotNull { _library.value.tracksById[it] }
        playTracks(tracks, 0)
    }

    fun playFavorites() {
        playTracks(favoriteTracks.value, 0)
    }

    // ---- Manual ordering (drag and drop) ----

    /** Moves a track inside the library (optimistic in-memory change). */
    fun moveTrack(from: Int, to: Int) {
        _library.update { state ->
            val list = state.tracks.toMutableList()
            if (from !in list.indices || to !in list.indices || from == to) {
                state
            } else {
                list.add(to, list.removeAt(from))
                state.copy(tracks = list)
            }
        }
    }

    /** Saves the current library order. */
    fun persistTrackOrder() {
        val ids = _library.value.tracks.map { it.id }
        viewModelScope.launch { repository.saveTrackOrder(ids) }
    }

    /** Moves a track inside favorites (optimistic in-memory change). */
    fun moveFavorite(from: Int, to: Int) {
        val list = favoriteTracks.value.map { it.id }.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        list.add(to, list.removeAt(from))
        _favoriteOrder.value = list
    }

    /** Saves the current favorites order. */
    fun persistFavoriteOrder() {
        val ids = favoriteTracks.value.map { it.id }
        viewModelScope.launch { repository.saveFavoriteOrder(ids) }
    }

    /** Saves the track order of a playlist. */
    fun reorderPlaylistTracks(playlistId: String, trackIds: List<Long>) {
        viewModelScope.launch { repository.setPlaylistTrackOrder(playlistId, trackIds) }
    }

    fun togglePlayPause() = connection.togglePlayPause()
    fun next() = connection.next()
    fun previous() = connection.previous()
    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)
    fun playAt(index: Int) = connection.playAt(index)
    fun toggleShuffle() = connection.toggleShuffle()
    fun cycleRepeatMode() = connection.cycleRepeatMode()

    fun setSpeed(speed: Float) {
        connection.setSpeed(speed)
        viewModelScope.launch { repository.savePlaybackSpeed(speed) }
    }

    /** App-level volume (0f..1f), applied on top of the phone's media volume. */
    fun setVolume(volume: Float) {
        connection.setVolume(volume)
    }

    /** Saves the current volume when the slider is released. */
    fun persistVolume() {
        val volume = playback.value.volume
        viewModelScope.launch { repository.savePlaybackVolume(volume) }
    }

    // ---- Sleep timer ----

    /** Starts a countdown that pauses playback when it ends. */
    fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) return
        sleepTimerJob?.cancel()
        val totalMs = minutes * 60_000L
        _sleepTimerRemainingMs.value = totalMs
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalMs
            while (remaining > 0) {
                delay(1_000L)
                remaining = (remaining - 1_000L).coerceAtLeast(0L)
                _sleepTimerRemainingMs.value = remaining
            }
            connection.pause()
            _sleepTimerRemainingMs.value = null
            sleepTimerJob = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = null
    }

    // ---- Stopwatch: start/pause, reset, and laps ----

    /** Starts or pauses the stopwatch without losing elapsed time. */
    fun toggleStopwatch() {
        if (_stopwatchRunning.value) {
            stopwatchAccumulatedMs += SystemClock.elapsedRealtime() - stopwatchStartedAt
            stopwatchJob?.cancel()
            stopwatchJob = null
            _stopwatchRunning.value = false
            _stopwatchElapsedMs.value = stopwatchAccumulatedMs
        } else {
            stopwatchStartedAt = SystemClock.elapsedRealtime()
            _stopwatchRunning.value = true
            stopwatchJob = viewModelScope.launch {
                while (isActive) {
                    _stopwatchElapsedMs.value = stopwatchAccumulatedMs +
                        (SystemClock.elapsedRealtime() - stopwatchStartedAt)
                    delay(33L)
                }
            }
        }
    }

    /** Resets the stopwatch to zero and clears all laps. */
    fun resetStopwatch() {
        stopwatchJob?.cancel()
        stopwatchJob = null
        stopwatchAccumulatedMs = 0L
        stopwatchStartedAt = 0L
        _stopwatchRunning.value = false
        _stopwatchElapsedMs.value = 0L
        _stopwatchLaps.value = emptyList()
    }

    /** Records a lap with the current total elapsed time. */
    fun addStopwatchLap() {
        if (!_stopwatchRunning.value) return
        _stopwatchLaps.value = _stopwatchLaps.value + _stopwatchElapsedMs.value
    }

    // ---- Favorites and playlists ----

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch { repository.toggleFavorite(trackId) }
    }

    fun createPlaylist(name: String, trackId: Long? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name.trim(), listOfNotNull(trackId))
        }
    }

    fun addToPlaylist(playlistId: String, trackId: Long) {
        viewModelScope.launch { repository.addTrackToPlaylist(playlistId, trackId) }
    }

    fun removeFromPlaylist(playlistId: String, trackId: Long) {
        viewModelScope.launch { repository.removeTrackFromPlaylist(playlistId, trackId) }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch { repository.deletePlaylist(playlistId) }
    }

    // ---- Borrar canciones del dispositivo ----

    /** Resultado de un borrado. La UI lo muestra una vez y llama a [consumeDeleteResult]. */
    private val _deleteResult = MutableStateFlow<DeleteResult?>(null)
    val deleteResult: StateFlow<DeleteResult?> = _deleteResult.asStateFlow()

    /** Confirmacion del sistema que la UI debe lanzar antes de borrar los archivos. */
    private val _deleteRequest = MutableStateFlow<DeleteRequest?>(null)
    val deleteRequest: StateFlow<DeleteRequest?> = _deleteRequest.asStateFlow()

    private var deleteRemaining: List<Track> = emptyList()
    private val deletedTrackIds = mutableSetOf<Long>()
    private var deleteTotal = 0
    private var deleteDone = 0
    private var deleteRunning = false

    /** Elimina el archivo de audio del dispositivo. */
    fun deleteTrack(track: Track) {
        deleteTracks(listOf(track))
    }

    /** Elimina varios archivos de audio del dispositivo. */
    fun deleteTracks(tracks: List<Track>) {
        if (tracks.isEmpty() || deleteRunning) return
        deleteRunning = true
        deleteTotal = tracks.size
        deleteDone = 0
        deletedTrackIds.clear()
        deleteRemaining = tracks
        continueDelete()
    }

    private fun continueDelete() {
        if (deleteRemaining.isEmpty()) {
            finishDelete()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestSystemDelete()
        } else {
            viewModelScope.launch(Dispatchers.IO) { deleteDirectly() }
        }
    }

    /**
     * Android 11+: el sistema muestra un dialogo de confirmacion y borra los
     * archivos el mismo cuando el usuario acepta.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestSystemDelete() {
        val batch = deleteRemaining.take(MAX_DELETE_REQUEST_SIZE)
        val pendingIntent = runCatching {
            MediaStore.createDeleteRequest(appContentResolver, batch.map { it.uri })
        }.getOrNull()

        if (pendingIntent == null) {
            // Si no se pudo crear la solicitud, intentamos borrar directamente
            // lo que la app tenga permitido.
            viewModelScope.launch(Dispatchers.IO) { deleteDirectly() }
            return
        }

        _deleteRequest.value = DeleteRequest(
            intentSender = pendingIntent.intentSender,
            tracks = batch,
            systemDeletes = true,
        )
    }

    /** Android 10 y anteriores: borra directamente, pidiendo permiso si hace falta. */
    private suspend fun deleteDirectly() {
        val pending = deleteRemaining
        val deletedTracks = mutableListOf<Track>()
        var index = 0

        while (index < pending.size) {
            val track = pending[index]
            when (val outcome = deleteOne(track)) {
                DeleteOutcome.Deleted -> deletedTracks += track
                is DeleteOutcome.NeedsConsent -> {
                    withContext(Dispatchers.Main) {
                        deleteDone += deletedTracks.size
                        deletedTrackIds += deletedTracks.map { it.id }
                        deleteRemaining = pending.drop(index)
                        _deleteRequest.value = DeleteRequest(
                            intentSender = outcome.intentSender,
                            tracks = listOf(track),
                            systemDeletes = false,
                        )
                    }
                    return
                }
                DeleteOutcome.Failed -> Unit
            }
            index++
        }

        withContext(Dispatchers.Main) {
            deleteDone += deletedTracks.size
            deletedTrackIds += deletedTracks.map { it.id }
            deleteRemaining = emptyList()
            finishDelete()
        }
    }

    private fun deleteOne(track: Track): DeleteOutcome =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            deleteOneWithConsent(track)
        } else {
            runCatching { appContentResolver.delete(track.uri, null, null) }
                .fold(
                    onSuccess = { count ->
                        if (count > 0) DeleteOutcome.Deleted else DeleteOutcome.Failed
                    },
                    onFailure = { DeleteOutcome.Failed },
                )
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteOneWithConsent(track: Track): DeleteOutcome =
        try {
            if (appContentResolver.delete(track.uri, null, null) > 0) {
                DeleteOutcome.Deleted
            } else {
                DeleteOutcome.Failed
            }
        } catch (e: RecoverableSecurityException) {
            DeleteOutcome.NeedsConsent(e.userAction.actionIntent.intentSender)
        } catch (e: Exception) {
            DeleteOutcome.Failed
        }

    /** La UI informa si el usuario acepto o cancelo la confirmacion del sistema. */
    fun onDeleteRequestResult(approved: Boolean) {
        val request = _deleteRequest.value ?: return
        _deleteRequest.value = null

        if (!approved) {
            finishDelete(cancelled = true)
            return
        }

        if (request.systemDeletes) {
            // El sistema ya borro estos archivos.
            deleteDone += request.tracks.size
            deletedTrackIds += request.tracks.map { it.id }
            deleteRemaining = deleteRemaining.drop(request.tracks.size)
            continueDelete()
        } else {
            // Android 10: el permiso ya fue concedido; reintentamos el borrado.
            viewModelScope.launch(Dispatchers.IO) { deleteDirectly() }
        }
    }

    private fun finishDelete(cancelled: Boolean = false) {
        deleteRunning = false
        deleteRemaining = emptyList()
        _deleteRequest.value = null

        val deletedIds = deletedTrackIds.toSet()
        if (deletedIds.isNotEmpty()) {
            // Quita las canciones de la biblioteca de inmediato, sin esperar al escaneo.
            _library.update { state ->
                val tracks = state.tracks.filterNot { it.id in deletedIds }
                state.copy(
                    tracks = tracks,
                    albums = buildAlbums(tracks),
                    artists = buildArtists(tracks),
                    tracksById = tracks.associateBy { track -> track.id },
                )
            }
        }
        // Force a fresh scan after a completed delete (and after a partial one)
        // so an in-flight scan from before cannot restore removed songs.
        if (!cancelled || deletedIds.isNotEmpty()) {
            refresh(force = true)
        }

        _deleteResult.value = DeleteResult(
            success = !cancelled && deleteDone == deleteTotal,
            deletedCount = deleteDone,
            total = deleteTotal,
            cancelled = cancelled,
        )
    }

    /** Marca el ultimo resultado como mostrado para que no se repita. */
    fun consumeDeleteResult() {
        _deleteResult.value = null
    }

    /** Confirmacion del sistema necesaria para borrar archivos multimedia. */
    data class DeleteRequest(
        val intentSender: IntentSender,
        /** Archivos incluidos en esta confirmacion. */
        val tracks: List<Track>,
        /** true si el sistema borra los archivos al aprobar (Android 11+). */
        val systemDeletes: Boolean,
    )

    /** Resultado de borrar una o varias canciones. */
    data class DeleteResult(
        val success: Boolean,
        val deletedCount: Int,
        val total: Int,
        val cancelled: Boolean = false,
    )

    private sealed interface DeleteOutcome {
        data object Deleted : DeleteOutcome
        data class NeedsConsent(val intentSender: IntentSender) : DeleteOutcome
        data object Failed : DeleteOutcome
    }

    private companion object {
        /** Android acepta como maximo 2000 URIs por solicitud de borrado. */
        const val MAX_DELETE_REQUEST_SIZE = 2000
    }

    fun renamePlaylist(playlistId: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.renamePlaylist(playlistId, name.trim()) }
    }

    // ---- Equalizer ----

    fun setEqualizerEnabled(enabled: Boolean) {
        EqualizerController.setEnabled(enabled)
        persistEqualizer()
    }

    fun setEqualizerBand(index: Int, levelMb: Int) {
        EqualizerController.setBandLevel(index, levelMb)
        persistEqualizer()
    }

    fun setBassBoost(strength: Int) {
        EqualizerController.setBassBoost(strength)
        persistEqualizer()
    }

    /** Applies the volume boost (100%..200%) in real time. */
    fun setVolumeBoost(percent: Int) {
        EqualizerController.setVolumeBoostPercent(percent)
    }

    /** Saves the chosen level when the slider is released (avoids writing on every pixel). */
    fun persistVolumeBoost() {
        persistEqualizer()
    }

    fun resetEqualizer() {
        EqualizerController.reset()
        persistEqualizer()
    }

    private fun persistEqualizer() {
        val settings = EqualizerController.currentSettings()
        viewModelScope.launch { repository.saveEqualizerSettings(settings) }
    }

    private fun applyTrackOrder(tracks: List<Track>, order: List<Long>): List<Track> {
        if (order.isEmpty()) return tracks
        val position = order.withIndex().associate { (index, id) -> id to index }
        val scannedPosition = tracks.withIndex().associate { (index, track) -> track.id to index }
        return tracks.sortedWith(
            compareBy(
                { position[it.id] ?: Int.MAX_VALUE },
                { scannedPosition[it.id] ?: Int.MAX_VALUE },
            ),
        )
    }

    override fun onCleared() {
        connection.release()
        super.onCleared()
    }
}
