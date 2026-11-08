package com.vasilispngs.videoplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.vasilispngs.videoplayer.R
import com.vasilispngs.videoplayer.data.Entry
import com.vasilispngs.videoplayer.data.Kind
import com.vasilispngs.videoplayer.data.Thumbnails

@Composable
fun FolderScreen(title: String, viewModel: FolderViewModel, thumbnails: Thumbnails, onOpen: (Screen) -> Unit) {
    val listing by viewModel.listing.collectAsStateWithLifecycle()
    FolderContent(title = title, listing = listing, thumbnails = thumbnails, onOpen = onOpen)
}

@Composable
internal fun FolderContent(title: String, listing: Listing, thumbnails: Thumbnails, onOpen: (Screen) -> Unit) {
    val entries = (listing as? Listing.Loaded)?.entries
    val focus = rememberInitialFocus(!entries.isNullOrEmpty())

    TvScreen(title = title) {
        when (val current = listing) {
            Listing.Loading -> Message(text = stringResource(R.string.loading))
            is Listing.Failed -> Message(text = stringResource(R.string.folder_failed).withCode(current.code))
            is Listing.Loaded if current.entries.isEmpty() -> Message(text = stringResource(R.string.folder_empty))
            is Listing.Loaded -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focus)
                    .focusRestorer(),
                contentPadding = TvContentPadding,
                verticalArrangement = Arrangement.spacedBy(TvLineSpacing)
            ) {
                items(current.entries, key = { entry -> entry.uri.toString() }, contentType = { entry -> entry.kind }) { entry ->
                    EntryItem(
                        entry = entry,
                        thumbnails = thumbnails,
                        onClick = when (entry.kind) {
                            Kind.Folder -> {
                                { onOpen(Screen.Folder(entry.uri.toString(), entry.name)) }
                            }
                            Kind.Video -> {
                                { onOpen(Screen.Player(entry.uri.toString())) }
                            }
                            Kind.File -> {
                                {}
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryItem(entry: Entry, thumbnails: Thumbnails, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(text = entry.name) },
        leadingContent = {
            Thumbnail(
                video = entry.uri.takeIf { entry.kind == Kind.Video },
                icon = when (entry.kind) {
                    Kind.Folder -> R.drawable.ic_folder
                    Kind.Video -> R.drawable.ic_movie
                    Kind.File -> R.drawable.ic_draft
                },
                thumbnails = thumbnails,
                width = ListThumbnailHeight * CardDefaults.HorizontalImageAspectRatio,
                modifier = Modifier.clip(MaterialTheme.shapes.small)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TvListPadding)
    )
}
