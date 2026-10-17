package cn.awbz.davplayer.ui

import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import cn.awbz.davplayer.R
import cn.awbz.davplayer.data.*
import cn.awbz.davplayer.databinding.ActivityDetailBinding
import java.util.Locale

class DetailActivity : BaseActivity() {
    private lateinit var b: ActivityDetailBinding
    private lateinit var work: Work
    private var detail: WorkDetail? = null
    private var selectedSeason = ""
    private var resourceScope = RequestScope()
    private var resourceFuture: java.util.concurrent.Future<*>? = null
    private var resourceGeneration = 0
    private val landscape get() = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    private fun dimension(id: Int) = resources.getDimensionPixelSize(id)
    private val posterPreviews = mutableListOf<ImageView>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        work = Work(intent.getStringExtra("connection") ?: return finish(), intent.getStringExtra("path") ?: return finish(),
            intent.getStringExtra("name") ?: "作品", intent.getStringExtra("cover"))
        b = ActivityDetailBinding.inflate(layoutInflater); setContentView(b.root)
        b.cover.setImageDrawable(PosterPlaceholder()); b.title.text = work.name; b.play.isEnabled = false
        b.play.setSingleLine(true); b.play.ellipsize = TextUtils.TruncateAt.END
        val screen = resources.displayMetrics
        b.hero.layoutParams = b.hero.layoutParams.apply {
            height = if (landscape || resources.configuration.screenWidthDp >= 600) dimension(R.dimen.detail_poster_height) else minOf((screen.widthPixels * 1.35).toInt(), (screen.heightPixels * .70).toInt())
        }
        b.back.setImageDrawable(PlayerIcon(PlayerIcon.Kind.BACK)); b.back.setOnClickListener { finish() }
        b.refresh.contentDescription = "刷新当前海报和简介"
        b.refresh.setImageDrawable(PlayerIcon(PlayerIcon.Kind.REPLAY)); b.refresh.setOnClickListener { load(true) }
        b.play.setCompoundDrawables(PlayerIcon(PlayerIcon.Kind.PLAY).apply { setBounds(0, 0, dp(22), dp(22)); setColorFilter(Color.BLACK, android.graphics.PorterDuff.Mode.SRC_IN) }, null, null, null)
        b.play.compoundDrawablePadding = dp(12); b.play.setPadding(dp(20), 0, dp(20), 0)
        selectedSeason = savedInstanceState?.getString("season").orEmpty()
    }
    override fun onResume() { super.onResume(); detail?.let { render(it) }; load() }
    private fun load(force: Boolean = false) {
        cancelResources()
        resetJobs()
        if (detail == null) { b.play.isEnabled = false; b.status.text = "正在读取目录…" }
        val connection = store.connection(work.connectionId) ?: return error("连接配置不存在")
        b.cover.setImageDrawable(PosterPlaceholder())
        job({ library.detail(connection, work, it) }, { result ->
            detail = result; work = result.work; render(result)
            loadResources(connection, result, force)
        }, { b.status.visibility = View.VISIBLE; b.status.text = it })
    }

    private fun cancelResources() {
        resourceGeneration++
        val previous = resourceScope; resourceScope = RequestScope()
        resourceFuture?.cancel(true); resourceFuture = null
        app.storageWorker.execute { previous.cancel() }
    }
    private fun loadResources(connection: Connection, base: WorkDetail, force: Boolean) {
        val expected = resourceGeneration; val scope = resourceScope
        // Cache I/O and bitmap decoding stay off the UI thread.
        job({ app.detailResources.cached(connection, base.assets) }, { cached ->
            if (expected != resourceGeneration || scope.isCancelled) return@job
            applyResources(base, cached)
            val missingCover = base.assets.cover != null && cached.cover == null
            val missingPlot = base.assets.nfo != null && cached.metadata.plot.isBlank()
            if (!force && !missingCover && !missingPlot) return@job
            resourceFuture = app.detailResources.refresh(connection, base.assets, force, scope) { update ->
                handler.post {
                    if (alive && expected == resourceGeneration && !scope.isCancelled) applyResources(base, update)
                }
            }
        }, { /* Resources are optional; directory playback remains available. */ })
    }
    private fun applyResources(base: WorkDetail, snapshot: DetailResources.Snapshot) {
        val updated = base.copy(metadata = snapshot.metadata, description = snapshot.metadata.plot,
            notices = base.notices + snapshot.notices,
            work = base.work.copy(coverPath = if (snapshot.cover != null) base.assets.cover else null))
        detail = updated; work = updated.work
        renderMetadata(updated)
        if (snapshot.cover != null) b.cover.setImageBitmap(snapshot.cover) else b.cover.setImageDrawable(PosterPlaceholder())
        posterPreviews.forEach { image ->
            if (snapshot.cover != null) image.setImageBitmap(snapshot.cover) else image.setImageDrawable(PosterPlaceholder())
        }
    }
    override fun onPause() { cancelResources(); super.onPause() }
    private fun renderMetadata(result: WorkDetail) {
        val meta = result.metadata
        b.title.text = meta.title.ifEmpty { work.name }
        val info = mutableListOf<String>()
        meta.rating?.let { info.add("★ " + String.format(Locale.ROOT, "%.1f", it)) }
        if (meta.year.isNotEmpty()) info.add(meta.year)
        meta.runtimeMinutes?.let { info.add(if (it >= 60) "${it / 60} 小时 ${it % 60} 分钟" else "$it 分钟") }
        if (result.episodes.size > 1) info.add("共 ${result.episodes.size} 集")
        b.metadata.text = info.joinToString("  ·  "); b.metadata.visibility = if (info.isEmpty()) View.GONE else View.VISIBLE
        b.genres.text = meta.genres.joinToString(" / "); b.genres.visibility = if (meta.genres.isEmpty()) View.GONE else View.VISIBLE
        b.description.text = meta.plot.ifEmpty { "暂无简介" }
        b.expand.visibility = if (meta.plot.isEmpty()) View.GONE else View.VISIBLE
        b.expand.setOnClickListener {
            showDescription(meta.plot)
        }
        b.status.text = result.notices.joinToString("\n").ifEmpty { if (result.episodes.isEmpty()) "未找到视频文件" else "" }
        b.status.visibility = if (b.status.text.isEmpty()) View.GONE else View.VISIBLE
    }
    private fun render(result: WorkDetail) {
        renderMetadata(result)
        val last = store.history().firstOrNull { it.connectionId == work.connectionId && it.workPath == work.path }
        val lastEpisode = result.episodes.firstOrNull { it.path == last?.videoPath }
        val playable = lastEpisode ?: result.episodes.firstOrNull()
        b.play.isEnabled = playable != null
        val resumeNumber = result.seasons.firstOrNull { season -> season.episodes.any { it.path == lastEpisode?.path } }
            ?.episodes?.indexOfFirst { it.path == lastEpisode?.path }?.plus(1) ?: 1
        b.play.text = if (lastEpisode != null && last != null && last.position > 0) "继续播放 ${if (result.episodes.size > 1) "第 ${resumeNumber} 集 · " else ""}${formatTime(last.position)}" else "播放"
        b.play.setOnClickListener { if (playable != null) PlayerActivity.launch(this, work, playable, if (playable.path == last?.videoPath) last.position else 0) }
        b.seasons.removeAllViews(); posterPreviews.clear()
        val hasSeasons = result.seasons.any { it.name.isNotBlank() }
        if (result.episodes.isEmpty() || (result.episodes.size == 1 && !hasSeasons)) return
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
        if (hasSeasons) b.seasons.addView(tabScroll)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val episodesScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
        b.seasons.addView(episodesScroll, LinearLayout.LayoutParams(-1, -2).apply {
            if (hasSeasons) topMargin = dimension(R.dimen.episode_season_gap)
        })
        fun select(index: Int) {
            selectedSeason = result.seasons[index].name; row.removeAllViews(); posterPreviews.clear()
            for (i in 0 until tabs.childCount) tabs.getChildAt(i).isSelected = i == index
            result.seasons[index].episodes.forEachIndexed { number, episode ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4))
                    isFocusable = true; isClickable = true; setBackgroundResource(R.drawable.focus_card)
                    contentDescription = "播放 ${episode.name}"
                    setOnClickListener { PlayerActivity.launch(this@DetailActivity, work, episode, if (episode.path == last?.videoPath) last.position else 0) }
                }
                val preview = FrameLayout(this)
                val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageDrawable(if (b.cover.drawable is PosterPlaceholder) PosterPlaceholder() else b.cover.drawable); setBackgroundColor(Color.rgb(27, 33, 41)) }
                posterPreviews.add(image)
                preview.addView(image, FrameLayout.LayoutParams(-1, -1))
                preview.addView(ImageView(this).apply { setImageDrawable(PlayerIcon(PlayerIcon.Kind.PLAY)); setPadding(dp(10), dp(10), dp(10), dp(10)); setBackgroundResource(R.drawable.player_button) }, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
                card.addView(preview, LinearLayout.LayoutParams(dimension(R.dimen.episode_image_width), dimension(R.dimen.episode_image_height)))
                card.addView(text("第 ${number + 1} 集", if (landscape) 14f else 16f).apply { maxLines = 1; setPadding(dp(8), dp(8), dp(8), 0) }, LinearLayout.LayoutParams(dimension(R.dimen.episode_image_width), dimension(R.dimen.episode_title_height)))
                card.addView(text(episode.name, if (landscape) 11f else 12f).apply {
                    maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                    setTextColor(resources.getColor(R.color.text_secondary)); setPadding(dp(8), 0, dp(8), dp(8))
                }, LinearLayout.LayoutParams(dimension(R.dimen.episode_image_width), dimension(R.dimen.episode_subtitle_height)))
                row.addView(card, LinearLayout.LayoutParams(dimension(R.dimen.episode_card_width), -2).apply { rightMargin = dp(8) })
            }
            episodesScroll.scrollTo(0, 0)
        }
        result.seasons.forEachIndexed { index, season ->
            tabs.addView(button(season.name.ifEmpty { "第 ${index + 1} 季" }) { select(index) }.apply { setBackgroundResource(R.drawable.ui_button); textSize = if (landscape) 16f else 18f }, LinearLayout.LayoutParams(-2, dimension(R.dimen.episode_season_height)).apply { rightMargin = dp(16) })
        }
        var index = result.seasons.indexOfFirst { it.name == selectedSeason }
        if (index < 0) index = result.seasons.indexOfFirst { it.name == last?.season }.coerceAtLeast(0)
        select(index)
    }
    private fun showDescription(plot: String) {
        val restoreFocus = b.expand.hasFocus()
        UiDialog.Builder(this).setTitle("剧情简介").setView(text(plot, 16f).apply {
            setTextColor(resources.getColor(R.color.text_secondary))
            setPadding(0, 0, dp(8), dp(12)); setLineSpacing(dp(6).toFloat(), 1f)
        }).setPositiveButton("关闭", null).create().apply {
            setOnDismissListener { if (restoreFocus) b.expand.requestFocus() }
            show()
        }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("season", selectedSeason); super.onSaveInstanceState(outState) }
}
