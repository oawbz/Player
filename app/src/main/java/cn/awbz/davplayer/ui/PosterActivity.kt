package cn.awbz.davplayer.ui

import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.TextView
import cn.awbz.davplayer.data.*
import cn.awbz.davplayer.databinding.ItemPosterBinding
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import java.util.concurrent.Future

open class PosterActivity : BaseActivity() {
    private class Slot(val connection: Connection, val path: String, image: ImageView) {
        val image = WeakReference(image)
        var requested = false
        var visibleSince = 0L
        var lastVisibleAt = 0L
        var load: PosterLoad? = null
    }
    private class PosterLoad(val key: Pair<Connection, String>) {
        val scope = RequestScope()
        var future: Future<*>? = null
        val subscribers = linkedSetOf<Slot>()
    }
    private val slots = IdentityHashMap<ImageView, Slot>()
    private val loads = mutableMapOf<Pair<Connection, String>, PosterLoad>()
    private val visibleRect = Rect()
    private var postersActive = false
    private var refreshScope = RequestScope()
    private var refreshFuture: Future<*>? = null
    private var refreshButton: View? = null
    private val posterTick = Runnable { updateVisiblePosters() }
    protected val refreshingMetadata get() = refreshFuture != null
    protected fun refreshMetadata(connection: Connection, category: Category, status: TextView, button: View, onWorks: (List<Work>) -> Unit) {
        if (refreshingMetadata) return
        refreshScope = RequestScope(); val scope = refreshScope
        refreshButton = button; button.isEnabled = false
        status.visibility = View.VISIBLE; status.text = "正在读取作品目录…"
        refreshFuture = app.detailResources.refreshCategory(library, connection, category, scope) { progress ->
            handler.post {
                if (!alive || !postersActive || scope !== refreshScope || scope.isCancelled) return@post
                progress.works?.let(onWorks)
                val work = progress.work; val connection = progress.connection
                if (work != null && connection != null && progress.snapshot != null) {
                    val path = PosterFiles.path(connection, work.path)
                    slots.forEach { (image, slot) ->
                        if (slot.connection == connection && slot.path == path) {
                            cancel(slot)
                            if (image.isShown && image.getGlobalVisibleRect(visibleRect) && !visibleRect.isEmpty) {
                                slot.requested = true
                                progress.snapshot.cover?.let { image.setImageBitmap(it) } ?: image.setImageDrawable(PosterPlaceholder())
                            }
                        }
                    }
                }
                status.text = if (progress.finished) {
                    if (progress.failures > 0) "海报刷新完成，${progress.failures} 项未完成，可再次刷新重试"
                    else "海报已缓存"
                } else if (progress.total == 0) "正在读取作品目录…" else "正在刷新海报 ${progress.done} / ${progress.total}"
                if (progress.finished) finishMetadataRefresh()
            }
        }
        val future = refreshFuture
        app.workers.execute {
            try { future?.get() } catch (_: Exception) {
                handler.post {
                    if (alive && postersActive && scope === refreshScope && !scope.isCancelled && refreshFuture === future) {
                        status.text = "海报刷新中断，可再次刷新重试"
                        finishMetadataRefresh()
                    }
                }
            }
        }
    }
    private fun finishMetadataRefresh() {
        refreshFuture = null
        refreshButton?.isEnabled = true; refreshButton = null
        // Rebind visible artwork after the category refresh.
        slots.values.forEach { cancel(it) }
        window.decorView.invalidate()
    }
    private fun cancelMetadataRefresh() {
        refreshFuture?.cancel(true); refreshFuture = null
        val scope = refreshScope
        app.storageWorker.execute { scope.cancel() }
        refreshButton?.isEnabled = true; refreshButton = null
    }
    private val observer = ViewTreeObserver.OnPreDrawListener { updateVisiblePosters(); true }
    protected fun clearPosterRequests() {
        slots.values.forEach { cancel(it) }; slots.clear()
    }
    private fun cancel(slot: Slot) {
        slot.load?.let { request ->
            request.subscribers.remove(slot)
            if (request.subscribers.isEmpty()) {
                if (loads[request.key] === request) loads.remove(request.key)
                request.future?.cancel(true)
                app.storageWorker.execute { request.scope.cancel() }
            }
        }
        slot.load = null; slot.requested = false; slot.visibleSince = 0; slot.lastVisibleAt = 0
    }
    private fun requestPoster(slot: Slot) {
        slot.requested = true
        val key = slot.connection to slot.path
        val current = loads[key]
        if (current != null) {
            current.subscribers.add(slot); slot.load = current
            return
        }
        val request = PosterLoad(key)
        loads[key] = request; request.subscribers.add(slot); slot.load = request
        request.future = app.detailResources.poster(slot.connection, slot.path, request.scope) { bitmap ->
            handler.post {
                if (loads[key] !== request) return@post
                loads.remove(key)
                request.subscribers.forEach { subscriber ->
                    if (subscriber.load === request) {
                        subscriber.load = null
                        val image = subscriber.image.get()
                        if (alive && postersActive && !request.scope.isCancelled && image != null && image.tag === subscriber) {
                            if (bitmap != null) image.setImageBitmap(bitmap) else image.setImageDrawable(PosterPlaceholder())
                        }
                    }
                }
                request.subscribers.clear()
            }
        }
    }
    private fun updateVisiblePosters() {
        handler.removeCallbacks(posterTick)
        if (!postersActive) return
        val now = System.nanoTime() / 1_000_000
        var waiting = false
        val iterator = slots.entries.iterator()
        while (iterator.hasNext()) {
            val (image, slot) = iterator.next()
            if (image.windowToken == null) { cancel(slot); iterator.remove(); continue }
            val visible = image.isShown && image.getGlobalVisibleRect(visibleRect) && !visibleRect.isEmpty
            if (!visible) {
                slot.visibleSince = 0
                // Brief offscreen transitions do not abort a download another visible card needs.
                if (slot.lastVisibleAt != 0L && now - slot.lastVisibleAt < 250) { waiting = true; continue }
                cancel(slot)
                if (image.drawable !is PosterPlaceholder) image.setImageDrawable(PosterPlaceholder())
                continue
            }
            slot.lastVisibleAt = now
            if (slot.requested) continue
            val cached = app.detailResources.memoryPoster(slot.connection, slot.path)
            if (cached != null) { slot.requested = true; image.setImageBitmap(cached); continue }
            if (slot.visibleSince == 0L) slot.visibleSince = now
            if (now - slot.visibleSince < 180) { waiting = true; continue }
            requestPoster(slot)
        }
        if (waiting) handler.postDelayed(posterTick, 100)
    }
    override fun onResume() {
        super.onResume(); postersActive = true
        window.decorView.viewTreeObserver.addOnPreDrawListener(observer)
        window.decorView.invalidate()
    }
    override fun onPause() {
        postersActive = false; handler.removeCallbacks(posterTick)
        window.decorView.viewTreeObserver.takeIf { it.isAlive }?.removeOnPreDrawListener(observer)
        cancelMetadataRefresh()
        // Retain bindings for short returns; cancelled tasks never retain the old screen scope.
        slots.values.forEach { cancel(it) }
        super.onPause()
    }
    override fun onDestroy() { handler.removeCallbacks(posterTick); cancelMetadataRefresh(); clearPosterRequests(); super.onDestroy() }
    protected fun poster(work: Work, history: History? = null): View {
        val b = ItemPosterBinding.inflate(layoutInflater)
        bindPoster(b, work, history)
        return b.root
    }
    protected fun bindPoster(b: ItemPosterBinding, initial: Work, history: History? = null) {
        val connection = store.connection(initial.connectionId)
        val path = connection?.let { runCatching { PosterFiles.path(it, initial.path) }.getOrNull() }
        val work = initial.copy(coverPath = path)
        b.title.text = work.name
        val previous = slots[b.image]
        if (connection != null && path != null && (previous == null || previous.connection != connection || previous.path != path)) {
            previous?.let { cancel(it) }
            val slot = Slot(connection, path, b.image); slots[b.image] = slot; b.image.tag = slot
            val cached = app.detailResources.memoryPoster(connection, path)
            slot.requested = cached != null
            if (cached != null) b.image.setImageBitmap(cached) else b.image.setImageDrawable(PosterPlaceholder())
        } else if (connection == null || path == null) {
            previous?.let { cancel(it) }; slots.remove(b.image); b.image.tag = null; b.image.setImageDrawable(PosterPlaceholder())
        }
        b.subtitle.visibility = if (history == null) View.GONE else View.VISIBLE
        b.subtitle.text = if (history == null) "" else listOf(history.season, "已看 ${formatTime(history.position)}").filter { it.isNotEmpty() }.joinToString(" · ")
        b.progress.visibility = if (history != null && history.duration > 0) View.VISIBLE else View.GONE
        if (history != null && history.duration > 0) b.progress.progress = ((history.position.coerceAtMost(history.duration) * 1000) / history.duration).toInt()
        b.root.setOnClickListener { openWork(work) }
    }
    protected fun openWork(work: Work) {
        startActivity(Intent(this, DetailActivity::class.java).putExtra("connection", work.connectionId)
            .putExtra("path", work.path).putExtra("name", work.name).putExtra("cover", work.coverPath))
    }
}
