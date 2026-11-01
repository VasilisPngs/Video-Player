package com.vasilispngs.videoplayer.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasilispngs.videoplayer.data.Entry
import com.vasilispngs.videoplayer.data.ErrorCode
import com.vasilispngs.videoplayer.data.Library
import com.vasilispngs.videoplayer.data.errorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface Listing {
    data object Loading : Listing
    data class Loaded(val entries: List<Entry>) : Listing
    data class Failed(val code: ErrorCode) : Listing
}

class FolderViewModel(private val folder: Uri, private val library: Library) : ViewModel() {
    private val state = MutableStateFlow<Listing>(Listing.Loading)

    val listing: StateFlow<Listing> = state.asStateFlow()

    init {
        viewModelScope.launch {
            state.value = try {
                Listing.Loaded(library.list(folder))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Listing.Failed(exception.errorCode())
            }
        }
    }
}
