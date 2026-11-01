package com.vasilispngs.videoplayer.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.vasilispngs.videoplayer.data.AudioLanguage
import com.vasilispngs.videoplayer.data.Playback
import com.vasilispngs.videoplayer.data.PlaybackSettings
import com.vasilispngs.videoplayer.data.SubtitleLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class TracksTest {
    @Test
    fun `plays the first audio track whatever its language`() {
        val choice = chooseTracks(tracks(audio("a1", "en"), audio("a2", "el")), PlaybackSettings(), null)

        assertEquals("a1", choice.audio?.id)
    }

    @Test
    fun `skips an audio track the television cannot play`() {
        val choice = chooseTracks(tracks(audio("a1", "en", supported = false), audio("a2", "en")), PlaybackSettings(), null)

        assertEquals("a2", choice.audio?.id)
    }

    @Test
    fun `shows full Greek subtitles before forced ones`() {
        val choice = chooseTracks(tracks(audio("a1", "en"), text("t1", "el", forced = true), text("t2", "en"), text("t3", "el")), PlaybackSettings(), null)

        assertEquals("t3", choice.text?.id)
    }

    @Test
    fun `shows no subtitles when none are Greek`() {
        val choice = chooseTracks(tracks(audio("a1", "en"), text("t1", "en")), PlaybackSettings(), null)

        assertNull(choice.text)
    }

    @Test
    fun `shows only forced Greek subtitles when the audio is Greek`() {
        val withForced = chooseTracks(tracks(audio("a1", "el"), text("t1", "el"), text("t2", "el", forced = true)), PlaybackSettings(), null)
        val withoutForced = chooseTracks(tracks(audio("a1", "el"), text("t1", "el")), PlaybackSettings(), null)

        assertEquals("t2", withForced.text?.id)
        assertNull(withoutForced.text)
    }

    @Test
    fun `keeps subtitles in another chosen language even when the audio is in it`() {
        val settings = PlaybackSettings(subtitleLanguage = SubtitleLanguage.English)

        val choice = chooseTracks(tracks(audio("a1", "en"), text("t1", "el"), text("t2", "en")), settings, null)

        assertEquals("t2", choice.text?.id)
    }

    @Test
    fun `shows no subtitles when they are off`() {
        val choice = chooseTracks(tracks(audio("a1", "en"), text("t1", "el")), PlaybackSettings(subtitleLanguage = SubtitleLanguage.Off), null)

        assertNull(choice.text)
    }

    @Test
    fun `plays the audio language chosen in Settings`() {
        val choice = chooseTracks(tracks(audio("a1", "en"), audio("a2", "el")), PlaybackSettings(audioLanguage = AudioLanguage.Greek), null)

        assertEquals("a2", choice.audio?.id)
    }

    @Test
    fun `restores the tracks remembered for the file before the rules`() {
        val saved = Playback(uri = "file:///movie.mkv", size = 1, audioTrack = "a2", textChosen = true, textTrack = null)

        val choice = chooseTracks(tracks(audio("a1", "en"), audio("a2", "el"), text("t1", "el")), PlaybackSettings(), saved)

        assertEquals("a2", choice.audio?.id)
        assertNull(choice.text)
    }

    @Test
    fun `restores remembered subtitles`() {
        val saved = Playback(uri = "file:///movie.mkv", size = 1, textChosen = true, textTrack = "t1")

        val choice = chooseTracks(tracks(audio("a1", "el"), text("t1", "en")), PlaybackSettings(), saved)

        assertEquals("t1", choice.text?.id)
    }

    private fun tracks(vararg groups: Tracks.Group) = Tracks(groups.toList())

    private fun audio(id: String, language: String, supported: Boolean = true) =
        group(id, Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage(language).build(), supported)

    private fun text(id: String, language: String, forced: Boolean = false) = group(
        id,
        Format.Builder()
            .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setLanguage(language)
            .setSelectionFlags(if (forced) C.SELECTION_FLAG_FORCED else 0)
            .build(),
        true
    )

    private fun group(id: String, format: Format, supported: Boolean) = Tracks.Group(
        TrackGroup(id, format),
        false,
        intArrayOf(if (supported) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_SUBTYPE),
        booleanArrayOf(false)
    )
}
