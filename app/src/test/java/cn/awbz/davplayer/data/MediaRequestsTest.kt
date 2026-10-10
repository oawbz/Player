package cn.awbz.davplayer.data

import okhttp3.Credentials
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class MediaRequestsTest {
    @Test fun followsSignedVideoRedirectPreservingRangeAndSameOriginAuth() {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/download/video.mp4?sign=fixture"))
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 10-11/12").setBody("ok"))
            val connection = Connection("test", "test", server.url("/dav/").toString(), "fixture", "fixture")
            MediaRequests.open(DavClient(), connection, server.url("/dav/video.mp4"), "bytes=10-").use {
                assertEquals(206, it.code()); assertEquals("ok", it.body()!!.string())
            }
            val first = server.takeRequest(); val next = server.takeRequest()
            assertEquals(Credentials.basic("fixture", "fixture"), next.getHeader("Authorization"))
            assertEquals(first.getHeader("Range"), next.getHeader("Range"))
            assertEquals("/download/video.mp4?sign=fixture", next.path)
        } finally { server.shutdown() }
    }
    @Test fun crossOriginDropsAuthAndNeverRestoresIt() {
        val dav = MockWebServer(); val cdn = MockWebServer(); dav.start(); cdn.start()
        try {
            dav.enqueue(MockResponse().setResponseCode(302).setHeader("Location", cdn.url("/video?sign=fixture")))
            cdn.enqueue(MockResponse().setResponseCode(307).setHeader("Location", dav.url("/download")))
            dav.enqueue(MockResponse().setBody("ok"))
            val connection = Connection("test", "test", dav.url("/dav/").toString(), "fixture", "fixture")
            MediaRequests.open(DavClient(), connection, dav.url("/dav/video"), "bytes=0-0").use { assertEquals(200, it.code()) }
            assertNotNull(dav.takeRequest().getHeader("Authorization"))
            val cross = cdn.takeRequest(); assertNull(cross.getHeader("Authorization")); assertEquals("bytes=0-0", cross.getHeader("Range"))
            assertNull(dav.takeRequest().getHeader("Authorization"))
        } finally { dav.shutdown(); cdn.shutdown() }
    }
    @Test fun rejectsRedirectLoopAndEmbeddedCredentialsWithoutSendingNextRequest() {
        val server = MockWebServer(); server.start()
        try {
            val connection = Connection("test", "test", server.url("/dav/").toString(), "", "")
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/dav/video"))
            try { MediaRequests.open(DavClient(), connection, server.url("/dav/video"), null).close(); fail() } catch (_: DavFailure) {}
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://fixture:fixture@localhost/video"))
            try { MediaRequests.open(DavClient(), connection, server.url("/dav/video"), null).close(); fail() } catch (_: DavFailure) {}
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }
}
