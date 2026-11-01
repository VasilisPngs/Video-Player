package com.vasilispngs.videoplayer

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.net.http.HttpEngine
import android.os.LocaleList
import androidx.compose.material3.ComposeMaterial3Flags
import androidx.compose.material3.ExperimentalMaterial3Api
import com.vasilispngs.videoplayer.data.AppDatabase
import com.vasilispngs.videoplayer.data.AppPreferences
import com.vasilispngs.videoplayer.data.Library
import com.vasilispngs.videoplayer.data.SelfUpdater
import com.vasilispngs.videoplayer.data.Thumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class VideoPlayerApplication : Application() {
    val container by lazy { AppContainer(this) }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate() {
        super.onCreate()
        ComposeMaterial3Flags.isSnackbarStylingFixEnabled = true
        getSystemService(LocaleManager::class.java).run {
            if (applicationLocales.isEmpty) applicationLocales = LocaleList.forLanguageTags(GREEK_LOCALE)
        }
    }
}

class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = AppDatabase.create(context)
    private val http by lazy { HttpEngine.Builder(context).build() }

    val preferences = AppPreferences(context)
    val playback = database.playback()
    val library = Library(context)
    val thumbnails = Thumbnails(context)
    val selfUpdater = SelfUpdater(context, scope) { http.openConnection(it) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as VideoPlayerApplication).container

private const val GREEK_LOCALE = "el"
