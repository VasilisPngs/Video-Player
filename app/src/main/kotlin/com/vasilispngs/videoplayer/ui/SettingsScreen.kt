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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.vasilispngs.videoplayer.R
import com.vasilispngs.videoplayer.data.AudioLanguage
import com.vasilispngs.videoplayer.data.PlaybackSettings
import com.vasilispngs.videoplayer.data.SelfUpdate
import com.vasilispngs.videoplayer.data.SubtitleLanguage
import com.vasilispngs.videoplayer.data.ThemeMode
import kotlin.math.roundToInt

private enum class Section { AppUpdate, AudioLanguage, SubtitleLanguage, CaptionPreferences, Theme }

private const val SIDEBAR_FRACTION = 0.4f

@Composable
fun SettingsScreen(viewModel: HomeViewModel, themeMode: ThemeMode) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val selfUpdate by viewModel.selfUpdate.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.updateApp() }

    SettingsContent(
        settings = settings,
        themeMode = themeMode,
        selfUpdate = selfUpdate,
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
    onUpdateApp: () -> Unit,
    onAudioLanguage: (AudioLanguage) -> Unit,
    onSubtitleLanguage: (SubtitleLanguage) -> Unit,
    onCaptionPreferences: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit
) {
    val sections = remember(selfUpdate == SelfUpdate.Idle) {
        Section.entries.filter { it != Section.AppUpdate || selfUpdate != SelfUpdate.Idle }
    }
    var chosen by rememberSaveable { mutableStateOf(Section.AudioLanguage) }
    val section = chosen.takeIf { it in sections } ?: sections.first()
    val focus = rememberInitialFocus()
    val sidebar = remember { FocusRequester() }
    var optionsFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = optionsFocused,
        onBackCompleted = { sidebar.requestFocus() }
    )

    TvScreen(title = stringResource(R.string.settings)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = TvListPadding),
            horizontalArrangement = Arrangement.spacedBy(TvGutter)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth(SIDEBAR_FRACTION)
                    .fillMaxHeight()
                    .focusRequester(focus)
                    .focusRequester(sidebar)
                    .focusRestorer(),
                contentPadding = TvContentPadding,
                verticalArrangement = Arrangement.spacedBy(TvLineSpacing)
            ) {
                items(sections, key = { it.name }) { item ->
                    ListItem(
                        selected = item == section,
                        onClick = {
                            when (item) {
                                Section.AppUpdate -> if (selfUpdate == SelfUpdate.Available || selfUpdate is SelfUpdate.Failed) onUpdateApp()
                                Section.CaptionPreferences -> onCaptionPreferences()
                                else -> focusManager.moveFocus(FocusDirection.Right)
                            }
                        },
                        headlineContent = { Text(text = stringResource(item.titleRes)) },
                        supportingContent = { Text(text = item.value(settings, themeMode, selfUpdate)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) chosen = item }
                    )
                }
            }
            val options = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onFocusChanged { optionsFocused = it.hasFocus }
            when (section) {
                Section.AudioLanguage -> OptionList(
                    options = AudioLanguage.entries,
                    current = settings.audioLanguage,
                    label = { stringResource(it.labelRes) },
                    onSelect = onAudioLanguage,
                    modifier = options
                )
                Section.SubtitleLanguage -> OptionList(
                    options = SubtitleLanguage.entries,
                    current = settings.subtitleLanguage,
                    label = { stringResource(it.labelRes) },
                    onSelect = onSubtitleLanguage,
                    modifier = options
                )
                Section.Theme -> OptionList(
                    options = ThemeMode.entries,
                    current = themeMode,
                    label = { stringResource(it.labelRes) },
                    onSelect = onThemeMode,
                    modifier = options
                )
                Section.AppUpdate, Section.CaptionPreferences -> Unit
            }
        }
    }
}

@Composable
internal fun <T> OptionList(
    options: List<T>,
    current: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    currentFocus: FocusRequester = remember { FocusRequester() },
    footer: @Composable (() -> Unit)? = null
) {
    val focused = options.indexOf(current).coerceAtLeast(0)
    Column(
        modifier = modifier
            .focusRestorer(currentFocus)
            .verticalScroll(rememberScrollState())
            .padding(TvContentPadding),
        verticalArrangement = Arrangement.spacedBy(TvLineSpacing)
    ) {
        options.forEachIndexed { index, option ->
            val chosen = option == current
            ListItem(
                selected = chosen,
                onClick = { onSelect(option) },
                headlineContent = { Text(text = label(option)) },
                trailingContent = if (chosen) {
                    { Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null) }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (index == focused) Modifier.focusRequester(currentFocus) else Modifier)
            )
        }
        footer?.invoke()
    }
}

@Composable
private fun Section.value(settings: PlaybackSettings, themeMode: ThemeMode, selfUpdate: SelfUpdate): String = when (this) {
    Section.AppUpdate -> when (selfUpdate) {
        is SelfUpdate.Downloading -> stringResource(R.string.self_update_downloading, (selfUpdate.progress * 100).roundToInt())
        SelfUpdate.Installing -> stringResource(R.string.self_update_installing)
        is SelfUpdate.Failed -> stringResource(R.string.self_update_failed).withCode(selfUpdate.code)
        else -> stringResource(R.string.self_update_install)
    }
    Section.AudioLanguage -> stringResource(settings.audioLanguage.labelRes)
    Section.SubtitleLanguage -> stringResource(settings.subtitleLanguage.labelRes)
    Section.CaptionPreferences -> stringResource(R.string.caption_size_and_style)
    Section.Theme -> stringResource(themeMode.labelRes)
}

@get:StringRes
private val Section.titleRes: Int
    get() = when (this) {
        Section.AppUpdate -> R.string.self_update_available
        Section.AudioLanguage -> R.string.audio_language
        Section.SubtitleLanguage -> R.string.subtitle_language
        Section.CaptionPreferences -> R.string.caption_preferences
        Section.Theme -> R.string.theme
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
