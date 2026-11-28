package com.vasilispngs.videoplayer.ui

import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.vasilispngs.videoplayer.ui.theme.VideoPlayerTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowWindowManagerGlobal
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television")
class OptionDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun enterKeyboardMode() {
        setTouchMode(false)
    }

    @After
    fun restoreTouchMode() {
        setTouchMode(true)
    }

    @Test
    fun everyOptionOfALongListIsReachableWithTheDpad() {
        val options = (1..8).map { "Option $it" }
        var dialogInput: InputModeManager? = null
        compose.setContent {
            VideoPlayerTheme {
                OptionDialog(
                    title = "Options",
                    options = options,
                    current = options.first(),
                    label = { option ->
                        dialogInput = LocalInputModeManager.current
                        option
                    },
                    onDismiss = {},
                    onSelect = {}
                )
            }
        }
        compose.runOnIdle { dialogInput!!.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithText(options.first()).requestFocus()
        options.drop(1).forEach { option ->
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithText(option).assertIsFocused().assertIsDisplayed()
        }
    }

    @Test
    fun focusesTheFirstOptionWhenNoneIsSelected() {
        compose.setContent {
            VideoPlayerTheme {
                OptionDialog(
                    title = "Audio",
                    options = listOf("Track 1", "Track 2"),
                    current = null,
                    label = { it.orEmpty() },
                    onDismiss = {},
                    onSelect = {}
                )
            }
        }

        compose.onNodeWithText("Track 1").assertIsFocused()
    }

    @Test
    fun focusesCancelWhenThereAreNoOptions() {
        compose.setContent {
            VideoPlayerTheme {
                OptionDialog<String?>(
                    title = "Audio",
                    options = emptyList(),
                    current = null,
                    label = { it.orEmpty() },
                    onDismiss = {},
                    onSelect = {}
                )
            }
        }

        compose.onNode(isFocused()).assertIsDisplayed()
    }

    private fun setTouchMode(inTouchMode: Boolean) {
        ReflectionHelpers.callStaticMethod<Unit>(
            ShadowWindowManagerGlobal::class.java,
            "setInTouchMode",
            ClassParameter.from(Boolean::class.javaPrimitiveType, inTouchMode)
        )
    }
}
