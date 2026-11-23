package com.lefiru.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lefiru.music.data.Playlist
import com.lefiru.music.data.Track
import com.lefiru.music.ui.components.EmptyState
import com.lefiru.music.ui.components.SongRow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Playlist detail screen: lets the user play, remove, and reorder
 * tracks by long-pressing and dragging.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    tracksById: Map<Long, Track>,
    favorites: Set<Long>,
    currentTrackId: Long?,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayAll: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onRemoveTrack: (Long) -> Unit,
    onSaveOrder: (List<Long>) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    // Local order: updates instantly while dragging and is saved on drop.
    var order by remember(playlist.id) { mutableStateOf(playlist.trackIds) }
    LaunchedEffect(playlist.trackIds) {
        if (playlist.trackIds != order) order = playlist.trackIds
    }

    val tracks = remember(order, tracksById) {
        order.mapNotNull { tracksById[it] }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = playlist.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${tracks.size} canciones",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onPlayAll, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Reproducir lista")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (tracks.isEmpty()) {
            EmptyState(
                title = "Lista vacia",
                subtitle = "Agrega canciones desde el menu de cualquier cancion.",
            )
            return@Scaffold
        }

        val haptic = LocalHapticFeedback.current
        val listState = rememberLazyListState()
        val reorderState = rememberReorderableLazyListState(listState) { from, to ->
            order = order.toMutableList().apply { add(to.index, removeAt(from.index)) }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
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
                items(tracks, key = { it.id }) { track ->
                    ReorderableItem(reorderState, key = track.id) { isDragging ->
                        SongRow(
                            track = track,
                            isFavorite = track.id in favorites,
                            isCurrent = track.id == currentTrackId,
                            onRemove = { onRemoveTrack(track.id) },
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
                                    onDragStopped = { onSaveOrder(order) },
                                ),
                            onClick = {
                                onPlay(tracks, tracks.indexOfFirst { it.id == track.id })
                            },
                            onToggleFavorite = { onToggleFavorite(track.id) },
                        )
                    }
                }
            }
        }
    }
}
