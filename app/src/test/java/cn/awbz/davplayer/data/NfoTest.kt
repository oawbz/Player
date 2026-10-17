package cn.awbz.davplayer.data

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
        val meta = Nfo.parse("<movie><plot>A &amp; B</plot><premiered>2025-02-03</premiered><runtime>-5</runtime><rating>9.1</rating></movie>")
        assertEquals("A & B", meta.plot); assertEquals("2025", meta.year); assertNull(meta.runtimeMinutes); assertEquals(9.1, meta.rating!!, .001)
        assertNull(Nfo.parse("<movie><rating>NaN</rating><runtime>unknown</runtime></movie>").rating)
    }
    @Test fun rejectsDtdTruncationAndOversizedInput() {
        for (xml in listOf("<!DOCTYPE movie [<!ENTITY x SYSTEM 'file:///fixture'>]><movie><plot>&x;</plot></movie>", "<movie><plot>incomplete", "<html/>", " ".repeat(Nfo.LIMIT + 1))) {
            try { Nfo.parse(xml); fail("must reject invalid NFO") } catch (_: MediaFailure) {}
        }
    }
    @Test fun usesOnlyFixedMovieNfoPath() {
        val c = Connection("fixture", "fixture", "https://example.invalid/", "", "")
        assertEquals("/series/movie.nfo", Nfo.path(c, "/series/"))
    }
    @Test fun readsRichScrapedFieldsAndActorRolesWithoutThumbUrls() {
        val meta = Nfo.parse("""<movie><title>片名</title><originaltitle>Original</originaltitle>
<tagline>一句话</tagline><mpaa>PG</mpaa><director>导演甲</director><director>导演甲</director>
<credits>编剧</credits><country>中国</country><studio>公司</studio>
<actor><name>演员甲</name><role>角色甲</role><thumb>https://example.invalid/a.jpg</thumb></actor>
<actor><role>没有姓名</role></actor><actor><name>演员乙</name></actor></movie>""")
        assertEquals("Original", meta.originalTitle)
        assertEquals("一句话", meta.tagline); assertEquals("PG", meta.certification)
        assertEquals(listOf("导演甲"), meta.directors); assertEquals(listOf("编剧"), meta.writers)
        assertEquals(listOf("中国"), meta.countries); assertEquals(listOf("公司"), meta.studios)
        assertEquals(listOf(CastMember("演员甲", "角色甲"), CastMember("演员乙")), meta.cast)
        assertEquals(meta, MetadataCodec.decode(MetadataCodec.encode(meta)))
    }
}
