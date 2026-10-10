package cn.awbz.davplayer.data

import okhttp3.Credentials
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class DavClientTest {
    private fun multistatus(vararg items: Pair<String, Boolean>): String = "<d:multistatus xmlns:d='DAV:'>" + items.joinToString("") { (href, folder) ->
        "<d:response><d:href>$href</d:href><d:propstat><d:prop><d:resourcetype>${if (folder) "<d:collection/>" else ""}</d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
    } + "</d:multistatus>"
    @Test fun authenticatedDirectoryAndNfoRequestsUseSameHeaders() {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(207).setBody(multistatus("/dav/作品/" to true)))
            server.enqueue(MockResponse().setBody("简介\n第二段"))
            val connection = Connection("fixture", "fixture", server.url("/dav/").toString(), "fixture-user", "fixture-password")
            val dav = DavClient(); val scope = Requests()
            assertEquals("作品", dav.list(connection, "/dav/", scope).single().name)
            assertEquals("简介\n第二段", String(dav.read(connection, "/dav/movie.nfo", scope, 65536), Charsets.UTF_8))
            val listing = server.takeRequest(); val description = server.takeRequest()
            assertEquals("PROPFIND", listing.method); assertEquals("1", listing.getHeader("Depth"))
            assertEquals(Credentials.basic("fixture-user", "fixture-password"), listing.getHeader("Authorization"))
            assertEquals(listing.getHeader("Authorization"), description.getHeader("Authorization"))
        } finally { server.shutdown() }
    }
    @Test fun blocksRedirectsAndBoundedContent() {
        val server = MockWebServer(); server.start()
        try {
            val connection = Connection("fixture", "fixture", server.url("/dav/").toString(), "", "")
            val dav = DavClient()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://other.invalid/file"))
            try { dav.read(connection, "/dav/file", Requests(), 16); fail("must reject redirect") } catch (_: DavFailure) {}
            server.enqueue(MockResponse().setBody("this content is too large"))
            try { dav.read(connection, "/dav/file", Requests(), 4); fail("must enforce limit") } catch (_: DavFailure) {}
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }
    @Test fun findsDirectVideosSeasonsAndDescriptionWithoutDeepTraversal() {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(207).setBody(multistatus("/dav/作品/第10集.mp4" to false,
                "/dav/作品/第2集.mp4" to false, "/dav/作品/第2季/" to true, "/dav/作品/图片/" to true,
                "/dav/作品/movie.nfo" to false, "/dav/作品/cover.jpg" to false)))
            server.enqueue(MockResponse().setResponseCode(207).setBody(multistatus("/dav/作品/图片/extra.png" to false)))
            server.enqueue(MockResponse().setResponseCode(207).setBody(multistatus("/dav/作品/第2季/第1集.mkv" to false, "/dav/作品/第2季/更深层/" to true)))
            server.enqueue(MockResponse().setBody("\uFEFF<movie><plot>作品简介\n保留换行</plot><year>2026</year></movie>"))
            val connection = Connection("fixture", "fixture", server.url("/dav/").toString(), "", "")
            val detail = Library(DavClient()).detail(connection, Work("fixture", "/dav/作品/", "作品"), Requests())
            assertEquals(listOf("未分季", "第2季"), detail.seasons.map { it.name })
            assertEquals(listOf("第2集.mp4", "第10集.mp4"), detail.seasons[0].episodes.map { it.name })
            assertEquals("作品简介\n保留换行", detail.description)
            assertEquals(4, server.requestCount)
        } finally { server.shutdown() }
    }
    @Test fun cancelledScopeNeverStartsARequest() {
        val scope = Requests(); scope.cancel()
        val connection = Connection("fixture", "fixture", "https://example.invalid/dav/", "", "")
        try { DavClient().list(connection, "/dav/", scope); fail("must cancel") } catch (_: DavFailure) {}
    }
}
