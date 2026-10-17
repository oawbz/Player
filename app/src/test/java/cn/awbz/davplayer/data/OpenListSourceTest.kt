package cn.awbz.davplayer.data

import cn.awbz.davplayer.data.source.OpenListSource
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OpenListSourceTest {
    private fun reply(data: String, code: Int = 200) = MockResponse().setBody("{\"code\":$code,\"data\":$data}")
    @Test fun loginTokenRefreshAndSignedUnicodeDownload() {
        MockWebServer().use { server ->
            server.enqueue(reply("{\"token\":\"first\"}"))
            server.enqueue(reply("null", 401))
            server.enqueue(reply("{\"token\":\"second\"}"))
            server.enqueue(reply("{\"raw_url\":\"https://example.com/电影/100%25.mkv?sign=a%2Bb%3Ac\"}"))
            val c = Connection("test", "test", server.url("/openlist/").toString(), "user", "secret")
            val url = OpenListSource.resolve(c, "/openlist/电影/100%25.mkv", RequestScope())
            assertTrue(url.contains("https://example.com/%E7%94%B5%E5%BD%B1/100%25.mkv?sign=a%2Bb%3Ac"))
            val login = server.takeRequest(); assertEquals("/openlist/api/auth/login", login.path)
            assertNull(login.getHeader("Authorization"))
            val first = server.takeRequest(); assertEquals("first", first.getHeader("Authorization"))
            assertEquals("/电影/100%.mkv", JSONObject(first.body.readUtf8()).getString("path"))
            server.takeRequest()
            assertEquals("second", server.takeRequest().getHeader("Authorization"))
        }
    }
    @Test fun paginatedDirectoryUsesStableEncodedPaths() {
        MockWebServer().use { server ->
            val items = (1..100).joinToString(",") { "{\"name\":\"$it\",\"is_dir\":true}" }
            server.enqueue(reply("{\"content\":[$items],\"total\":101}"))
            server.enqueue(reply("{\"content\":[{\"name\":\"电影 101\",\"is_dir\":true}],\"total\":101}"))
            val c = Connection("test", "test", server.url("/").toString(), "", "")
            val rows = OpenListSource.list(c, "/", RequestScope())
            assertEquals(101, rows.size)
            assertEquals("/%E7%94%B5%E5%BD%B1%20101/", rows.last().path)
            server.takeRequest()
            assertEquals(2, JSONObject(server.takeRequest().body.readUtf8()).getInt("page"))
        }
    }
    @Test fun cancelledScopeNeverSendsRequest() {
        MockWebServer().use { server ->
            val c = Connection("test", "test", server.url("/").toString(), "", "")
            try { OpenListSource.list(c, "/", RequestScope().apply { cancel() }); fail() } catch (_: MediaFailure) { }
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun concurrentFirstCallsLoginOnlyOnce() {
        MockWebServer().use { server ->
            val logins = java.util.concurrent.atomic.AtomicInteger()
            server.setDispatcher(object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse =
                    if (request.path == "/api/auth/login") {
                        logins.incrementAndGet()
                        reply("{\"token\":\"shared\"}").setBodyDelay(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                    } else reply("{\"content\":[],\"total\":0}")
            })
            val c = Connection("test", "test", server.url("/").toString(), "user", "secret")
            val pool = java.util.concurrent.Executors.newFixedThreadPool(3)
            try {
                val futures = (1..3).map { pool.submit<List<MediaEntry>> { OpenListSource.list(c, "/", RequestScope()) } }
                futures.forEach { assertTrue(it.get(5, java.util.concurrent.TimeUnit.SECONDS).isEmpty()) }
                assertEquals(1, logins.get())
                assertEquals(4, server.requestCount)
            } finally { pool.shutdownNow() }
        }
    }
}
