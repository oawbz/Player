package cn.awbz.davplayer.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/** Opt-in, bounded numeric sampling. Never logs media identifiers or exception text. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal class PlaybackProbe : AnalyticsListener {
    private val handler = Handler(Looper.getMainLooper())
    private val id = nextId.incrementAndGet()
    private val received = AtomicLong()
    private val starts = AtomicLong()
    private val ends = AtomicLong()
    private var generation = 0
    private var started = 0L
    private var player: ExoPlayer? = null
    private var firstFrame = false
    private val tick = object : Runnable {
        override fun run() {
            val p = player ?: return
            val elapsed = SystemClock.elapsedRealtime() - started
            if (elapsed > 180_000) return
            val video = p.videoDecoderCounters
            val audio = p.audioDecoderCounters
            video?.ensureUpdated(); audio?.ensureUpdated()
            log("sample", "ms=$elapsed state=${p.playbackState} want=${p.playWhenReady.n()} playing=${p.isPlaying.n()} loading=${p.isLoading.n()} suppressed=${p.playbackSuppressionReason} pos=${p.currentPosition} end=${p.bufferedPosition} total=${p.totalBufferedDuration} duration=${p.duration} bytes=${received.get()} starts=${starts.get()} ends=${ends.get()} first=${firstFrame.n()} tracks=${p.currentTracks.groups.count { it.isSelected }} vin=${video?.queuedInputBufferCount ?: -1} vout=${video?.renderedOutputBufferCount ?: -1} vdrop=${video?.droppedBufferCount ?: -1} vskip=${video?.skippedOutputBufferCount ?: -1} ain=${audio?.queuedInputBufferCount ?: -1} aout=${audio?.renderedOutputBufferCount ?: -1}")
            handler.postDelayed(this, 1000)
        }
    }
    fun attach(player: ExoPlayer) { this.player = player; player.addAnalyticsListener(this) }
    fun open(generation: Int) {
        handler.removeCallbacks(tick)
        this.generation = generation; started = SystemClock.elapsedRealtime(); firstFrame = false
        log("open", "bytes=${received.get()} starts=${starts.get()} ends=${ends.get()}")
        handler.post(tick)
    }
    fun transferStart(offset: Long, length: Long) { starts.incrementAndGet(); log("transfer", "offset=$offset length=$length") }
    fun bytes(count: Int) { received.addAndGet(count.toLong()) }
    fun transferEnd() { ends.incrementAndGet() }
    override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
        firstFrame = true; log("frame", "ms=${SystemClock.elapsedRealtime() - started}")
    }
    override fun onLoadStarted(eventTime: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData) = load(1, info, data)
    override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData) = load(2, info, data)
    override fun onLoadCanceled(eventTime: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData) = load(3, info, data)
    override fun onLoadError(eventTime: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData, error: IOException, wasCanceled: Boolean) {
        load(4, info, data)
        val http = error as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
        log("error", "task=${info.loadTaskId} http=${http?.responseCode ?: 0} canceled=${wasCanceled.n()}")
    }
    private fun load(event: Int, info: LoadEventInfo, data: MediaLoadData) = log("load", "event=$event task=${info.loadTaskId} type=${data.dataType} track=${data.trackType} offset=${info.dataSpec.position} length=${info.dataSpec.length} bytes=${info.bytesLoaded} ms=${info.loadDurationMs}")
    private fun log(event: String, numbers: String) {
        if (started != 0L && SystemClock.elapsedRealtime() - started <= 180_000) Log.i("PlaybackProbe", "id=$id gen=$generation event=$event $numbers")
    }
    fun release() { handler.removeCallbacks(tick); player?.removeAnalyticsListener(this); player = null }
    private fun Boolean.n() = if (this) 1 else 0
    companion object { private val nextId = AtomicLong() }
}
