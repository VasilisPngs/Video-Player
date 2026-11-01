@file:OptIn(UnstableApi::class)

package com.vasilispngs.videoplayer.ui

import android.hardware.display.DisplayManager
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.DefaultTrackNameProvider
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.state.rememberErrorState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.vasilispngs.videoplayer.R
import com.vasilispngs.videoplayer.appContainer
import com.vasilispngs.videoplayer.data.ErrorCode
import com.vasilispngs.videoplayer.data.SubtitlePosition
import com.vasilispngs.videoplayer.data.SubtitleSize
import com.vasilispngs.videoplayer.data.fileName
import com.vasilispngs.videoplayer.player.audio
import com.vasilispngs.videoplayer.player.buildPlayer
import com.vasilispngs.videoplayer.player.matchFrameRate
import com.vasilispngs.videoplayer.player.text
import com.vasilispngs.videoplayer.ui.theme.VideoPlayerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val NEXT_COUNTDOWN_SECONDS = 10
private const val PROGRESS_TICK_MS = 1_000L

private enum class Menu { Audio, Subtitles }

@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onExit: () -> Unit, onNext: (Uri) -> Unit) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }
    val window = LocalActivity.current?.window
    val displayManager = remember(context) { context.getSystemService(DisplayManager::class.java) }
    val scope = rememberCoroutineScope()
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val failure by viewModel.failure.collectAsStateWithLifecycle()

    LifecycleStartEffect(viewModel) {
        val exo = buildPlayer(context, container.smb, viewModel.delay)
        val session = MediaSession.Builder(context, exo).setId(viewModel.uri.toString()).build()
        player = exo
        val start = scope.launch { viewModel.start(exo) { frameRate -> window?.matchFrameRate(displayManager, frameRate) } }
        onStopOrDispose {
            start.cancel()
            viewModel.stop(exo)
            session.release()
            exo.release()
            player = null
        }
    }

    VideoPlayerTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.scrim) {
            player?.let { PlayerContent(player = it, viewModel = viewModel, onExit = onExit, onNext = onNext) }
            failure?.let { CenteredMessage(text = stringResource(R.string.playback_failed).withCode(it)) }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerContent(player: ExoPlayer, viewModel: PlayerViewModel, onExit: () -> Unit, onNext: (Uri) -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
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
    val playing = !playPause.showPlay
    val hide = {
        root.requestFocus()
        controls = false
        syncing = false
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

    LaunchedEffect(Unit) { root.requestFocus() }

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
        isBackEnabled = controls || syncing,
        onBackCompleted = hide
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (playing) Modifier.keepScreenOn() else Modifier)
            .onFocusChanged { rootFocused = it.isFocused }
            .focusRequester(root)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                interactions++
                if (!rootFocused) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        Util.handlePlayPauseButtonAction(player)
                        controls = !player.playWhenReady
                    }
                    Key.DirectionLeft -> {
                        player.seekBack()
                        controls = true
                    }
                    Key.DirectionRight -> {
                        player.seekForward()
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
            subtitles = { Subtitles(player = player, size = settings.subtitleSize, position = settings.subtitlePosition) }
        )
        if (playPause.showBuffering) LoadingIndicator(modifier = Modifier.align(Alignment.Center))
        if (controls) {
            Controls(
                player = player,
                playing = playing,
                playFocus = playFocus,
                onAudio = { menu = Menu.Audio },
                onSubtitles = { menu = Menu.Subtitles }
            )
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
        error?.let { CenteredMessage(text = stringResource(R.string.playback_failed).withCode(ErrorCode.Named(it.errorCodeName))) }
    }

    val tracks = remember(menu) { player.currentTracks }
    when (menu) {
        Menu.Audio -> OptionDialog<Tracks.Group?>(
            title = stringResource(R.string.audio),
            options = tracks.audio,
            current = tracks.audio.firstOrNull(Tracks.Group::isSelected),
            label = { group -> group?.let { trackName(it) }.orEmpty() },
            onDismiss = { menu = null },
            onSelect = { group ->
                menu = null
                group?.let { viewModel.chooseAudio(player, it) }
            }
        )
        Menu.Subtitles -> {
            val current = tracks.text.firstOrNull(Tracks.Group::isSelected)
            OptionDialog<Tracks.Group?>(
                title = stringResource(R.string.subtitles),
                options = listOf(null) + tracks.text,
                current = current,
                label = { group -> group?.let { trackName(it) } ?: stringResource(R.string.subtitles_off) },
                onDismiss = { menu = null },
                onSelect = {
                    menu = null
                    viewModel.chooseText(player, it)
                },
                extraButton = {
                    TextButton(
                        onClick = {
                            menu = null
                            controls = false
                            syncing = true
                        },
                        enabled = current != null,
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(text = stringResource(R.string.subtitle_sync))
                    }
                }
            )
        }
        null -> Unit
    }
}

