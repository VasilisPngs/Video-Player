package com.vasilispngs.videoplayer.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.vasilispngs.videoplayer.data.AppPreferences
import com.vasilispngs.videoplayer.data.ErrorCode
import com.vasilispngs.videoplayer.data.Kind
import com.vasilispngs.videoplayer.data.Library
import com.vasilispngs.videoplayer.data.Playback
import com.vasilispngs.videoplayer.data.PlaybackDao
import com.vasilispngs.videoplayer.data.errorCode
import com.vasilispngs.videoplayer.data.fileName
import com.vasilispngs.videoplayer.data.parent
import com.vasilispngs.videoplayer.player.SubtitleDelay
import com.vasilispngs.videoplayer.player.chooseTracks
import com.vasilispngs.videoplayer.player.externalSubtitles
import com.vasilispngs.videoplayer.player.id
import com.vasilispngs.videoplayer.player.select
import com.vasilispngs.videoplayer.player.selectAudio
import com.vasilispngs.videoplayer.player.selectText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val FINISHED_FRACTION = 0.95
private const val SUBTITLE_DELAY_STEP_MS = 100L

class PlayerViewModel(
    val uri: Uri,
    private val library: Library,
    private val playback: PlaybackDao,
    private val preferences: AppPreferences,
    private val appScope: CoroutineScope
) : ViewModel() {
    private val delayMs = MutableStateFlow(0L)
    private val failed = MutableStateFlow<ErrorCode?>(null)
    private val source = viewModelScope.async { load() }
    private var memory: Playback? = null
    private var prepared = false
    private var autoplay = true

    val delay = SubtitleDelay()

    val subtitleDelayMs: StateFlow<Long> = delayMs.asStateFlow()

    val failure: StateFlow<ErrorCode?> = failed.asStateFlow()

    var next: Uri? = null
        private set

    suspend fun start(player: Player, matchFrameRate: suspend (Float) -> Unit) {
        val item = try {
            source.await()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            failed.value = exception.errorCode()
            return
        }
        val saved = checkNotNull(memory)
        player.setMediaItem(item, saved.positionMs)
        prepared = true
        player.prepare()
        val tracks = player.tracks()
        player.select(chooseTracks(tracks, preferences.playback.first(), saved))
        tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }?.let { matchFrameRate(it.getTrackFormat(0).frameRate) }
        player.playWhenReady = autoplay
    }

    fun stop(player: Player) {
        autoplay = false
        val saved = memory ?: return
        if (!prepared) return
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: saved.durationMs
        val position = player.currentPosition
        val finished = player.playbackState == Player.STATE_ENDED || (duration > 0 && position >= duration * FINISHED_FRACTION)
        val update = saved.copy(
            positionMs = if (finished) 0 else position,
            durationMs = duration,
            subtitleDelayMs = delayMs.value,
            updatedAt = System.currentTimeMillis()
        )
        memory = update
        appScope.launch { playback.save(update) }
    }

    fun chooseAudio(player: Player, group: Tracks.Group) {
        player.selectAudio(group)
        memory = memory?.copy(audioTrack = group.id)
    }

    fun chooseText(player: Player, group: Tracks.Group?) {
        player.selectText(group)
        memory = memory?.copy(textChosen = true, textTrack = group?.id)
    }

    fun shiftSubtitles(later: Boolean) {
        delayMs.value += if (later) SUBTITLE_DELAY_STEP_MS else -SUBTITLE_DELAY_STEP_MS
        delay.us = delayMs.value * 1000
    }

    private suspend fun load(): MediaItem {
        val siblings = library.list(uri.parent)
        val size = siblings.firstOrNull { it.uri == uri }?.size ?: library.size(uri)
        val videos = siblings.filter { it.kind == Kind.Video }
        val index = videos.indexOfFirst { it.uri == uri }
        next = if (index >= 0) videos.getOrNull(index + 1)?.uri else null
        val saved = playback.find(uri.toString(), size) ?: Playback(uri.toString(), size)
        memory = saved
        delayMs.value = saved.subtitleDelayMs
        delay.us = saved.subtitleDelayMs * 1000
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(uri.fileName).build())
            .setSubtitleConfigurations(library.externalSubtitles(uri.fileName, siblings))
            .build()
    }
}

private suspend fun Player.tracks(): Tracks = callbackFlow {
    val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            trySend(tracks)
        }
    }
    addListener(listener)
    trySend(currentTracks)
    awaitClose { removeListener(listener) }
}.first { !it.isEmpty }
