package com.lefiru.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lefiru.music.data.AlbumGroup
import com.lefiru.music.data.ArtistGroup
import com.lefiru.music.data.Playlist
import com.lefiru.music.data.Track
import com.lefiru.music.ui.LibraryViewModel
import com.lefiru.music.ui.components.AlbumArt
import com.lefiru.music.ui.components.EmptyState
import com.lefiru.music.ui.components.SongRow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SongsTab(
    viewModel: LibraryViewModel,
    tracks: List<Track>,
    favorites: Set<Long>,
    query: String,
    currentTrackId: Long?,
    onAddToPlaylist: (Track) -> Unit,
) {
    val filtered = remember(tracks, query) {
        if (query.isBlank()) {
            tracks
        } else {
            tracks.filter { track ->
                track.title.contains(query, ignoreCase = true) ||
                    track.artist.contains(query, ignoreCase = true) ||
                    track.album.contains(query, ignoreCase = true)
            }
        }
    }

    if (filtered.isEmpty()) {
        EmptyState(
            title = "Sin canciones",
            subtitle = if (query.isBlank()) {
                "Copia archivos de audio al telefono y pulsa actualizar."
            } else {
                "No hay resultados para \"$query\"."
            },
        )
        return
    }

    // Reordering is disabled while searching: the saved order is that of the full library.
    val reorderEnabled = query.isBlank()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        if (reorderEnabled) viewModel.moveTrack(from.index, to.index)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (reorderEnabled) {
            Text(
                text = "Manten presionada una cancion y arrastrala para cambiar el orden",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(filtered, key = { it.id }) { track ->
                ReorderableItem(reorderState, key = track.id) { isDragging ->
                    SongRow(
                        track = track,
                        isFavorite = track.id in favorites,
                        isCurrent = track.id == currentTrackId,
                        modifier = Modifier
                            .background(
                                if (isDragging) {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                } else {
                                    Color.Transparent
                                },
                            )
                            .longPressDraggableHandle(
                                enabled = reorderEnabled,
                                onDragStarted = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = { viewModel.persistTrackOrder() },
                            ),
                        onClick = {
                            viewModel.playTracks(filtered, filtered.indexOfFirst { it.id == track.id })
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(track.id) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumsTab(
    viewModel: LibraryViewModel,
    albums: List<AlbumGroup>,
    query: String,
) {
    val filtered = remember(albums, query) {
        albums.filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
    }

    if (filtered.isEmpty()) {
        EmptyState("Sin albumes", "La musica que agregues aparecera aqui.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(filtered, key = { "${it.id}-${it.name}" }) { album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (album.tracks.isNotEmpty()) viewModel.playAlbum(album)
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArt(uri = album.artworkUri.toString(), size = 56.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = album.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${album.artist} · ${album.tracks.size} canciones",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { viewModel.playAlbum(album) }) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Reproducir album")
                }
            }
        }
    }
}

@Composable
fun ArtistsTab(
    viewModel: LibraryViewModel,
    artists: List<ArtistGroup>,
    query: String,
) {
    val filtered = remember(artists, query) {
        artists.filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true)
        }
    }

    if (filtered.isEmpty()) {
        EmptyState("Sin artistas", "Los artistas de tu musica apareceran aqui.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(filtered, key = { it.name }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.playArtist(artist) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArt(uri = artist.artworkUri.toString(), size = 56.dp, corner = 28.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = artist.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${artist.tracks.size} canciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { viewModel.playArtist(artist) }) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Reproducir artista")
                }
            }
        }
    }
}

@Composable
fun PlaylistsTab(
    viewModel: LibraryViewModel,
    playlists: List<Playlist>,
    tracksById: Map<Long, Track>,
    onOpenPlaylist: (Playlist) -> Unit,
) {
    if (playlists.isEmpty()) {
        EmptyState(
            title = "Sin listas",
            subtitle = "Crea una lista con el boton + o desde el menu de una cancion.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(playlists, key = { it.id }) { playlist ->
            val trackCount = playlist.trackIds.count { it in tracksById }
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPlaylist(playlist) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "$trackCount canciones",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { viewModel.playPlaylist(playlist) },
                        enabled = trackCount > 0,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Reproducir lista")
                    }
                    IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar lista")
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesTab(
    viewModel: LibraryViewModel,
    favoriteTracks: List<Track>,
    currentTrackId: Long?,
    onAddToPlaylist: (Track) -> Unit,
) {
    if (favoriteTracks.isEmpty()) {
        EmptyState(
            title = "Sin favoritos",
            subtitle = "Toca el corazon de una cancion para guardarla aqui.",
        )
        return
    }

    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.moveFavorite(from.index, to.index)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Manten presionada una cancion y arrastrala para cambiar el orden",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(favoriteTracks, key = { it.id }) { track ->
                ReorderableItem(reorderState, key = track.id) { isDragging ->
                    SongRow(
                        track = track,
                        isFavorite = true,
                        isCurrent = track.id == currentTrackId,
                        modifier = Modifier
                            .background(
                                if (isDragging) {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                } else {
                                    Color.Transparent
                                },
                            )
                            .longPressDraggableHandle(
                                onDragStarted = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = { viewModel.persistFavoriteOrder() },
                            ),
                        onClick = {
                            viewModel.playTracks(
                                favoriteTracks,
                                favoriteTracks.indexOfFirst { it.id == track.id },
                            )
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(track.id) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                    )
                }
            }
        }
    }
}
