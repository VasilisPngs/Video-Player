package com.vasilispngs.videoplayer.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import com.vasilispngs.videoplayer.data.ENGLISH
import com.vasilispngs.videoplayer.data.Entry
import com.vasilispngs.videoplayer.data.GREEK
import com.vasilispngs.videoplayer.data.Kind
import com.vasilispngs.videoplayer.data.Library

private val SUBTITLE_TYPES = mapOf(
    "srt" to MimeTypes.APPLICATION_SUBRIP,
    "ass" to MimeTypes.TEXT_SSA,
    "ssa" to MimeTypes.TEXT_SSA,
    "vtt" to MimeTypes.TEXT_VTT
)
private val GREEK_TAGS = setOf("el", "gr", "gre", "ell", "greek")
private val ENGLISH_TAGS = setOf("en", "eng", "english")
private const val FORCED_TAG = "forced"
private const val SAMPLE_BYTES = 64 * 1024

suspend fun Library.externalSubtitles(video: String, siblings: List<Entry>): List<SubtitleConfiguration> {
    val base = video.substringBeforeLast('.')
    val longer = siblings.filter { it.kind == Kind.Video }.map { it.name.substringBeforeLast('.') }.filter { it.length > base.length }
    return siblings.mapNotNull { entry ->
        val mimeType = SUBTITLE_TYPES[entry.name.substringAfterLast('.', "").lowercase()] ?: return@mapNotNull null
        val stem = entry.name.substringBeforeLast('.')
        if (!stem.belongsTo(base) || longer.any(stem::belongsTo)) return@mapNotNull null
        val tags = stem.substring(base.length).lowercase().split('.').filter(String::isNotEmpty)
        val language = when {
            tags.any { it in GREEK_TAGS } -> GREEK
            tags.any { it in ENGLISH_TAGS } -> ENGLISH
            isGreek(head(entry.uri, SAMPLE_BYTES)) -> GREEK
            else -> null
        }
        SubtitleConfiguration.Builder(entry.uri)
            .setId(entry.name)
            .setLabel(entry.name)
            .setMimeType(mimeType)
            .setLanguage(language)
            .setSelectionFlags(if (FORCED_TAG in tags) C.SELECTION_FLAG_FORCED else 0)
            .build()
    }
}

private fun String.belongsTo(base: String) = startsWith(base, ignoreCase = true) && (length == base.length || this[base.length] == '.')

internal fun isGreek(bytes: ByteArray): Boolean {
    val charset = SubtitleCharsetDetector.detect(bytes, 0, bytes.size) ?: Charsets.UTF_8
    val letters = String(bytes, charset).filter(Char::isLetter)
    return letters.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.GREEK } * 2 > letters.length
}
