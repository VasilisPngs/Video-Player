package com.vasilispngs.videoplayer.player

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Window
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

private const val EXACT_MISMATCH = 0.0002f
private const val NEAR_MISMATCH = 0.002f
private val SWITCH_TIMEOUT = 5.seconds

fun Display.modeFor(frameRate: Float): Display.Mode? {
    val current = mode
    val candidates = supportedModes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
    val refreshRate = refreshRateFor(current.refreshRate, candidates.map(Display.Mode::getRefreshRate), frameRate) ?: return null
    return candidates.first { it.refreshRate == refreshRate }
}

internal fun refreshRateFor(current: Float, available: List<Float>, frameRate: Float): Float? {
    if (frameRate <= 0f) return null
    for (limit in floatArrayOf(EXACT_MISMATCH, NEAR_MISMATCH)) {
        val matching = available.filter { mismatch(it, frameRate) < limit }
        if (current in matching) return null
        matching.minOrNull()?.let { return it }
    }
    return null
}

private fun mismatch(refreshRate: Float, frameRate: Float): Float {
    val ratio = refreshRate / frameRate
    val multiple = ratio.roundToInt()
    return if (multiple < 1) Float.MAX_VALUE else abs(ratio - multiple) / multiple
}

suspend fun Window.matchFrameRate(displayManager: DisplayManager, frameRate: Float) {
    val display = decorView.display ?: return
    val target = display.modeFor(frameRate) ?: return
    coroutineScope {
        val switched = async(start = CoroutineStart.UNDISPATCHED) {
            callbackFlow {
                val listener = object : DisplayManager.DisplayListener {
                    override fun onDisplayAdded(displayId: Int) = Unit

                    override fun onDisplayRemoved(displayId: Int) = Unit

                    override fun onDisplayChanged(displayId: Int) {
                        if (displayId == display.displayId) trySend(Unit)
                    }
                }
                displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
                awaitClose { displayManager.unregisterDisplayListener(listener) }
            }.first { display.mode.modeId == target.modeId }
        }
        attributes = attributes.apply { preferredDisplayModeId = target.modeId }
        withTimeoutOrNull(SWITCH_TIMEOUT) { switched.await() } ?: switched.cancel()
    }
}

fun Window.resetDisplayMode() {
    if (attributes.preferredDisplayModeId != 0) attributes = attributes.apply { preferredDisplayModeId = 0 }
}
