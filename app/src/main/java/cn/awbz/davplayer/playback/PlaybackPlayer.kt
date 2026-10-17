package cn.awbz.davplayer.playback

import android.view.SurfaceHolder
import cn.awbz.davplayer.data.Connection

enum class PlaybackEngine(val label: String) {
    EXOPLAYER("ExoPlayer"), MEDIAPLAYER("MediaPlayer");
    companion object {
        fun stored(value: String?) = entries.firstOrNull { it.name == value } ?: EXOPLAYER
    }
}

interface PlaybackPlayer {
    interface Listener {
        fun ready()
        fun buffering(active: Boolean)
        fun ended()
        fun failed(message: String)
        fun warning(message: String?)
        fun videoSize(width: Int, height: Int, pixelRatio: Float)
    }
    data class AudioTrack(val id: String, val preferenceKey: String, val label: String, val selected: Boolean, val playable: Boolean)
    fun open(connection: Connection, path: String, holder: SurfaceHolder, position: Long, playing: Boolean)
    fun play()
    fun pause()
    fun seek(position: Long)
    val position: Long
    val duration: Long
    /** null means this engine does not expose transfer rate. */
    val downloadSpeed: Long?
    val bufferedDuration: Long
    fun audioTracks(): List<AudioTrack>
    fun selectAudioTrack(id: String, result: (Boolean) -> Unit)
    fun release()
}
