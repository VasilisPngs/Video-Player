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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television")
class OptionDialogTest {
    @get:Rule
    val compose = createComposeRule()

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
}
