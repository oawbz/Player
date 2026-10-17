package cn.awbz.davplayer.data

import org.junit.Assert.*
import org.junit.Test

class EpisodePageTest {
    @Test fun largeSeasonRemainsCompleteWithoutCreatingAllCards() {
        val count = 5000
        val ranges = (0 until EpisodePage.of(count, 0).pages).map { EpisodePage.of(count, it) }
        assertTrue(ranges.all { it.end - it.start <= EpisodePage.SIZE })
        assertEquals((0 until count).toList(), ranges.flatMap { (it.start until it.end).toList() })
        assertEquals(ranges.last(), EpisodePage.of(count, Int.MAX_VALUE))
        assertEquals(ranges.first(), EpisodePage.of(count, -1))
        assertEquals(0, EpisodePage.of(0, 0).end)
    }
}
