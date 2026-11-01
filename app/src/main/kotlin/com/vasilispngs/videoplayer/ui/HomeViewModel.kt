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
import com.vasilispngs.videoplayer.data.Computer
import com.vasilispngs.videoplayer.data.ErrorCode
import com.vasilispngs.videoplayer.data.Library
import com.vasilispngs.videoplayer.data.Playback
import com.vasilispngs.videoplayer.data.PlaybackDao
import com.vasilispngs.videoplayer.data.PlaybackSettings
import com.vasilispngs.videoplayer.data.SelfUpdate
import com.vasilispngs.videoplayer.data.SelfUpdater
import com.vasilispngs.videoplayer.data.Smb
import com.vasilispngs.videoplayer.data.SubtitleLanguage
import com.vasilispngs.videoplayer.data.SubtitlePosition
import com.vasilispngs.videoplayer.data.SubtitleSize
import com.vasilispngs.videoplayer.data.ThemeMode
import com.vasilispngs.videoplayer.data.Volume
import com.vasilispngs.videoplayer.data.errorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

sealed interface Connection {
    data object Idle : Connection
    data object Connecting : Connection
    data object Connected : Connection
    data class Failed(val code: ErrorCode) : Connection
}

class HomeViewModel(
    private val preferences: AppPreferences,
    private val smb: Smb,
    library: Library,
    playback: PlaybackDao,
    private val selfUpdater: SelfUpdater
) : ViewModel() {
    private val connection = MutableStateFlow<Connection>(Connection.Idle)

    val volumes: StateFlow<List<Volume>?> = library.volumes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val computers: StateFlow<List<Computer>?> = smb.saved.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val inProgress: StateFlow<List<Playback>?> = playback.inProgress().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val settings: StateFlow<PlaybackSettings> = preferences.playback.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PlaybackSettings())

    val themeMode: StateFlow<ThemeMode?> = preferences.themeMode
        .onStart { preferences.restoreThemeMode() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val adding: StateFlow<Connection> = connection.asStateFlow()

    val selfUpdate: StateFlow<SelfUpdate> = selfUpdater.status

    val updateConfirmation: StateFlow<Intent?> = selfUpdater.confirmation

    init {
        selfUpdater.check()
    }

    fun addComputer(host: String, share: String, user: String, password: String) {
        connection.value = Connection.Connecting
        viewModelScope.launch {
            connection.value = try {
                smb.add(host, share, user, password)
                Connection.Connected
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Connection.Failed(exception.errorCode())
            }
        }
    }

    fun connectionHandled() {
        connection.value = Connection.Idle
    }

    fun removeComputer(computer: Computer) {
        viewModelScope.launch { smb.remove(computer) }
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

    fun setSubtitleSize(value: SubtitleSize) {
        viewModelScope.launch { preferences.setSubtitleSize(value) }
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
                HomeViewModel(container.preferences, container.smb, container.library, container.playback, container.selfUpdater)
            }
        }
    }
}
