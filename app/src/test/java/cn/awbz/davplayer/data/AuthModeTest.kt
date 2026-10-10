package cn.awbz.davplayer.data

import okhttp3.Credentials
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AuthModeTest {
    @Test fun migratesLegacyConnectionsAndHonorsExplicitAnonymous() {
        assertEquals(AuthMode.ANONYMOUS, AuthMode.stored(null, "", ""))
        assertEquals(AuthMode.BASIC, AuthMode.stored(null, "fixture", ""))
        assertEquals(AuthMode.BASIC, AuthMode.stored(null, "", "fixture"))
        assertEquals(AuthMode.ANONYMOUS, AuthMode.stored("ANONYMOUS", "fixture", "fixture"))
    }
    @Test fun anonymousNeverSendsRetainedCredentials() {
        val server = MockWebServer(); server.start()
        try {
            val c = Connection("fixture", "fixture", server.url("/").toString(), "fixture-user", "fixture-password", AuthMode.ANONYMOUS)
            val dav = DavClient()
            assertTrue(dav.headers(c).isEmpty())
            server.enqueue(MockResponse().setResponseCode(207).setBody("<d:multistatus xmlns:d='DAV:'/>"))
            server.enqueue(MockResponse().setBody("fixture"))
            dav.list(c, "/", Requests()); dav.read(c, "/cover.jpg", Requests(), 1024)
            assertNull(server.takeRequest().getHeader("Authorization"))
            assertNull(server.takeRequest().getHeader("Authorization"))
            assertFalse(c.toString().contains("fixture-password"))
        } finally { server.shutdown() }
    }
    @Test fun basicAllowsAnEmptyPassword() {
        val c = Connection("fixture", "fixture", "https://example.invalid/", "fixture-user", "", AuthMode.BASIC)
        assertEquals(Credentials.basic("fixture-user", ""), DavClient().headers(c)["Authorization"])
    }
}
