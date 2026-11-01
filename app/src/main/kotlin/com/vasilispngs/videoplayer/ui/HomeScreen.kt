package com.vasilispngs.videoplayer.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val warning = if (allFiles) 0 else 1
    val sources = warning + usb.size
    val empty = loaded && progress.isEmpty() && sources == 0
    val focus = rememberInitialFocus(loaded && !empty)
    val listState = rememberLazyListState()
    val thumbnailSize = rememberThumbnailSize()

    ReportDrawnWhen { loaded }

    ListScreen(
        title = stringResource(R.string.home),
        focus = focus,
        onBack = null,
        action = { modifier -> BarAction(icon = R.drawable.ic_settings, label = stringResource(R.string.settings), onClick = onSettings, modifier = modifier) }
    ) { contentPadding, nestedScroll ->
        if (empty) {
            Message(text = stringResource(R.string.usb_empty), contentPadding = contentPadding)
        } else {
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
                if (!loaded) return@LazyColumn
                if (progress.isNotEmpty()) {
                    item(key = "continue", contentType = HEADER) { SectionHeader(text = stringResource(R.string.continue_watching)) }
                    itemsIndexed(progress, key = { _, item -> "${item.uri}#${item.size}" }, contentType = { _, _ -> ROW }) { index, item ->
                        ContinueItem(
                            item = item,
                            index = index,
                            count = progress.size,
                            thumbnails = thumbnails,
                            thumbnailSize = thumbnailSize,
                            onClick = { onOpen(Screen.Player(item.uri)) }
                        )
                    }
                }
                if (sources > 0) {
                    item(key = "sources", contentType = HEADER) { SectionHeader(text = stringResource(R.string.sources)) }
                    if (!allFiles) {
                        item(key = "all-files", contentType = ROW) { AllFilesItem(count = sources, onClick = onAllFiles) }
                    }
                    itemsIndexed(usb, key = { _, volume -> volume.uri.toString() }, contentType = { _, _ -> ROW }) { index, volume ->
                        VolumeItem(volume = volume, index = warning + index, count = sources, onClick = { onOpen(Screen.Folder(volume.uri.toString(), volume.name)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueItem(item: Playback, index: Int, count: Int, thumbnails: Thumbnails, thumbnailSize: Size, onClick: () -> Unit) {
    val video = remember(item.uri) { item.uri.toUri() }
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = listItemColors(),
        leadingContent = { Thumbnail(video = video, icon = R.drawable.ic_movie, thumbnails = thumbnails, size = thumbnailSize) },
        supportingContent = if (item.durationMs > item.positionMs) {
            {
                val minutes = ceil((item.durationMs - item.positionMs) / MILLIS_PER_MINUTE).toInt()
                Text(text = pluralStringResource(R.plurals.minutes_left, minutes, minutes))
            }
        } else {
            null
        }
    ) {
        Text(text = video.fileName)
    }
}

@Composable
private fun AllFilesItem(count: Int, onClick: (() -> Unit)?) {
    val leading = @Composable {
        Icon(painter = painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error)
    }
    val supporting = @Composable {
        Text(
            text = stringResource(
                R.string.all_files_access_description,
                stringResource(R.string.all_files_access_command, LocalContext.current.packageName)
            )
        )
    }
    val title = @Composable { Text(text = stringResource(R.string.all_files_access)) }
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = ListItemDefaults.segmentedShapes(0, count),
            colors = listItemColors(),
            leadingContent = leading,
            supportingContent = supporting,
            content = title
        )
    } else {
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(0, count),
            colors = listItemColors(),
            leadingContent = leading,
            supportingContent = supporting,
            content = title
        )
    }
}

@Composable
private fun VolumeItem(volume: Volume, index: Int, count: Int, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = listItemColors(),
        leadingContent = { Icon(painter = painterResource(R.drawable.ic_usb), contentDescription = null) }
    ) {
        Text(text = volume.name)
    }
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
