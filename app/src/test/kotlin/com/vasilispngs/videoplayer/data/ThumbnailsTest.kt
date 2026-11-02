package com.vasilispngs.videoplayer.data

import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThumbnailsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private val size = Size(228, 128)

    @After
    fun resetRetriever() {
        ShadowMediaMetadataRetriever.reset()
    }

    @Test
    fun `shows the frame from the middle of the video and keeps it for the next launch`() = runTest {
        val video = folder.newFile("Movie.mkv")
        addVideo(video)
        val thumbnails = Thumbnails(context)

        val frame = thumbnails.load(Uri.fromFile(video), size)
        ShadowMediaMetadataRetriever.reset()

        assertEquals(VIDEO_WIDTH, frame?.width)
        assertSame(frame, thumbnails.cached(Uri.fromFile(video), size))
        assertEquals(VIDEO_WIDTH, Thumbnails(context).load(Uri.fromFile(video), size)?.width)
    }

    @Test
    fun `remembers a video without a frame instead of reading it again`() = runTest {
        val video = folder.newFile("Broken.mkv")

        assertNull(Thumbnails(context).load(Uri.fromFile(video), size))
        addVideo(video)

        assertNull(Thumbnails(context).load(Uri.fromFile(video), size))
    }

    @Test
    fun `keeps nothing for a video that is gone`() = runTest {
        assertNull(Thumbnails(context).load(Uri.fromFile(File(folder.root, "Gone.mkv")), size))
        assertNull(File(context.cacheDir, "frames").list()?.takeIf { it.isNotEmpty() })
    }

    private fun addVideo(video: File) {
        val source = DataSource.toDataSource(video.path)
        ShadowMediaMetadataRetriever.addMetadata(source, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, "$VIDEO_WIDTH")
        ShadowMediaMetadataRetriever.addMetadata(source, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, "$VIDEO_HEIGHT")
        ShadowMediaMetadataRetriever.addMetadata(source, MediaMetadataRetriever.METADATA_KEY_DURATION, "$DURATION_MS")
        val frame = createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT).apply { eraseColor(Color.RED) }
        ShadowMediaMetadataRetriever.addFrame(source, MIDDLE_US, frame)
    }

    private companion object {
        const val VIDEO_WIDTH = 160
        const val VIDEO_HEIGHT = 90
        const val DURATION_MS = 9_960_000L
        const val MIDDLE_US = 4_980_000_000L
    }
}
