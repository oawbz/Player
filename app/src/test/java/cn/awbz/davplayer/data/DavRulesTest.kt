package cn.awbz.davplayer.data

import okhttp3.HttpUrl
import org.junit.Assert.*
import org.junit.Test

class DavRulesTest {
    @Test fun sortsEpisodeNumbersWithoutOverflow() {
        val names = listOf("第10集.mkv", "第2集.mkv", "第01集.mkv", "第99999999999999999999集.mkv")
        assertEquals(listOf("第01集.mkv", "第2集.mkv", "第10集.mkv", "第99999999999999999999集.mkv"), names.sortedWith(NaturalOrder))
        assertTrue(NaturalOrder.compare("第0集", "第1集") < 0)
        assertEquals(0, NaturalOrder.compare("Season 01", "Season 01"))
    }
    @Test fun preservesUnicodeSpacesAndLiteralPercentHash() {
        val base = DavUrls.base("https://example.invalid/dav/")
        val url = base.newBuilder().addPathSegment("中文 空格%#?&").addPathSegment("").build()
        val connection = Connection("test", "测试", base.toString(), "", "")
        assertEquals("中文 空格%#?&", DavUrls.fromPath(connection, url.encodedPath()).pathSegments()[1])
        assertTrue(DavUrls.directChild(base, url))
    }
    @Test fun enforcesOriginRootAndDirectDepth() {
        val root = DavUrls.base("https://example.invalid/dav/")
        fun url(s: String) = HttpUrl.parse(s)!!
        assertFalse(DavUrls.inside(root, url("https://other.invalid/dav/a")))
        assertFalse(DavUrls.inside(root, url("https://example.invalid/dav-other/a")))
        assertFalse(DavUrls.inside(root, url("https://example.invalid/dav/a?token=secret")))
        assertFalse(DavUrls.inside(root, url("https://example.invalid/dav/a%2Fb")))
        assertFalse(DavUrls.directChild(root, url("https://example.invalid/dav/a/b")))
        assertTrue(DavUrls.directChild(root, url("https://example.invalid/dav/a/")))
    }
    @Test fun rejectsEmbeddedCredentialsAndSignedUrls() {
        listOf("https://user:pass@example.invalid/dav", "https://example.invalid/dav?token=example", "file:///dav").forEach {
            try { DavUrls.base(it); fail("must reject") } catch (_: DavFailure) {}
        }
    }
    @Test fun coverPriorityAndVideoRecognitionAreDeterministic() {
        val root = DavUrls.base("https://example.invalid/dav/")
        fun file(name: String) = DavEntry(root.newBuilder().addPathSegment(name).build(), name, false)
        val files = listOf(file("z.jpg"), file("cover.png"), file("POSTER.JPG"), file("第10集.MKV"), file("第2集.mp4"), file("字幕.srt"))
        assertEquals("POSTER.JPG", MediaRules.cover(files)?.name)
        assertEquals(listOf("第2集.mp4", "第10集.MKV"), MediaRules.episodes(files).map { it.name })
    }
    @Test fun parsesSuccessfulPropstatsWithNamespacesAndEscapedNames() {
        val xml = """<x:multistatus xmlns:x="DAV:">
            <x:response><x:href>/dav/</x:href><x:propstat><x:prop><x:resourcetype><x:collection/></x:resourcetype></x:prop><x:status>HTTP/1.1 200 OK</x:status></x:propstat></x:response>
            <x:response><x:href>/dav/中文%20%26%23/</x:href><x:propstat><x:prop><x:resourcetype><x:collection/></x:resourcetype></x:prop><x:status>HTTP/1.1 200 OK</x:status></x:propstat></x:response>
            <x:response><x:href>/dav/ignored/</x:href><x:propstat><x:prop><x:resourcetype><x:collection/></x:resourcetype></x:prop><x:status>HTTP/1.1 403 Forbidden</x:status></x:propstat></x:response>
            <x:response><x:href>/dav/a.mp4</x:href><x:propstat><x:prop><x:resourcetype/></x:prop><x:status>HTTP/2 200 OK</x:status></x:propstat></x:response>
            <x:response><x:href>https://other.invalid/dav/escape/</x:href><x:propstat><x:prop><x:resourcetype/></x:prop><x:status>HTTP/1.1 200 OK</x:status></x:propstat></x:response>
        </x:multistatus>"""
        val entries = DavXml.parse(xml, DavUrls.base("https://example.invalid/dav/"))
        assertEquals(2, entries.size)
        assertEquals("中文 &#", entries.first { it.directory }.name)
        assertEquals("a.mp4", entries.first { !it.directory }.name)
    }
    @Test fun rejectsDtdMalformedAndNonDavResponses() {
        listOf("<!DOCTYPE x [<!ENTITY external SYSTEM 'file:///test'>]><x/>", "<d:multistatus xmlns:d='DAV:'><broken>", "<html/>").forEach {
            try { DavXml.parse(it, DavUrls.base("https://example.invalid/")); fail("must reject") } catch (_: DavFailure) {}
        }
    }
}
