package com.lefiru.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackFormattingTest {

    @Test
    fun `formats minutes and seconds`() {
        assertEquals("1:05", formatDuration(65_000))
    }

    @Test
    fun `pads seconds with a leading zero`() {
        assertEquals("0:07", formatDuration(7_000))
    }

    @Test
    fun `formats exact minutes`() {
        assertEquals("60:00", formatDuration(3_600_000))
    }

    @Test
    fun `returns placeholder for unknown duration`() {
        assertEquals("--:--", formatDuration(0))
    }

    @Test
    fun `returns placeholder for max duration`() {
        assertEquals("--:--", formatDuration(Long.MAX_VALUE))
    }
}
