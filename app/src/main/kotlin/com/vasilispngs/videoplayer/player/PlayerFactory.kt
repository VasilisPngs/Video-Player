@file:OptIn(UnstableApi::class)

package com.vasilispngs.videoplayer.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.extractor.text.CharsetDetector
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import com.sigpwned.chardet4j.Chardet
import java.nio.charset.Charset
import kotlin.jvm.optionals.getOrNull

internal const val SEEK_INCREMENT_MS = 10_000L

class SubtitleDelay {
    @Volatile
    var us: Long = 0
}

internal fun buildPlayer(context: Context, delay: SubtitleDelay): ExoPlayer {
    val mediaSources = DefaultMediaSourceFactory(context).setSubtitleParserFactory(DefaultSubtitleParserFactory(SubtitleCharsetDetector))
    return ExoPlayer.Builder(context, PlayerRenderersFactory(context, delay), mediaSources)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
        .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
        .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
        .build()
}

private class PlayerRenderersFactory(context: Context, private val delay: SubtitleDelay) : DefaultRenderersFactory(context) {
    init {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
        setEnableDecoderFallback(true)
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        super.buildVideoRenderers(
            context,
            EXTENSION_RENDERER_MODE_OFF,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out
        )
    }

    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>
    ) {
        out.add(DelayedTextRenderer(TextRenderer(output, outputLooper), delay))
    }
}

private class DelayedTextRenderer(renderer: Renderer, private val delay: SubtitleDelay) : ForwardingRenderer(renderer) {
    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        super.render(positionUs - delay.us, elapsedRealtimeUs)
    }
}

internal object SubtitleCharsetDetector : CharsetDetector {
    override fun detect(data: ByteArray, offset: Int, length: Int): Charset? =
        Chardet.detectCharsetName(data, offset, length, null).getOrNull()?.let { runCatching { Charset.forName(it) }.getOrNull() }
}
