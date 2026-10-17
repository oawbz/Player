package cn.awbz.davplayer.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import cn.awbz.davplayer.data.*
import cn.awbz.davplayer.data.source.OpenListSource
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** System MediaPlayer owns media reads and decoding; OpenList only resolves the URL. */
class SystemPlayer(context: Context, private val listener: PlaybackPlayer.Listener) : PlaybackPlayer {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val resolver = Executors.newSingleThreadExecutor()
    private var resolving: Future<*>? = null
    private var scope = RequestScope()
    private var player: MediaPlayer? = null
    private var generation = 0
    private var released = false
    private var prepared = false
    private var desiredPlay = false
    private var initialSeek = false
    private var seekPending = false
    private var requestedPosition = 0L
    private var bufferPercent = 0
    private var timeout: Runnable? = null

    override fun open(connection: Connection, path: String, holder: SurfaceHolder, position: Long, playing: Boolean) {
        check(!released)
        generation++
        val token = generation
        closeCurrent()
        prepared = false; desiredPlay = playing; requestedPosition = position.coerceAtLeast(0)
        bufferPercent = 0; initialSeek = false; seekPending = false
        scope = RequestScope(); val requests = scope
        listener.warning(null); listener.buffering(true)
        val deadline = Runnable { if (!released && token == generation) fail("MediaPlayer 准备超时，请重试") }
        timeout = deadline; handler.postDelayed(deadline, 30_000)
        resolving = resolver.submit {
            try {
                val url = OpenListSource.resolve(connection, path, requests)
                handler.post {
                    if (released || token != generation || requests.isCancelled) return@post
                    try {
                        val next = MediaPlayer(); player = next
                        fun valid(mp: MediaPlayer) = !released && token == generation && player === mp
                        next.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                        next.setDisplay(holder)
                        next.setOnPreparedListener { mp -> if (valid(mp)) {
                            try {
                                prepared = true
                                listener.videoSize(mp.videoWidth, mp.videoHeight, 1f)
                                if (requestedPosition > 0 && duration > 0) {
                                    initialSeek = true; seekPending = true
                                    requestedPosition = requestedPosition.coerceAtMost(duration)
                                    mp.seekTo(requestedPosition.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                                } else finishPreparing()
                            } catch (_: Exception) { if (valid(mp)) fail("MediaPlayer 无法恢复播放进度") }
                        } }
                        next.setOnSeekCompleteListener { mp -> if (valid(mp)) {
                            seekPending = false
                            if (initialSeek) { initialSeek = false; finishPreparing() }
                            else listener.buffering(false)
                        } }
                        next.setOnVideoSizeChangedListener { mp, width, height -> if (valid(mp)) listener.videoSize(width, height, 1f) }
                        next.setOnBufferingUpdateListener { mp, percent -> if (valid(mp)) bufferPercent = percent.coerceIn(0, 100) }
                        next.setOnInfoListener { mp, what, _ ->
                            if (valid(mp)) when (what) {
                                MediaPlayer.MEDIA_INFO_BUFFERING_START -> listener.buffering(true)
                                MediaPlayer.MEDIA_INFO_BUFFERING_END -> if (!initialSeek) listener.buffering(false)
                                MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> if (!initialSeek) listener.buffering(false)
                            }
                            true
                        }
                        next.setOnCompletionListener { mp -> if (valid(mp)) { desiredPlay = false; listener.ended() } }
                        next.setOnErrorListener { mp, what, extra ->
                            if (valid(mp)) {
                                PlaybackTrace.write("system error code=$what extra=$extra")
                                fail("MediaPlayer 播放失败（错误 $what / $extra）")
                            }
                            true
                        }
                        next.setDataSource(context, Uri.parse(url))
                        next.prepareAsync()
                    } catch (_: Exception) { if (!released && token == generation) fail("MediaPlayer 无法打开此媒体") }
                }
            } catch (_: Exception) {
                handler.post { if (!released && token == generation) fail("获取播放地址失败，请重试") }
            }
        }
    }
    private fun finishPreparing() {
        timeout?.let(handler::removeCallbacks); timeout = null
        if (desiredPlay) play()
        if (!prepared) return
        listener.buffering(false); listener.ready()
    }
    private fun fail(message: String) {
        generation++; prepared = false
        closeCurrent()
        listener.failed(message)
    }
    private fun closeCurrent() {
        scope.cancel(); resolving?.cancel(true); resolving = null
        timeout?.let(handler::removeCallbacks); timeout = null
        val old = player; player = null
        runCatching { old?.setDisplay(null) }
        runCatching { old?.release() }
    }
    override fun play() {
        desiredPlay = true
        if (prepared && !initialSeek) try { player?.start() } catch (_: Exception) { fail("MediaPlayer 无法继续播放") }
    }
    override fun pause() {
        desiredPlay = false
        if (prepared) try { if (player?.isPlaying == true) player?.pause() } catch (_: Exception) { fail("MediaPlayer 无法暂停") }
    }
    override fun seek(position: Long) {
        if (!prepared) { requestedPosition = position.coerceAtLeast(0); return }
        requestedPosition = position.coerceIn(0, duration)
        seekPending = true
        try { player?.seekTo(requestedPosition.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
        catch (_: Exception) { seekPending = false; fail("MediaPlayer 无法跳转进度") }
    }
    override val position get() = if (seekPending || !prepared) requestedPosition else runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(requestedPosition)
    override val duration get() = if (prepared) runCatching { (player?.duration?.toLong() ?: 0L).coerceAtLeast(0) }.getOrDefault(0L) else 0L
    override val downloadSpeed: Long? get() = null
    override val bufferedDuration get() = (duration * bufferPercent / 100 - position).coerceAtLeast(0)
    override fun audioTracks(): List<PlaybackPlayer.AudioTrack> {
        if (!prepared) return emptyList()
        return runCatching {
            val mp = player ?: return emptyList()
            val selected = mp.getSelectedTrack(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO)
            mp.trackInfo.mapIndexedNotNull { index, track ->
                if (track.trackType != MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) null else {
                    val language = track.language.orEmpty().takeUnless { it == "und" }.orEmpty()
                    val label = language.takeIf { it.isNotBlank() }?.let { java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale.SIMPLIFIED_CHINESE) }
                        ?: "音轨 ${index + 1}"
                    PlaybackPlayer.AudioTrack(index.toString(), "system|$index|$language", label, index == selected, true)
                }
            }
        }.getOrDefault(emptyList())
    }
    override fun selectAudioTrack(id: String, result: (Boolean) -> Unit) {
        val index = id.toIntOrNull()
        if (!prepared || index == null || audioTracks().none { it.id == id }) return result(false)
        val success = runCatching {
            player?.selectTrack(index)
            player?.getSelectedTrack(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) == index
        }.getOrDefault(false)
        result(success)
    }
    override fun release() {
        if (released) return
        released = true; generation++; prepared = false
        closeCurrent(); resolver.shutdownNow(); handler.removeCallbacksAndMessages(null)
    }
}
