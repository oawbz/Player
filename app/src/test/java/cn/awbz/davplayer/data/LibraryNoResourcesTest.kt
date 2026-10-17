package cn.awbz.davplayer.data

import org.junit.Assert.*
import org.junit.Test

class LibraryNoResourcesTest {
    @Test fun worksListsOnlyTheCategoryDirectory() {
        val calls = mutableListOf<String>()
        val catalog = object : MediaCatalog {
            override fun list(connection: Connection, path: String, requests: RequestScope): List<MediaEntry> {
                calls.add(path)
                return listOf(MediaEntry("/category/work/", "work", true))
            }
        }
        val c = Connection("fixture", "fixture", "https://example.invalid/", "", "")
        val works = Library(catalog).works(c, Category("cat", "cat", c.id, "/category/"), RequestScope())
        assertEquals(listOf("work"), works.map { it.name })
        assertEquals(listOf("/category/"), calls)
    }
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
        assertEquals("/dav/work/movie.nfo", result.assets.nfo)
        assertNull(result.work.coverPath)
    }
    @Test fun seasonsUseOwnAssetsAndFallbackIndependentlyWithoutExtraLists() {
        val calls = mutableListOf<String>()
        val catalog = object : MediaCatalog {
            override fun list(connection: Connection, path: String, requests: RequestScope): List<MediaEntry> {
                calls.add(path)
                return when (path) {
                    "/work/" -> listOf(MediaEntry("/work/S1/", "S1", true),
                        MediaEntry("/work/S2/", "S2", true), MediaEntry("/work/S3/", "S3", true),
                        MediaEntry("/work/01.mp4", "01.mp4", false))
                    "/work/S1/" -> listOf(MediaEntry(path + "01.mp4", "01.mp4", false),
                        MediaEntry(path + "movie.nfo", "movie.nfo", false),
                        MediaEntry(path + "poster.jpg", "poster.jpg", false))
                    "/work/S2/" -> listOf(MediaEntry(path + "01.mp4", "01.mp4", false),
                        MediaEntry(path + "poster.jpg", "poster.jpg", false))
                    else -> listOf(MediaEntry(path + "01.mp4", "01.mp4", false),
                        MediaEntry(path + "movie.nfo", "movie.nfo", true))
                }
            }
        }
        val c = Connection("fixture", "fixture", "https://example.invalid/", "", "")
        val result = Library(catalog).detail(c, Work(c.id, "/work/", "work"), RequestScope())
        assertEquals(listOf("/work/", "/work/S1/", "/work/S2/", "/work/S3/"), calls)
        assertEquals(DetailAssets("/work/movie.nfo", "/work/poster.jpg"), result.assets)
        assertNull(result.seasons[0].assets) // Direct episodes keep root artwork.
        assertEquals(DetailAssets("/work/S1/movie.nfo", "/work/S1/poster.jpg"), result.seasons[1].assets)
        assertEquals(DetailAssets("/work/movie.nfo", "/work/S2/poster.jpg"), result.seasons[2].assets)
        assertEquals(result.assets, result.seasons[3].assets)
        assertEquals(4, result.episodes.size)
        val playable = Library(catalog)
        playable.handoff(c, result)
        assertEquals(result, playable.takePlaybackDetail(c, result.work, result.seasons[1].episodes[0]))
    }
}
