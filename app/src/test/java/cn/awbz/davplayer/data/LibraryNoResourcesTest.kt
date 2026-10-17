package cn.awbz.davplayer.data

import org.junit.Assert.*
import org.junit.Test

class LibraryNoResourcesTest {
    @Test fun artworkAndNfoNeverBecomeResourceRequests() {
        val calls = mutableListOf<String>()
        val catalog = object : MediaCatalog {
            override fun list(connection: Connection, path: String, requests: RequestScope): List<MediaEntry> {
                calls.add(path)
                return listOf(MediaEntry("/dav/work/movie.nfo", "movie.nfo", false),
                    MediaEntry("/dav/work/poster.jpg", "poster.jpg", false),
                    MediaEntry("/dav/work/01.mp4", "01.mp4", false))
            }
        }
        val connection = Connection("fixture", "fixture", "https://example.invalid/dav/", "", "")
        val result = Library(catalog).detail(connection, Work("fixture", "/dav/work/", "work", "/dav/work/poster.jpg"), RequestScope())
        assertEquals(listOf("/dav/work/"), calls)
        assertEquals(1, result.episodes.size)
        assertNull(result.work.coverPath)
        assertEquals("", result.description)
        assertEquals("", result.metadata.plot)
        assertEquals(DetailAssets("/dav/work/movie.nfo", "/dav/work/poster.jpg"), result.assets)
    }
    @Test fun detailCanFetchFixedPosterEvenWhenListingDoesNotContainIt() {
        val catalog = object : MediaCatalog {
            override fun list(connection: Connection, path: String, requests: RequestScope) =
                listOf(MediaEntry("/dav/work/01.mp4", "01.mp4", false))
        }
        val connection = Connection("fixture", "fixture", "https://example.invalid/dav/", "", "")
        val result = Library(catalog).detail(connection, Work("fixture", "/dav/work/", "work"), RequestScope())
        assertEquals("/dav/work/poster.jpg", result.assets.cover)
        assertNull(result.assets.nfo)
        assertNull(result.work.coverPath)
    }
}
