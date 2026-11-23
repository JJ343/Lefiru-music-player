package com.lefiru.music.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.compose.runtime.Immutable
import androidx.media3.common.Player
import com.lefiru.music.data.EqualizerSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Audio quality controls applied to the real ExoPlayer audio session.
 * Everything is wrapped in runCatching because some manufacturers do not expose
 * all effects.
 */
object EqualizerController {

    private const val MAX_BASS_STRENGTH = 1000

    /** Allowed volume boost: 100% (original) to 200% (approx. +6 dB). */
    const val MIN_VOLUME_PERCENT = 100
    const val MAX_VOLUME_PERCENT = 200

    @Immutable
    data class EqBand(
        val index: Int,
        val label: String,
        val levelMb: Int,
        val minMb: Int,
        val maxMb: Int,
    )

    @Immutable
    data class EqState(
        val available: Boolean = false,
        val enabled: Boolean = false,
        val bands: List<EqBand> = emptyList(),
        val bassBoost: Int = 0,
        val loudnessGainMb: Int = 0,
        /** Effective volume percentage: 100 = original, 200 = maximum boost. */
        val volumeBoostPercent: Int = 100,
        /** Whether the device exposes LoudnessEnhancer (requires audio playback). */
        val loudnessAvailable: Boolean = false,
    )

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudness: LoudnessEnhancer? = null
    private var currentSessionId: Int = 0
    private var pendingSettings: EqualizerSettings? = null
    private var pendingVolumeBoostPercent: Int? = null

    fun attach(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                bindSession(audioSessionId)
            }
        })
    }

    /** Re-applies the settings saved by the user. */
    fun restore(settings: EqualizerSettings) {
        pendingSettings = settings
        if (equalizer != null || loudness != null) applySettings(settings)
    }

    @Suppress("DEPRECATION")
    private fun bindSession(sessionId: Int) {
        if (sessionId <= 0 || sessionId == currentSessionId) return
        currentSessionId = sessionId
        releaseEffects()

        // Each effect is attempted separately: some devices do not expose the
        // equalizer but do allow volume boosting (and vice versa).
        equalizer = runCatching {
            Equalizer(0, sessionId).apply { enabled = true }
        }.getOrNull()
        bassBoost = runCatching { BassBoost(0, sessionId).apply { enabled = true } }.getOrNull()
        loudness = runCatching { LoudnessEnhancer(sessionId).apply { enabled = true } }.getOrNull()

        val eq = equalizer
        val range = eq?.let { runCatching { it.bandLevelRange }.getOrNull() } ?: shortArrayOf(-1500, 1500)
        val bands = eq?.let { effect ->
            runCatching {
                (0 until effect.numberOfBands.toInt()).map { index ->
                    val band = index.toShort()
                    val freqHz = effect.getCenterFreq(band) / 1000
                    EqBand(
                        index = index,
                        label = formatFrequency(freqHz),
                        levelMb = effect.getBandLevel(band).toInt(),
                        minMb = range[0].toInt(),
                        maxMb = range[1].toInt(),
                    )
                }
            }.getOrDefault(emptyList())
        }.orEmpty()

        _state.value = EqState(
            available = eq != null,
            enabled = eq != null,
            bands = bands,
            bassBoost = bassBoost?.let { runCatching { it.roundedStrength.toInt() }.getOrDefault(0) } ?: 0,
            loudnessGainMb = 0,
            volumeBoostPercent = pendingVolumeBoostPercent ?: pendingSettings?.volumeBoostPercent ?: MIN_VOLUME_PERCENT,
            loudnessAvailable = loudness != null,
        )

        pendingSettings?.let { applySettings(it) }
        // If the user changed the volume before the session existed, apply it now.
        pendingVolumeBoostPercent?.let { setVolumeBoostPercent(it) }
    }

    private fun applySettings(settings: EqualizerSettings) {
        setEnabled(settings.enabled)
        settings.bands.forEach { (index, level) -> setBandLevel(index, level) }
        setBassBoost(settings.bassBoost)
        setVolumeBoostPercent(settings.volumeBoostPercent)
    }

    fun setEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
        runCatching { bassBoost?.enabled = enabled }
        _state.value = _state.value.copy(enabled = enabled)
    }

    @Suppress("DEPRECATION")
    fun setBandLevel(index: Int, levelMb: Int) {
        val eq = equalizer ?: return
        val band = index.toShort()
        runCatching { eq.setBandLevel(band, levelMb.toShort()) }
        _state.value = _state.value.copy(
            bands = _state.value.bands.map { if (it.index == index) it.copy(levelMb = levelMb) else it }
        )
    }

    @Suppress("DEPRECATION")
    fun setBassBoost(strength: Int) {
        val value = strength.coerceIn(0, MAX_BASS_STRENGTH)
        runCatching { bassBoost?.setStrength(value.toShort()) }
        _state.value = _state.value.copy(bassBoost = value)
    }

    /**
     * Adjusts the volume boost. 100% = original and 200% = double amplitude (~+6 dB).
     * Converted to millibels on a logarithmic scale, matching how hearing works.
     */
    fun setVolumeBoostPercent(percent: Int) {
        val value = percent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)
        val gainMb = percentToGainMb(value)
        pendingVolumeBoostPercent = value
        runCatching { loudness?.setTargetGain(gainMb) }
        _state.value = _state.value.copy(
            volumeBoostPercent = value,
            loudnessGainMb = gainMb,
        )
    }

    private fun percentToGainMb(percent: Int): Int =
        (2000.0 * log10(percent / 100.0)).roundToInt().coerceAtLeast(0)

    fun reset() {
        val eq = equalizer
        if (eq != null) {
            runCatching {
                (0 until eq.numberOfBands.toInt()).forEach { eq.setBandLevel(it.toShort(), 0) }
            }
        }
        setBassBoost(0)
        setVolumeBoostPercent(MIN_VOLUME_PERCENT)
    }

    fun currentSettings(): EqualizerSettings = EqualizerSettings(
        enabled = _state.value.enabled,
        bands = _state.value.bands.associate { it.index to it.levelMb },
        bassBoost = _state.value.bassBoost,
        volumeBoostPercent = _state.value.volumeBoostPercent,
    )

    private fun formatFrequency(freqHz: Int): String =
        if (freqHz >= 1000) "${freqHz / 1000} kHz" else "$freqHz Hz"

    private fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        bassBoost = null
        loudness = null
    }

    fun release() {
        releaseEffects()
        currentSessionId = 0
        _state.value = EqState()
    }
}
