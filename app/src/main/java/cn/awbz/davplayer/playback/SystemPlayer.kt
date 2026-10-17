package cn.awbz.davplayer.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.AudioManager
import android.os.Build
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
    private var mediaDuration = 0L
    private var confirmedPosition = 0L
    private var inFlightPosition = 0L
    private var seekTimeout: Runnable? = null
    private var selectedAudio = -1
    private var timeout: Runnable? = null

    override fun open(connection: Connection, path: String, holder: SurfaceHolder, position: Long, playing: Boolean) {
        check(!released)
        generation++
        val token = generation
        closeCurrent()
        prepared = false; desiredPlay = playing; requestedPosition = position.coerceAtLeast(0)
        mediaDuration = 0; confirmedPosition = requestedPosition; selectedAudio = -1; initialSeek = false; seekPending = false
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
                        if (Build.VERSION.SDK_INT >= 21) next.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                        else next.setAudioStreamType(AudioManager.STREAM_MUSIC)
                        next.setDisplay(holder)
                        next.setOnPreparedListener { mp -> if (valid(mp)) {
                            try {
                                prepared = true
                                mediaDuration = mp.duration.toLong().coerceAtLeast(0)
                                listener.videoSize(mp.videoWidth, mp.videoHeight, 1f)
                                if (requestedPosition > 0 && duration > 0) {
                                    initialSeek = true
                                    requestedPosition = requestedPosition.coerceAtMost(duration)
                                    beginSeek(mp)
                                } else finishPreparing()
                            } catch (_: Exception) { if (valid(mp)) fail("MediaPlayer 无法恢复播放进度") }
                        } }
                        next.setOnSeekCompleteListener { mp -> if (valid(mp)) {
                            if (!seekPending) return@setOnSeekCompleteListener
                            seekTimeout?.let(handler::removeCallbacks); seekTimeout = null
                            if (requestedPosition != inFlightPosition) {
                                beginSeek(mp)
                                return@setOnSeekCompleteListener
                            }
                            val actual = mp.currentPosition.toLong().coerceAtLeast(0)
                            PlaybackTrace.systemSeek(1, requestedPosition, actual)
                            if (kotlin.math.abs(actual - requestedPosition) > 30_000) {
                                fail("跳转未完成，点击重试")
                                return@setOnSeekCompleteListener
                            }
                            seekPending = false; confirmedPosition = actual
                            if (initialSeek) { initialSeek = false; finishPreparing() }
                            else listener.buffering(false)
                        } }
                        next.setOnVideoSizeChangedListener { mp, width, height -> if (valid(mp)) listener.videoSize(width, height, 1f) }
                        next.setOnInfoListener { mp, what, _ ->
                            if (valid(mp)) when (what) {
                                MediaPlayer.MEDIA_INFO_BUFFERING_START -> listener.buffering(true)
                                MediaPlayer.MEDIA_INFO_BUFFERING_END -> if (!initialSeek && !seekPending) listener.buffering(false)
                                MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> if (!initialSeek && !seekPending) listener.buffering(false)
                            }
                            true
                        }
                        next.setOnCompletionListener { mp -> if (valid(mp)) {
                            val actual = mp.currentPosition.toLong().coerceAtLeast(0)
                            PlaybackTrace.systemSeek(2, requestedPosition, actual)
                            // A transport EOF may arrive as completion with currentPosition reset to zero.
                            val endPosition = if (seekPending) requestedPosition else maxOf(confirmedPosition, actual)
                            if (mediaDuration > 0 && mediaDuration - endPosition > 2_000) {
                                confirmedPosition = endPosition; requestedPosition = endPosition
                                fail("播放意外中断，点击重试")
                            } else {
                                seekTimeout?.let(handler::removeCallbacks); seekTimeout = null
                                seekPending = false; confirmedPosition = mediaDuration
                                desiredPlay = false; listener.ended()
                            }
                        } }
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
        if (!seekPending) requestedPosition = confirmedPosition
        generation++; prepared = false
        closeCurrent()
        listener.failed(message)
    }
    private fun closeCurrent() {
        scope.cancel(); resolving?.cancel(true); resolving = null
        timeout?.let(handler::removeCallbacks); timeout = null
        seekTimeout?.let(handler::removeCallbacks); seekTimeout = null
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
        // MediaPlayer callbacks have no request ID. Submit only one native seek at a time;
        // later user requests replace the queued target, never the in-flight callback.
        if (!seekPending) player?.let(::beginSeek)
    }
    private fun beginSeek(mp: MediaPlayer) {
        seekTimeout?.let(handler::removeCallbacks)
        seekPending = true; inFlightPosition = requestedPosition
        val token = generation
        val deadline = Runnable { if (!released && token == generation && player === mp && seekPending) fail("跳转超时，点击重试") }
        seekTimeout = deadline; handler.postDelayed(deadline, 30_000)
        PlaybackTrace.systemSeek(0, requestedPosition, confirmedPosition)
        listener.buffering(true)
        try { mp.seekTo(requestedPosition.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
        catch (_: Exception) { fail("MediaPlayer 无法跳转进度") }
    }
    override val position: Long get() {
        if (seekPending || !prepared) return requestedPosition
        val actual = runCatching { player?.currentPosition?.toLong() }.getOrNull()
        if (actual != null) confirmedPosition = actual.coerceIn(0, mediaDuration)
        return confirmedPosition
    }
    override val duration get() = mediaDuration
    override val downloadSpeed: Long? get() = null
    // Whole-file integer percentages cannot describe buffered time after a range seek.
    override val bufferedDuration: Long? get() = null
    override fun audioTracks(): List<PlaybackPlayer.AudioTrack> {
        if (!prepared) return emptyList()
        return runCatching {
            val mp = player ?: return emptyList()
            val selected = if (Build.VERSION.SDK_INT >= 21) mp.getSelectedTrack(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO)
                else selectedAudio
            mp.trackInfo.mapIndexedNotNull { index, track ->
                if (track.trackType != MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) null else {
                    val language = track.language.orEmpty().takeUnless { it == "und" }.orEmpty()
                    val label = language.takeIf { it.isNotBlank() }?.let { java.util.Locale(it.replace('-', '_').substringBefore('_')).getDisplayLanguage(java.util.Locale.SIMPLIFIED_CHINESE) }
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
            if (Build.VERSION.SDK_INT >= 21) player?.getSelectedTrack(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) == index
            else true
        }.getOrDefault(false)
        if (success) selectedAudio = index
        result(success)
    }
    override fun release() {
        if (released) return
        released = true; generation++; prepared = false
        closeCurrent(); resolver.shutdownNow(); handler.removeCallbacksAndMessages(null)
    }
}
