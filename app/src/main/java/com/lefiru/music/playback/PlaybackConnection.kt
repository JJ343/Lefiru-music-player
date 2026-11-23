package com.lefiru.music.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.compose.runtime.Immutable
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.lefiru.music.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
data class PlaybackState(
    val connected: Boolean = false,
    val mediaId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val queueIds: List<Long> = emptyList(),
    val currentIndex: Int = 0,
    val error: String? = null,
)

fun Track.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setTrackNumber(trackNumber)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .apply {
            if (artworkUri != Uri.EMPTY) setArtworkUri(artworkUri)
        }
        .build()

    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(metadata)
        .build()
}

/**
 * Connection to MusicService. Exposes player state as a
 * StateFlow together with the playback commands used by the UI.
 */
class PlaybackConnection(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingAction: ((MediaController) -> Unit)? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** Isolated playback position: the ticker only touches this, not the whole state. */
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish()
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future

        future.addListener(
            {
                val connectedController = runCatching { future.get() }.getOrNull()
                if (connectedController == null) {
                    _state.update { it.copy(error = "No se pudo conectar el reproductor") }
                    return@addListener
                }
                controller = connectedController
                connectedController.addListener(listener)
                _state.update { it.copy(connected = true, error = null) }
                pendingAction?.invoke(connectedController)
                pendingAction = null
                publish()
                startTicker()
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun startTicker() {
        if (ticker != null) return
        ticker = scope.launch {
            while (isActive) {
                publishPosition()
                delay(500)
            }
        }
    }

    private fun publish() {
        val player = controller ?: return
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        _state.update {
            it.copy(
                connected = true,
                mediaId = item?.mediaId?.toLongOrNull(),
                title = metadata?.title?.toString().orEmpty(),
                artist = metadata?.artist?.toString().orEmpty(),
                album = metadata?.albumTitle?.toString().orEmpty(),
                artworkUri = metadata?.artworkUri?.toString(),
                isPlaying = player.isPlaying,
                durationMs = player.duration.coerceAtLeast(0L).takeIf { d -> d != Long.MAX_VALUE } ?: 0L,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                hasNext = player.hasNextMediaItem(),
                hasPrevious = player.hasPreviousMediaItem(),
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                speed = player.playbackParameters.speed,
                queueIds = (0 until player.mediaItemCount).mapNotNull { index ->
                    player.getMediaItemAt(index).mediaId.toLongOrNull()
                },
                currentIndex = player.currentMediaItemIndex,
                error = null,
            )
        }
    }

    private fun publishPosition() {
        val player = controller ?: return
        // The ticker (500 ms) only updates the position so the whole UI is not
        // recomposed just because the progress bar moved.
        _positionMs.update { player.currentPosition.coerceAtLeast(0L) }
    }

    private fun withController(action: (MediaController) -> Unit) {
        val player = controller
        if (player != null) {
            action(player)
        } else {
            pendingAction = action
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val items = tracks.map { it.toMediaItem() }
        val index = startIndex.coerceIn(0, items.lastIndex)
        withController { player ->
            player.setMediaItems(items, index, 0L)
            player.prepare()
            player.play()
        }
    }

    fun playNext(track: Track) {
        withController { player ->
            player.addMediaItem(track.toMediaItem())
        }
    }

    fun togglePlayPause() {
        withController { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
        }
    }

    fun next() {
        withController { player ->
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.play()
            }
        }
    }

    fun previous() {
        withController { player ->
            if (player.currentPosition > 3_000L) {
                player.seekTo(0L)
            } else if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
                player.play()
            } else {
                player.seekTo(0L)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        withController { player -> player.seekTo(positionMs.coerceAtLeast(0L)) }
    }

    /** Pauses playback if anything is playing (used by the sleep timer). */
    fun pause() {
        withController { player ->
            if (player.isPlaying) player.pause()
        }
    }

    fun playAt(index: Int) {
        withController { player ->
            if (index in 0 until player.mediaItemCount) {
                player.seekToDefaultPosition(index)
                player.play()
            }
        }
    }

    fun toggleShuffle() {
        withController { player -> player.shuffleModeEnabled = !player.shuffleModeEnabled }
    }

    fun cycleRepeatMode() {
        withController { player ->
            player.repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun setSpeed(speed: Float) {
        withController { player -> player.setPlaybackSpeed(speed) }
    }

    fun release() {
        ticker?.cancel()
        ticker = null
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        scope.cancel()
    }
}
