package com.vasilispngs.videoplayer.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.core.net.toUri
import com.vasilispngs.videoplayer.R
import com.vasilispngs.videoplayer.data.Computer
import com.vasilispngs.videoplayer.data.Entry
import com.vasilispngs.videoplayer.data.ErrorCode
import com.vasilispngs.videoplayer.data.Kind
import com.vasilispngs.videoplayer.data.Playback
import com.vasilispngs.videoplayer.data.PlaybackSettings
import com.vasilispngs.videoplayer.data.SelfUpdate
import com.vasilispngs.videoplayer.data.ThemeMode
import com.vasilispngs.videoplayer.data.Volume
import com.vasilispngs.videoplayer.ui.theme.VideoPlayerTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AccessibilityTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var dark by mutableStateOf(false)

    @Before
    fun enableAccessibilityChecks() {
        compose.enableAccessibilityChecks()
    }

    @Test
    fun home() {
        showHome()
        checkLightAndDark()
    }

    @Test
    fun addComputer() {
        showHome()
        compose.onNodeWithText(string(R.string.add_computer)).performClick()
        checkLightAndDark()
    }

    @Test
    fun removeComputer() {
        showHome()
        compose.onAllNodesWithContentDescription(string(R.string.remove)).onFirst().performClick()
        checkLightAndDark()
    }

    @Test
    fun settings() {
        show {
            SettingsContent(
                settings = PlaybackSettings(),
                themeMode = ThemeMode.System,
                selfUpdate = SelfUpdate.Downloading(DOWNLOADED),
                onBack = {},
                onUpdateApp = {},
                onAudioLanguage = {},
                onSubtitleLanguage = {},
                onSubtitleSize = {},
                onSubtitlePosition = {},
                onThemeMode = {}
            )
        }
        checkLightAndDark()
        compose.onNodeWithText(string(R.string.subtitle_size)).performClick()
        checkLightAndDark()
    }

    @Test
    fun folder() {
        showFolder(
            Listing.Loaded(
                listOf(
                    Entry("Season 1", "file:///storage/usb/Season%201".toUri(), Kind.Folder, 0),
                    Entry("Movie.mkv", "file:///storage/usb/Movie.mkv".toUri(), Kind.Video, SIZE),
                    Entry("Movie.el.srt", "file:///storage/usb/Movie.el.srt".toUri(), Kind.File, 1)
                )
            )
        )
        checkLightAndDark()
    }

    @Test
    fun emptyFolder() {
        showFolder(Listing.Loaded(emptyList()))
        checkLightAndDark()
    }

    @Test
    fun failedFolder() {
        showFolder(Listing.Failed(ErrorCode.Named("STATUS_ACCESS_DENIED")))
        checkLightAndDark()
    }

    private fun showHome() = show {
        HomeContent(
            volumes = listOf(Volume("SanDisk", "file:///storage/usb".toUri())),
            computers = listOf(Computer(id = 1, host = "192.168.1.10", share = "Ταινίες", user = "user", password = ByteArray(0))),
            inProgress = listOf(Playback(uri = "file:///storage/usb/Movie.mkv", size = SIZE, positionMs = POSITION, durationMs = DURATION)),
            allFiles = false,
            onAllFiles = {},
            adding = Connection.Idle,
            onOpen = {},
            onSettings = {},
            onAddComputer = { _, _, _, _ -> },
            onAddingHandled = {},
            onRemoveComputer = {}
        )
    }

    private fun showFolder(listing: Listing) = show {
        FolderContent(title = "SanDisk", listing = listing, onOpen = {}, onBack = {})
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            VideoPlayerTheme(darkTheme = dark) { content() }
        }
    }

    private fun checkLightAndDark() {
        dark = false
        compose.onRoot().tryPerformAccessibilityChecks()
        dark = true
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    private fun string(@StringRes id: Int): String = compose.activity.getString(id)

    private companion object {
        const val DOWNLOADED = 0.4f
        const val SIZE = 4_000_000_000L
        const val POSITION = 1_800_000L
        const val DURATION = 7_200_000L
    }
}
