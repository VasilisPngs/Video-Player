package com.vasilispngs.videoplayer.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vasilispngs.videoplayer.R
import com.vasilispngs.videoplayer.data.AudioLanguage
import com.vasilispngs.videoplayer.data.PlaybackSettings
import com.vasilispngs.videoplayer.data.SelfUpdate
import com.vasilispngs.videoplayer.data.SubtitleLanguage
import com.vasilispngs.videoplayer.data.ThemeMode
import kotlin.math.roundToInt

private enum class Choice { AudioLanguage, SubtitleLanguage, Theme }

private const val PLAYBACK_ROWS = 3

@Composable
fun SettingsScreen(
    viewModel: HomeViewModel,
    themeMode: ThemeMode,
    listState: LazyListState,
    barState: TopAppBarState,
    focus: FocusRequester
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val selfUpdate by viewModel.selfUpdate.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.updateApp() }

    SettingsContent(
        settings = settings,
        themeMode = themeMode,
        selfUpdate = selfUpdate,
        listState = listState,
        barState = barState,
        focus = focus,
        onUpdateApp = {
            if (askForNotifications(context, activity)) {
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.updateApp()
            }
        },
        onAudioLanguage = viewModel::setAudioLanguage,
        onSubtitleLanguage = viewModel::setSubtitleLanguage,
        onCaptionPreferences = { val _ = runCatching { context.startActivity(Intent(Settings.ACTION_CAPTIONING_SETTINGS)) } },
        onThemeMode = viewModel::setThemeMode
    )
}

@Composable
internal fun SettingsContent(
    settings: PlaybackSettings,
    themeMode: ThemeMode,
    selfUpdate: SelfUpdate,
    listState: LazyListState,
    barState: TopAppBarState,
    focus: FocusRequester,
    onUpdateApp: () -> Unit,
    onAudioLanguage: (AudioLanguage) -> Unit,
    onSubtitleLanguage: (SubtitleLanguage) -> Unit,
    onCaptionPreferences: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit
) {
    var choice by rememberSaveable { mutableStateOf<Choice?>(null) }

    ListScreen(title = stringResource(R.string.settings), focus = focus, onBack = null, barState = barState) { contentPadding, nestedScroll ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScroll)
                .focusRequester(focus)
                .focusRestorer(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            if (selfUpdate != SelfUpdate.Idle) {
                item(key = "app-update", contentType = ROW) { AppUpdateItem(status = selfUpdate, onUpdate = onUpdateApp) }
            }

            item(key = "playback", contentType = HEADER) { SectionHeader(text = stringResource(R.string.playback)) }
            item(key = "audio-language", contentType = ROW) {
                ValueItem(R.string.audio_language, stringResource(settings.audioLanguage.labelRes), 0, PLAYBACK_ROWS) { choice = Choice.AudioLanguage }
            }
            item(key = "subtitle-language", contentType = ROW) {
                ValueItem(R.string.subtitle_language, stringResource(settings.subtitleLanguage.labelRes), 1, PLAYBACK_ROWS) { choice = Choice.SubtitleLanguage }
            }
            item(key = "caption-preferences", contentType = ROW) {
                ValueItem(R.string.caption_preferences, stringResource(R.string.caption_size_and_style), 2, PLAYBACK_ROWS, onCaptionPreferences)
            }

            item(key = "appearance", contentType = HEADER) { SectionHeader(text = stringResource(R.string.appearance)) }
            item(key = "theme", contentType = ROW) {
                ValueItem(R.string.theme, stringResource(themeMode.labelRes), 0, 1) { choice = Choice.Theme }
            }
        }
    }

    val close = { choice = null }
    when (choice) {
        Choice.AudioLanguage -> OptionDialog(
            title = stringResource(R.string.audio_language),
            options = AudioLanguage.entries,
            current = settings.audioLanguage,
            label = { stringResource(it.labelRes) },
            onDismiss = close,
            onSelect = {
                close()
                onAudioLanguage(it)
            }
        )
        Choice.SubtitleLanguage -> OptionDialog(
            title = stringResource(R.string.subtitle_language),
            options = SubtitleLanguage.entries,
            current = settings.subtitleLanguage,
            label = { stringResource(it.labelRes) },
            onDismiss = close,
            onSelect = {
                close()
                onSubtitleLanguage(it)
            }
        )
        Choice.Theme -> OptionDialog(
            title = stringResource(R.string.theme),
            options = ThemeMode.entries,
            current = themeMode,
            label = { stringResource(it.labelRes) },
            onDismiss = close,
            onSelect = {
                close()
                onThemeMode(it)
            }
        )
        null -> Unit
    }
}

@Composable
private fun ValueItem(@StringRes title: Int, value: String, index: Int, count: Int, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = listItemColors(),
        supportingContent = { Text(text = value) }
    ) {
        Text(text = stringResource(title))
    }
}

@Composable
private fun AppUpdateItem(status: SelfUpdate, onUpdate: () -> Unit) {
    val shapes = ListItemDefaults.segmentedShapes(0, 1)
    val headline = @Composable { Text(text = stringResource(R.string.self_update_available)) }
    val supporting = @Composable {
        when (status) {
            is SelfUpdate.Downloading -> Column {
                Text(
                    text = stringResource(R.string.self_update_downloading, (status.progress * 100).roundToInt()),
                    style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR_FIGURES)
                )
                LinearWavyProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
            }
            SelfUpdate.Installing -> Column {
                Text(text = stringResource(R.string.self_update_installing))
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is SelfUpdate.Failed -> Text(text = stringResource(R.string.self_update_failed).withCode(status.code))
            else -> Text(text = stringResource(R.string.self_update_install))
        }
    }
    if (status == SelfUpdate.Available || status is SelfUpdate.Failed) {
        SegmentedListItem(onClick = onUpdate, shapes = shapes, colors = listItemColors(), supportingContent = supporting, content = headline)
    } else {
        SegmentedListItem(shapes = shapes, colors = listItemColors(), supportingContent = supporting, content = headline)
    }
}

private fun askForNotifications(context: Context, activity: Activity?): Boolean =
    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
        activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == false

@get:StringRes
private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    }

@get:StringRes
private val AudioLanguage.labelRes: Int
    get() = when (this) {
        AudioLanguage.First -> R.string.audio_language_first
        AudioLanguage.Greek -> R.string.language_greek
        AudioLanguage.English -> R.string.language_english
    }

@get:StringRes
private val SubtitleLanguage.labelRes: Int
    get() = when (this) {
        SubtitleLanguage.Greek -> R.string.language_greek
        SubtitleLanguage.English -> R.string.language_english
        SubtitleLanguage.Off -> R.string.subtitles_off
    }
