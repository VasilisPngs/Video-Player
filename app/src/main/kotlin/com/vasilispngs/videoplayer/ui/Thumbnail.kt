package com.vasilispngs.videoplayer.ui

import android.net.Uri
import android.util.Size
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.vasilispngs.videoplayer.data.Thumbnails

internal val ListThumbnailHeight = 64.dp
internal val CardWidth = 196.dp

@Composable
internal fun Thumbnail(
    video: Uri?,
    @DrawableRes icon: Int,
    thumbnails: Thumbnails,
    width: Dp,
    modifier: Modifier = Modifier
) {
    val height = width / CardDefaults.HorizontalImageAspectRatio
    val density = LocalDensity.current
    val size = remember(density, width) { with(density) { Size(width.roundToPx(), height.roundToPx()) } }
    val image by produceState(video?.let { thumbnails.cached(it, size) }, video, size) {
        value = video?.let { thumbnails.cached(it, size) ?: thumbnails.load(it, size) }
    }
    Box(
        modifier = modifier
            .size(width, height)
            .background(MaterialTheme.colorScheme.surfaceVariant),
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
