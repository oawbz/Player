package cn.awbz.davplayer.data

import android.util.Log
import cn.awbz.davplayer.BuildConfig

/** Numeric playback diagnostics only: never include URLs, headers or credentials. */
object PlaybackTrace {
    fun write(message: String) { if (BuildConfig.DEBUG) runCatching { Log.d("DavPlayback", message) } }
}
