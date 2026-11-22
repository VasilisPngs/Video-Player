package com.vasilispngs.videoplayer.ui

import android.net.Uri
import android.view.KeyEvent
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import com.vasilispngs.videoplayer.R
import com.vasilispngs.videoplayer.data.Entry
import com.vasilispngs.videoplayer.data.ErrorCode
import com.vasilispngs.videoplayer.data.Kind
import com.vasilispngs.videoplayer.data.Thumbnails
import com.vasilispngs.videoplayer.ui.theme.VideoPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television")
class FolderContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val thumbnails = Thumbnails(context)
    private val opened = mutableListOf<Screen>()
    private val deleted = mutableListOf<String>()
    private var failuresShown = 0

    @Test
    fun `asks before deleting a video held with the remote and then focuses the next one`() {
        show(listOf(file("Episode 1.el.srt"), video("Episode 1.mkv"), video("Episode 2.mkv")), keyboard = true)
        val episode = compose.onNodeWithText("Episode 1.mkv").requestFocus()

        episode.performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(LONG_PRESS_MS)
        episode.performKeyInput { keyUp(Key.DirectionCenter) }
        compose.onNodeWithText(string(R.string.delete_message_subtitles, "Episode 1.mkv")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cancel)).assertIsFocused()
        releaseHeldKeyInDialog()
        compose.onNodeWithText(string(R.string.delete_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.delete)).performClick()

        assertTrue(opened.isEmpty())
        assertEquals(listOf("Episode 1.mkv"), deleted)
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText("Episode 2.mkv").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `asks before deleting a video held by touch and keeps it on cancel`() {
        show(listOf(video("Movie.mkv")))

        compose.onNodeWithText("Movie.mkv").performTouchInput { longClick() }
        compose.onNodeWithText(string(R.string.delete_message, "Movie.mkv")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cancel)).performClick()

        compose.onNode(isDialog()).assertDoesNotExist()
        assertTrue(deleted.isEmpty())
        assertTrue(opened.isEmpty())
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `asks before deleting a whole folder held by touch`() {
        show(listOf(folder("Season 1"), video("Movie.mkv")))

        compose.onNodeWithText("Season 1").performTouchInput { longClick() }
        compose.onNodeWithText(string(R.string.delete_folder_message, "Season 1")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.delete)).performClick()

        assertEquals(listOf("Season 1"), deleted)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `says why a video could not be deleted`() {
        compose.mainClock.autoAdvance = false
        show(listOf(video("Movie.mkv")), failure = ErrorCode.Named("AccessDeniedException"))
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText(string(R.string.error_with_code, string(R.string.delete_failed), "AccessDeniedException")).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(SNACKBAR_MS)
        compose.runOnIdle { assertEquals(1, failuresShown) }
    }

    private fun releaseHeldKeyInDialog() {
        val dialog = compose.onNode(isDialog()).fetchSemanticsNode().root as View
        compose.runOnIdle {
            dialog.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1))
            dialog.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        }
    }

    private fun show(entries: List<Entry>, keyboard: Boolean = false, failure: ErrorCode? = null) {
        if (keyboard) InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        compose.setContent {
            if (keyboard) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
            }
            VideoPlayerTheme {
                FolderContent(
                    title = "SanDisk",
                    listing = Listing.Loaded(entries),
                    deleteFailure = failure,
                    thumbnails = thumbnails,
                    onOpen = { opened += it },
                    onDelete = { deleted += it.name },
                    onDeleteFailureShown = { failuresShown++ },
                    onBack = {}
                )
            }
        }
    }

    private fun video(name: String) = Entry(name, Uri.fromFile(File(DRIVE, name)), Kind.Video, 1)

    private fun file(name: String) = Entry(name, Uri.fromFile(File(DRIVE, name)), Kind.File, 1)

    private fun folder(name: String) = Entry(name, Uri.fromFile(File(DRIVE, name)), Kind.Folder, 0)

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private companion object {
        const val DRIVE = "/storage/1234-5678/Series"
        const val LONG_PRESS_MS = 1_000L
        const val SNACKBAR_MS = 10_000L
    }
}
