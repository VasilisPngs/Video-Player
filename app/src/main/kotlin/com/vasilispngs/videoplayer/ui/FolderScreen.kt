package com.vasilispngs.videoplayer.ui

import android.util.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vasilispngs.videoplayer.R
import com.vasilispngs.videoplayer.data.Entry
import com.vasilispngs.videoplayer.data.ErrorCode
import com.vasilispngs.videoplayer.data.Kind
import com.vasilispngs.videoplayer.data.Thumbnails
import com.vasilispngs.videoplayer.player.subtitlesOf

@Composable
fun FolderScreen(title: String, viewModel: FolderViewModel, thumbnails: Thumbnails, onOpen: (Screen) -> Unit, onBack: () -> Unit) {
    val listing by viewModel.listing.collectAsStateWithLifecycle()
    val deleteFailure by viewModel.deleteFailure.collectAsStateWithLifecycle()
    FolderContent(
        title = title,
        listing = listing,
        deleteFailure = deleteFailure,
        thumbnails = thumbnails,
        onOpen = onOpen,
        onDelete = viewModel::delete,
        onDeleteFailureShown = viewModel::deleteFailureShown,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FolderContent(
    title: String,
    listing: Listing,
    deleteFailure: ErrorCode?,
    thumbnails: Thumbnails,
    onOpen: (Screen) -> Unit,
    onDelete: (Entry) -> Unit,
    onDeleteFailureShown: () -> Unit,
    onBack: () -> Unit
) {
    val entries = (listing as? Listing.Loaded)?.entries
    val focus = rememberInitialFocus(!entries.isNullOrEmpty())
    val listState = rememberLazyListState()
    val thumbnailSize = rememberThumbnailSize()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val motion = MaterialTheme.motionScheme
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    deleteFailure?.let { code ->
        val message = stringResource(R.string.delete_failed).withCode(code)
        LaunchedEffect(code, message) {
            snackbarHostState.showSnackbar(message)
            onDeleteFailureShown()
        }
    }

    entries?.firstOrNull { it.uri.toString() == deleting }?.let { target ->
        DeleteDialog(
            message = when {
                target.kind == Kind.Folder -> stringResource(R.string.delete_folder_message, target.name)
                entries.subtitlesOf(target.name).isNotEmpty() -> stringResource(R.string.delete_message_subtitles, target.name)
                else -> stringResource(R.string.delete_message, target.name)
            },
            onDismiss = { deleting = null },
            onDelete = {
                deleting = null
                val _ = focusManager.moveFocus(FocusDirection.Down) || focusManager.moveFocus(FocusDirection.Up)
                onDelete(target)
            }
        )
    }

    ListScreen(title = title, focus = focus, onBack = onBack, snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { contentPadding, nestedScroll ->
        when (val current = listing) {
            Listing.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            is Listing.Failed -> Message(text = stringResource(R.string.folder_failed).withCode(current.code), contentPadding = contentPadding)
            is Listing.Loaded if current.entries.isEmpty() -> Message(text = stringResource(R.string.folder_empty), contentPadding = contentPadding)
            is Listing.Loaded -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScroll)
                    .focusRequester(focus)
                    .focusRestorer(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                itemsIndexed(current.entries, key = { _, entry -> entry.uri.toString() }, contentType = { _, entry -> entry.kind }) { index, entry ->
                    EntryItem(
                        entry = entry,
                        index = index,
                        count = current.entries.size,
                        thumbnails = thumbnails,
                        thumbnailSize = thumbnailSize,
                        onClick = when (entry.kind) {
                            Kind.Folder -> {
                                { onOpen(Screen.Folder(entry.uri.toString(), entry.name)) }
                            }
                            Kind.Video -> {
                                { onOpen(Screen.Player(entry.uri.toString())) }
                            }
                            Kind.File -> null
                        },
                        onLongClick = if (entry.kind == Kind.File) {
                            null
                        } else {
                            { deleting = entry.uri.toString() }
                        },
                        modifier = Modifier.animateItem(
                            fadeInSpec = motion.defaultEffectsSpec(),
                            placementSpec = motion.defaultSpatialSpec(),
                            fadeOutSpec = motion.defaultEffectsSpec()
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryItem(
    entry: Entry,
    index: Int,
    count: Int,
    thumbnails: Thumbnails,
    thumbnailSize: Size,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    modifier: Modifier
) {
    val leading = @Composable {
        Thumbnail(
            video = entry.uri.takeIf { entry.kind == Kind.Video },
            icon = when (entry.kind) {
                Kind.Folder -> R.drawable.ic_folder
                Kind.Video -> R.drawable.ic_movie
                Kind.File -> R.drawable.ic_draft
            },
            thumbnails = thumbnails,
            size = thumbnailSize
        )
    }
    val name = @Composable { Text(text = entry.name) }
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = ListItemDefaults.segmentedShapes(index, count),
            modifier = modifier,
            colors = listItemColors(),
            leadingContent = leading,
            onLongClick = onLongClick,
            onLongClickLabel = onLongClick?.let { stringResource(R.string.delete) },
            content = name
        )
    } else {
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index, count),
            modifier = modifier,
            colors = listItemColors(),
            leadingContent = leading,
            content = name
        )
    }
}

@Composable
private fun DeleteDialog(message: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val focus = rememberInitialFocus()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDelete, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.delete))
            }
        },
        modifier = Modifier.onPreviewKeyEvent { it.nativeKeyEvent.repeatCount > 0 },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes(), modifier = Modifier.focusRequester(focus)) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        title = { Text(text = stringResource(R.string.delete_title)) },
        text = { Text(text = message) },
        properties = dialogProperties()
    )
}