@Composable
private fun Subtitles(player: Player, size: SubtitleSize, position: SubtitlePosition) {
    val context = LocalContext.current
    val view = remember(context) { SubtitleView(context).apply { setUserDefaultStyle() } }
    DisposableEffect(player, view) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                view.setCues(cueGroup.cues)
            }
        }
        player.addListener(listener)
        view.setCues(player.currentCues.cues)
        onDispose { player.removeListener(listener) }
    }
    AndroidView(
        factory = { view },
        modifier = Modifier.fillMaxSize(),
        update = {
            it.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * size.scale)
            it.setBottomPaddingFraction(SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION * position.scale)
        }
    )
}

@Composable
private fun BoxScope.Controls(
    player: Player,
    playing: Boolean,
    playFocus: FocusRequester,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit
) {
    val progress = rememberProgressStateWithTickInterval(player, PROGRESS_TICK_MS)
    val playPause = rememberPlayPauseButtonState(player)
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
    )
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = TvOverscanHorizontal, vertical = TvOverscanVertical),
        verticalArrangement = Arrangement.spacedBy(ContentMargin)
    ) {
        if (progress.durationMs > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ContentMargin)) {
                Text(text = elapsed(progress.currentPositionMs), style = MaterialTheme.typography.labelLarge)
                LinearWavyProgressIndicator(
                    progress = { progress.currentPositionMs.toFloat() / progress.durationMs },
                    modifier = Modifier.weight(1f),
                    amplitude = { if (playing) 1f else 0f }
                )
                Text(text = elapsed(progress.durationMs), style = MaterialTheme.typography.labelLarge)
            }
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(ContentMargin),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlButton(icon = R.drawable.ic_replay_10, label = R.string.rewind, onClick = player::seekBack)
                FilledIconButton(
                    onClick = playPause::onClick,
                    shapes = IconButtonDefaults.shapes(IconButtonDefaults.largeRoundShape, IconButtonDefaults.largePressedShape),
                    modifier = Modifier
                        .size(IconButtonDefaults.largeContainerSize())
                        .focusRequester(playFocus),
                    enabled = playPause.isEnabled
                ) {
                    Icon(
                        painter = painterResource(if (playPause.showPlay) R.drawable.ic_play else R.drawable.ic_pause),
                        contentDescription = stringResource(if (playPause.showPlay) R.string.play else R.string.pause),
                        modifier = Modifier.size(IconButtonDefaults.largeIconSize)
                    )
                }
                ControlButton(icon = R.drawable.ic_forward_10, label = R.string.fast_forward, onClick = player::seekForward)
            }
            Row(modifier = Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(SmallSpacing)) {
                ControlButton(icon = R.drawable.ic_audiotrack, label = R.string.audio, onClick = onAudio)
                ControlButton(icon = R.drawable.ic_subtitles, label = R.string.subtitles, onClick = onSubtitles)
            }
        }
    }
}

@Composable
private fun ControlButton(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(IconButtonDefaults.mediumRoundShape, IconButtonDefaults.mediumPressedShape),
        modifier = modifier.size(IconButtonDefaults.mediumContainerSize())
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(label),
            modifier = Modifier.size(IconButtonDefaults.mediumIconSize)
        )
    }
}

@Composable
private fun SubtitleSync(delayMs: Long, onShift: (later: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    HorizontalFloatingToolbar(expanded = true, modifier = modifier) {
        ControlButton(icon = R.drawable.ic_remove, label = R.string.subtitle_sync_earlier, onClick = { onShift(false) })
        Column(
            modifier = Modifier.padding(horizontal = ContentMargin),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
    Surface(modifier = modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(ContentMargin), verticalArrangement = Arrangement.spacedBy(SmallSpacing)) {
            Text(text = stringResource(R.string.next_video), style = MaterialTheme.typography.labelLarge)
            Text(text = name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = pluralStringResource(R.plurals.next_video_in, remaining, remaining),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onPlay, shapes = ButtonDefaults.shapes(), modifier = Modifier.focusRequester(focus)) {
                Text(text = stringResource(R.string.play))
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
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

