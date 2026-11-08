package com.vasilispngs.videoplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.vasilispngs.videoplayer.R
import com.vasilispngs.videoplayer.appContainer
import com.vasilispngs.videoplayer.data.ErrorCode
import com.vasilispngs.videoplayer.data.ThemeMode
import com.vasilispngs.videoplayer.player.resetDisplayMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

internal val TvOverscanHorizontal = 48.dp
internal val TvOverscanVertical = 27.dp
internal val TvGutter = 20.dp
internal val TvLineSpacing = 4.dp
private val ListItemInset = 16.dp
internal val TvListPadding = TvOverscanHorizontal - ListItemInset
internal const val TABULAR_FIGURES = "tnum"

@Serializable
sealed interface Screen : NavKey {
    @Serializable
    data object Home : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data class Folder(val uri: String, val title: String) : Screen

    @Serializable
    data class Player(val uri: String) : Screen
}

@Composable
fun VideoPlayerApp(viewModel: HomeViewModel, themeMode: ThemeMode, modifier: Modifier = Modifier) {
    val backStack = rememberSerializable(serializer = serializer()) { NavBackStack<Screen>(Screen.Home) }
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }
    val window = LocalWindow.current
    val playing = backStack.last() is Screen.Player
    SideEffect(playing) { if (!playing) window?.resetDisplayMode() }
    val updateConfirmation by viewModel.updateConfirmation.collectAsStateWithLifecycle()
    updateConfirmation?.let { confirmation ->
        SideEffect(confirmation) {
            viewModel.updateConfirmationShown(confirmation)
            val _ = runCatching { context.startActivity(confirmation) }
        }
    }
    val navigationBarScrim = WindowInsets.navigationBars.getBottom(LocalDensity.current) > 0
    SideEffect(window, navigationBarScrim) { window?.isNavigationBarContrastEnforced = navigationBarScrim }

    val open: (Screen) -> Unit = { screen -> if (backStack.last() != screen) backStack.add(screen) }
    val goBack: () -> Unit = { backStack.removeLastOrNull() }

    Surface(modifier = modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = goBack,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
            entryProvider = entryProvider {
                entry<Screen.Home> {
                    HomeScreen(viewModel = viewModel, thumbnails = container.thumbnails, onOpen = open, onSettings = { open(Screen.Settings) })
                }
                entry<Screen.Settings> {
                    SettingsScreen(viewModel = viewModel, themeMode = themeMode)
                }
                entry<Screen.Folder> { key ->
                    val folder = viewModel { FolderViewModel(key.uri.toUri(), container.library) }
                    FolderScreen(title = key.title, viewModel = folder, thumbnails = container.thumbnails, onOpen = open)
                }
                entry<Screen.Player> { key ->
                    val player = viewModel {
                        PlayerViewModel(key.uri.toUri(), container.library, container.playback, container.preferences, container.scope)
                    }
                    PlayerScreen(
                        viewModel = player,
                        onExit = goBack,
                        onNext = { next -> backStack[backStack.lastIndex] = Screen.Player(next.toString()) }
                    )
                }
            }
        )
    }
}

internal val TvContentPadding = PaddingValues(top = TvGutter, bottom = TvOverscanVertical)

@Composable
internal fun TvScreen(title: String, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = TvOverscanVertical)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TvOverscanHorizontal),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            action?.invoke()
        }
        content()
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .padding(horizontal = TvOverscanHorizontal)
            .semantics { heading() }
    )
}

@Composable
internal fun rememberInitialFocus(ready: Boolean = true): FocusRequester {
    val focus = remember { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(ready) {
        if (ready && inputModeManager.inputMode == InputMode.Keyboard) focus.requestFocus()
    }
    return focus
}

@Composable
internal fun Message(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TvOverscanHorizontal)
            .padding(TvContentPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun String.withCode(code: ErrorCode?): String {
    val resources = LocalResources.current
    return code?.let {
        resources.getString(
            R.string.error_with_code,
            this,
            when (it) {
                is ErrorCode.Http -> resources.getString(R.string.error_code_http, it.status)
                is ErrorCode.Named -> it.name
            }
        )
    } ?: this
}
