package cn.awbz.davplayer.data

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SourceHttpTest {
    @Test fun metadataReusesConnectionWithoutSendingApiCredentials() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("one")); server.enqueue(MockResponse().setBody("two"))
            val c = Connection("test", "test", server.url("/").toString(), "user", "secret")
            val http = ResourceHttp { _, p, _ -> server.url(p).toString() }
            assertEquals("one", http.get(c, "/poster.jpg", 100, RequestScope()).data.toString(Charsets.UTF_8))
            assertEquals("two", http.get(c, "/poster.jpg", 100, RequestScope()).data.toString(Charsets.UTF_8))
            val a = server.takeRequest(); val b = server.takeRequest()
            assertEquals(0, a.sequenceNumber); assertEquals(1, b.sequenceNumber)
            assertNull(a.getHeader("Authorization")); assertNull(b.getHeader("Authorization"))
            assertNotEquals("close", a.getHeader("Connection"))
        }
    }
    @Test fun activeCallCancellationActuallyExits() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("late").setBodyDelay(1, TimeUnit.SECONDS))
            val scope = RequestScope(); val worker = Executors.newSingleThreadExecutor()
            try {
                val task = worker.submit<Boolean> {
                    try { SourceHttp.execute(Request.Builder().url(server.url("/")).build(), 100, scope); false }
                    catch (_: Exception) { true }
                }
                assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
                scope.cancel()
                assertTrue(task.get(2, TimeUnit.SECONDS))
                assertEquals(1, server.requestCount)
            } finally { scope.cancel(); worker.shutdownNow() }
        }
    }
    @Test fun rateLimitedResponseIsReturnedOnceWithoutImplicitRetry() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "60"))
            val response = SourceHttp.execute(Request.Builder().url(server.url("/")).build(), 100, RequestScope())
            assertEquals(503, response.status); assertEquals(60L, response.retryAfterSeconds)
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun apiAndMetadataShareTransportButNotAuthentication() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"code\":200,\"data\":{\"token\":\"api-token\"}}"))
            server.enqueue(MockResponse().setBody("{\"code\":200,\"data\":{\"raw_url\":\"${server.url("/poster.jpg")}\"}}"))
            server.enqueue(MockResponse().setBody("image"))
            val c = Connection("test", "test", server.url("/").toString(), "user", "secret")
            assertEquals("image", ResourceHttp().get(c, "/poster.jpg", 100, RequestScope()).data.toString(Charsets.UTF_8))
            val login = server.takeRequest(); val api = server.takeRequest(); val download = server.takeRequest()
            assertNull(login.getHeader("Authorization")); assertEquals("api-token", api.getHeader("Authorization"))
            assertNull(download.getHeader("Authorization"))
            assertEquals(0, login.sequenceNumber); assertEquals(1, api.sequenceNumber); assertEquals(2, download.sequenceNumber)
        }
    }
}
