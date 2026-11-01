@file:OptIn(UnstableApi::class)

package com.vasilispngs.videoplayer.player

import android.content.Context
import android.media.MediaCodecInfo.CodecProfileLevel
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.CodecSpecificDataUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.NalUnitUtil
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ForwardingExtractor
import androidx.media3.extractor.ForwardingExtractorOutput
import androidx.media3.extractor.ForwardingExtractorsFactory
import androidx.media3.extractor.ForwardingTrackOutput
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.CharsetDetector
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import com.sigpwned.chardet4j.Chardet
import com.vasilispngs.videoplayer.data.Smb
import java.nio.charset.Charset
import kotlin.jvm.optionals.getOrNull

internal const val SEEK_INCREMENT_MS = 10_000L

class SubtitleDelay {
    @Volatile
    var us: Long = 0
}

internal fun buildPlayer(context: Context, smb: Smb, delay: SubtitleDelay): ExoPlayer {
    val mediaSources = DefaultMediaSourceFactory(
        DefaultDataSource.Factory(context, SmbDataSource.Factory(smb)),
        BaseLayerExtractorsFactory(DefaultExtractorsFactory())
    ).setSubtitleParserFactory(DefaultSubtitleParserFactory(SubtitleCharsetDetector))
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

private val dolbyVisionProfile7 by lazy {
    MediaCodecUtil.getDecoderInfos(MimeTypes.VIDEO_DOLBY_VISION, false, false).any { decoder ->
        decoder.profileLevels.any { it.profile == CodecProfileLevel.DolbyVisionProfileDvheDtb }
    }
}

private class BaseLayerExtractorsFactory(factory: ExtractorsFactory) : ForwardingExtractorsFactory(factory) {
    override fun createExtractors(): Array<Extractor> = super.createExtractors().map(::baseLayer).toTypedArray()

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        super.createExtractors(uri, responseHeaders).map(::baseLayer).toTypedArray()

    private fun baseLayer(extractor: Extractor): Extractor = if (dolbyVisionProfile7) extractor else BaseLayerExtractor(extractor)
}

private class BaseLayerExtractor(extractor: Extractor) : ForwardingExtractor(extractor) {
    override fun init(output: ExtractorOutput) {
        super.init(object : ForwardingExtractorOutput(output) {
            override fun track(id: Int, type: Int): TrackOutput = object : ForwardingTrackOutput(super.track(id, type)) {
                override fun format(format: Format) {
                    super.format(format.baseLayer())
                }
            }
        })
    }
}

private fun Format.baseLayer(): Format {
    if (sampleMimeType != MimeTypes.VIDEO_DOLBY_VISION) return this
    if (CodecSpecificDataUtil.getCodecProfileAndLevel(this)?.first != CodecProfileLevel.DolbyVisionProfileDvheDtb) return this
    return buildUpon()
        .setSampleMimeType(MimeTypes.VIDEO_H265)
        .setCodecs(NalUnitUtil.getH265BaseLayerCodecsString(initializationData))
        .build()
}

internal object SubtitleCharsetDetector : CharsetDetector {
    override fun detect(data: ByteArray, offset: Int, length: Int): Charset? =
        Chardet.detectCharsetName(data, offset, length, null).getOrNull()?.let { runCatching { Charset.forName(it) }.getOrNull() }
}
