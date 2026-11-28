@file:OptIn(UnstableApi::class)

package com.vasilispngs.videoplayer.player

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererConfiguration
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.text.CharsetDetector
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import com.sigpwned.chardet4j.Chardet
import java.nio.charset.Charset
import kotlin.jvm.optionals.getOrNull

internal const val SEEK_INCREMENT_MS = 10_000L
private const val TS_TIMESTAMP_SEARCH_PACKETS = 1500
private const val REWIND_WINDOW_US = 300_000_000L

class SubtitleDelay {
    @Volatile
    var us: Long = 0
        set(value) {
            field = value
            onChanged?.invoke()
        }

    internal var onChanged: (() -> Unit)? = null
}

internal fun buildPlayer(context: Context, delay: SubtitleDelay): ExoPlayer {
    val extractors = DefaultExtractorsFactory()
        .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
        .setTsExtractorTimestampSearchBytes(TS_TIMESTAMP_SEARCH_PACKETS * TsExtractor.TS_PACKET_SIZE)
    val mediaSources = DefaultMediaSourceFactory(context, extractors).setSubtitleParserFactory(DefaultSubtitleParserFactory(SubtitleCharsetDetector))
    val tracks = DefaultTrackSelector(
        context,
        DefaultTrackSelector.Parameters.Builder().setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true).build()
    )
    val renderers = PlayerRenderersFactory(context, delay)
    val player = ExoPlayer.Builder(context, renderers, mediaSources)
        .setTrackSelector(tracks)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true
        )
        .setHandleAudioBecomingNoisy(!context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))
        .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
        .build()
    delay.onChanged = {
        val looper = player.applicationLooper
        val notify = {
            if (!player.isReleased && player.playbackState != Player.STATE_IDLE) {
                renderers.textRenderer?.let { val _ = player.createMessage(it).setType(Renderer.MSG_CUSTOM_BASE).send() }
            }
        }
        if (Thread.currentThread() === looper.thread) notify() else Handler(looper).post(notify)
    }
    return player
}

internal fun ExoPlayer.skip(forward: Boolean) {
    setSeekParameters(if (forward) SeekParameters.NEXT_SYNC else SeekParameters.PREVIOUS_SYNC)
    val target = (currentPosition + if (forward) SEEK_INCREMENT_MS else -SEEK_INCREMENT_MS).coerceAtLeast(0)
    seekTo(duration.takeIf { it != C.TIME_UNSET }?.let(target::coerceAtMost) ?: target)
}

private class PlayerRenderersFactory(context: Context, private val delay: SubtitleDelay) : DefaultRenderersFactory(context) {
    var textRenderer: DelayedTextRenderer? = null
        private set

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
        out.add(DelayedTextRenderer(TextRenderer(output, outputLooper), delay).also { textRenderer = it })
    }
}

internal class DelayedTextRenderer(
    private val text: TextRenderer,
    private val delay: SubtitleDelay
) : ForwardingRenderer(text) {
    private var originalStream: SampleStream? = null
    private var rewindStream: RewindableSampleStream? = null
    private var timeline: Timeline = Timeline.EMPTY
    private var periodId: MediaSource.MediaPeriodId? = null
    private var streamOffsetUs: Long = 0
    private var appliedDelayUs: Long = 0

    override fun getStream(): SampleStream? = originalStream

    override fun setTimeline(timeline: Timeline) {
        this.timeline = timeline
        super.setTimeline(timeline)
        if (isCurrentStreamFinal) updateFinalStreamEnd()
    }

    override fun enable(
        configuration: RendererConfiguration,
        formats: Array<Format>,
        stream: SampleStream,
        positionUs: Long,
        joining: Boolean,
        mayRenderStartOfStream: Boolean,
        startPositionUs: Long,
        offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId
    ) {
        val currentDelayUs = delay.us
        appliedDelayUs = currentDelayUs
        originalStream = stream
        streamOffsetUs = offsetUs
        periodId = mediaPeriodId
        val wrapped = RewindableSampleStream(stream, formats.firstOrNull()).also { rewindStream = it }
        super.enable(
            configuration,
            formats,
            wrapped,
            positionUs - currentDelayUs,
            joining,
            mayRenderStartOfStream,
            startPositionUs - currentDelayUs,
            offsetUs,
            mediaPeriodId
        )
    }

    override fun replaceStream(
        formats: Array<Format>,
        stream: SampleStream,
        startPositionUs: Long,
        offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId
    ) {
        val currentDelayUs = delay.us
        appliedDelayUs = currentDelayUs
        val wrapped = if (stream === originalStream) {
            checkNotNull(rewindStream)
        } else {
            originalStream = stream
            RewindableSampleStream(stream, formats.firstOrNull()).also { rewindStream = it }
        }
        streamOffsetUs = offsetUs
        periodId = mediaPeriodId
        super.replaceStream(formats, wrapped, startPositionUs - currentDelayUs, offsetUs, mediaPeriodId)
    }

    override fun setCurrentStreamFinal() {
        super.setCurrentStreamFinal()
        updateFinalStreamEnd()
    }

    override fun resetPosition(positionUs: Long, sampleStreamIsResetToKeyFrame: Boolean) {
        val currentDelayUs = delay.us
        appliedDelayUs = currentDelayUs
        rewindStream?.clear()
        super.resetPosition(positionUs - currentDelayUs, sampleStreamIsResetToKeyFrame)
    }

    override fun handleMessage(messageType: Int, payload: Any?) {
        if (messageType != Renderer.MSG_CUSTOM_BASE) super.handleMessage(messageType, payload)
    }

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        val currentDelayUs = delay.us
        val shifted = positionUs - currentDelayUs
        if (currentDelayUs != appliedDelayUs) {
            val movedBackward = currentDelayUs > appliedDelayUs
            appliedDelayUs = currentDelayUs
            if (movedBackward || isEnded) {
                val wasFinal = isCurrentStreamFinal
                rewindStream?.rewind()
                super.resetPosition(shifted, true)
                if (wasFinal) {
                    super.setCurrentStreamFinal()
                    updateFinalStreamEnd()
                }
            } else if (isCurrentStreamFinal) {
                updateFinalStreamEnd()
            }
        }
        val stream = rewindStream
        do {
            val before = stream?.position ?: 0
            super.render(shifted, elapsedRealtimeUs)
        } while (!isEnded && (stream?.position ?: 0) > before)
        stream?.discardBefore(shifted - streamOffsetUs - REWIND_WINDOW_US)
    }

    override fun disable() {
        originalStream = null
        rewindStream = null
        periodId = null
        super.disable()
    }

    override fun reset() {
        originalStream = null
        rewindStream = null
        periodId = null
        super.reset()
    }

    private fun updateFinalStreamEnd() {
        val id = periodId
        val durationUs = if (!timeline.isEmpty && id != null && timeline.getIndexOfPeriod(id.periodUid) != C.INDEX_UNSET) {
            timeline.getPeriodByUid(id.periodUid, Timeline.Period()).durationUs
        } else {
            C.TIME_UNSET
        }
        val finalEndUs = if (durationUs != C.TIME_UNSET) streamOffsetUs + durationUs - appliedDelayUs else C.TIME_UNSET
        text.setFinalStreamEndPositionUs(finalEndUs)
    }
}

