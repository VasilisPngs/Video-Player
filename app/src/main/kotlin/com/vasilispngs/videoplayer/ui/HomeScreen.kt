package com.vasilispngs.videoplayer.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClassicCard
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.vasilispngs.videoplayer.R
import com.vasilispngs.videoplayer.data.Playback
import com.vasilispngs.videoplayer.data.Thumbnails
import com.vasilispngs.videoplayer.data.Volume
import com.vasilispngs.videoplayer.data.fileName
import kotlin.math.ceil

private const val MILLIS_PER_MINUTE = 60_000.0

@Composable
fun HomeScreen(viewModel: HomeViewModel, thumbnails: Thumbnails, onOpen: (Screen) -> Unit, onSettings: () -> Unit) {
    val volumes by viewModel.volumes.collectAsStateWithLifecycle()
    val inProgress by viewModel.inProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var allFiles by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    val allFilesSettings = remember(context) { context.allFilesSettings() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allFiles = Environment.isExternalStorageManager() }

    HomeContent(
        volumes = volumes,
        inProgress = inProgress,
        allFiles = allFiles,
        onAllFiles = allFilesSettings?.let { intent -> { context.open(intent) } },
        thumbnails = thumbnails,
        onOpen = onOpen,
        onSettings = onSettings
    )
}

@Composable
internal fun HomeContent(
    volumes: List<Volume>?,
    inProgress: List<Playback>?,
    allFiles: Boolean,
    onAllFiles: (() -> Unit)?,
    thumbnails: Thumbnails,
    onOpen: (Screen) -> Unit,
    onSettings: () -> Unit
) {
    val loaded = volumes != null && inProgress != null
    val progress = inProgress.orEmpty()
    val usb = volumes.orEmpty()
    val sources = usb.size + if (allFiles) 0 else 1
    val empty = loaded && progress.isEmpty() && sources == 0
    val focus = rememberInitialFocus(loaded && !empty)

    ReportDrawnWhen { loaded }

    TvScreen(
        title = stringResource(R.string.home),
        action = {
            IconButton(onClick = onSettings) {
                Icon(painter = painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
            }
        }
    ) {
        if (empty) {
            Message(text = stringResource(R.string.usb_empty))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focus)
                    .focusRestorer(),
                contentPadding = TvContentPadding,
                verticalArrangement = Arrangement.spacedBy(TvLineSpacing)
            ) {
                if (!loaded) return@LazyColumn
                if (progress.isNotEmpty()) {
                    item(key = "continue", contentType = HEADER) { SectionTitle(text = stringResource(R.string.continue_watching)) }
                    item(key = "continue-row", contentType = CARDS) {
                        LazyRow(
                            modifier = Modifier.focusRestorer(),
                            contentPadding = PaddingValues(horizontal = TvOverscanHorizontal, vertical = TvGutter),
                            horizontalArrangement = Arrangement.spacedBy(TvGutter)
                        ) {
                            items(progress, key = { item -> "${item.uri}#${item.size}" }) { item ->
                                ContinueCard(item = item, thumbnails = thumbnails, onClick = { onOpen(Screen.Player(item.uri)) })
                            }
                        }
                    }
                }
                if (sources > 0) {
                    item(key = "sources", contentType = HEADER) { SectionTitle(text = stringResource(R.string.sources)) }
                    if (!allFiles) {
                        item(key = "all-files", contentType = ROW) { AllFilesItem(onClick = onAllFiles) }
                    }
                    items(usb, key = { volume -> volume.uri.toString() }, contentType = { ROW }) { volume ->
                        ListItem(
                            selected = false,
                            onClick = { onOpen(Screen.Folder(volume.uri.toString(), volume.name)) },
                            headlineContent = { Text(text = volume.name) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = TvListPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(item: Playback, thumbnails: Thumbnails, onClick: () -> Unit) {
    val video = remember(item.uri) { item.uri.toUri() }
    ClassicCard(
        onClick = onClick,
        image = { Thumbnail(video = video, icon = R.drawable.ic_movie, thumbnails = thumbnails, width = CardWidth) },
        title = { Text(text = video.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        subtitle = {
            if (item.durationMs > item.positionMs) {
                val minutes = ceil((item.durationMs - item.positionMs) / MILLIS_PER_MINUTE).toInt()
                Text(text = pluralStringResource(R.plurals.minutes_left, minutes, minutes))
            }
        },
        modifier = Modifier.width(CardWidth)
    )
}

@Composable
private fun AllFilesItem(onClick: (() -> Unit)?) {
    ListItem(
        selected = false,
        onClick = onClick ?: {},
        headlineContent = { Text(text = stringResource(R.string.all_files_access)) },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.all_files_access_description,
                    stringResource(R.string.all_files_access_command, LocalContext.current.packageName)
                )
            )
        },
        leadingContent = {
            Icon(painter = painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TvListPadding)
    )
}

private fun Context.allFilesSettings(): Intent? =
    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", packageName, null))
        .takeIf { it.resolveActivity(packageManager) != null }

private fun Context.open(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

internal const val HEADER = "header"
internal const val ROW = "row"
private const val CARDS = "cards"
