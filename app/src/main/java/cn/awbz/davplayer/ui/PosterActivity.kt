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
    private class Slot(val connection: Connection, val path: String) {
        var requested = false
        var scope: RequestScope? = null
        var future: Future<*>? = null
    }
    private val slots = IdentityHashMap<ImageView, Slot>()
    private val visibleRect = Rect()
    private var postersActive = false
    private var refreshScope = RequestScope()
    private var refreshFuture: Future<*>? = null
    private var refreshButton: View? = null
    protected val refreshingMetadata get() = refreshFuture != null
    protected fun refreshMetadata(connection: Connection, category: Category, status: TextView, button: View) {
        if (refreshingMetadata) return
        refreshScope = RequestScope(); val scope = refreshScope
        refreshButton = button; button.isEnabled = false
        status.visibility = View.VISIBLE; status.text = "正在读取作品目录…"
        refreshFuture = app.detailResources.refreshCategory(library, connection, category, scope) { progress ->
            handler.post {
                if (!alive || !postersActive || scope !== refreshScope || scope.isCancelled) return@post
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
                    if (progress.failures > 0) "资料刷新完成，${progress.failures} 项未完成，可再次刷新重试"
                    else "资料已缓存 · ${progress.total} 部作品"
                } else if (progress.total == 0) "正在读取作品目录…" else "正在刷新资料 ${progress.done} / ${progress.total}"
                if (progress.finished) finishMetadataRefresh()
            }
        }
        val future = refreshFuture
        app.workers.execute {
            try { future?.get() } catch (_: Exception) {
                handler.post {
                    if (alive && postersActive && scope === refreshScope && !scope.isCancelled && refreshFuture === future) {
                        status.text = "资料刷新中断，可再次刷新重试"
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
        slot.future?.cancel(true); slot.future = null
        slot.scope?.let { scope -> app.storageWorker.execute { scope.cancel() } }; slot.scope = null
        slot.requested = false
    }
    private fun updateVisiblePosters() {
        if (!postersActive) return
        val iterator = slots.entries.iterator()
        while (iterator.hasNext()) {
            val (image, slot) = iterator.next()
            if (!image.isAttachedToWindow) { cancel(slot); iterator.remove(); continue }
            val visible = image.isShown && image.getGlobalVisibleRect(visibleRect) && !visibleRect.isEmpty
            if (!visible) {
                cancel(slot)
                // The disk cache and bounded bitmap LRU retain artwork, not offscreen view trees.
                if (image.drawable !is PosterPlaceholder) image.setImageDrawable(PosterPlaceholder())
                continue
            }
            if (slot.requested) continue
            slot.requested = true
            val cached = app.detailResources.memoryPoster(slot.connection, slot.path)
            if (cached != null) { image.setImageBitmap(cached); continue }
            val scope = RequestScope(); slot.scope = scope
            val reference = WeakReference(image)
            slot.future = app.detailResources.poster(slot.connection, slot.path, scope) { bitmap ->
                handler.post {
                    val target = reference.get()
                    if (alive && postersActive && slot.scope === scope && !scope.isCancelled && target != null && target.tag === slot) {
                        if (bitmap != null) target.setImageBitmap(bitmap) else target.setImageDrawable(PosterPlaceholder())
                    }
                }
            }
        }
    }
    override fun onResume() {
        super.onResume(); postersActive = true
        window.decorView.viewTreeObserver.addOnPreDrawListener(observer)
        window.decorView.invalidate()
    }
    override fun onPause() {
        postersActive = false
        window.decorView.viewTreeObserver.takeIf { it.isAlive }?.removeOnPreDrawListener(observer)
        cancelMetadataRefresh(); clearPosterRequests(); super.onPause()
    }
    override fun onDestroy() { cancelMetadataRefresh(); clearPosterRequests(); super.onDestroy() }
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
            val slot = Slot(connection, path); slots[b.image] = slot; b.image.tag = slot
            b.image.setImageDrawable(PosterPlaceholder())
        } else if (connection == null || path == null) {
            previous?.let { cancel(it) }; slots.remove(b.image); b.image.tag = null; b.image.setImageDrawable(PosterPlaceholder())
        }
        b.subtitle.visibility = if (history == null) View.GONE else View.VISIBLE
        b.subtitle.text = if (history == null) "" else listOf(history.season, "已看 ${formatTime(history.position)}").filter { it.isNotEmpty() }.joinToString(" · ")
        b.progress.visibility = if (history != null && history.duration > 0) View.VISIBLE else View.GONE
        if (history != null && history.duration > 0) b.progress.progress = ((history.position.coerceAtMost(history.duration) * 1000) / history.duration).toInt()
        b.root.setOnClickListener { if (history != null) PlayerActivity.launch(this, work, Episode(history.videoPath, history.videoName, history.season), history.position)
            else openWork(work) }
    }
    protected fun openWork(work: Work) {
        startActivity(Intent(this, DetailActivity::class.java).putExtra("connection", work.connectionId)
            .putExtra("path", work.path).putExtra("name", work.name).putExtra("cover", work.coverPath))
    }
}
