package com.vasilispngs.videoplayer.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.SeekParameters
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class SkipTest {
    private val player = buildPlayer(RuntimeEnvironment.getApplication(), SubtitleDelay())

    @After
    fun release() {
        player.release()
    }

    @Test
    fun `jumps ten seconds forward to the next keyframe`() {
        player.skip(forward = true)

        assertEquals(SeekParameters.NEXT_SYNC, player.seekParameters)
        assertEquals(SEEK_INCREMENT_MS, player.currentPosition)
    }

    @Test
    fun `jumps ten seconds back to the previous keyframe without passing the start`() {
        player.skip(forward = true)
        player.skip(forward = true)

        player.skip(forward = false)
        assertEquals(SeekParameters.PREVIOUS_SYNC, player.seekParameters)
        assertEquals(SEEK_INCREMENT_MS, player.currentPosition)
        player.skip(forward = false)
        player.skip(forward = false)
        assertEquals(0L, player.currentPosition)
    }
}
