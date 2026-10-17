package cn.awbz.davplayer.playback

import org.junit.Assert.*
import org.junit.Test

class PlaybackWaitTest {
    @Test fun waitingTimesOutButNormalPlaybackNeverDoes() {
        val wait = PlaybackWait()
        assertFalse(wait.expired(true, 0))
        assertFalse(wait.expired(true, 44_999))
        assertTrue(wait.expired(true, 45_000))
        assertFalse(wait.expired(false, 999_999))
    }
    @Test fun PauseAndFocusLossDoNotConsumeTheNextWaitingBudget() {
        val wait = PlaybackWait()
        wait.expired(true, 0)
        assertFalse(wait.expired(false, 40_000))
        assertFalse(wait.expired(true, 100_000))
        assertFalse(wait.expired(true, 144_999))
        assertTrue(wait.expired(true, 145_000))
    }
    @Test fun NewEpisodeOrSeekGetsItsOwnBudget() {
        val wait = PlaybackWait()
        wait.expired(true, 0); wait.reset()
        assertFalse(wait.expired(true, 100_000))
        assertFalse(wait.expired(true, 144_999))
    }
}
