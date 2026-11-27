package com.lefiru.music.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackSelectionTest {

    @Test
    fun `selects all visible tracks when nothing is selected`() {
        assertEquals(
            setOf(1L, 2L, 3L),
            toggleSelectAll(selected = emptySet(), visibleIds = setOf(1L, 2L, 3L)),
        )
    }

    @Test
    fun `adds visible tracks to an existing selection`() {
        assertEquals(
            setOf(1L, 2L, 3L),
            toggleSelectAll(selected = setOf(1L), visibleIds = setOf(2L, 3L)),
        )
    }

    @Test
    fun `clears the whole selection when every visible track is selected`() {
        assertEquals(
            emptySet<Long>(),
            toggleSelectAll(selected = setOf(1L, 2L, 3L), visibleIds = setOf(1L, 2L)),
        )
    }

    @Test
    fun `keeps the selection when there are no visible tracks`() {
        assertEquals(
            setOf(1L),
            toggleSelectAll(selected = setOf(1L), visibleIds = emptySet()),
        )
    }

    @Test
    fun `does nothing when there is no selection and no visible tracks`() {
        assertEquals(
            emptySet<Long>(),
            toggleSelectAll(selected = emptySet(), visibleIds = emptySet()),
        )
    }
}
