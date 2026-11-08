package com.vasilispngs.videoplayer.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import com.vasilispngs.videoplayer.data.Library
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.nio.charset.Charset

@RunWith(RobolectricTestRunner::class)
class ExternalSubtitlesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val library = Library(RuntimeEnvironment.getApplication())

    @Test
    fun `loads the subtitles next to a video with the same name`() = runTest {
        write("Movie.mkv", "")
        write("Movie.el.srt", ENGLISH_TEXT)
        write("Movie.srt", GREEK_TEXT, Charset.forName("windows-1253"))
        write("movie.GR.ass", ENGLISH_TEXT)
        write("Movie.en.forced.srt", ENGLISH_TEXT)
        write("Movie.Unknown.srt", ENGLISH_TEXT)
        write("Movie 2.srt", GREEK_TEXT)
        write("Movie.nfo", ENGLISH_TEXT)

        val subtitles = library.externalSubtitles("Movie.mkv", library.list(Uri.fromFile(folder.root))).associateBy { it.id }

        assertEquals(setOf("Movie.el.srt", "Movie.srt", "movie.GR.ass", "Movie.en.forced.srt", "Movie.Unknown.srt"), subtitles.keys)
        assertEquals("el", subtitles.getValue("Movie.el.srt").language)
        assertEquals("el", subtitles.getValue("Movie.srt").language)
        assertEquals("el", subtitles.getValue("movie.GR.ass").language)
        assertEquals(MimeTypes.TEXT_SSA, subtitles.getValue("movie.GR.ass").mimeType)
        assertEquals("en", subtitles.getValue("Movie.en.forced.srt").language)
        assertEquals(C.SELECTION_FLAG_FORCED, subtitles.getValue("Movie.en.forced.srt").selectionFlags)
        assertEquals(null, subtitles.getValue("Movie.Unknown.srt").language)
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subtitles.getValue("Movie.srt").mimeType)
    }

    @Test
    fun `leaves out the subtitles of another video whose name goes on from this one`() = runTest {
        write("Blade.Runner.mkv", "")
        write("Blade.Runner.Final.Cut.mkv", "")
        write("Blade.Runner.el.srt", GREEK_TEXT)
        write("Blade.Runner.Final.Cut.el.srt", GREEK_TEXT)
        write("Blade.Runner.Final.Cut.srt", ENGLISH_TEXT)
        val siblings = library.list(Uri.fromFile(folder.root))

        val first = library.externalSubtitles("Blade.Runner.mkv", siblings).map { it.id }.toSet()
        val second = library.externalSubtitles("Blade.Runner.Final.Cut.mkv", siblings).map { it.id }.toSet()

        assertEquals(setOf("Blade.Runner.el.srt"), first)
        assertEquals(setOf("Blade.Runner.Final.Cut.el.srt", "Blade.Runner.Final.Cut.srt"), second)
    }

    private fun write(name: String, text: String, charset: Charset = Charsets.UTF_8) {
        folder.newFile(name).writeBytes(text.toByteArray(charset))
    }

    private companion object {
        const val GREEK_TEXT = "1\n00:00:01,000 --> 00:00:04,000\nΚαλημέρα. Πώς είσαι σήμερα;\n\n2\n00:00:04,500 --> 00:00:07,000\nΆργησες πολύ απόψε…\n"
        const val ENGLISH_TEXT = "1\n00:00:01,000 --> 00:00:04,000\nGood morning. How are you today?\n"
    }
}
