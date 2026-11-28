@file:OptIn(UnstableApi::class)

package com.vasilispngs.videoplayer.ui

import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vasilispngs.videoplayer.data.AppDatabase
import com.vasilispngs.videoplayer.data.AppPreferences
import com.vasilispngs.videoplayer.data.Library
import com.vasilispngs.videoplayer.data.Playback
import com.vasilispngs.videoplayer.data.PlaybackDao
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@kotlin.OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlayerStartTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `enables no audio or text track before the chosen ones`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val movie = folder.newFile("movie.mkv")
        val uri = Uri.fromFile(movie)
        val database = AppDatabase.create(context)
        try {
            database.playback().save(Playback(uri.toString(), movie.length(), audioTrack = "0:2"))
            val preferences = AppPreferences(
                context,
                PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "app.preferences_pb") }
            )
            val viewModel = PlayerViewModel(uri, Library(context), database.playback(), preferences, backgroundScope)
            val player = FakePlayer(
                Tracks(
                    listOf(
                        group("0:0", Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).setFrameRate(23.976f).build()),
                        group("0:1", Format.Builder().setSampleMimeType(MimeTypes.AUDIO_DTS_HD).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()),
                        group("0:2", Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3).build())
                    )
                )
            )

            viewModel.start(player) {}

            assertEquals(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT), player.disabledWhenPrepared)
            assertFalse(C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes)
            assertEquals("0:2", player.trackSelectionParameters.overrides.keys.single { it.type == C.TRACK_TYPE_AUDIO }.id)
        } finally {
            database.close()
        }
    }

    @Test
    fun `does not hang when preparation fails with no tracks`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val movie = folder.newFile("broken.mkv")
        val uri = Uri.fromFile(movie)
        val database = AppDatabase.create(context)
        try {
            val preferences = AppPreferences(
                context,
                PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "broken.preferences_pb") }
            )
            val viewModel = PlayerViewModel(uri, Library(context), database.playback(), preferences, backgroundScope)
            val player = FakePlayer(
                Tracks.EMPTY,
                error = PlaybackException("Corrupt", null, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)
            )

            viewModel.start(player) {}

            assertTrue(player.playWhenReady)
        } finally {
            database.close()
        }
    }

    @Test
    fun `clears progress of an ended stream with unknown duration even if player stops afterward`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val movie = folder.newFile("live.ts")
        val uri = Uri.fromFile(movie)
        var saved = Playback(uri.toString(), movie.length(), positionMs = 45_000)
        val dao = object : PlaybackDao {
            override fun inProgress(): Flow<List<Playback>> = emptyFlow()
            override suspend fun find(uri: String, size: Long): Playback = saved
            override suspend fun save(playback: Playback) {
                saved = playback
            }
            override suspend fun forget(uri: String) = Unit
        }
        val preferences = AppPreferences(
            context,
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "live.preferences_pb") }
        )
        val viewModel = PlayerViewModel(uri, Library(context), dao, preferences, backgroundScope)
        val player = FakePlayer(
            Tracks(listOf(group("0:0", Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build())))
        )

        viewModel.start(player) {}
        shadowOf(Looper.getMainLooper()).idle()
        player.finishAt(90_000)
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.stop(player)
        player.stop()
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.stop(player)
        runCurrent()

        assertEquals(0L, saved.positionMs)
    }

    private fun group(id: String, format: Format) =
        Tracks.Group(TrackGroup(id, format), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))

    private class FakePlayer(
        private val loaded: Tracks,
        private val error: PlaybackException? = null
    ) : SimpleBasePlayer(Looper.getMainLooper()) {
        var disabledWhenPrepared: Set<Int>? = null
            private set
        private var parameters = TrackSelectionParameters.DEFAULT
        private var item: MediaItem? = null
        private var prepared = false
        private var stateCode = Player.STATE_IDLE
        private var positionMs = 0L
        private var playing = false

        fun finishAt(position: Long) {
            positionMs = position
            stateCode = Player.STATE_ENDED
            invalidateState()
        }

        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(
                item?.let {
                    listOf(
                        MediaItemData.Builder("movie")
                            .setMediaItem(it)
                            .setTracks(if (stateCode != Player.STATE_IDLE) loaded else Tracks.EMPTY)
                            .build()
                    )
                }.orEmpty()
            )
            .setPlaybackState(stateCode)
            .setPlayerError(if (prepared) error else null)
            .setContentPositionMs(positionMs)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setTrackSelectionParameters(parameters)
            .build()

        override fun handleSetMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
            item = mediaItems.single()
            if (startPositionMs != C.TIME_UNSET) positionMs = startPositionMs
            return Futures.immediateVoidFuture()
        }

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            this.positionMs = positionMs
            return Futures.immediateVoidFuture()
        }

        override fun handlePrepare(): ListenableFuture<*> {
            disabledWhenPrepared = parameters.disabledTrackTypes
            prepared = true
            stateCode = if (error != null) Player.STATE_IDLE else Player.STATE_READY
            return Futures.immediateVoidFuture()
        }

        override fun handleStop(): ListenableFuture<*> {
            prepared = false
            stateCode = Player.STATE_IDLE
            return Futures.immediateVoidFuture()
        }

        override fun handleSetTrackSelectionParameters(trackSelectionParameters: TrackSelectionParameters): ListenableFuture<*> {
            parameters = trackSelectionParameters
            return Futures.immediateVoidFuture()
        }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            playing = playWhenReady
            return Futures.immediateVoidFuture()
        }
    }
}
