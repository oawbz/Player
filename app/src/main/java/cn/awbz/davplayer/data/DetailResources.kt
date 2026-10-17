package cn.awbz.davplayer.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Detail metadata and visible list artwork share one cancellable, drainable resource lane. */
class DetailResources(root: File, control: Executor) {
    private val cache = ResourceCache(File(root, "openlist-resources-v1"))
    private val http = ResourceHttp()
    private val posters = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    val gate = ResourceGate(Executors.newSingleThreadExecutor(), control)
    data class Snapshot(val metadata: MediaMetadata = MediaMetadata(), val cover: Bitmap? = null, val notices: List<String> = emptyList())
    private fun metadata(entry: ResourceCache.Entry?) = entry?.let { runCatching { MetadataCodec.decode(it.data) }.getOrNull() }
    fun cached(connection: Connection, assets: DetailAssets, maxSide: Int = 1600): Snapshot {
        val meta = assets.nfo?.let { path ->
            val entry = cache.read(connection, path, 2 * Nfo.LIMIT)
            metadata(entry).also { if (entry != null && entry.data.isNotEmpty() && it == null) cache.remove(connection, path) }
        } ?: MediaMetadata()
        return Snapshot(meta, assets.cover?.let { cachedPoster(connection, it, maxSide) })
    }
    private fun cachedPoster(connection: Connection, path: String, maxSide: Int): Bitmap? =
        cache.read(connection, path, IMAGE_LIMIT)?.takeIf { it.data.isNotEmpty() }?.let { entry ->
            runCatching { bitmap(entry.data, maxSide) }.getOrElse { cache.remove(connection, path); null }
        }
    fun memoryPoster(connection: Connection, path: String): Bitmap? = posters.get(cache.key(connection, path))
    /** Cache first; visible cards fetch missing posters through the playback drain barrier. */
    fun poster(connection: Connection, path: String, scope: RequestScope, update: (Bitmap?) -> Unit): Future<*> = gate.submit(scope) {
        try {
            val key = cache.key(connection, path)
            var image = posters.get(key) ?: cachedPoster(connection, path, 480)
            if (image == null && !scope.isCancelled && !Thread.currentThread().isInterrupted) {
                val entry = fetch(connection, path, IMAGE_LIMIT, false, scope) { bytes ->
                    bitmap(bytes, 480).recycle(); bytes
                }
                image = entry?.takeIf { it.data.isNotEmpty() }?.let { bitmap(it.data, 480) }
            }
            if (!scope.isCancelled && !Thread.currentThread().isInterrupted) {
                if (image != null) posters.put(key, image)
                update(image)
            }
        } catch (_: Exception) { if (!scope.isCancelled && !Thread.currentThread().isInterrupted) update(null) }
    }
    private fun fetch(connection: Connection, path: String, limit: Int, force: Boolean, scope: RequestScope,
        missingContent: Boolean = false, transform: (ByteArray) -> ByteArray): ResourceCache.Entry? {
        if (scope.isCancelled || Thread.currentThread().isInterrupted) throw MediaFailure("请求已取消")
        val old = cache.read(connection, path, limit)
        if (!missingContent && !PosterFiles.shouldFetch(old, force)) return old
        PlaybackTrace.write("resource start kind=${if (limit == IMAGE_LIMIT) "poster" else "nfo"}")
        try {
            val response = http.get(connection, path, if (limit == IMAGE_LIMIT) IMAGE_LIMIT else Nfo.LIMIT, scope)
            if (scope.isCancelled || Thread.currentThread().isInterrupted) throw MediaFailure("请求已取消")
            val now = System.currentTimeMillis()
            val entry = when (response.status) {
                // Missing resources can be retried on the next visible request or detail entry.
                404 -> ResourceCache.Entry(ByteArray(0), "", "", now)
                else -> ResourceCache.Entry(transform(response.data), "", "", now)
            }
            if (scope.isCancelled || Thread.currentThread().isInterrupted) throw MediaFailure("请求已取消")
            cache.write(connection, path, entry)
            if (limit == IMAGE_LIMIT) posters.remove(cache.key(connection, path))
            return entry
        } finally { PlaybackTrace.write("resource end") }
    }
    fun refresh(connection: Connection, assets: DetailAssets, force: Boolean, scope: RequestScope, update: (Snapshot) -> Unit): Future<*> = gate.submit(scope) {
        refreshNow(connection, assets, force, scope, maxSide = 1600, update = update)
    }
    private fun refreshNow(connection: Connection, assets: DetailAssets, force: Boolean, scope: RequestScope,
        maxSide: Int, update: (Snapshot) -> Unit): Snapshot {
        var result = cached(connection, assets, maxSide)
        val notices = mutableListOf<String>()
        fun emit() { if (!scope.isCancelled && !Thread.currentThread().isInterrupted) update(result.copy(notices = notices.toList())) }
        assets.nfo?.let { path ->
            try {
                val entry = fetch(connection, path, 2 * Nfo.LIMIT, force, scope, missingContent = !force && result.metadata.plot.isBlank()) { bytes ->
                    val xml = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                    MetadataCodec.encode(Nfo.parse(xml))
                }
                result = result.copy(metadata = metadata(entry) ?: MediaMetadata())
            } catch (e: Exception) { if (scope.isCancelled || Thread.currentThread().isInterrupted) throw e; notices.add("简介暂时无法更新，可稍后刷新") }
            emit()
        }
        assets.cover?.let { path ->
            try {
                val entry = fetch(connection, path, IMAGE_LIMIT, force, scope, missingContent = !force && result.cover == null) { bytes -> bitmap(bytes, 480).recycle(); bytes }
                result = result.copy(cover = entry?.takeIf { it.data.isNotEmpty() }?.let { bitmap(it.data, maxSide) })
            } catch (e: Exception) { if (scope.isCancelled || Thread.currentThread().isInterrupted) throw e; notices.add("封面暂时无法更新，可稍后刷新") }
            emit()
        }
        return result.copy(notices = notices.toList())
    }
    data class Progress(val done: Int, val total: Int, val failures: Int, val connection: Connection? = null,
        val work: Work? = null, val snapshot: Snapshot? = null, val finished: Boolean = false)
    /** Category refresh runs serially inside the same cancellable playback drain barrier. */
    fun refreshCategory(library: Library, connection: Connection, category: Category, scope: RequestScope,
        update: (Progress) -> Unit): Future<*> = gate.submit(scope) {
        fun cancelled() = scope.isCancelled || Thread.currentThread().isInterrupted
        update(Progress(0, 0, 0))
        val works = try { library.works(connection, category, scope) }
            catch (e: Exception) { if (cancelled()) return@submit; throw e }
        var done = 0
        var failures = 0
        for (work in works) {
            if (cancelled()) return@submit
            var snapshot: Snapshot? = null
            try {
                val entries = library.list(connection, work.path, scope)
                val assets = DetailAssets(Nfo.select(entries, work.name, MediaRules.episodes(entries))?.path, PosterFiles.path(connection, work.path))
                snapshot = refreshNow(connection, assets, true, scope, 480) { }
                if (snapshot.notices.isNotEmpty()) failures++
                val key = cache.key(connection, assets.cover!!)
                snapshot.cover?.let { posters.put(key, it) } ?: posters.remove(key)
            } catch (_: Exception) { if (cancelled()) return@submit; failures++ }
            if (cancelled()) return@submit
            done++
            update(Progress(done, works.size, failures, connection, work, snapshot))
        }
        if (!cancelled()) update(Progress(done, works.size, failures, finished = true))
    }
    private fun bitmap(bytes: ByteArray, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 100_000_000L) throw MediaFailure("封面尺寸不受支持")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw MediaFailure("封面无法读取")
    }
    companion object { private const val IMAGE_LIMIT = 8 * 1024 * 1024 }
}
