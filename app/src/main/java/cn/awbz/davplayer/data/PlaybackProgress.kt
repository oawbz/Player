package cn.awbz.davplayer.data

object PlaybackProgress {
    fun resumePosition(position: Long, duration: Long): Long =
        if (duration > 0 && position >= duration) 0 else position.coerceAtLeast(0)
}
