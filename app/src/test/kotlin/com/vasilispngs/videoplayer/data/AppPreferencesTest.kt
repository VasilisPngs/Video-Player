package com.vasilispngs.videoplayer.data

import android.app.UiModeManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AppPreferencesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private val uiModeManager = context.getSystemService(UiModeManager::class.java)

    @Test
    fun `starts with the system theme`() = runTest {
        assertEquals(ThemeMode.System, preferences().themeMode.first())
    }

    @Test
    fun `applies the chosen theme`() = runTest {
        preferences().setThemeMode(ThemeMode.Dark)

        assertEquals(UiModeManager.MODE_NIGHT_YES, shadowOf(uiModeManager).applicationNightMode)
    }

    @Test
    fun `starts with the first audio track, Greek subtitles and Android's medium size`() = runTest {
        assertEquals(
            PlaybackSettings(AudioLanguage.First, SubtitleLanguage.Greek, SubtitleSize.Medium, SubtitlePosition.Low),
            preferences().playback.first()
        )
    }

    @Test
    fun `remembers every playback setting`() = runTest {
        val preferences = preferences()
        preferences.setAudioLanguage(AudioLanguage.English)
        preferences.setSubtitleLanguage(SubtitleLanguage.Off)
        preferences.setSubtitleSize(SubtitleSize.VeryLarge)
        preferences.setSubtitlePosition(SubtitlePosition.VeryHigh)

        assertEquals(
            PlaybackSettings(AudioLanguage.English, SubtitleLanguage.Off, SubtitleSize.VeryLarge, SubtitlePosition.VeryHigh),
            preferences.playback.first()
        )
    }

    private fun TestScope.preferences() = AppPreferences(
        context,
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "app.preferences_pb") }
    )
}
