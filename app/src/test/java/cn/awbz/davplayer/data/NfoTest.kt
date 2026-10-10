package cn.awbz.davplayer.data

import okhttp3.HttpUrl
import org.junit.Assert.*
import org.junit.Test

class NfoTest {
    @Test fun readsMovieCdataGenresYearRuntimeAndDefaultNormalizedRating() {
        val meta = Nfo.parse("""<movie><title>测试影片</title><plot><![CDATA[第一段
第二段]]></plot><year>2026</year><runtime>106</runtime><genre>剧情</genre><genre>喜剧</genre>
<ratings><rating name="imdb" max="10"><value>7.0</value></rating><rating name="fixture" max="100" default="true"><value>85</value></rating></ratings></movie>""")
        assertEquals("测试影片", meta.title); assertEquals("第一段\n第二段", meta.plot)
        assertEquals("2026", meta.year); assertEquals(106, meta.runtimeMinutes); assertEquals(8.5, meta.rating!!, .0001)
        assertEquals(listOf("剧情", "喜剧"), meta.genres)
    }
    @Test fun readsLegacyRatingAndDateWhileIgnoringInvalidNumbers() {
        val meta = Nfo.parse("<tvshow><plot>A &amp; B</plot><premiered>2025-02-03</premiered><runtime>-5</runtime><rating>9.1</rating></tvshow>")
        assertEquals("A & B", meta.plot); assertEquals("2025", meta.year); assertNull(meta.runtimeMinutes); assertEquals(9.1, meta.rating!!, .001)
        assertNull(Nfo.parse("<movie><rating>NaN</rating><runtime>unknown</runtime></movie>").rating)
    }
    @Test fun rejectsDtdTruncationAndOversizedInput() {
        for (xml in listOf("<!DOCTYPE movie [<!ENTITY x SYSTEM 'file:///fixture'>]><movie><plot>&x;</plot></movie>", "<movie><plot>incomplete", "<html/>", " ".repeat(Nfo.LIMIT + 1))) {
            try { Nfo.parse(xml); fail("must reject invalid NFO") } catch (_: DavFailure) {}
        }
    }
    @Test fun picksKnownSidecarWithoutChoosingAnArbitraryEpisodeNfo() {
        fun entry(name: String) = DavEntry(HttpUrl.parse("https://fixture.invalid/dav/$name")!!, name, false)
        val list = listOf(entry("unrelated.nfo"), entry("Film.nfo"), entry("movie.nfo"))
        assertEquals("movie.nfo", Nfo.select(list, "Film", listOf(Episode("/dav/Film.mkv", "Film.mkv")))!!.name)
        assertNull(Nfo.select(listOf(entry("a.nfo"), entry("b.nfo")), "Show", emptyList()))
    }
}
