package cn.awbz.davplayer.data

import org.junit.Assert.*
import org.junit.Test

class LibraryHandoffTest {
    private val c = Connection("fixture", "fixture", "https://example.invalid/", "", "")
    private val work = Work(c.id, "/work/", "work")
    private val episode = Episode("/work/01.mp4", "01.mp4")
    @Test fun playbackConsumesRecentDetailWithoutAnotherScan() {
        var count = 0
        val library = Library(object : MediaCatalog {
            override fun list(connection: Connection, path: String, requests: RequestScope): List<MediaEntry> {
                count++
                return listOf(MediaEntry(episode.path, episode.name, false))
            }
        })
        val scope = RequestScope()
        val detail = library.detail(c, work, scope)
        library.list(c, work.path, scope)
        assertEquals(1, count)
        assertSame(detail, library.takePlaybackDetail(c, work, episode))
        assertNull(library.takePlaybackDetail(c, work, episode))
        assertEquals(1, count)
        library.detail(c, work, RequestScope())
        assertEquals(2, count)
    }
    @Test fun retainedDetailCanBeHandedOffAgainAfterLongPlaybackWithoutDirectoryReads() {
        var now = 0L
        var reads = 0
        val library = Library(object : MediaCatalog {
            override fun list(connection: Connection, path: String, requests: RequestScope): List<MediaEntry> {
                reads++; return listOf(MediaEntry(episode.path, episode.name, false))
            }
        }, clock = { now })
        val detail = library.detail(c, work, RequestScope())
        assertSame(detail, library.takePlaybackDetail(c, work, episode))
        now = 1_000_000
        library.handoff(c, detail)
        assertSame(detail, library.takePlaybackDetail(c, work, episode))
        assertNull(library.takePlaybackDetail(c, work, episode))
        assertEquals(1, reads)
    }
    @Test fun expiredOrChangedAccountHandoffsAreNeverUsed() {
        var now = 0L
        val library = Library(object : MediaCatalog {
            override fun list(connection: Connection, path: String, requests: RequestScope) = listOf(MediaEntry(episode.path, episode.name, false))
        }, clock = { now })
        library.detail(c, work, RequestScope())
        assertNull(library.takePlaybackDetail(c.copy(password = "changed"), work, episode))
        now = 120_001
        assertNull(library.takePlaybackDetail(c, work, episode))
    }
}
