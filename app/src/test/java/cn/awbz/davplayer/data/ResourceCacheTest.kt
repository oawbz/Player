package cn.awbz.davplayer.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResourceCacheTest {
    @get:Rule val temp = TemporaryFolder()
    private val connection = Connection("fixture", "fixture", "https://example.invalid/dav/", "", "")
    @Test fun parsedMetadataAndValidatorsSurviveReopenWithoutUtfLengthLimit() {
        val root = temp.newFolder(); val meta = MediaMetadata(plot = "简介".repeat(40000), year = "2026", rating = 8.5, genres = listOf("剧情"))
        val entry = ResourceCache.Entry(MetadataCodec.encode(meta), "etag-v1", "yesterday", 123L)
        ResourceCache(root).write(connection, "/dav/movie.nfo", entry)
        val restored = ResourceCache(root).read(connection, "/dav/movie.nfo", 2 * Nfo.LIMIT)!!
        assertEquals(meta, MetadataCodec.decode(restored.data)); assertEquals("etag-v1", restored.etag); assertEquals(123L, restored.checkedAt)
        assertNull(ResourceCache(root).read(connection.copy(password = "changed"), "/dav/movie.nfo", 2 * Nfo.LIMIT))
    }
    @Test fun explicitlyDownloadedLibrarySurvivesFormer64MiBLimitAndReopen() {
        val root = temp.newFolder(); val cache = ResourceCache(root)
        val bytes = ByteArray(1024 * 1024) { 42 }
        repeat(65) { cache.write(connection, "/dav/$it.jpg", ResourceCache.Entry(bytes, "", "", 1)) }
        val reopened = ResourceCache(root)
        repeat(65) { assertArrayEquals(bytes, reopened.read(connection, "/dav/$it.jpg", bytes.size)!!.data) }
    }
    @Test fun truncatedFilesAreIgnored() {
        val root = temp.newFolder(); val cache = ResourceCache(root)
        cache.write(connection, "/dav/poster.jpg", ResourceCache.Entry(byteArrayOf(42), "", "", 1))
        root.listFiles()!!.single().writeBytes(byteArrayOf(1, 2))
        assertNull(cache.read(connection, "/dav/poster.jpg", 1024))
    }
    @Test(expected = java.io.IOException::class) fun unavailableStorageCannotReportSuccessfulWrite() {
        ResourceCache(temp.newFile()).write(connection, "/dav/poster.jpg", ResourceCache.Entry(byteArrayOf(42), "", "", 1))
    }
}
