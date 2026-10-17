package cn.awbz.davplayer.playback

/** Only time spent waiting while playback is requested counts; pause/focus loss resets it. */
class PlaybackWait(private val timeoutMs: Long = 45_000) {
    private var since: Long? = null
    fun expired(waiting: Boolean, nowMs: Long): Boolean {
        if (!waiting) { reset(); return false }
        val start = since ?: nowMs.also { since = it }
        return nowMs - start >= timeoutMs
    }
    fun reset() { since = null }
}
