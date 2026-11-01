package com.vasilispngs.videoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class SubtitleCharsetTest {
    @Test
    fun `reads Greek subtitles saved by Windows`() = assertReadsBack(GREEK_WITH_PUNCTUATION, Charset.forName("windows-1253"))

    @Test
    fun `reads Greek subtitles saved as ISO-8859-7`() = assertReadsBack(GREEK, Charset.forName("ISO-8859-7"))

    @Test
    fun `reads Greek subtitles saved as UTF-8 without a byte order mark`() = assertReadsBack(GREEK_WITH_PUNCTUATION, Charsets.UTF_8)

    @Test
    fun `reads Greek subtitles saved as UTF-8 with a byte order mark`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + GREEK_WITH_PUNCTUATION.toByteArray(Charsets.UTF_8)

        assertEquals(Charsets.UTF_8, SubtitleCharsetDetector.detect(bytes, 0, bytes.size))
    }

    @Test
    fun `reads Greek subtitles saved as UTF-16`() {
        val bytes = GREEK_WITH_PUNCTUATION.toByteArray(Charsets.UTF_16)

        assertEquals(Charsets.UTF_16BE, SubtitleCharsetDetector.detect(bytes, 0, bytes.size))
    }

    @Test
    fun `knows Greek subtitles by their letters in any encoding`() {
        assertTrue(isGreek(GREEK_WITH_PUNCTUATION.toByteArray(Charset.forName("windows-1253"))))
        assertTrue(isGreek(GREEK.toByteArray(Charset.forName("ISO-8859-7"))))
        assertTrue(isGreek(GREEK_WITH_PUNCTUATION.toByteArray(Charsets.UTF_8)))
        assertFalse(isGreek(ENGLISH.toByteArray(Charsets.UTF_8)))
    }

    private fun assertReadsBack(text: String, charset: Charset) {
        val bytes = text.toByteArray(charset)

        assertEquals(text, String(bytes, checkNotNull(SubtitleCharsetDetector.detect(bytes, 0, bytes.size))))
    }

    private companion object {
        val GREEK = """
            1
            00:00:01,000 --> 00:00:04,000
            Καλημέρα. Πώς είσαι σήμερα;

            2
            00:00:04,500 --> 00:00:07,000
            Άργησες πολύ απόψε, ξέρεις.

            3
            00:00:07,500 --> 00:00:10,000
            Ναι, συγγνώμη. Είχε πολλή κίνηση στον δρόμο.

            4
            00:00:10,500 --> 00:00:13,000
            Έλα, κάθισε. Το φαγητό θα κρυώσει.

            5
            00:00:13,500 --> 00:00:16,000
            Ευχαριστώ. Μυρίζει υπέροχα, όπως πάντα.

        """.trimIndent()

        val GREEK_WITH_PUNCTUATION = GREEK.replace("ξέρεις.", "ξέρεις…").replace("Ναι,", "«Ναι»,")

        val ENGLISH = """
            1
            00:00:01,000 --> 00:00:04,000
            Good morning. How are you today?

            2
            00:00:04,500 --> 00:00:07,000
            You were very late tonight, you know.

        """.trimIndent()
    }
}
