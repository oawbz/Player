package cn.awbz.davplayer.ui

import android.graphics.Color
import android.graphics.Rect
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
    private var loadedConnection: Connection? = null
    private var activeAssets: DetailAssets? = null
    private var forceSelectedResources = false
    private var episodePage = 0
    private var directoryLoading = false
    private var directoryNotices: List<String> = emptyList()
    private var directoryError: String? = null
    private fun assets(connection: Connection) = DetailAssets(Nfo.path(connection, work.path), PosterFiles.path(connection, work.path))
    private var selectedSeason = ""
    private var resourceScope = RequestScope()
    private var resourceFuture: java.util.concurrent.Future<*>? = null
    private var resourceGeneration = 0
    private val landscape get() = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    private fun dimension(id: Int) = resources.getDimensionPixelSize(id)
    private val posterBounds = Rect()
    private val descriptionBounds = Rect()
    private val posterPreviews = mutableListOf<ImageView>()
    private var renderedFacts: List<DetailFacts.Fact>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        work = Work(intent.getStringExtra("connection") ?: return finish(), intent.getStringExtra("path") ?: return finish(),
            intent.getStringExtra("name") ?: "作品", intent.getStringExtra("cover"))
        b = ActivityDetailBinding.inflate(layoutInflater); setContentView(b.root)
        b.cover.setImageDrawable(PosterPlaceholder()); b.title.text = work.name; b.play.isEnabled = false
        val fitPlot = Runnable {
            if (alive && landscape) {
                b.hero.getDrawingRect(posterBounds)
                b.root.offsetDescendantRectToMyCoords(b.hero, posterBounds)
                b.description.getDrawingRect(descriptionBounds)
                b.root.offsetDescendantRectToMyCoords(b.description, descriptionBounds)
                b.description.fitHeight(posterBounds.bottom - descriptionBounds.top)
            }
        }
        val plotLayout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (landscape) { handler.removeCallbacks(fitPlot); handler.post(fitPlot) }
        }
        b.hero.addOnLayoutChangeListener(plotLayout)
        b.description.addOnLayoutChangeListener(plotLayout)
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
    override fun onResume() {
        super.onResume()
        val connection = store.connection(work.connectionId) ?: return finish()
        if (detail == null || loadedConnection != connection) load()
        else {
            render(detail!!)
        }
    }
    private fun load(force: Boolean = false) {
        cancelResources(); resetJobs()
        val connection = store.connection(work.connectionId) ?: return error("连接配置不存在")
        if (loadedConnection != null && loadedConnection != connection) { detail = null }
        loadedConnection = connection
        forceSelectedResources = force
        directoryLoading = true; directoryError = null
        b.play.isEnabled = false; b.status.visibility = View.VISIBLE; b.status.text = "正在读取目录…"
        val base = detail ?: WorkDetail(work, emptyList(), "", assets = assets(connection))
        // Show disk resources before starting any directory/network reads. Directory failure keeps them visible.
        job({ app.detailResources.cached(connection, base.assets) }, { cached ->
            applyResources(base, cached)
            readDirectory(connection)
        }, { readDirectory(connection) })
    }
    private fun readDirectory(connection: Connection) {
        job({ library.detail(connection, work, it) }, { result ->
            directoryLoading = false; directoryError = null
            directoryNotices = result.notices
            detail = result; work = result.work
            render(result)
        }, {
            directoryLoading = false; directoryError = it
            detail?.let { result -> render(result) }
            b.status.visibility = View.VISIBLE; b.status.text = it
            if (detail == null) loadResources(connection, WorkDetail(work, emptyList(), "", assets = assets(connection)), forceSelectedResources)
            // Cached artwork and NFO remain available even when directories cannot be read.
        })
    }

    private fun cancelResources() {
        resourceGeneration++
        activeAssets = null
        val previous = resourceScope; resourceScope = RequestScope()
        resourceFuture?.cancel(true); resourceFuture = null
        app.storageWorker.execute { previous.cancel() }
    }
    private fun loadResources(connection: Connection, base: WorkDetail, force: Boolean) {
        if (!force && activeAssets == base.assets) return
        cancelResources()
        activeAssets = base.assets
        // Never show the previous season while the selected season's disk cache is read.
        applyResources(base, DetailResources.Snapshot())
        val expected = resourceGeneration; val scope = resourceScope
        // Cache I/O and bitmap decoding stay off the UI thread.
        job({ app.detailResources.cached(connection, base.assets) }, { cached ->
            if (expected != resourceGeneration || scope.isCancelled) return@job
            applyResources(base, cached)
            refreshResources(connection, base, cached, force)
        }, { /* Resources are optional; directory playback remains available. */ })
    }
    private fun refreshResources(connection: Connection, base: WorkDetail, cached: DetailResources.Snapshot, force: Boolean) {
        val missingCover = base.assets.cover != null && cached.cover == null
        val missingNfo = base.assets.nfo != null && !cached.nfoCached
        if (!force && !missingCover && !missingNfo) return
        val expected = resourceGeneration; val scope = resourceScope
        resourceFuture = app.detailResources.refresh(connection, base.assets, force, scope) { update ->
            handler.post {
                if (alive && expected == resourceGeneration && !scope.isCancelled) applyResources(base, update)
            }
        }
    }
    private fun applyResources(base: WorkDetail, snapshot: DetailResources.Snapshot) {
        val updated = base.copy(metadata = snapshot.metadata, description = snapshot.metadata.plot,
            notices = directoryNotices + snapshot.notices,
            work = base.work.copy(coverPath = if (snapshot.cover != null) base.assets.cover else null))
        // Presentation may use season assets; keep the root directory/playback identity intact.
        work = updated.work
        renderMetadata(updated)
        if (snapshot.cover != null) b.cover.setImageBitmap(snapshot.cover) else b.cover.setImageDrawable(PosterPlaceholder())
        posterPreviews.forEach { image ->
            if (snapshot.cover != null) image.setImageBitmap(snapshot.cover) else image.setImageDrawable(PosterPlaceholder())
        }
    }
    override fun onPause() { cancelResources(); super.onPause() }
    private fun updatePresentation(result: WorkDetail) {
        if (!landscape) return
        // Movie artwork anchors the page; series leave room below for episode navigation.
        val movie = result.seasons.none { it.name.isNotBlank() } && result.episodes.size <= 1
        val screen = resources.displayMetrics
        val availableHeight = (screen.heightPixels - dimension(R.dimen.detail_toolbar_height) - dp(32)).coerceAtLeast(dp(168))
        val posterHeight = minOf(dp(if (movie) 360 else 216),
            (availableHeight * if (movie) .90f else .60f).toInt(),
            (screen.widthPixels * .28f * 1.5f).toInt()).coerceAtLeast(dp(144))
        val posterWidth = posterHeight * 2 / 3
        b.hero.layoutParams = b.hero.layoutParams.apply { width = posterWidth; height = posterHeight }
        val rightWidth = screen.widthPixels - 2 * dimension(R.dimen.detail_content_padding) - dp(24) - posterWidth
        val buttonSpace = rightWidth.coerceAtLeast(dp(48))
        b.play.minWidth = minOf(dp(if (movie) 240 else 320), buttonSpace)
        b.play.maxWidth = if (movie) minOf(dp(240), buttonSpace) else buttonSpace
        b.play.layoutParams = b.play.layoutParams.apply {
            width = if (movie) minOf(dp(240), buttonSpace) else android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        }
        val tall = resources.configuration.screenHeightDp >= 480
        b.title.textSize = if (tall) 28f else 22f
        b.description.textSize = if (tall) 16f else 14f
        b.seasons.visibility = if (movie || result.episodes.isEmpty()) View.GONE else View.VISIBLE
    }
    private fun renderMetadata(result: WorkDetail) {
        updatePresentation(result)
        val meta = result.metadata
        b.title.text = meta.title.ifEmpty { work.name }
        val info = mutableListOf<String>()
        meta.rating?.let { info.add("★ " + String.format(Locale.ROOT, "%.1f", it)) }
        if (meta.year.isNotEmpty()) info.add(meta.year)
        meta.runtimeMinutes?.let { info.add(if (it >= 60) "${it / 60} 小时 ${it % 60} 分钟" else "$it 分钟") }
        if (result.episodes.size > 1) info.add("共 ${result.episodes.size} 集")
        b.metadata.text = info.joinToString("  ·  "); b.metadata.visibility = if (info.isEmpty()) View.GONE else View.VISIBLE
        b.genres.text = meta.genres.joinToString(" / "); b.genres.visibility = if (meta.genres.isEmpty()) View.GONE else View.VISIBLE
        val hasPlot = meta.plot.isNotBlank()
        b.plotHeading.visibility = if (hasPlot && !landscape) View.VISIBLE else View.GONE
        b.description.visibility = if (hasPlot) View.VISIBLE else View.GONE
        b.description.setPlot(if (hasPlot) meta.plot else "", if (hasPlot) ({ showDescription(meta.plot) }) else null)
        renderFacts(meta)
        b.status.text = if (directoryLoading) "正在读取目录…" else directoryError ?: result.notices.joinToString("\n").ifEmpty { if (result.episodes.isEmpty()) "未找到视频文件" else "" }
        b.status.visibility = if (b.status.text.isEmpty()) View.GONE else View.VISIBLE
    }
    private fun renderFacts(meta: MediaMetadata) {
        val facts = DetailFacts.of(meta)
        if (facts == renderedFacts) return
        renderedFacts = facts
        b.facts.removeAllViews()
        b.facts.visibility = if (facts.isEmpty()) View.GONE else View.VISIBLE
        if (facts.isEmpty()) return
        val columns = if (landscape && resources.configuration.screenWidthDp >= 700) 2 else 1
        var row: LinearLayout? = null
        var used = 0
        for (fact in facts) {
            if (row == null || used == columns || fact.wide) {
                if (row != null && used < columns) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
                b.facts.addView(row, LinearLayout.LayoutParams(-1, -2))
                used = 0
            }
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(12))
                setBackgroundResource(R.drawable.detail_fact_panel)
            }
            cell.addView(TextView(this).apply {
                text = fact.label; textSize = 12f
                setTextColor(resources.getColor(R.color.text_secondary))
            }, LinearLayout.LayoutParams(-1, -2))
            cell.addView(TextView(this).apply {
                text = fact.value; textSize = if (landscape) 14f else 16f
                setTextColor(resources.getColor(R.color.text_primary))
                setLineSpacing(dp(3).toFloat(), 1f)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply {
                bottomMargin = dp(8)
                if (!fact.wide && used == 0 && columns == 2) rightMargin = dp(8)
            })
            used++
            if (fact.wide) { row = null; used = 0 }
        }
        if (row != null && used < columns) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
    }
    private fun render(result: WorkDetail) {
        renderMetadata(result)
        val last = store.history().firstOrNull { it.connectionId == work.connectionId && it.workPath == work.path }
        val lastEpisode = result.episodes.firstOrNull { it.path == last?.videoPath }
        val playable = lastEpisode ?: result.episodes.firstOrNull()
        b.play.isEnabled = playable != null
        val resumeNumber = result.seasons.firstOrNull { season -> season.episodes.any { it.path == lastEpisode?.path } }
            ?.episodes?.indexOfFirst { it.path == lastEpisode?.path }?.plus(1) ?: 1
        val resumePosition = PlaybackProgress.resumePosition(last?.position ?: 0, last?.duration ?: 0)
        b.play.text = if (lastEpisode != null && last != null && last.duration > 0 && last.position >= last.duration) "重新播放" else if (lastEpisode != null && last != null && last.position > 0) "继续播放 ${if (result.episodes.size > 1) "第 ${resumeNumber} 集 · " else ""}${formatTime(last.position)}" else "播放"
        b.play.setOnClickListener { if (playable != null) launch(playable, if (playable.path == last?.videoPath) resumePosition else 0) }
        b.seasons.removeAllViews(); posterPreviews.clear()
        val hasSeasons = result.seasons.any { it.name.isNotBlank() }
        if (result.episodes.isEmpty() || (result.episodes.size == 1 && !hasSeasons)) {
            loadedConnection?.let { loadResources(it, result, forceSelectedResources) }
            forceSelectedResources = false
            return
        }
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
        if (hasSeasons) b.seasons.addView(tabScroll)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val episodesScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
        b.seasons.addView(episodesScroll, LinearLayout.LayoutParams(-1, -2).apply {
            if (hasSeasons) topMargin = dimension(R.dimen.episode_season_gap)
        })
        fun select(index: Int, page: Int = 0) {
            selectedSeason = result.seasons[index].name; row.removeAllViews(); posterPreviews.clear()
            val episodes = result.seasons[index].episodes
            val window = EpisodePage.of(episodes.size, page)
            val pages = window.pages
            episodePage = window.page
            if (episodePage > 0) row.addView(button("上一页") { select(index, episodePage - 1); row.getChildAt(if (episodePage > 0) 1 else 0)?.requestFocus() }, LinearLayout.LayoutParams(dp(100), -2))
            for (i in 0 until tabs.childCount) tabs.getChildAt(i).isSelected = i == index
            episodes.subList(window.start, window.end).forEachIndexed { offset, episode ->
                val number = window.start + offset
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4))
                    isFocusable = true; isClickable = true; setBackgroundResource(R.drawable.focus_card)
                    contentDescription = "播放 ${episode.name}"
                    setOnClickListener { launch(episode, if (episode.path == last?.videoPath) resumePosition else 0) }
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
            if (pages > 1) {
                row.addView(text("${episodePage + 1} / $pages 页"))
                if (episodePage + 1 < pages) row.addView(button("下一页") { select(index, episodePage + 1); row.getChildAt(1)?.requestFocus() }, LinearLayout.LayoutParams(dp(100), -2))
            }
            episodesScroll.scrollTo(0, 0)
            val season = result.seasons[index]
            loadedConnection?.let { loadResources(it, result.copy(assets = season.assets ?: result.assets), forceSelectedResources) }
            forceSelectedResources = false
        }
        result.seasons.forEachIndexed { index, season ->
            tabs.addView(button(season.name.ifEmpty { "第 ${index + 1} 季" }) { select(index) }.apply { setBackgroundResource(R.drawable.ui_button); textSize = if (landscape) 16f else 18f }, LinearLayout.LayoutParams(-2, dimension(R.dimen.episode_season_height)).apply { rightMargin = dp(16) })
        }
        var index = result.seasons.indexOfFirst { it.name == selectedSeason }
        if (index < 0) index = result.seasons.indexOfFirst { it.name == last?.season }.coerceAtLeast(0)
        select(index, episodePage)
    }
    private fun launch(episode: Episode, position: Long) {
        val connection = loadedConnection ?: return
        detail?.let { library.handoff(connection, it) }
        PlayerActivity.launch(this, work, episode, position)
    }
    private fun showDescription(plot: String) {
        val restoreFocus = b.description.hasFocus()
        UiDialog.Builder(this).setTitle("剧情简介").setView(text(plot, 16f).apply {
            setTextColor(resources.getColor(R.color.text_secondary))
            setPadding(0, 0, dp(8), dp(12)); setLineSpacing(dp(6).toFloat(), 1f)
        }).setPositiveButton("关闭", null).create().apply {
            setOnDismissListener { if (restoreFocus) b.description.requestFocus() }
            show()
        }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("season", selectedSeason); super.onSaveInstanceState(outState) }
}
