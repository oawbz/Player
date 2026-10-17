package cn.awbz.davplayer.data

import org.junit.Assert.*
import org.junit.Test

class PosterFilesTest {
    private val connection = Connection("fixture", "fixture", "https://example.invalid/dav/", "", "")
    @Test fun directPosterPathPreservesEncodingAndRejectsOutOfRootPaths() {
        assertEquals("/dav/%E4%B8%8B%E6%B5%B7%20%231/poster.jpg", PosterFiles.path(connection, "/dav/%E4%B8%8B%E6%B5%B7%20%231/"))
        assertEquals("/dav/work/poster.jpg", PosterFiles.path(connection, "/dav/work"))
        try { PosterFiles.path(connection, "/elsewhere/work/"); fail() } catch (_: MediaFailure) { }
    }
    @Test fun detailEntryRetriesMissingArtButNeverRevalidatesExistingArt() {
        val oldImage = ResourceCache.Entry(byteArrayOf(1), "etag", "", 1L)
        val recentMiss = ResourceCache.Entry(ByteArray(0), "", "", 1_000_000L)
        assertFalse(PosterFiles.shouldFetch(oldImage, force = false))
        assertTrue(PosterFiles.shouldFetch(oldImage, force = true))
        assertTrue(PosterFiles.shouldFetch(recentMiss, force = false))
        assertTrue(PosterFiles.shouldFetch(null, force = false))
    }
}
