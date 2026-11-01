package com.vasilispngs.videoplayer.ui

import android.net.Uri
import android.util.Size
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vasilispngs.videoplayer.data.Thumbnails

private val ThumbnailWidth = 114.dp
private val ThumbnailHeight = 64.dp

@Composable
internal fun rememberThumbnailSize(): Size {
    val density = LocalDensity.current
    return remember(density) { with(density) { Size(ThumbnailWidth.roundToPx(), ThumbnailHeight.roundToPx()) } }
}

@Composable
internal fun Thumbnail(video: Uri?, @DrawableRes icon: Int, thumbnails: Thumbnails, size: Size) {
    val image by produceState(video?.let { thumbnails.cached(it, size) }, video, size) {
        value = video?.let { thumbnails.cached(it, size) ?: thumbnails.load(it, size) }
    }
    Box(
        modifier = Modifier
            .size(ThumbnailWidth, ThumbnailHeight)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        val frame = image
        if (frame != null) {
            Image(bitmap = frame, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(painter = painterResource(icon), contentDescription = null)
        }
    }
}
