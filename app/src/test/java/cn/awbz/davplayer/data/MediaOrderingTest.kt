package cn.awbz.davplayer.data

import org.junit.Assert.*
import org.junit.Test

class MediaOrderingTest {
    @Test fun episodesExcludeDotFilesAndKeepNaturalFileNameOrder() {
        val files = listOf("._第1集.mp4", ".hidden.mkv", "第10集.mkv", "第2集.mp4", "第01集.mp4", "poster.jpg")
            .map { MediaEntry("/dav/work/$it", it, false) }
        assertEquals(listOf("第01集.mp4", "第2集.mp4", "第10集.mkv"), MediaRules.episodes(files).map { it.name })
    }
    @Test fun worksSortNewestFirstThenNamesWithUnknownTimesLastWithoutExtraDirectoryCalls() {
        val calls = mutableListOf<String>()
        val catalog = object : MediaCatalog {
            override fun list(connection: Connection, path: String, requests: RequestScope): List<MediaEntry> {
                calls.add(path)
                return listOf(MediaEntry("/dav/unknown10/", "unknown10", true), MediaEntry("/dav/older/", "older", true, 1),
                    MediaEntry("/dav/new10/", "new10", true, 2), MediaEntry("/dav/new2/", "new2", true, 2),
                    MediaEntry("/dav/unknown2/", "unknown2", true), MediaEntry("/dav/not-work.mp4", "not-work.mp4", false, 3))
            }
        }
        val connection = Connection("fixture", "fixture", "https://example.invalid/dav/", "", "")
        val works = Library(catalog).works(connection, Category("fixture", "fixture", connection.id, "/dav/"), RequestScope())
        assertEquals(listOf("new2", "new10", "older", "unknown2", "unknown10"), works.map { it.name })
        assertEquals(listOf("/dav/"), calls)
    }
    @Test fun hiddenFilesAndFoldersAreRemovedAtTheSharedDirectoryBoundary() {
        val calls = mutableListOf<String>()
        val catalog = object : MediaCatalog {
            override fun list(connection: Connection, path: String, requests: RequestScope): List<MediaEntry> {
                calls.add(path)
                return if (path == "/dav/") listOf(MediaEntry("/dav/.hidden/", ".hidden", true),
                    MediaEntry("/dav/.DS_Store", ".DS_Store", false), MediaEntry("/dav/._01.mp4", "._01.mp4", false),
                    MediaEntry("/dav/.metadata.nfo", ".metadata.nfo", false), MediaEntry("/dav/season/", "season", true),
                    MediaEntry("/dav/01.mp4", "01.mp4", false)) else listOf(MediaEntry(path + "02.mp4", "02.mp4", false))
            }
        }
        val c = Connection("fixture", "fixture", "https://example.invalid/dav/", "", "")
        val library = Library(catalog)
        assertEquals(listOf("season", "01.mp4"), library.list(c, "/dav/", RequestScope()).map { it.name })
        assertEquals(listOf("season"), library.works(c, Category("fixture", "fixture", c.id, "/dav/"), RequestScope()).map { it.name })
        assertEquals(listOf("01.mp4", "02.mp4"), library.detail(c, Work(c.id, "/dav/", "fixture"), RequestScope()).episodes.map { it.name })
        assertFalse(calls.contains("/dav/.hidden/"))
    }
    @Test fun timestampsHandleOffsetsFractionsAndCreationFallback() {
        val utc = SourceTime.parse("2026-10-17T04:00:00.123456789Z")
        assertNotNull(utc)
        assertEquals(utc, SourceTime.parse("2026-10-17T12:00:00.123+08:00"))
        assertEquals(utc, SourceTime.parse("2026-10-17T12:00:00.123+0800"))
        assertEquals(SourceTime.parse("2026-10-17"), SourceTime.modified("invalid", "2026-10-17"))
        assertNull(SourceTime.modified("2026-02-30T12:00:00Z", null))
        assertNull(SourceTime.modified(null, null))
    }
}
