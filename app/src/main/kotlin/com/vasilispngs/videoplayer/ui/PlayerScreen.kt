@file:OptIn(UnstableApi::class)

package com.vasilispngs.videoplayer.ui

import android.hardware.display.DisplayManager
import android.net.Uri
import android.text.format.DateUtils
import android.view.accessibility.CaptioningManager
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.DefaultTrackNameProvider
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.state.rememberCuesState
import androidx.media3.ui.compose.state.rememberErrorState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.tv.material3.Button
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.vasilispngs.videoplayer.R
import com.vasilispngs.videoplayer.data.ErrorCode
import com.vasilispngs.videoplayer.data.fileName
import com.vasilispngs.videoplayer.player.audio
import com.vasilispngs.videoplayer.player.buildPlayer
import com.vasilispngs.videoplayer.player.matchFrameRate
import com.vasilispngs.videoplayer.player.text
import com.vasilispngs.videoplayer.ui.theme.VideoPlayerTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val NEXT_COUNTDOWN_SECONDS = 10
private const val PROGRESS_TICK_MS = 1_000L
private const val PANEL_FRACTION = 0.32f
private const val PROGRESS_TRACK_ALPHA = 0.24f
private val ProgressHeight = 4.dp

private enum class Menu { Audio, Subtitles }

@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onExit: () -> Unit, onNext: (Uri) -> Unit) {
    val context = LocalContext.current
    val window = LocalWindow.current
    val displayManager = remember(context) { context.getSystemService(DisplayManager::class.java) }
    val scope = rememberCoroutineScope()
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var frameRateNotice by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    val failure by viewModel.failure.collectAsStateWithLifecycle()

    LifecycleStartEffect(viewModel) {
        val exo = buildPlayer(context, viewModel.delay)
        val session = MediaSession.Builder(context, exo).setId(viewModel.uri.toString()).build()
        player = exo
        val start = scope.launch {
            viewModel.start(exo) { frameRate -> window?.matchFrameRate(displayManager, frameRate) { frameRateNotice = it } }
        }
        onStopOrDispose {
            start.cancel()
            viewModel.stop(exo)
            session.release()
            exo.release()
            player = null
        }
    }

    VideoPlayerTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.scrim, contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            player?.let { PlayerContent(player = it, viewModel = viewModel, frameRateNotice = frameRateNotice, onExit = onExit, onNext = onNext) }
            failure?.let { Message(text = stringResource(R.string.playback_failed).withCode(it)) }
        }
    }
}

