package com.vasilispngs.videoplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vasilispngs.videoplayer.R
import com.vasilispngs.videoplayer.data.Entry
import com.vasilispngs.videoplayer.data.Kind

@Composable
fun FolderScreen(title: String, viewModel: FolderViewModel, onOpen: (Screen) -> Unit, onBack: () -> Unit) {
    val listing by viewModel.listing.collectAsStateWithLifecycle()
    FolderContent(title = title, listing = listing, onOpen = onOpen, onBack = onBack)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FolderContent(title: String, listing: Listing, onOpen: (Screen) -> Unit, onBack: () -> Unit) {
    val entries = (listing as? Listing.Loaded)?.entries
    val focus = rememberInitialFocus(!entries.isNullOrEmpty())
    val listState = rememberLazyListState()

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
private fun EntryItem(entry: Entry, index: Int, count: Int, onClick: (() -> Unit)?) {
    val icon = @Composable {
        Icon(
            painter = painterResource(
                when (entry.kind) {
                    Kind.Folder -> R.drawable.ic_folder
                    Kind.Video -> R.drawable.ic_movie
                    Kind.File -> R.drawable.ic_draft
                }
            ),
            contentDescription = null
        )
    }
    val name = @Composable { Text(text = entry.name) }
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = ListItemDefaults.segmentedShapes(index, count),
            colors = listItemColors(),
            leadingContent = icon,
            content = name
        )
    } else {
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index, count),
            colors = listItemColors(),
            leadingContent = icon,
            content = name
        )
    }
}

@Composable
private fun Message(text: String, contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}
