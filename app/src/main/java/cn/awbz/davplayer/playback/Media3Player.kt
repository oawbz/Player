package cn.awbz.davplayer.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import androidx.media3.common.*
import androidx.media3.datasource.*
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import cn.awbz.davplayer.data.Connection
import cn.awbz.davplayer.data.source.OpenListSource
import cn.awbz.davplayer.data.RequestScope
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** One Media3 player. OpenList returns the URL; official data sources own all media reads. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class Media3Player(context: Context, private val listener: Listener) {
    interface Listener {
        fun firstFrame()
        fun ready()
        fun buffering(active: Boolean)
        fun ended()
        fun failed(message: String)
        fun warning(message: String?)
        fun videoSize(width: Int, height: Int, pixelRatio: Float)
    }
    data class AudioTrack(val id: String, val preferenceKey: String, val label: String, val selected: Boolean, val playable: Boolean)

    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val resolver = Executors.newSingleThreadExecutor()
    private val meter = TransferMeter()
    private val probe = if (cn.awbz.davplayer.BuildConfig.PLAYBACK_DIAGNOSTICS) PlaybackProbe() else null
    @Volatile private var generation = 0
    @Volatile private var released = false
    private var renderedVideo = false
    private var resolving: Future<*>? = null
    private var resolveScope = RequestScope()
    private var resolveTimeout: Runnable? = null
    private var pendingAudio: Pair<String, (Boolean) -> Unit>? = null
    private val audioTimeout = Runnable { pendingAudio?.second?.invoke(false); pendingAudio = null }
    private val network = DefaultHttpDataSource.Factory().setTransferListener(object : TransferListener {
        override fun onTransferInitializing(source: DataSource, spec: DataSpec, network: Boolean) {}
        override fun onTransferStart(source: DataSource, spec: DataSpec, network: Boolean) { probe?.transferStart(spec.position, spec.length) }
        override fun onBytesTransferred(source: DataSource, spec: DataSpec, network: Boolean, bytes: Int) { meter.progress(bytes); probe?.bytes(bytes) }
        override fun onTransferEnd(source: DataSource, spec: DataSpec, network: Boolean) { probe?.transferEnd() }
    })
    private val player = ExoPlayer.Builder(context)
        .setRenderersFactory(DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .apply { if (android.os.Build.VERSION.SDK_INT >= 23) forceEnableMediaCodecAsynchronousQueueing() })
        .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(DefaultDataSource.Factory(context, network)))
        .build().also { exo ->
            probe?.attach(exo)
            exo.addListener(object : Player.Listener {
                private fun active() = !released && exo.currentMediaItem?.mediaId == generation.toString()
                override fun onPlaybackStateChanged(state: Int) {
                    if (!active()) return
                    listener.buffering(state == Player.STATE_BUFFERING)
                    if (state == Player.STATE_READY) { listener.ready(); reportAudio() }
                    if (state == Player.STATE_ENDED) listener.ended()
                }
                override fun onRenderedFirstFrame() {
                    if (active()) { renderedVideo = true; listener.firstFrame() }
                }
                override fun onTracksChanged(tracks: Tracks) {
                    if (!active()) return
                    pendingAudio?.let { (id, callback) ->
                        if (audioTracks().any { it.id == id && it.selected }) {
                            pendingAudio = null; handler.removeCallbacks(audioTimeout); callback(true)
                        }
                    }
                    if (exo.playbackState == Player.STATE_READY) reportAudio()
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (active()) { listener.failed("播放失败（错误 ${error.errorCode}）") }
                }
                override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                    if (active()) listener.videoSize(size.width, size.height, size.pixelWidthHeightRatio)
                }
            })
        }
    private fun reportAudio() {
        val audio = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        listener.warning(AudioAvailability.warning(audio.isNotEmpty(), audio.any { it.isSelected },
            audio.flatMap { group -> (0 until group.length).map { group.getTrackFormat(it).sampleMimeType.orEmpty() } }))
    }
    fun open(connection: Connection, path: String, holder: SurfaceHolder, position: Long, playing: Boolean) {
        check(!released)
        renderedVideo = false
        val token = ++generation
        probe?.open(token)
        resolveScope.cancel(); resolving?.cancel(true)
        resolveTimeout?.let(handler::removeCallbacks)
        pendingAudio = null; handler.removeCallbacks(audioTimeout)
        player.stop(); player.clearMediaItems(); meter.reset()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO).setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).build()
        player.setVideoSurfaceHolder(holder)
        player.playWhenReady = playing
        listener.warning(null); listener.buffering(true)
        resolveScope = RequestScope()
        val scope = resolveScope
        val timeout = Runnable {
            if (!released && token == generation && player.currentMediaItem == null) {
                generation++; resolveScope.cancel(); resolving?.cancel(true); listener.failed("获取播放地址超时，请重试")
            }
        }
        resolveTimeout = timeout; handler.postDelayed(timeout, 30_000)
        resolving = resolver.submit {
            try {
                val url = OpenListSource.resolve(connection, path, scope)
                handler.post {
                    if (!released && token == generation) {
                        handler.removeCallbacks(timeout)
                        player.setMediaItem(MediaItem.Builder().setMediaId(token.toString()).setUri(url).build(), position.coerceAtLeast(0))
                        player.prepare()
                    }
                }
            } catch (_: Exception) {
                // SDK exception messages may contain credentials or signed URLs.
                handler.post { if (!released && token == generation) {
                    handler.removeCallbacks(timeout); listener.failed("获取播放地址失败，请重试")
                } }
            }
        }
    }
    fun play() = player.play()
    fun pause() = player.pause()
    fun seek(position: Long) {
        val target = position.coerceIn(0, duration)
        if (target == player.currentPosition) return
        renderedVideo = false; player.seekTo(target)
    }
    val isBuffering get() = player.playbackState == Player.STATE_BUFFERING
    val hasVideo get() = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
    val hasRenderedVideo get() = renderedVideo
    val position get() = player.currentPosition.coerceAtLeast(0)
    val duration get() = player.duration.takeIf { it > 0 } ?: 0L
    val downloadSpeed get() = meter.speed()
    val bufferedDuration get() = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0)
    fun audioTracks(): List<AudioTrack> {
        val result = mutableListOf<AudioTrack>()
        player.currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) for (index in 0 until group.length) {
                val format = group.getTrackFormat(index)
                val playable = group.isTrackSupported(index, true)
                val language = format.language?.takeUnless { it == "und" }.orEmpty()
                val name = format.label?.takeIf { it.isNotBlank() } ?: language.takeIf { it.isNotBlank() }
                    ?.let { (if (android.os.Build.VERSION.SDK_INT >= 21) java.util.Locale.forLanguageTag(it) else java.util.Locale(it.substringBefore('-'))).getDisplayLanguage(java.util.Locale.SIMPLIFIED_CHINESE) }
                    ?: "音轨 ${result.size + 1}"
                val channels = when (format.channelCount) { 1 -> "单声道"; 2 -> "立体声"; 6 -> "5.1"; 8 -> "7.1"; else -> "" }
                val codec = AudioAvailability.codec(format.sampleMimeType.orEmpty())
                val label = listOf(name, channels, codec).filter { it.isNotBlank() }.joinToString(" · ") + if (playable) "" else "（不支持）"
                val key = listOf(language, format.sampleMimeType.orEmpty(), format.channelCount.toString(), format.label.orEmpty()).joinToString("|")
                result.add(AudioTrack("$groupIndex:$index", key, label, group.isTrackSelected(index), playable))
            }
        }
        return result
    }
    fun selectAudioTrack(id: String, result: (Boolean) -> Unit) {
        val selected = audioTracks().firstOrNull { it.id == id && it.playable } ?: return result(false)
        if (selected.selected) return result(true)
        val parts = id.split(':').mapNotNull { it.toIntOrNull() }
        if (parts.size != 2) return result(false)
        val group = player.currentTracks.groups.getOrNull(parts[0]) ?: return result(false)
        pendingAudio = id to result
        handler.removeCallbacks(audioTimeout); handler.postDelayed(audioTimeout, 2000)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO).setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, parts[1])).build()
    }
    fun release() {
        if (released) return
        released = true; generation++; resolveScope.cancel(); resolving?.cancel(true); resolver.shutdownNow()
        handler.removeCallbacksAndMessages(null)
        pendingAudio = null
        probe?.release()
        player.release()
    }
}
