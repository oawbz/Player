package cn.awbz.davplayer.ui

import org.junit.Assert.*
import org.junit.Test

class InlinePlotTextTest {
    @Test fun reservesSpaceForTheWholeAction() {
        val suffix = "…【查看简介】"
        val compact = InlinePlotText.fit("这是一段很长很长很长的电影简介", suffix) { it.length <= 14 }
        assertEquals(14, compact.length)
        assertTrue(compact.endsWith(suffix))
        assertTrue(compact.startsWith("这是一段"))
    }
    @Test fun neverSplitsAnEmojiWhenReservingSpace() {
        val text = "电影😀简介"
        assertEquals("电影", InlinePlotText.prefix(text, 3))
        assertEquals("电影😀…查看", InlinePlotText.fit(text, "…查看") { it.length <= 7 })
    }
    @Test fun trailingLineBreaksDoNotPutTheActionOnANewLine() {
        assertEquals("简介…查看", InlinePlotText.fit("简介\n\n", "…查看") { it.length <= 20 })
    }
}
