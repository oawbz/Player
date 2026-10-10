package cn.awbz.davplayer.ui

import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import cn.awbz.davplayer.data.*
import cn.awbz.davplayer.databinding.ItemPosterBinding

open class PosterActivity : BaseActivity() {
    protected fun poster(work: Work, history: History? = null): View {
        val b = ItemPosterBinding.inflate(layoutInflater)
        bindPoster(b, work, history)
        return b.root
    }
    protected fun bindPoster(b: ItemPosterBinding, initial: Work, history: History? = null) {
        val token = Any(); b.root.tag = token
        var work = initial
        b.title.text = work.name; b.image.setImageDrawable(null)
        b.subtitle.text = if (history == null) "" else listOf(history.season, history.videoName, "已看 ${formatTime(history.position)}").filter { it.isNotEmpty() }.joinToString(" · ")
        b.progress.visibility = if (history != null && history.duration > 0) View.VISIBLE else View.GONE
        if (history != null && history.duration > 0) b.progress.progress = ((history.position.coerceAtMost(history.duration) * 1000) / history.duration).toInt()
        b.root.setOnClickListener { if (history != null) PlayerActivity.launch(this, work, Episode(history.videoPath, history.videoName, history.season), history.position)
            else openWork(work) }
        var started = false
        lateinit var watcher: ViewTreeObserver.OnPreDrawListener
        watcher = ViewTreeObserver.OnPreDrawListener {
            if (b.root.tag !== token) {
                if (b.root.viewTreeObserver.isAlive) b.root.viewTreeObserver.removeOnPreDrawListener(watcher)
            } else if (!started && b.root.getGlobalVisibleRect(Rect())) {
                started = true
                if (b.root.viewTreeObserver.isAlive) b.root.viewTreeObserver.removeOnPreDrawListener(watcher)
                store.connection(work.connectionId)?.let { connection ->
                    job({ app.posters.load(connection, work, it) }, { (resolved, image) ->
                        if (b.root.tag === token) { work = resolved; b.image.setImageBitmap(image) }
                    }, { /* Keep a readable placeholder when an optional cover fails. */ })
                }
            }
            true
        }
        b.root.viewTreeObserver.addOnPreDrawListener(watcher)
    }
    protected fun openWork(work: Work) {
        startActivity(Intent(this, DetailActivity::class.java).putExtra("connection", work.connectionId)
            .putExtra("path", work.path).putExtra("name", work.name).putExtra("cover", work.coverPath))
    }
}