@Composable
private fun PlayerContent(
    player: ExoPlayer,
    viewModel: PlayerViewModel,
    frameRateNotice: CompletableDeferred<Unit>?,
    onExit: () -> Unit,
    onNext: (Uri) -> Unit
) {
    val subtitleDelay by viewModel.subtitleDelayMs.collectAsStateWithLifecycle()
    val playPause = rememberPlayPauseButtonState(player)
    val error = rememberErrorState(player).error
    val exit by rememberUpdatedState(onExit)
    var controls by remember { mutableStateOf(false) }
    var focusControls by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Menu?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var next by remember { mutableStateOf<Uri?>(null) }
    var interactions by remember { mutableIntStateOf(0) }
    var rootFocused by remember { mutableStateOf(false) }
    val root = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val audioFocus = remember { FocusRequester() }
    val subtitlesFocus = remember { FocusRequester() }
    val playing = !playPause.showPlay
    val hide = {
        root.requestFocus()
        controls = false
        syncing = false
    }
    val closeMenu: () -> Unit = {
        val opener = if (menu == Menu.Audio) audioFocus else subtitlesFocus
        menu = null
        opener.requestFocus()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_ENDED) return
                val upcoming = viewModel.next
                if (upcoming != null) next = upcoming else exit()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val switching = frameRateNotice != null
    LaunchedEffect(switching) { if (!switching) root.requestFocus() }

    LaunchedEffect(controls, focusControls) {
        if (controls && focusControls) {
            playFocus.requestFocus()
            focusControls = false
        }
    }

    LaunchedEffect(controls, interactions, playing, menu) {
        if (controls && playing && menu == null) {
            delay(PlayerControlView.DEFAULT_SHOW_TIMEOUT_MS.milliseconds)
            hide()
        }
    }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = controls || syncing || menu != null,
        onBackCompleted = { if (menu != null) closeMenu() else hide() }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (playing) Modifier.keepScreenOn() else Modifier)
            .onFocusChanged { rootFocused = it.isFocused }
            .focusRequester(root)
            .focusable()
            .onKeyEvent { event ->
                val seek = event.key == Key.DirectionLeft || event.key == Key.DirectionRight
                if (event.type == KeyEventType.KeyUp && seek) player.isScrubbingModeEnabled = false
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                interactions++
                if (!rootFocused) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        Util.handlePlayPauseButtonAction(player)
                        controls = !player.playWhenReady
                    }
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (event.nativeKeyEvent.repeatCount > 0) player.isScrubbingModeEnabled = true
                        if (event.key == Key.DirectionLeft) player.seekBack() else player.seekForward()
                        controls = true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        controls = true
                        focusControls = true
                    }
                    else -> return@onKeyEvent false
                }
                true
            }
    ) {
        ContentFrame(
            player = player,
            modifier = Modifier.fillMaxSize(),
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            subtitles = { Subtitles(player = player) }
        )
        AnimatedVisibility(
            visible = controls,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Controls(
                    player = player,
                    title = viewModel.uri.fileName,
                    playing = playing,
                    playFocus = playFocus,
                    audioFocus = audioFocus,
                    subtitlesFocus = subtitlesFocus,
                    onAudio = { menu = Menu.Audio },
                    onSubtitles = { menu = Menu.Subtitles }
                )
            }
        }
        if (syncing) {
            SubtitleSync(
                delayMs = subtitleDelay,
                onShift = viewModel::shiftSubtitles,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = TvOverscanVertical)
            )
        }
        next?.let { upcoming ->
            NextVideo(
                name = upcoming.fileName,
                onPlay = {
                    player.stop()
                    onNext(upcoming)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = TvOverscanHorizontal, vertical = TvOverscanVertical)
            )
        }
        menu?.let { open ->
            val tracks = remember(open) { player.currentTracks }
            TrackPanel(
                title = stringResource(if (open == Menu.Audio) R.string.audio else R.string.subtitles),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) { focus ->
                when (open) {
                    Menu.Audio -> OptionList<Tracks.Group?>(
                        options = tracks.audio,
                        current = tracks.audio.firstOrNull(Tracks.Group::isSelected),
                        label = { group -> group?.let { trackName(it) }.orEmpty() },
                        onSelect = { group ->
                            closeMenu()
                            group?.let { viewModel.chooseAudio(player, it) }
                        },
                        currentFocus = focus
                    )
                    Menu.Subtitles -> {
                        val current = tracks.text.firstOrNull(Tracks.Group::isSelected)
                        OptionList<Tracks.Group?>(
                            options = listOf(null) + tracks.text,
                            current = current,
                            label = { group -> group?.let { trackName(it) } ?: stringResource(R.string.subtitles_off) },
                            onSelect = {
                                closeMenu()
                                viewModel.chooseText(player, it)
                            },
                            currentFocus = focus,
                            footer = {
                                ListItem(
                                    selected = false,
                                    onClick = {
                                        menu = null
                                        controls = false
                                        syncing = true
                                    },
                                    enabled = current != null,
                                    headlineContent = { Text(text = stringResource(R.string.subtitle_sync)) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        )
                    }
                }
            }
        }
        frameRateNotice?.let { notice -> FrameRateNotice(onPlay = { notice.complete(Unit) }, modifier = Modifier.align(Alignment.Center)) }
        error?.let { Message(text = stringResource(R.string.playback_failed).withCode(ErrorCode.Named(it.errorCodeName))) }
    }
}

@Composable
private fun TrackPanel(title: String, modifier: Modifier = Modifier, content: @Composable (FocusRequester) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(PANEL_FRACTION)
            .focusProperties { onExit = { cancelFocusChange() } }
    ) {
        Column(modifier = Modifier.padding(start = TvGutter, top = TvOverscanVertical, end = TvListPadding)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .padding(horizontal = TvOverscanHorizontal - TvListPadding)
                    .semantics { heading() }
            )
            content(focus)
        }
    }
}

@Composable
private fun Subtitles(player: Player) {
    val context = LocalContext.current
    val view = remember(context) { SubtitleView(context) }
    val captioning = remember(context) { context.getSystemService(CaptioningManager::class.java) }
    var style by remember(captioning) { mutableStateOf(captioning.userStyle) }
    var scale by remember(captioning) { mutableFloatStateOf(captioning.fontScale) }
    DisposableEffect(captioning) {
        val listener = object : CaptioningManager.CaptioningChangeListener() {
            override fun onUserStyleChanged(userStyle: CaptioningManager.CaptionStyle) {
                style = userStyle
            }

            override fun onFontScaleChanged(fontScale: Float) {
                scale = fontScale
            }
        }
        captioning.addCaptioningChangeListener(listener)
        onDispose { captioning.removeCaptioningChangeListener(listener) }
    }
    val cues = rememberCuesState(player).cues
    AndroidView(
        factory = { view },
        modifier = Modifier.fillMaxSize(),
        update = {
            it.setStyle(CaptionStyleCompat.createFromCaptionStyle(style))
            it.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale)
            it.setCues(cues)
        }
    )
}

