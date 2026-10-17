package cn.awbz.davplayer.data

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class MediaPathsTest {
    private val connection = Connection("id", "name", "https://example.invalid/dav/", "", "")
    @Test fun preservesUnicodeSpacesPercentHashAndPlus() {
        val root = MediaPaths.base(connection.baseUrl)
        val url = MediaPaths.child(root, "中文 空格%#?&+", true)
        assertEquals("中文 空格%#?&+", MediaPaths.segments(MediaPaths.fromPath(connection, url.rawPath))[1])
        assertTrue(MediaPaths.inside(root, url))
    }
    @Test fun rejectsOtherOriginsAndEncodedSeparators() {
        val root = MediaPaths.base(connection.baseUrl)
        listOf("https://other.invalid/dav/a", "https://example.invalid/dav-other/a", "https://example.invalid/dav/a?secret=x",
            "https://example.invalid/dav/a%2Fb", "https://example.invalid/dav/%2e%2e/", "https://example.invalid/dav/a%5Cb").forEach {
            assertFalse(it, MediaPaths.inside(root, URI(it)))
        }
        assertTrue(MediaPaths.inside(root, URI("https://EXAMPLE.invalid:443/dav/a")))
    }
    @Test(expected = MediaFailure::class) fun rejectsTraversalBeforeResolving() { MediaPaths.fromPath(connection, "/dav/../private/a") }
    @Test(expected = MediaFailure::class) fun rejectsCredentialsInSourceUrl() { MediaPaths.base("https://user:secret@example.invalid/dav/") }
    @Test fun parentStopsAtConfiguredRoot() {
        assertEquals("/dav/", MediaPaths.parent(connection, "/dav/a/"))
        assertEquals("/dav/", MediaPaths.parent(connection, "/dav/"))
    }
    @Test fun sortsNumbersWithoutOverflow() {
        val names = listOf("第10集.mkv", "第2集.mkv", "第01集.mkv", "第99999999999999999999集.mkv")
        assertEquals(listOf("第01集.mkv", "第2集.mkv", "第10集.mkv", "第99999999999999999999集.mkv"), names.sortedWith(NaturalOrder))
    }
}
