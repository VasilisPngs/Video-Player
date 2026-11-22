package com.vasilispngs.videoplayer.ui

import android.net.Uri
import android.os.Looper
import com.vasilispngs.videoplayer.data.Entry
import com.vasilispngs.videoplayer.data.ErrorCode
import com.vasilispngs.videoplayer.data.Kind
import com.vasilispngs.videoplayer.data.Library
import com.vasilispngs.videoplayer.data.Playback
import com.vasilispngs.videoplayer.data.PlaybackDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
class FolderViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val forgotten = CopyOnWriteArrayList<String>()
    private val playback = object : PlaybackDao {
        override fun inProgress(): Flow<List<Playback>> = emptyFlow()

        override suspend fun find(uri: String, size: Long): Playback? = null

        override suspend fun save(playback: Playback) = Unit

        override suspend fun forget(uri: String) {
            forgotten += uri
        }
    }

    @After
    fun cancelAppScope() {
        appScope.cancel()
    }

    @Test
    fun `deletes a video with its subtitles and forgets where it stopped`() {
        listOf("Episode 1.mkv", "Episode 1.el.srt", "Episode 1.en.srt", "Episode 2.mkv", "Episode 2.el.srt").forEach(folder::newFile)
        val viewModel = viewModel()
        val episode = await { viewModel.entries()?.firstOrNull { it.name == "Episode 1.mkv" } }

        viewModel.delete(episode)

        val left = await { viewModel.entries()?.takeIf { it.size == 2 } }
        assertEquals(listOf("Episode 2.el.srt", "Episode 2.mkv"), left.map(Entry::name))
        assertEquals(listOf("Episode 2.el.srt", "Episode 2.mkv"), folder.root.list()?.sorted())
        assertEquals(listOf(episode.uri.toString()), forgotten)
        assertNull(viewModel.deleteFailure.value)
    }

    @Test
    fun `keeps a video it could not delete and says why`() {
        val stuck = folder.newFolder("Movie.mkv")
        File(stuck, "Movie.nfo").createNewFile()
        val viewModel = viewModel()
        await { viewModel.entries() }

        viewModel.delete(Entry(stuck.name, Uri.fromFile(stuck), Kind.Video, 0))

        val failure = await { viewModel.deleteFailure.value }
        assertEquals(ErrorCode.Named("DirectoryNotEmptyException"), failure)
        assertTrue(stuck.exists())
        assertTrue(forgotten.isEmpty())
        viewModel.deleteFailureShown()
        assertNull(viewModel.deleteFailure.value)
    }

    private fun viewModel() = FolderViewModel(Uri.fromFile(folder.root), Library(RuntimeEnvironment.getApplication()), playback, appScope)

    private fun FolderViewModel.entries() = (listing.value as? Listing.Loaded)?.entries

    private fun <T : Any> await(value: () -> T?): T {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            value()?.let { return it }
            Thread.sleep(POLL_MS)
        }
        return checkNotNull(value())
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 10L
    }
}
