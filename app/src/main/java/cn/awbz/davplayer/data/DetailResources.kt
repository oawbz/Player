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
class DetailResources(root: File, control: Executor,
    private val posters: LruCache<String, Bitmap> = posterCache(),
    private val decodePoster: (ByteArray, Int) -> Bitmap = ::bitmap,
    private val http: ResourceHttp = ResourceHttp(),
    serial: java.util.concurrent.ExecutorService = Executors.newSingleThreadExecutor()) {
    private val cache = ResourceCache(File(root, "openlist-resources-v2"))
    // Explicitly requested resource-cache reset; never touch settings or history.
    init { control.execute { File(root, "openlist-resources-v1").deleteRecursively() } }
    fun cacheUsage() = cache.usage()
    fun clearCache(scope: RequestScope): Future<ResourceCache.Usage> = gate.submit(scope) {
        scope.check()
        try { cache.clear() } finally { posters.evictAll() }
    }
    private val cooldown = RetryCooldown()
    val gate = ResourceGate(serial, control)
    data class Snapshot(val metadata: MediaMetadata = MediaMetadata(), val cover: Bitmap? = null, val notices: List<String> = emptyList(), val nfoCached: Boolean = false)
    private fun metadata(entry: ResourceCache.Entry?) = entry?.let { runCatching { MetadataCodec.decode(it.data) }.getOrNull() }
    fun cached(connection: Connection, assets: DetailAssets, maxSide: Int = 1600): Snapshot {
        var nfoCached = false
        val meta = assets.nfo?.let { path ->
            val entry = cache.read(connection, path, 2 * Nfo.LIMIT)
            val parsed = metadata(entry)
            nfoCached = entry != null && (entry.data.isEmpty() || parsed != null)
            if (entry != null && !nfoCached) cache.remove(connection, path)
            parsed
        } ?: MediaMetadata()
        return Snapshot(meta, assets.cover?.let { cachedPoster(connection, it, maxSide) }, nfoCached = nfoCached)
    }

    private fun cachedPoster(connection: Connection, path: String, maxSide: Int): Bitmap? =
        cache.read(connection, path, IMAGE_LIMIT)?.takeIf { it.data.isNotEmpty() }?.let { entry ->
            runCatching {
                val image = decodePoster(entry.data, maxSide)
                val key = cache.key(connection, path)
                if (posters.get(key) == null) posters.put(key, if (maxSide <= POSTER_SIDE) image else decodePoster(entry.data, POSTER_SIDE))
                image
            }.getOrElse { cache.remove(connection, path); null }
        }
    fun memoryPoster(connection: Connection, path: String): Bitmap? = posters.get(cache.key(connection, path))
    /** Cache first; visible cards fetch missing posters through the playback drain barrier. */
    fun poster(connection: Connection, path: String, scope: RequestScope, update: (Bitmap?) -> Unit): Future<*> = gate.submit(scope) {
        try {
            val key = cache.key(connection, path)
            var image = posters.get(key) ?: cachedPoster(connection, path, POSTER_SIDE)
            if (image == null && cooldown.allowed(key) && !scope.isCancelled && !Thread.currentThread().isInterrupted) {
                fetch(connection, path, IMAGE_LIMIT, false, scope) { it }
                image = posters.get(key)
                if (image == null) cooldown.failed(key)
            }
            if (!scope.isCancelled && !Thread.currentThread().isInterrupted) {
                if (image != null) posters.put(key, image)
                update(image)
            }
        } catch (e: Exception) { if (!scope.isCancelled && !Thread.currentThread().isInterrupted) { cooldown.failed(cache.key(connection, path), e); update(null) } }
    }
    private fun fetch(connection: Connection, path: String, limit: Int, force: Boolean, scope: RequestScope,
        missingContent: Boolean = false, transform: (ByteArray) -> ByteArray): ResourceCache.Entry? {
        if (scope.isCancelled || Thread.currentThread().isInterrupted) throw MediaFailure("请求已取消")
        val old = cache.read(connection, path, limit)
        if (!missingContent && !PosterFiles.shouldFetch(old, force)) return old
        val response = http.get(connection, path, if (limit == IMAGE_LIMIT) IMAGE_LIMIT else Nfo.LIMIT, scope)
        if (scope.isCancelled || Thread.currentThread().isInterrupted) throw MediaFailure("请求已取消")
        var thumbnail: Bitmap? = null
        val entry = when (response.status) {
            // Missing resources can be retried on the next visible request or detail entry.
            404 -> ResourceCache.Entry(ByteArray(0))
            else -> {
                val data = transform(response.data)
                if (limit == IMAGE_LIMIT) thumbnail = decodePoster(data, POSTER_SIDE)
                ResourceCache.Entry(data)
            }
        }
        if (scope.isCancelled || Thread.currentThread().isInterrupted) throw MediaFailure("请求已取消")
        cache.write(connection, path, entry)
        if (entry.data.isNotEmpty()) cooldown.succeeded(cache.key(connection, path))
        if (limit == IMAGE_LIMIT) {
            val key = cache.key(connection, path)
            // Publish only after validation and the atomic disk write. Returning lists reuse it.
            if (thumbnail != null) posters.put(key, thumbnail) else posters.remove(key)
        }
        return entry
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
            if (force || !result.nfoCached) try {
                val entry = fetch(connection, path, 2 * Nfo.LIMIT, force, scope) { bytes ->
                    val xml = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                    MetadataCodec.encode(Nfo.parse(xml))
                }
                result = result.copy(metadata = metadata(entry) ?: MediaMetadata(), nfoCached = entry != null)
            } catch (e: Exception) { if (scope.isCancelled || Thread.currentThread().isInterrupted) throw e; notices.add("简介暂时无法更新，可稍后刷新") }
            emit()
        }
        assets.cover?.let { path ->
            try {
                if (force || result.cover == null) {
                    val entry = fetch(connection, path, IMAGE_LIMIT, force, scope, missingContent = !force && result.cover == null) { it }
                    result = result.copy(cover = entry?.takeIf { it.data.isNotEmpty() }?.let {
                        if (maxSide <= POSTER_SIDE) posters.get(cache.key(connection, path)) ?: decodePoster(it.data, maxSide)
                        else decodePoster(it.data, maxSide)
                    })
                }
            } catch (e: Exception) { if (scope.isCancelled || Thread.currentThread().isInterrupted) throw e; notices.add("封面暂时无法更新，可稍后刷新") }
            emit()
        }
        return result.copy(notices = notices.toList())
    }
    data class Progress(val done: Int, val total: Int, val failures: Int, val connection: Connection? = null,
        val work: Work? = null, val snapshot: Snapshot? = null, val finished: Boolean = false, val works: List<Work>? = null)
    /** Category refresh runs serially inside the same cancellable playback drain barrier. */
    fun refreshCategory(library: Library, connection: Connection, category: Category, scope: RequestScope,
        update: (Progress) -> Unit): Future<*> = gate.submit(scope) {
        fun cancelled() = scope.isCancelled || Thread.currentThread().isInterrupted
        update(Progress(0, 0, 0))
        val works = try { library.works(connection, category, scope) }
            catch (e: Exception) { if (cancelled()) return@submit; throw e }
        update(Progress(0, works.size, 0, works = works))
        var done = 0
        var failures = 0
        for (work in works) {
            if (cancelled()) return@submit
            var snapshot: Snapshot? = null
            try {
                val assets = DetailAssets(cover = PosterFiles.path(connection, work.path))
                snapshot = refreshNow(connection, assets, true, scope, 480) { }
                if (snapshot.notices.isNotEmpty()) failures++
            } catch (_: Exception) { if (cancelled()) return@submit; failures++ }
            if (cancelled()) return@submit
            done++
            update(Progress(done, works.size, failures, connection, work, snapshot))
        }
        if (!cancelled()) update(Progress(done, works.size, failures, finished = true))
    }
    companion object {
        private const val IMAGE_LIMIT = 8 * 1024 * 1024
        private const val POSTER_SIDE = 480
        private fun posterCache() = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }
        private fun bitmap(bytes: ByteArray, maxSide: Int): Bitmap {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 100_000_000L) throw MediaFailure("封面尺寸不受支持")
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw MediaFailure("封面无法读取")
        }
    }
}
