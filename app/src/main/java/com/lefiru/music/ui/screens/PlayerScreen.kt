package com.lefiru.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.lefiru.music.data.formatDuration
import com.lefiru.music.playback.EqualizerController
import com.lefiru.music.ui.LibraryViewModel
import com.lefiru.music.ui.components.AlbumArt
import kotlin.math.roundToInt

private val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private val sleepTimerOptions = listOf(5, 10, 15, 30, 45, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: LibraryViewModel, onClose: () -> Unit) {
    val playback by viewModel.playback.collectAsState()
    // Position is collected separately: the ticker only updates the progress bar,
    // not the whole player screen (artwork, text, buttons...).
    val positionMs by viewModel.playbackPosition.collectAsState()
    val equalizer by viewModel.equalizerState.collectAsState()
    val sleepTimerRemainingMs by viewModel.sleepTimerRemainingMs.collectAsState()

    var showEqualizer by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val max = playback.durationMs.coerceAtLeast(1L).toFloat()
    val sliderValue = if (dragging) dragValue else positionMs.toFloat().coerceIn(0f, max)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Reproduciendo", style = MaterialTheme.typography.titleMedium)
                            sleepTimerRemainingMs?.let { remaining ->
                                Text(
                                    text = "Pausa en ${formatDuration(remaining)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Cerrar")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSleepTimer = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Bedtime,
                                contentDescription = "Temporizador de apagado",
                                tint = if (sleepTimerRemainingMs != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(onClick = { showEqualizer = true }) {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = "Ecualizador")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                AlbumArt(
                    uri = playback.artworkUri,
                    size = null,
                    corner = 28.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )

                Spacer(Modifier.height(28.dp))
                Text(
                    text = playback.title.ifBlank { "Sin reproduccion" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = playback.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(20.dp))

                Slider(
                    value = sliderValue,
                    onValueChange = { value ->
                        dragging = true
                        dragValue = value
                    },
                    onValueChangeFinished = {
                        viewModel.seekTo(dragValue.toLong())
                        dragging = false
                    },
                    valueRange = 0f..max,
                    enabled = playback.durationMs > 0,
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = formatDuration(if (dragging) dragValue.toLong() else positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatDuration(playback.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Aleatorio",
                            tint = if (playback.shuffle) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = { viewModel.previous() }) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Anterior",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    FilledIconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(
                            imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playback.isPlaying) "Pausar" else "Reproducir",
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    IconButton(
                        onClick = { viewModel.next() },
                        enabled = playback.hasNext,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Siguiente",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    IconButton(onClick = { viewModel.cycleRepeatMode() }) {
                        Icon(
                            imageVector = if (playback.repeatMode == Player.REPEAT_MODE_ONE) {
                                Icons.Rounded.RepeatOne
                            } else {
                                Icons.Rounded.Repeat
                            },
                            contentDescription = "Repetir",
                            tint = if (playback.repeatMode != Player.REPEAT_MODE_OFF) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Velocidad",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    speedOptions.forEach { speed ->
                        FilterChip(
                            selected = kotlin.math.abs(playback.speed - speed) < 0.01f,
                            onClick = { viewModel.setSpeed(speed) },
                            label = { Text(if (speed == 1f) "1x" else "${speed}x") },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Volumen",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(playback.volume * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = playback.volume,
                    onValueChange = viewModel::setVolume,
                    onValueChangeFinished = viewModel::persistVolume,
                    valueRange = 0f..1f,
                    steps = 19,
                )

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Refuerzo extra",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${equalizer.volumeBoostPercent}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Slider(
                    value = equalizer.volumeBoostPercent.toFloat(),
                    onValueChange = { percent -> viewModel.setVolumeBoost(percent.toInt()) },
                    onValueChangeFinished = viewModel::persistVolumeBoost,
                    valueRange = EqualizerController.MIN_VOLUME_PERCENT.toFloat()..
                        EqualizerController.MAX_VOLUME_PERCENT.toFloat(),
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary,
                    ),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showEqualizer) {
        EqualizerSheet(
            state = equalizer,
            onEnabled = viewModel::setEqualizerEnabled,
            onBand = viewModel::setEqualizerBand,
            onBass = viewModel::setBassBoost,
            onReset = viewModel::resetEqualizer,
            onDismiss = { showEqualizer = false },
        )
    }

    if (showSleepTimer) {
        SleepTimerSheet(
            remainingMs = sleepTimerRemainingMs,
            onSelect = { minutes ->
                viewModel.startSleepTimer(minutes)
                showSleepTimer = false
            },
            onCancel = {
                viewModel.cancelSleepTimer()
                showSleepTimer = false
            },
            onDismiss = { showSleepTimer = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    remainingMs: Long?,
    onSelect: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Temporizador", style = MaterialTheme.typography.titleLarge)

            if (remainingMs != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Pausando en ${formatDuration(remainingMs)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Duracion",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sleepTimerOptions.forEach { minutes ->
                    FilterChip(
                        selected = false,
                        onClick = { onSelect(minutes) },
                        label = { Text(if (minutes < 60) "$minutes min" else "1 hora") },
                    )
                }
            }

            if (remainingMs != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel) {
                    Text("Cancelar temporizador", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    state: EqualizerController.EqState,
    onEnabled: (Boolean) -> Unit,
    onBand: (Int, Int) -> Unit,
    onBass: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Ecualizador", style = MaterialTheme.typography.titleLarge)
                }
                Switch(checked = state.enabled, onCheckedChange = onEnabled)
            }

            if (!state.available) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "El ecualizador no esta disponible en este dispositivo o todavia no hay audio reproduciendose.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                return@Column
            }

            Spacer(Modifier.height(16.dp))
            state.bands.forEach { band ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = band.label,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(64.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        modifier = Modifier.weight(1f),
                        value = band.levelMb.toFloat(),
                        onValueChange = { level -> onBand(band.index, level.toInt()) },
                        valueRange = band.minMb.toFloat()..band.maxMb.toFloat(),
                        enabled = state.enabled,
                    )
                    Text(
                        text = "${band.levelMb / 100}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(40.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Bass boost", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    modifier = Modifier.weight(1f),
                    value = state.bassBoost.toFloat(),
                    onValueChange = { onBass(it.toInt()) },
                    valueRange = 0f..1000f,
                    enabled = state.enabled,
                )
                Text(
                    text = "${state.bassBoost / 10}%",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(48.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onReset, enabled = state.enabled) {
                Text("Restablecer")
            }
        }
    }
}
