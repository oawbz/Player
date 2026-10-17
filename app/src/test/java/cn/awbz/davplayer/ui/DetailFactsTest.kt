package cn.awbz.davplayer.ui

import cn.awbz.davplayer.data.CastMember
import cn.awbz.davplayer.data.MediaMetadata
import org.junit.Assert.*
import org.junit.Test

class DetailFactsTest {
    @Test fun emptyAndPlaceholderValuesNeverLeaveLabels() {
        assertTrue(DetailFacts.of(MediaMetadata()).isEmpty())
        assertTrue(DetailFacts.of(MediaMetadata(title = "片名", originalTitle = "片名",
            directors = listOf(" ", "未知"), studios = listOf("N/A"), tagline = "--",
            cast = listOf(CastMember("", "角色")))).isEmpty())
    }
    @Test fun populatedFieldsRemainIndependentAndCastHasNoEmptyRoleLabel() {
        val facts = DetailFacts.of(MediaMetadata(originalTitle = "Original", premiered = "2026-10-17",
            directors = listOf("甲", "甲", ""), cast = listOf(CastMember("乙"), CastMember("丙", "丁"))))
        assertEquals(listOf("原名", "首映日期", "导演", "演员"), facts.map { it.label })
        assertEquals("甲", facts[2].value)
        assertEquals("乙  ·  丙（饰 丁）", facts.last().value)
        assertTrue(facts.last().wide)
        assertFalse(facts.first().wide)
    }
}
