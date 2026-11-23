package com.lefiru.music.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<Long> = emptyList(),
)
