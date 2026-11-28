package com.vasilispngs.videoplayer.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasilispngs.videoplayer.data.Entry
import com.vasilispngs.videoplayer.data.ErrorCode
import com.vasilispngs.videoplayer.data.Kind
import com.vasilispngs.videoplayer.data.Library
import com.vasilispngs.videoplayer.data.PlaybackDao
import com.vasilispngs.videoplayer.data.errorCode
import com.vasilispngs.videoplayer.player.subtitlesOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface Listing {
    data object Loading : Listing
    data class Loaded(val entries: List<Entry>) : Listing
    data class Failed(val code: ErrorCode) : Listing
}

class FolderViewModel(
    private val folder: Uri,
    private val library: Library,
    private val playback: PlaybackDao,
    private val appScope: CoroutineScope
) : ViewModel() {
    private val state = MutableStateFlow<Listing>(Listing.Loading)
    private val failure = MutableStateFlow<ErrorCode?>(null)

    val listing: StateFlow<Listing> = state.asStateFlow()
    val deleteFailure: StateFlow<ErrorCode?> = failure.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun delete(entry: Entry) {
        val siblings = (state.value as? Listing.Loaded)?.entries.orEmpty()
        val subtitles = if (entry.kind == Kind.Video) siblings.subtitlesOf(entry.name) else emptyList()
        viewModelScope.launch {
            appScope.async { remove(entry, subtitles) }.await()?.let { failure.value = it }
            load()
        }
    }

    fun deleteFailureShown() {
        failure.value = null
    }

    private suspend fun remove(target: Entry, subtitles: List<Entry>): ErrorCode? = try {
        library.delete(target.uri) { deleted -> playback.forget(deleted.toString()) }
        playback.forget(target.uri.toString())
        subtitles.forEach { library.delete(it.uri) }
        null
    } catch (exception: IOException) {
        exception.errorCode()
    }

    private suspend fun load() {
        state.value = try {
            Listing.Loaded(library.list(folder))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Listing.Failed(exception.errorCode())
        }
    }
}
