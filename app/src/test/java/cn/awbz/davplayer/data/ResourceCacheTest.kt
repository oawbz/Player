package cn.awbz.davplayer.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResourceCacheTest {
    @get:Rule val temp = TemporaryFolder()
    private val connection = Connection("fixture", "fixture", "https://example.invalid/dav/", "", "")
    @Test fun parsedMetadataSurvivesReopenWithoutUtfLengthLimit() {
        val root = temp.newFolder(); val meta = MediaMetadata(plot = "简介".repeat(40000), year = "2026", rating = 8.5, genres = listOf("剧情"))
        val entry = ResourceCache.Entry(MetadataCodec.encode(meta))
        ResourceCache(root).write(connection, "/dav/movie.nfo", entry)
        val restored = ResourceCache(root).read(connection, "/dav/movie.nfo", 2 * Nfo.LIMIT)!!
        assertEquals(meta, MetadataCodec.decode(restored.data))
        assertNull(ResourceCache(root).read(connection.copy(password = "changed"), "/dav/movie.nfo", 2 * Nfo.LIMIT))
    }
    @Test fun downloadedLibrarySurvivesReopen() {
        val root = temp.newFolder(); val cache = ResourceCache(root)
        val bytes = ByteArray(1024 * 1024) { 42 }
        repeat(65) { cache.write(connection, "/dav/$it.jpg", ResourceCache.Entry(bytes)) }
        val reopened = ResourceCache(root)
        repeat(65) { assertArrayEquals(bytes, reopened.read(connection, "/dav/$it.jpg", bytes.size)!!.data) }
    }
    @Test fun existingCacheHeaderRemainsReadableWithoutValidators() {
        val root = temp.newFolder(); val cache = ResourceCache(root)
        val path = "/poster.jpg"; val bytes = byteArrayOf(1, 2, 3)
        val file = java.io.File(root, cache.key(connection, path) + ".bin")
        java.io.DataOutputStream(file.outputStream()).use { out ->
            out.writeInt(1); out.writeLong(123)
            out.writeUTF("existing-etag"); out.writeUTF("existing-modified")
            out.writeInt(bytes.size); out.write(bytes)
        }
        assertArrayEquals(bytes, cache.read(connection, path, 100)!!.data)
        cache.write(connection, path, ResourceCache.Entry(bytes))
        assertArrayEquals(bytes, ResourceCache(root).read(connection, path, 100)!!.data)
    }
    @Test fun truncatedFilesAreIgnored() {
        val root = temp.newFolder(); val cache = ResourceCache(root)
        cache.write(connection, "/dav/poster.jpg", ResourceCache.Entry(byteArrayOf(42)))
        root.listFiles()!!.single().writeBytes(byteArrayOf(1, 2))
        assertNull(cache.read(connection, "/dav/poster.jpg", 1024))
    }
    @Test(expected = java.io.IOException::class) fun unavailableStorageCannotReportSuccessfulWrite() {
        ResourceCache(temp.newFile()).write(connection, "/dav/poster.jpg", ResourceCache.Entry(byteArrayOf(42)))
    }
}
