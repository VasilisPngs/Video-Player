package com.vasilispngs.videoplayer.data

import android.app.UiModeManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appPreferences: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

enum class ThemeMode(val nightMode: Int) {
    System(UiModeManager.MODE_NIGHT_AUTO),
    Light(UiModeManager.MODE_NIGHT_NO),
    Dark(UiModeManager.MODE_NIGHT_YES)
}

enum class AudioLanguage(val code: String?) {
    First(null),
    Greek(GREEK),
    English(ENGLISH)
}

enum class SubtitleLanguage(val code: String?) {
    Greek(GREEK),
    English(ENGLISH),
    Off(null)
}

enum class SubtitlePosition(val scale: Float) {
    VeryLow(0.5f),
    Low(1f),
    Higher(2f),
    VeryHigh(3f)
}

data class PlaybackSettings(
    val audioLanguage: AudioLanguage = AudioLanguage.First,
    val subtitleLanguage: SubtitleLanguage = SubtitleLanguage.Greek,
    val subtitlePosition: SubtitlePosition = SubtitlePosition.Low
)

class AppPreferences(
    private val context: Context,
    private val store: DataStore<Preferences> = context.appPreferences
) {
    val themeMode: Flow<ThemeMode> = store.data.map { it.read(THEME_MODE, ThemeMode.System) }.distinctUntilChanged()

    val playback: Flow<PlaybackSettings> = store.data.map {
        PlaybackSettings(
            audioLanguage = it.read(AUDIO_LANGUAGE, AudioLanguage.First),
            subtitleLanguage = it.read(SUBTITLE_LANGUAGE, SubtitleLanguage.Greek),
            subtitlePosition = it.read(SUBTITLE_POSITION, SubtitlePosition.Low)
        )
    }.distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
        applyNightMode(mode)
    }

    suspend fun restoreThemeMode(): ThemeMode = themeMode.first().also(::applyNightMode)

    suspend fun setAudioLanguage(value: AudioLanguage) = store.edit { it[AUDIO_LANGUAGE] = value.name }

    suspend fun setSubtitleLanguage(value: SubtitleLanguage) = store.edit { it[SUBTITLE_LANGUAGE] = value.name }

    suspend fun setSubtitlePosition(value: SubtitlePosition) = store.edit { it[SUBTITLE_POSITION] = value.name }

    private inline fun <reified T : Enum<T>> Preferences.read(key: Preferences.Key<String>, default: T): T =
        enumValues<T>().firstOrNull { it.name == this[key] } ?: default

    private fun applyNightMode(mode: ThemeMode) {
        context.getSystemService(UiModeManager::class.java).setApplicationNightMode(mode.nightMode)
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUDIO_LANGUAGE = stringPreferencesKey("audio_language")
        val SUBTITLE_LANGUAGE = stringPreferencesKey("subtitle_language")
        val SUBTITLE_POSITION = stringPreferencesKey("subtitle_position")
    }
}

internal const val GREEK = "el"
internal const val ENGLISH = "en"
