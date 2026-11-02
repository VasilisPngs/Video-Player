package com.vasilispngs.videoplayer.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DisplayModesTest {
    @Test
    fun `switches a 60 Hz television to the exact rate of a film`() {
        assertEquals(23.976f, refreshRateFor(60f, TELEVISION, 23.976f))
        assertEquals(24f, refreshRateFor(60f, TELEVISION, 24f))
        assertEquals(25f, refreshRateFor(60f, TELEVISION, 25f))
        assertEquals(29.97f, refreshRateFor(60f, TELEVISION, 29.97f))
    }

    @Test
    fun `keeps a refresh rate that already shows every frame evenly`() {
        assertNull(refreshRateFor(60f, TELEVISION, 30f))
        assertNull(refreshRateFor(120f, TELEVISION + 120f, 24f))
        assertNull(refreshRateFor(50f, TELEVISION, 25f))
    }

    @Test
    fun `uses the nearest rate when the exact one is missing`() {
        assertEquals(24f, refreshRateFor(60f, listOf(60f, 50f, 24f), 23.976f))
    }

    @Test
    fun `uses a multiple of the frame rate when the rate itself is missing`() {
        assertEquals(50f, refreshRateFor(60f, listOf(60f, 50f), 25f))
    }

    @Test
    fun `stays put when no refresh rate fits or the frame rate is unknown`() {
        assertNull(refreshRateFor(60f, listOf(60f), 24f))
        assertNull(refreshRateFor(60f, TELEVISION, -1f))
    }

    @Test
    fun `shows the notice through the switch and two seconds after it`() = runTest {
        val events = mutableListOf<String>()

        switchWithNotice({ events += "${if (it == null) "hide" else "show"} $currentTime" }) { true.also { events += "switch $currentTime" } }

        assertEquals(listOf("show 0", "switch 0", "hide 2000"), events)
    }

    @Test
    fun `plays at once when the notice is dismissed`() = runTest {
        val events = mutableListOf<String>()

        switchWithNotice({ notice ->
            notice?.complete(Unit)
            events += "${if (notice == null) "hide" else "show"} $currentTime"
        }) { true.also { events += "switch $currentTime" } }

        assertEquals(listOf("show 0", "switch 0", "hide 0"), events)
    }

    @Test
    fun `plays at once when the television keeps its mode`() = runTest {
        val events = mutableListOf<String>()

        switchWithNotice({ events += "${if (it == null) "hide" else "show"} $currentTime" }) { false.also { events += "switch $currentTime" } }

        assertEquals(listOf("show 0", "switch 0", "hide 0"), events)
    }

    private companion object {
        val TELEVISION = listOf(60f, 59.94f, 50f, 30f, 29.97f, 25f, 24f, 23.976f)
    }
}
