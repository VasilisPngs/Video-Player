@file:OptIn(UnstableApi::class)

package com.vasilispngs.videoplayer.player

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.RendererConfiguration
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.extractor.text.CueEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SubtitleDelayTest {
    private val encoder = CueEncoder()
    private val format = Format.Builder()
        .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
        .setCueReplacementBehavior(Format.CUE_REPLACEMENT_BEHAVIOR_MERGE)
        .build()

    @Test
    fun `replays an already-passed cue when subtitles are shifted later and updates while paused`() {
        val delay = SubtitleDelay()
        var displayed = emptyList<String>()
        val output = TextOutput { group: CueGroup -> displayed = group.cues.map { it.text.toString() } }
        val renderer = DelayedTextRenderer(TextRenderer(output, Looper.getMainLooper()), delay)
        val stream = FakeCueStream(
            format,
            listOf(
                1_000_000L to encoder.encode(listOf(Cue.Builder().setText("First").build()), 1_000_000L),
                4_000_000L to encoder.encode(listOf(Cue.Builder().setText("Second").build()), 1_000_000L)
            )
        )
        val timeline = SinglePeriodTimeline(10_000_000L, true, false, false, null, MediaItem.EMPTY)
        val periodId = MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0))
        renderer.init(0, PlayerId.UNSET, Clock.DEFAULT)
        renderer.setTimeline(timeline)
        renderer.enable(RendererConfiguration.DEFAULT, arrayOf(format), stream, 0L, false, true, 0L, 0L, periodId)

        render(renderer, 1_500_000L)
        assertEquals(listOf("First"), displayed)

        render(renderer, 3_000_000L)
        assertEquals(emptyList<String>(), displayed)

        delay.us = 1_500_000L
        render(renderer, 3_000_000L)
        assertEquals(listOf("First"), displayed)

        delay.us = -1_500_000L
        render(renderer, 3_000_000L)
        assertEquals(listOf("Second"), displayed)
    }

    @Test
    fun `ends at the period end for both positive and negative subtitle delays`() {
        val delay = SubtitleDelay().apply { us = -2_000_000L }
        var displayed = emptyList<String>()
        val output = TextOutput { group: CueGroup -> displayed = group.cues.map { it.text.toString() } }
        val renderer = DelayedTextRenderer(TextRenderer(output, Looper.getMainLooper()), delay)
        val stream = FakeCueStream(
            format,
            listOf(11_000_000L to encoder.encode(listOf(Cue.Builder().setText("Late").build()), 500_000L))
        )
        val timeline = SinglePeriodTimeline(10_000_000L, true, false, false, null, MediaItem.EMPTY)
        val periodId = MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0))
        renderer.init(0, PlayerId.UNSET, Clock.DEFAULT)
        renderer.setTimeline(timeline)
        renderer.enable(RendererConfiguration.DEFAULT, arrayOf(format), stream, 0L, false, true, 0L, 0L, periodId)
        renderer.setCurrentStreamFinal()

        render(renderer, 9_200_000L)
        assertFalse(renderer.isEnded)
        assertEquals(listOf("Late"), displayed)

        render(renderer, 10_000_000L)
        assertTrue(renderer.isEnded)

        delay.us = 3_000_000L
        render(renderer, 9_500_000L)
        assertFalse(renderer.isEnded)
        render(renderer, 10_000_000L)
        assertTrue(renderer.isEnded)
    }

    private fun render(renderer: DelayedTextRenderer, positionUs: Long) {
        renderer.render(positionUs, 0L)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private class FakeCueStream(
        private val format: Format,
        private val cues: List<Pair<Long, ByteArray>>
    ) : SampleStream {
        private var formatRead = false
        private var index = 0

        override fun isReady(): Boolean = true

        override fun maybeThrowError() = Unit

        override fun readData(formatHolder: FormatHolder, buffer: DecoderInputBuffer, readFlags: Int): Int {
            if (!formatRead || readFlags and SampleStream.FLAG_REQUIRE_FORMAT != 0) {
                formatHolder.format = format
                formatRead = true
                return C.RESULT_FORMAT_READ
            }
            if (index >= cues.size) {
                buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM)
                return C.RESULT_BUFFER_READ
            }
            val (timeUs, bytes) = cues[index]
            buffer.timeUs = timeUs
            buffer.setFlags(C.BUFFER_FLAG_KEY_FRAME)
            if (readFlags and SampleStream.FLAG_OMIT_SAMPLE_DATA == 0) {
                buffer.ensureSpaceForWrite(bytes.size)
                buffer.data?.put(bytes)
            }
            if (readFlags and SampleStream.FLAG_PEEK == 0) index++
            return C.RESULT_BUFFER_READ
        }

        override fun skipData(positionUs: Long): Int = 0
    }
}
