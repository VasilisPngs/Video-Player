package com.vasilispngs.videoplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.BitmapParams
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.roundToInt

private const val MEMORY_BYTES = 8 shl 20
private const val BYTES_PER_PIXEL = 4
private const val QUALITY = 90
private const val REPRESENTATIVE_FRAME = -1L

class Thumbnails(context: Context) {
    private val folder = File(context.cacheDir, "thumbnails")
    private val memory = object : LruCache<String, ImageBitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * BYTES_PER_PIXEL
    }
    private val frames = Dispatchers.IO.limitedParallelism(1)

    fun cached(video: Uri, size: Size): ImageBitmap? = memory[memoryKey(video, size)]

    suspend fun load(video: Uri, size: Size): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = video.path?.let(::File)?.takeIf(File::isFile) ?: return@withContext null
        val stored = File(folder, UUID.nameUUIDFromBytes("${file.path}|${file.length()}|${file.lastModified()}|$size".encodeToByteArray()).toString())
        val bitmap = if (stored.exists()) {
            read(stored)
        } else {
            withContext(frames) { if (stored.exists()) read(stored) else frame(file, size).also { store(it, stored, file) } }
        }
        bitmap?.asImageBitmap()?.also { memory.put(memoryKey(video, size), it) }
    }

    private fun read(stored: File): Bitmap? = if (stored.length() == 0L) {
        null
    } else {
        try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(stored))
        } catch (_: IOException) {
            null
        }
    }

    private fun store(bitmap: Bitmap?, stored: File, video: File) {
        if (bitmap == null && !video.isFile) return
        folder.mkdirs()
        val partial = File(folder, "${stored.name}.partial")
        partial.outputStream().use { bitmap?.compress(Bitmap.CompressFormat.WEBP_LOSSY, QUALITY, it) }
        partial.renameTo(stored)
    }

    private fun frame(video: File, size: Size): Bitmap? = try {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(video.path)
            val turned = retriever.number(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 180 != 0
            val width = retriever.number(if (turned) MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT else MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.number(if (turned) MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH else MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val scale = maxOf(size.width.toFloat() / width, size.height.toFloat() / height)
            when {
                width <= 0 || height <= 0 -> null
                scale >= 1f -> retriever.getFrameAtTime(REPRESENTATIVE_FRAME, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, BitmapParams())
                else -> retriever.getScaledFrameAtTime(
                    REPRESENTATIVE_FRAME,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    (width * scale).roundToInt(),
                    (height * scale).roundToInt(),
                    BitmapParams()
                )
            }
        }
    } catch (_: RuntimeException) {
        null
    }

    private fun MediaMetadataRetriever.number(key: Int): Int = extractMetadata(key)?.toIntOrNull() ?: 0

    private fun memoryKey(video: Uri, size: Size) = "$video|$size"
}