private class RewindableSampleStream(
    private val upstream: SampleStream,
    initialFormat: Format?
) : SampleStream {
    private class Sample(val format: Format?, val timeUs: Long, val flags: Int, val data: ByteArray)

    private val scratch = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL)
    private val samples = ArrayDeque<Sample>()
    private var currentFormat: Format? = initialFormat
    private var emittedFormat: Format? = initialFormat
    private var ended = false
    var position = 0
        private set

    fun rewind() {
        position = 0
    }

    fun clear() {
        samples.clear()
        position = 0
        ended = false
    }

    fun discardBefore(minTimeUs: Long) {
        while (position > 0 && samples.isNotEmpty() && samples.first().timeUs < minTimeUs) {
            samples.removeFirst()
            position--
        }
    }

    override fun isReady(): Boolean = position < samples.size || ended || upstream.isReady

    override fun maybeThrowError() = upstream.maybeThrowError()

    override fun readData(formatHolder: FormatHolder, buffer: DecoderInputBuffer, readFlags: Int): Int {
        if (position < samples.size) return readCached(formatHolder, buffer, readFlags)
        if (ended) {
            buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM)
            return C.RESULT_BUFFER_READ
        }
        if (readFlags and SampleStream.FLAG_REQUIRE_FORMAT != 0) {
            val result = upstream.readData(formatHolder, buffer, readFlags)
            if (result == C.RESULT_FORMAT_READ) {
                currentFormat = formatHolder.format
                emittedFormat = currentFormat
            }
            return result
        }
        scratch.clear()
        return when (val result = upstream.readData(formatHolder, scratch, 0)) {
            C.RESULT_NOTHING_READ -> C.RESULT_NOTHING_READ
            C.RESULT_FORMAT_READ -> {
                currentFormat = formatHolder.format
                emittedFormat = currentFormat
                C.RESULT_FORMAT_READ
            }
            C.RESULT_BUFFER_READ -> {
                if (scratch.isEndOfStream) {
                    ended = true
                    buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM)
                    C.RESULT_BUFFER_READ
                } else {
                    scratch.flip()
                    val bytes = scratch.data?.let { ByteArray(it.remaining()).also(it::get) } ?: ByteArray(0)
                    val flags = if (scratch.isKeyFrame) C.BUFFER_FLAG_KEY_FRAME else 0
                    samples.addLast(Sample(currentFormat, scratch.timeUs, flags, bytes))
                    readCached(formatHolder, buffer, readFlags)
                }
            }
            else -> result
        }
    }

    override fun skipData(positionUs: Long): Int = 0

    private fun readCached(formatHolder: FormatHolder, buffer: DecoderInputBuffer, readFlags: Int): Int {
        val sample = samples[position]
        if (readFlags and SampleStream.FLAG_REQUIRE_FORMAT != 0 || sample.format !== emittedFormat) {
            formatHolder.format = sample.format
            emittedFormat = sample.format
            return C.RESULT_FORMAT_READ
        }
        buffer.timeUs = sample.timeUs
        buffer.setFlags(sample.flags)
        if (readFlags and SampleStream.FLAG_OMIT_SAMPLE_DATA == 0) {
            buffer.ensureSpaceForWrite(sample.data.size)
            buffer.data?.put(sample.data)
        }
        if (readFlags and SampleStream.FLAG_PEEK == 0) position++
        return C.RESULT_BUFFER_READ
    }
}

internal object SubtitleCharsetDetector : CharsetDetector {
    override fun detect(data: ByteArray, offset: Int, length: Int): Charset? =
        Chardet.detectCharsetName(data, offset, length, null).getOrNull()?.let { runCatching { Charset.forName(it) }.getOrNull() }
}
