package com.lefiru.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import android.widget.Toast
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lefiru.music.data.Track
import com.lefiru.music.ui.components.MiniPlayer
import com.lefiru.music.ui.screens.AlbumsTab
import com.lefiru.music.ui.screens.ArtistsTab
import com.lefiru.music.ui.screens.FavoritesTab
import com.lefiru.music.ui.screens.PlayerScreen
import com.lefiru.music.ui.screens.PlaylistDetailScreen
import com.lefiru.music.ui.screens.PlaylistsTab
import com.lefiru.music.ui.screens.SongsTab
import com.lefiru.music.ui.screens.StopwatchScreen

private data class TabSpec(val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabSpec("Temas", Icons.Rounded.MusicNote),
    TabSpec("Albumes", Icons.Rounded.Album),
    TabSpec("Artistas", Icons.Rounded.Person),
    TabSpec("Listas", Icons.Rounded.QueueMusic),
    TabSpec("Favs", Icons.Rounded.Favorite),
    TabSpec("Crono", Icons.Rounded.Timer),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LefiruScreen(
    viewModel: LibraryViewModel,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
) {
    val library by viewModel.library.collectAsState()
    val playback by viewModel.playback.collectAsState()
    // Position is collected separately: the 500 ms ticker only moves the progress
    // bar and does not recompose the whole screen (tabs, rows, etc.).
    val positionMs by viewModel.playbackPosition.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoriteTracks by viewModel.favoriteTracks.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }
    var pendingTrackIdForNewList by remember { mutableStateOf<Long?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var openPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTrackIds by remember { mutableStateOf(emptySet<Long>()) }

    val onTrackSelected: (Long) -> Unit = { trackId ->
        selectedTrackIds = if (selectedTrackIds.contains(trackId)) {
            selectedTrackIds - trackId
        } else {
            selectedTrackIds + trackId
        }
    }
    val onToggleSelectAll: (Set<Long>) -> Unit = { visibleIds ->
        selectedTrackIds = toggleSelectAll(selectedTrackIds, visibleIds)
    }

    LaunchedEffect(permissionGranted) {
        viewModel.onPermissionResult(permissionGranted)
    }

    val context = LocalContext.current
    val deleteResult by viewModel.deleteResult.collectAsState()
    val deleteRequest by viewModel.deleteRequest.collectAsState()

    val deleteRequestLauncher = rememberLauncherForActivityResult(
        StartIntentSenderForResult(),
    ) { result ->
        viewModel.onDeleteRequestResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(deleteRequest) {
        val request = deleteRequest ?: return@LaunchedEffect
        deleteRequestLauncher.launch(
            IntentSenderRequest.Builder(request.intentSender).build(),
        )
    }

    LaunchedEffect(deleteResult) {
        val result = deleteResult ?: return@LaunchedEffect
        val message = when {
            result.cancelled && result.deletedCount == 0 -> "Eliminacion cancelada"
            result.cancelled -> "Eliminacion cancelada · ${result.deletedCount} de ${result.total} eliminadas"
            result.success && result.total == 1 -> "Cancion eliminada"
            result.success -> "${result.deletedCount} de ${result.total} canciones eliminadas"
            result.deletedCount == 0 && result.total == 1 -> "No se pudo eliminar la cancion"
            result.deletedCount == 0 -> "No se pudieron eliminar las canciones"
            else -> "${result.deletedCount} de ${result.total} canciones eliminadas"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        // Al cancelar se conserva la seleccion para poder reintentar.
        if (!result.cancelled) selectedTrackIds = emptySet()
        viewModel.consumeDeleteResult()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = {
                        if (searchActive) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Buscar canciones, albumes...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(
                                text = "Lefiru",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                    actions = {
                        if (searchActive) {
                            IconButton(
                                onClick = {
                                    searchActive = false
                                    searchQuery = ""
                                },
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Cerrar busqueda")
                            }
                        } else {
                            if (selectedTab == 3) {
                                IconButton(onClick = { showCreateDialog = true }) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Nueva lista")
                                }
                            }
                            if (selectedTab != 5) {
                                if (selectedTrackIds.isNotEmpty()) {
                                    IconButton(onClick = {
                                        val toDelete = library.tracksById.values
                                            .filter { it.id in selectedTrackIds }
                                        viewModel.deleteTracks(toDelete)
                                    }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar seleccionadas")
                                    }
                                } else {
                                    IconButton(onClick = { viewModel.refresh() }) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = "Actualizar")
                                    }
                                    IconButton(onClick = { searchActive = true }) {
                                        Icon(Icons.Rounded.Search, contentDescription = "Buscar")
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            bottomBar = {
                Column {
                    if (playback.mediaId != null) {
                        MiniPlayer(
                            state = playback,
                            positionMs = positionMs,
                            onOpen = { showPlayer = true },
                            onPlayPause = { viewModel.togglePlayPause() },
                            onNext = { viewModel.next() },
                        )
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = {
                                    selectedTab = index
                                    if (index == 5) {
                                        searchActive = false
                                        searchQuery = ""
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (library.loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // The stopwatch does not need audio permission: always show it.
                if (!permissionGranted && selectedTab != 5) {
                    PermissionGate(onRequestPermission = onRequestPermission)
                } else {
                    when (selectedTab) {
                        0 -> SongsTab(
                            viewModel = viewModel,
                            tracks = library.tracks,
                            favorites = favorites,
                            query = searchQuery,
                            currentTrackId = playback.mediaId,
                            onAddToPlaylist = { trackForPlaylist = it },
                            selectedTrackIds = selectedTrackIds,
                            onTrackSelected = onTrackSelected,
                            onToggleSelectAll = onToggleSelectAll,
                            onDeleteTrack = { track ->
                                viewModel.deleteTracks(listOf(track))
                            },
                        )

                        1 -> AlbumsTab(
                            viewModel = viewModel,
                            albums = library.albums,
                            query = searchQuery,
                        )

                        2 -> ArtistsTab(
                            viewModel = viewModel,
                            artists = library.artists,
                            query = searchQuery,
                        )

                        3 -> PlaylistsTab(
                            viewModel = viewModel,
                            playlists = playlists,
                            tracksById = library.tracksById,
                            onOpenPlaylist = { openPlaylistId = it.id },
                        )

                        4 -> FavoritesTab(
                            viewModel = viewModel,
                            favoriteTracks = favoriteTracks,
                            currentTrackId = playback.mediaId,
                            onAddToPlaylist = { trackForPlaylist = it },
                            selectedTrackIds = selectedTrackIds,
                            onTrackSelected = onTrackSelected,
                            onToggleSelectAll = onToggleSelectAll,
                            onDeleteTrack = { track ->
                                viewModel.deleteTracks(listOf(track))
                            },
                        )

                        else -> StopwatchScreen(viewModel = viewModel)
                    }
                }
            }
        }

        val openPlaylist = playlists.firstOrNull { it.id == openPlaylistId }
        if (openPlaylist != null) {
            PlaylistDetailScreen(
                playlist = openPlaylist,
                tracksById = library.tracksById,
                favorites = favorites,
                currentTrackId = playback.mediaId,
                onPlay = { songs, index -> viewModel.playTracks(songs, index) },
                onPlayAll = { viewModel.playPlaylist(openPlaylist) },
                onToggleFavorite = viewModel::toggleFavorite,
                onRemoveTrack = { viewModel.removeFromPlaylist(openPlaylist.id, it) },
                onSaveOrder = { viewModel.reorderPlaylistTracks(openPlaylist.id, it) },
                onBack = { openPlaylistId = null },
            )
        }

        if (showPlayer && playback.mediaId != null) {
            PlayerScreen(viewModel = viewModel, onClose = { showPlayer = false })
        }
    }

    trackForPlaylist?.let { track ->
        AlertDialog(
            onDismissRequest = { trackForPlaylist = null },
            title = { Text("Agregar a lista") },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (playlists.isEmpty()) {
                        Text(
                            text = "Aun no tienes listas de reproduccion.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    playlists.forEach { playlist ->
                        TextButton(
                            onClick = {
                                viewModel.addToPlaylist(playlist.id, track.id)
                                trackForPlaylist = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = playlist.name,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            pendingTrackIdForNewList = track.id
                            trackForPlaylist = null
                            newPlaylistName = ""
                            showCreateDialog = true
                        },
                    ) {
                        Text("+ Nueva lista")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { trackForPlaylist = null }) { Text("Cerrar") }
            },
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newPlaylistName = ""
            },
            title = { Text("Nueva lista") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createPlaylist(newPlaylistName, pendingTrackIdForNewList)
                        newPlaylistName = ""
                        pendingTrackIdForNewList = null
                        showCreateDialog = false
                    },
                    enabled = newPlaylistName.isNotBlank(),
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        newPlaylistName = ""
                        pendingTrackIdForNewList = null
                    },
                ) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun PermissionGate(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Acceso a tu musica",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Lefiru necesita permiso para leer y eliminar los archivos de audio de tu telefono. No se sube nada a internet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Permitir acceso")
        }
    }
}