@Composable
private fun BoxScope.Controls(
    player: Player,
    title: String,
    playing: Boolean,
    playFocus: FocusRequester,
    audioFocus: FocusRequester,
    subtitlesFocus: FocusRequester,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit
) {
    val progress = rememberProgressStateWithTickInterval(player, PROGRESS_TICK_MS)
    val playPause = rememberPlayPauseButtonState(player)
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(CardDefaults.ScrimBrush)
    )
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = TvOverscanHorizontal, vertical = TvOverscanVertical),
        verticalArrangement = Arrangement.spacedBy(TvGutter)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (progress.durationMs > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TvGutter)) {
                Text(text = elapsed(progress.currentPositionMs), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES))
                ProgressLine(
                    progress = { progress.currentPositionMs.toFloat() / progress.durationMs },
                    modifier = Modifier.weight(1f)
                )
                Text(text = elapsed(progress.durationMs), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES))
            }
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(TvGutter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlButton(icon = R.drawable.ic_replay_10, label = R.string.rewind, onClick = player::seekBack)
                IconButton(
                    onClick = playPause::onClick,
                    modifier = Modifier
                        .size(IconButtonDefaults.LargeButtonSize)
                        .focusRequester(playFocus),
                    enabled = playPause.isEnabled
                ) {
                    Icon(
                        painter = painterResource(if (playPause.showPlay) R.drawable.ic_play else R.drawable.ic_pause),
                        contentDescription = stringResource(if (playPause.showPlay) R.string.play else R.string.pause),
                        modifier = Modifier.size(IconButtonDefaults.LargeIconSize)
                    )
                }
                ControlButton(icon = R.drawable.ic_forward_10, label = R.string.fast_forward, onClick = player::seekForward)
            }
            Row(modifier = Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(TvGutter)) {
                ControlButton(icon = R.drawable.ic_audiotrack, label = R.string.audio, onClick = onAudio, modifier = Modifier.focusRequester(audioFocus))
                ControlButton(
                    icon = R.drawable.ic_subtitles,
                    label = R.string.subtitles,
                    onClick = onSubtitles,
                    modifier = Modifier.focusRequester(subtitlesFocus)
                )
            }
        }
    }
}

@Composable
private fun ProgressLine(progress: () -> Float, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier.height(ProgressHeight)) {
        val y = size.height / 2
        drawLine(
            color = color.copy(alpha = PROGRESS_TRACK_ALPHA),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = size.height,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width * progress().coerceIn(0f, 1f), y),
            strokeWidth = size.height,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ControlButton(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(painter = painterResource(icon), contentDescription = stringResource(label))
    }
}

@Composable
private fun SubtitleSync(delayMs: Long, onShift: (later: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.extraLarge) {
        Row(
            modifier = Modifier.padding(TvGutter),
            horizontalArrangement = Arrangement.spacedBy(TvGutter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(icon = R.drawable.ic_remove, label = R.string.subtitle_sync_earlier, onClick = { onShift(false) })
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.subtitle_sync), style = MaterialTheme.typography.labelLarge)
                Text(text = stringResource(R.string.subtitle_sync_value, delayMs / 1000f), style = MaterialTheme.typography.titleMedium)
            }
            ControlButton(
                icon = R.drawable.ic_add,
                label = R.string.subtitle_sync_later,
                onClick = { onShift(true) },
                modifier = Modifier.focusRequester(focus)
            )
        }
    }
}

@Composable
private fun NextVideo(name: String, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    var remaining by remember { mutableIntStateOf(NEXT_COUNTDOWN_SECONDS) }
    val play by rememberUpdatedState(onPlay)
    LaunchedEffect(Unit) {
        focus.requestFocus()
        while (remaining > 0) {
            delay(1.seconds)
            remaining--
        }
        play()
    }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(TvGutter), verticalArrangement = Arrangement.spacedBy(TvLineSpacing)) {
            Text(text = stringResource(R.string.next_video), style = MaterialTheme.typography.labelLarge)
            Text(text = name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = pluralStringResource(R.plurals.next_video_in, remaining, remaining),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onPlay, modifier = Modifier.focusRequester(focus)) {
                Text(text = stringResource(R.string.play))
            }
        }
    }
}

@Composable
private fun FrameRateNotice(onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(TvGutter), verticalArrangement = Arrangement.spacedBy(TvLineSpacing)) {
            Text(text = stringResource(R.string.refresh_rate_changing), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onPlay, modifier = Modifier.focusRequester(focus)) {
                Text(text = stringResource(R.string.play))
            }
        }
    }
}

@Composable
private fun trackName(group: Tracks.Group): String {
    val resources = LocalResources.current
    val provider = remember(resources) { DefaultTrackNameProvider(resources) }
    val format = group.getTrackFormat(0)
    val name = provider.getTrackName(format)
    return format.label?.takeIf { it.isNotBlank() && it !in name }?.let { resources.getString(R.string.track_label, name, it) } ?: name
}

private fun elapsed(milliseconds: Long): String = DateUtils.formatElapsedTime(milliseconds.coerceAtLeast(0) / 1000)
