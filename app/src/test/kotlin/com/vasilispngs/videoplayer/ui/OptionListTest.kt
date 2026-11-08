package com.vasilispngs.videoplayer.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.vasilispngs.videoplayer.ui.theme.VideoPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television")
class OptionListTest {
    @get:Rule
    val compose = createComposeRule()

    private val options = (1..40).map { "Option $it" }
    private val list = FocusRequester()

    @Test
    fun everyOptionIsReachableWithTheDpad() {
        show(current = options.first())
        compose.onNodeWithText(options.first()).assertIsFocused()
        options.drop(1).forEach { option ->
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithText(option).assertIsFocused().assertIsDisplayed()
        }
    }

    @Test
    fun focusStartsOnTheCurrentOption() {
        show(current = options[34])
        compose.onNodeWithText(options[34]).assertIsFocused().assertIsDisplayed()
    }

    @Test
    fun movingIntoTheListFocusesTheCurrentOption() {
        lateinit var input: InputModeManager
        compose.setContent {
            input = LocalInputModeManager.current
            VideoPlayerTheme {
                Row {
                    ListItem(selected = false, onClick = {}, headlineContent = { Text(text = "Section") }, modifier = Modifier.weight(1f))
                    OptionList(options = options.take(3), current = options[1], label = { it }, onSelect = {}, modifier = Modifier.weight(1f))
                }
            }
        }
        compose.runOnIdle { input.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithText("Section").requestFocus()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText(options[1]).assertIsFocused()
    }

    private fun show(current: String) {
        lateinit var input: InputModeManager
        compose.setContent {
            input = LocalInputModeManager.current
            VideoPlayerTheme {
                OptionList(options = options, current = current, label = { it }, onSelect = {}, modifier = Modifier.fillMaxSize(), currentFocus = list)
            }
        }
        compose.runOnIdle { input.requestInputMode(InputMode.Keyboard) }
        compose.runOnIdle { list.requestFocus() }
    }
}
