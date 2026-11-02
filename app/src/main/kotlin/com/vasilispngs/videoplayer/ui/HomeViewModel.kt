package com.vasilispngs.videoplayer.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vasilispngs.videoplayer.VideoPlayerApplication
import com.vasilispngs.videoplayer.data.AppPreferences
import com.vasilispngs.videoplayer.data.AudioLanguage
import com.vasilispngs.videoplayer.data.Library
import com.vasilispngs.videoplayer.data.Playback
import com.vasilispngs.videoplayer.data.PlaybackDao
import com.vasilispngs.videoplayer.data.PlaybackSettings
import com.vasilispngs.videoplayer.data.SelfUpdate
import com.vasilispngs.videoplayer.data.SelfUpdater
import com.vasilispngs.videoplayer.data.SubtitleLanguage
import com.vasilispngs.videoplayer.data.SubtitlePosition
import com.vasilispngs.videoplayer.data.ThemeMode
import com.vasilispngs.videoplayer.data.Volume
import com.vasilispngs.videoplayer.data.onMounted
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

class HomeViewModel(
    private val preferences: AppPreferences,
    library: Library,
    playback: PlaybackDao,
    private val selfUpdater: SelfUpdater
) : ViewModel() {
    val volumes: StateFlow<List<Volume>?> = library.volumes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val inProgress: StateFlow<List<Playback>?> = combine(playback.inProgress(), volumes.filterNotNull()) { rows, mounted -> rows.onMounted(mounted) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val settings: StateFlow<PlaybackSettings> = preferences.playback.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PlaybackSettings())

    val themeMode: StateFlow<ThemeMode?> = preferences.themeMode
        .onStart { preferences.restoreThemeMode() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val selfUpdate: StateFlow<SelfUpdate> = selfUpdater.status

    val updateConfirmation: StateFlow<Intent?> = selfUpdater.confirmation

    init {
        selfUpdater.check()
    }

    fun setThemeMode(mode: ThemeMode) {
        if (themeMode.value == mode) return
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setAudioLanguage(value: AudioLanguage) {
        viewModelScope.launch { preferences.setAudioLanguage(value) }
    }

    fun setSubtitleLanguage(value: SubtitleLanguage) {
        viewModelScope.launch { preferences.setSubtitleLanguage(value) }
    }

    fun setSubtitlePosition(value: SubtitlePosition) {
        viewModelScope.launch { preferences.setSubtitlePosition(value) }
    }

    fun updateApp() = selfUpdater.update()

    fun updateConfirmationShown(intent: Intent) = selfUpdater.confirmationShown(intent)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as VideoPlayerApplication).container
                HomeViewModel(container.preferences, container.library, container.playback, container.selfUpdater)
            }
        }
    }
}
