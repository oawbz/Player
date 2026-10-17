package cn.awbz.davplayer.data

import org.junit.Assert.*
import org.junit.Test

class PlaybackProgressTest {
    @Test fun completedWorkRestartsButUnfinishedWorkResumes() {
        assertEquals(0L, PlaybackProgress.resumePosition(100, 100))
        assertEquals(0L, PlaybackProgress.resumePosition(110, 100))
        assertEquals(99L, PlaybackProgress.resumePosition(99, 100))
        assertEquals(0L, PlaybackProgress.resumePosition(-1, 100))
    }
}
