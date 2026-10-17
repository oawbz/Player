package cn.awbz.davplayer.data

import android.graphics.Bitmap
import android.util.LruCache
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class DetailPosterReuseTest {
    @get:Rule val temp = TemporaryFolder()
    private val connection = Connection("fixture", "fixture", "https://example.invalid/", "", "")
    private val path = "/work/poster.jpg"
    private class Fixture(val resources: DetailResources, val server: MockWebServer, val disk: ResourceCache,
        val decoded: MutableList<Pair<String, Int>>, val images: MutableMap<Pair<String, Int>, Bitmap>) {
        fun refresh(force: Boolean = false): DetailResources.Snapshot {
            var result: DetailResources.Snapshot? = null
            resources.refresh(Connection("fixture", "fixture", "https://example.invalid/", "", ""),
                DetailAssets(null, "/work/poster.jpg"), force, RequestScope()) { result = it }.get(3, TimeUnit.SECONDS)
            return result!!
        }
        fun poster(): Bitmap? {
            var result: Bitmap? = null
            resources.poster(Connection("fixture", "fixture", "https://example.invalid/", "", ""),
                "/work/poster.jpg", RequestScope()) { result = it }.get(3, TimeUnit.SECONDS)
            return result
        }
    }
    private fun fixture(test: (Fixture) -> Unit) {
        val serial = Executors.newSingleThreadExecutor()
        MockWebServer().use { server ->
            val root = temp.newFolder()
            @Suppress("UNCHECKED_CAST")
            val memory = mock(LruCache::class.java) as LruCache<String, Bitmap>
            val entries = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
            `when`(memory.get(anyString())).thenAnswer { entries[it.getArgument(0)] }
            `when`(memory.put(anyString(), any(Bitmap::class.java))).thenAnswer { entries.put(it.getArgument(0), it.getArgument(1)) }
            `when`(memory.remove(anyString())).thenAnswer { entries.remove(it.getArgument(0)) }
            val decoded = mutableListOf<Pair<String, Int>>()
            val images = mutableMapOf<Pair<String, Int>, Bitmap>()
            val resources = DetailResources(root, Executor { it.run() }, memory, { bytes, size ->
                val key = bytes.toString(Charsets.UTF_8) to size
                decoded.add(key)
                images.getOrPut(key) { mock(Bitmap::class.java) }
            }, ResourceHttp { _, _, _ -> server.url("/poster.jpg").toString() }, serial)
            try { test(Fixture(resources, server, ResourceCache(File(root, "openlist-resources-v1")), decoded, images)) }
            finally { serial.shutdownNow() }
        }
    }
    @Test fun detailDownloadPublishesListThumbnailBeforeReturningWithoutAnotherReadOrDecode() = fixture { f ->
        f.server.enqueue(MockResponse().setBody("first"))
        val detail = f.refresh()
        val thumbnail = f.images["first" to 480]!!
        assertSame(f.images["first" to 1600], detail.cover)
        assertSame(thumbnail, f.resources.memoryPoster(connection, path))
        assertEquals(listOf("first" to 480, "first" to 1600), f.decoded)
        // Removing the disk entry proves the returning list consumes the published bitmap itself.
        f.disk.remove(connection, path)
        assertSame(thumbnail, f.poster())
        assertEquals(2, f.decoded.size)
        assertEquals(1, f.server.requestCount)
    }
    @Test fun detailDiskHitAlsoWarmsListWithoutFetchingAgain() = fixture { f ->
        f.disk.write(connection, path, ResourceCache.Entry("cached".toByteArray()))
        f.resources.cached(connection, DetailAssets(null, path))
        val thumbnail = f.images["cached" to 480]!!
        f.disk.remove(connection, path)
        val decodes = f.decoded.size
        assertSame(thumbnail, f.poster())
        assertEquals(decodes, f.decoded.size)
        assertEquals(0, f.server.requestCount)
    }
    @Test fun manualRefreshReplacesListThumbnailAndFailureKeepsLastSuccessfulOne() = fixture { f ->
        f.server.enqueue(MockResponse().setBody("first")); f.refresh()
        f.server.enqueue(MockResponse().setBody("second")); f.refresh(force = true)
        val updated = f.images["second" to 480]!!
        assertSame(updated, f.resources.memoryPoster(connection, path))
        assertSame(updated, f.poster())
        f.server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(f.refresh(force = true).notices.isNotEmpty())
        assertSame(updated, f.poster())
        assertEquals(3, f.server.requestCount)
    }
}
