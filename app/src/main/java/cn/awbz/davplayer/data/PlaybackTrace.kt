package cn.awbz.davplayer.data

import android.util.Log
import cn.awbz.davplayer.BuildConfig

/** Numeric playback diagnostics only: never include URLs, headers or credentials. */
object PlaybackTrace {
    fun networkSummary() {
        val s = SourceHttp.summary()
        runCatching { Log.i("DavPlayback", "browse calls=${s.calls} connects=${s.connections} bytes=${s.bytes} active=${s.active} peak=${s.peak} elapsedMs=${s.elapsedMs} kinds=${s.kinds} status=${s.statuses}") }
    }
    fun systemSeek(event: Int, targetMs: Long, actualMs: Long) {
        runCatching { Log.i("DavPlayback", "systemSeek event=$event targetMs=$targetMs actualMs=$actualMs") }
    }
    fun write(message: String) { if (BuildConfig.DEBUG) runCatching { Log.d("DavPlayback", message) } }
}
