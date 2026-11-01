package com.vasilispngs.videoplayer.ui

import android.util.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vasilispngs.videoplayer.R
import com.vasilispngs.videoplayer.data.Entry
import com.vasilispngs.videoplayer.data.Kind
import com.vasilispngs.videoplayer.data.Thumbnails

@Composable
fun FolderScreen(title: String, viewModel: FolderViewModel, thumbnails: Thumbnails, onOpen: (Screen) -> Unit, onBack: () -> Unit) {
    val listing by viewModel.listing.collectAsStateWithLifecycle()
    FolderContent(title = title, listing = listing, thumbnails = thumbnails, onOpen = onOpen, onBack = onBack)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FolderContent(title: String, listing: Listing, thumbnails: Thumbnails, onOpen: (Screen) -> Unit, onBack: () -> Unit) {
    val entries = (listing as? Listing.Loaded)?.entries
    val focus = rememberInitialFocus(!entries.isNullOrEmpty())
    val listState = rememberLazyListState()
    val thumbnailSize = rememberThumbnailSize()

    ListScreen(title = title, focus = focus, onBack = onBack) { contentPadding, nestedScroll ->
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
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryItem(entry: Entry, index: Int, count: Int, thumbnails: Thumbnails, thumbnailSize: Size, onClick: (() -> Unit)?) {
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
            colors = listItemColors(),
            leadingContent = leading,
            content = name
        )
    } else {
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index, count),
            colors = listItemColors(),
            leadingContent = leading,
            content = name
        )
    }
}
