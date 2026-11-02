package com.vasilispngs.videoplayer.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.isCrossWindowBlurEnabled
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.vasilispngs.videoplayer.R
import com.vasilispngs.videoplayer.appContainer
import com.vasilispngs.videoplayer.data.ErrorCode
import com.vasilispngs.videoplayer.data.ThemeMode
import com.vasilispngs.videoplayer.player.resetDisplayMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.math.sign

internal val CompactMargin = 16.dp
internal val ContentMargin = 16.dp
private val WideMargin = 24.dp
private val MaxListWidth = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp
internal val SmallSpacing = 8.dp
private val HeaderSpacing = 20.dp
private val OptionHeight = 56.dp
internal val TvOverscanHorizontal = 48.dp
internal val TvOverscanVertical = 27.dp
private val ForwardSlide = 96.dp
private const val PREDICTIVE_BACK_EXIT_SCALE = 0.9f
private const val PREDICTIVE_BACK_ENTER_SCALE = 1.1f
private const val PREDICTIVE_BACK_FADE_THRESHOLD = 0.35f
private val PredictiveBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
private val PredictiveBackExitFade = Easing { (PredictiveBackEasing.transform(it) / PREDICTIVE_BACK_FADE_THRESHOLD).coerceAtMost(1f) }
private val PredictiveBackEnterFade = Easing {
    ((PredictiveBackEasing.transform(it) - PREDICTIVE_BACK_FADE_THRESHOLD) / (1f - PREDICTIVE_BACK_FADE_THRESHOLD)).coerceAtLeast(0f)
}
private val DialogBlurRadius = 10.dp
private const val BLURRED_SCRIM_ALPHA = 0.2f
internal const val SCRIM_ALPHA = 0.32f
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
    val window = LocalActivity.current?.window
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
    val motion = MaterialTheme.motionScheme
    val slide = with(LocalDensity.current) { ForwardSlide.roundToPx() }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = goBack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        transitionSpec = { forwardTransition(motion, slide) },
        popTransitionSpec = { backTransition(motion) },
        predictivePopTransitionSpec = { predictiveBackTransition() },
        entryProvider = entryProvider {
            entry<Screen.Home> {
                HomeScreen(viewModel = viewModel, thumbnails = container.thumbnails, onOpen = open, onSettings = { open(Screen.Settings) })
            }
            entry<Screen.Settings> {
                SettingsScreen(viewModel = viewModel, themeMode = themeMode, onBack = goBack)
            }
            entry<Screen.Folder> { key ->
                val folder = viewModel { FolderViewModel(key.uri.toUri(), container.library) }
                FolderScreen(title = key.title, viewModel = folder, thumbnails = container.thumbnails, onOpen = open, onBack = goBack)
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

@Composable
internal fun ListScreen(
    title: String,
    focus: FocusRequester,
    onBack: (() -> Unit)?,
    action: (@Composable (Modifier) -> Unit)? = null,
    content: @Composable (contentPadding: PaddingValues, nestedScroll: NestedScrollConnection) -> Unit
) {
    val windowInfo = currentWindowAdaptiveInfoV2()
    val margin = if (windowInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
        WideMargin
    } else {
        CompactMargin
    }
    val context = LocalContext.current
    val television = remember(context) { context.isTelevision }
    val overscan = if (television) {
        WindowInsets(TvOverscanHorizontal, TvOverscanVertical, TvOverscanHorizontal, TvOverscanVertical)
    } else {
        WindowInsets(0)
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val contentInsets = ScaffoldDefaults.contentWindowInsets.add(overscan.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
    var consumedInsets by remember { mutableStateOf(WindowInsets(0)) }
    BoxWithConstraints(modifier = Modifier.onConsumedWindowInsetsChanged { consumedInsets = it }) {
        val layoutDirection = LocalLayoutDirection.current
        val insets = contentInsets.exclude(consumedInsets).asPaddingValues()
        val safeWidth = maxWidth - insets.calculateStartPadding(layoutDirection) - insets.calculateEndPadding(layoutDirection)
        val sideMargin = ((safeWidth - MaxListWidth) / 2).coerceAtLeast(margin)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = title) },
                    navigationIcon = {
                        if (onBack != null) {
                            BarAction(
                                icon = R.drawable.ic_arrow_back,
                                label = stringResource(R.string.back),
                                onClick = onBack,
                                modifier = Modifier.focusProperties { down = focus }
                            )
                        }
                    },
                    actions = { action?.invoke(Modifier.focusProperties { down = focus }) },
                    windowInsets = TopAppBarDefaults.windowInsets.add(overscan.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                    contentPadding = PaddingValues(horizontal = sideMargin - CompactMargin),
                    scrollBehavior = scrollBehavior
                )
            },
            contentWindowInsets = contentInsets
        ) { padding ->
            content(
                PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection) + sideMargin,
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(layoutDirection) + sideMargin,
                    bottom = padding.calculateBottomPadding() + margin
                ),
                scrollBehavior.nestedScrollConnection
            )
        }
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text = label) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = modifier) {
            Icon(painter = painterResource(icon), contentDescription = label)
        }
    }
}

@Composable
internal fun Message(text: String, contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = ContentMargin, top = HeaderSpacing, end = ContentMargin, bottom = SmallSpacing)
            .semantics { heading() }
    )
}

@Composable
internal fun <T> OptionDialog(
    title: String,
    options: List<T>,
    current: T,
    label: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    extraButton: (@Composable () -> Unit)? = null
) {
    val focus = rememberInitialFocus()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        dismissButton = extraButton,
        title = { Text(text = title) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { option ->
                    val selected = option == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (selected) Modifier.focusRequester(focus) else Modifier)
                            .height(OptionHeight)
                            .selectable(selected = selected, onClick = { onSelect(option) }, role = Role.RadioButton),
                        horizontalArrangement = Arrangement.spacedBy(ContentMargin),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(text = label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        properties = dialogProperties()
    )
}

@Composable
internal fun dialogProperties() = DialogProperties(
    blurBehindRadius = DialogBlurRadius,
    scrimAlpha = if (LocalWindowInfo.current.isCrossWindowBlurEnabled) BLURRED_SCRIM_ALPHA else SCRIM_ALPHA
)

@Composable
internal fun listItemColors() = MaterialTheme.colorScheme.surfaceContainer.let { container ->
    ListItemDefaults.segmentedColors(containerColor = container, disabledContainerColor = container)
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

internal val Context.isTelevision: Boolean
    get() = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

private fun AnimatedContentTransitionScope<*>.forwardTransition(motion: MotionScheme, distance: Int) =
    (slideIntoContainer(SlideDirection.Start, motion.defaultSpatialSpec()) { it.sign * distance } + fadeIn(motion.fastEffectsSpec()))
        .togetherWith(slideOutOfContainer(SlideDirection.Start, motion.defaultSpatialSpec()) { it.sign * distance })

private fun backTransition(motion: MotionScheme) =
    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), PREDICTIVE_BACK_ENTER_SCALE))
        .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.defaultSpatialSpec(), PREDICTIVE_BACK_EXIT_SCALE))

private fun predictiveBackTransition() =
    (fadeIn(tween(easing = PredictiveBackEnterFade)) + scaleIn(tween(easing = PredictiveBackEasing), PREDICTIVE_BACK_ENTER_SCALE))
        .togetherWith(fadeOut(tween(easing = PredictiveBackExitFade)) + scaleOut(tween(easing = PredictiveBackEasing), PREDICTIVE_BACK_EXIT_SCALE))
