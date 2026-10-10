package cn.awbz.davplayer.playback

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import cn.awbz.davplayer.data.*
import java.util.concurrent.Executors

interface Engine {
    fun start(connection: Connection, path: String, holder: SurfaceHolder, position: Long, playing: Boolean)
    fun play()
    fun pause()
    fun seek(position: Long)
    val position: Long
    val duration: Long
    val playing: Boolean
    fun release()
    interface Listener {
        fun ready()
        fun buffering(active: Boolean)
        fun ended()
        fun failed(message: String)
        fun videoSize(width: Int, height: Int, pixelRatio: Float)
    }
}

/** One adapter instance per attempt. Release invalidates every pending native callback. */
class SystemEngine(private val context: Context, private val listener: Engine.Listener) : Engine {
    private val media = MediaPlayer()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var released = false
    private var prepared = false
    private var ended = false
    private var pendingSeek = false
    private var wantedPlay = true
    override fun start(connection: Connection, path: String, holder: SurfaceHolder, position: Long, playing: Boolean) {
        wantedPlay = playing
        media.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)
        media.setDisplay(holder)
        media.setOnPreparedListener {
            if (!released) {
                prepared = true
                if (position > 0) {
                    pendingSeek = true
                    media.seekTo(position.coerceAtMost(duration.coerceAtLeast(0)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                } else finishPrepare()
            }
        }
        media.setOnSeekCompleteListener { if (!released) { if (pendingSeek) { pendingSeek = false; finishPrepare() } } }
        media.setOnCompletionListener { if (!released) { ended = true; listener.ended() } }
        media.setOnErrorListener { _, _, _ -> if (!released) listener.failed("系统核心无法播放，请检查文件格式、认证、网络或手动切换核心"); true }
        media.setOnInfoListener { _, what, _ ->
            if (!released) when (what) {
                MediaPlayer.MEDIA_INFO_BUFFERING_START -> listener.buffering(true)
                MediaPlayer.MEDIA_INFO_BUFFERING_END -> listener.buffering(false)
            }; true
        }
        media.setOnVideoSizeChangedListener { _, width, height -> if (!released) listener.videoSize(width, height, 1f) }
        val url = DavUrls.fromPath(connection, path).toString()
        val headers = DavClient().headers(connection)
        io.execute {
            try {
                if (!released) { media.setDataSource(context, android.net.Uri.parse(url), headers); if (!released) media.prepareAsync() }
            } catch (_: Exception) { main.post { if (!released) listener.failed("系统核心无法打开媒体，请检查认证、网络或格式") } }
        }
    }
    private fun finishPrepare() {
        if (released) return
        listener.ready(); if (wantedPlay) play()
    }
    override fun play() { wantedPlay = true; if (prepared && !released && !pendingSeek) { ended = false; runCatching { media.start() } } }
    override fun pause() { wantedPlay = false; if (prepared && !released) runCatching { media.pause() } }
    override fun seek(position: Long) {
        if (prepared && !released) { ended = false; media.seekTo(position.coerceIn(0, duration).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
    }
    override val position: Long get() = if (ended) duration else if (prepared && !released) runCatching { media.currentPosition.toLong() }.getOrDefault(0) else 0
    override val duration: Long get() = if (prepared && !released) runCatching { media.duration.toLong().coerceAtLeast(0) }.getOrDefault(0) else 0
    override val playing: Boolean get() = prepared && !released && runCatching { media.isPlaying }.getOrDefault(false)
    override fun release() {
        released = true; prepared = false
        media.setOnPreparedListener(null); media.setOnCompletionListener(null); media.setOnErrorListener(null)
        media.setOnInfoListener(null); media.setOnSeekCompleteListener(null); media.setOnVideoSizeChangedListener(null)
        // Serialize against setDataSource/prepare; never wait for native network work on the UI thread.
        io.execute { runCatching { media.reset() }; media.release() }; io.shutdown()
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class ExoEngine(private val context: Context, private val dav: DavClient, private val listener: Engine.Listener) : Engine {
    private var player: ExoPlayer? = null
    override fun start(connection: Connection, path: String, holder: SurfaceHolder, position: Long, playing: Boolean) {
        val factory = DavDataSource.Factory(connection, dav)
        val exo = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build()
        player = exo
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (player !== exo) return
                listener.buffering(state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) listener.ready()
                if (state == Player.STATE_ENDED) listener.ended()
            }
            override fun onPlayerError(error: PlaybackException) {
                if (player === exo) listener.failed("ExoPlayer 无法播放（错误 ${error.errorCode}），请检查网络、认证、文件格式或手动切换核心")
            }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (player === exo) listener.videoSize(videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio)
            }
        })
        exo.setVideoSurfaceHolder(holder)
        exo.setMediaItem(MediaItem.fromUri(DavUrls.fromPath(connection, path).toString()))
        exo.seekTo(position.coerceAtLeast(0)); exo.playWhenReady = playing; exo.prepare()
    }
    override fun play() { player?.play() }
    override fun pause() { player?.pause() }
    override fun seek(position: Long) { player?.seekTo(position.coerceIn(0, duration)) }
    override val position get() = player?.currentPosition?.coerceAtLeast(0) ?: 0
    override val duration get() = player?.duration?.takeIf { it > 0 } ?: 0
    override val playing get() = player?.isPlaying ?: false
    override fun release() { val old = player; player = null; old?.release() }
}
