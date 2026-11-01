package com.vasilispngs.videoplayer

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vasilispngs.videoplayer.ui.HomeViewModel
import com.vasilispngs.videoplayer.ui.VideoPlayerApp
import com.vasilispngs.videoplayer.ui.theme.VideoPlayerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels { HomeViewModel.Factory }
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            themeMode?.let { current ->
                VideoPlayerTheme {
                    VideoPlayerApp(viewModel = viewModel, themeMode = current)
                }
                SideEffect { ready = true }
            }
        }
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                return ready
            }
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val light = resources.getBoolean(R.bool.light_system_bars)
        WindowCompat.getInsetsController(window, window.decorView).run {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
        window.setBackgroundDrawableResource(R.color.window_background)
    }
}
