package cn.awbz.davplayer.ui

import android.app.AlertDialog
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
    private val posterPreviews = mutableListOf<ImageView>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        work = Work(intent.getStringExtra("connection") ?: return finish(), intent.getStringExtra("path") ?: return finish(),
            intent.getStringExtra("name") ?: "作品", intent.getStringExtra("cover"))
        b = ActivityDetailBinding.inflate(layoutInflater); setContentView(b.root)
        b.title.text = work.name; b.play.isEnabled = false
        val screen = resources.displayMetrics
        b.hero.layoutParams = b.hero.layoutParams.apply {
            height = if (screen.widthPixels > screen.heightPixels) dp(300) else minOf((screen.widthPixels * 1.35).toInt(), (screen.heightPixels * .70).toInt())
        }
        b.back.setImageDrawable(PlayerIcon(PlayerIcon.Kind.BACK)); b.back.setOnClickListener { finish() }
        b.refresh.setImageDrawable(PlayerIcon(PlayerIcon.Kind.REPLAY)); b.refresh.setOnClickListener { library.clear(); app.posters.clear(); load() }
        b.play.setCompoundDrawables(PlayerIcon(PlayerIcon.Kind.PLAY).apply { setBounds(0, 0, dp(22), dp(22)); setColorFilter(Color.BLACK, android.graphics.PorterDuff.Mode.SRC_IN) }, null, null, null)
        b.play.compoundDrawablePadding = dp(12); b.play.setPadding(dp(20), 0, dp(20), 0)
        selectedSeason = savedInstanceState?.getString("season").orEmpty()
    }
    override fun onResume() { super.onResume(); if (detail == null) load() else render(detail!!) }
    private fun load() {
        resetJobs(); detail = null; b.play.isEnabled = false; b.status.text = "正在读取影片资料…"; b.seasons.removeAllViews()
        val connection = store.connection(work.connectionId) ?: return error("连接配置不存在")
        job({ library.detail(connection, work, it) }, { result ->
            detail = result; work = result.work; render(result)
            job({ app.posters.load(connection, work, it) }, { b.cover.setImageBitmap(it.second); posterPreviews.forEach { image -> image.setImageBitmap(it.second) } }, { })
        }, { b.status.text = it })
    }
    private fun render(result: WorkDetail) {
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
            val text = text(meta.plot).apply { setPadding(dp(20), dp(12), dp(20), dp(12)) }
            AlertDialog.Builder(this).setTitle("剧情简介").setView(ScrollView(this).apply { addView(text) }).setPositiveButton("关闭", null).show()
        }
        b.status.text = result.notices.joinToString("\n").ifEmpty { if (result.episodes.isEmpty()) "未找到视频文件" else "" }
        b.status.visibility = if (b.status.text.isEmpty()) View.GONE else View.VISIBLE
        val last = store.history().firstOrNull { it.connectionId == work.connectionId && it.workPath == work.path }
        val lastEpisode = result.episodes.firstOrNull { it.path == last?.videoPath }
        val playable = lastEpisode ?: result.episodes.firstOrNull()
        b.play.isEnabled = playable != null
        b.play.text = if (lastEpisode != null && last != null && last.position > 0) "继续播放 ${formatTime(last.position)}" else "播放"
        b.play.setOnClickListener { if (playable != null) PlayerActivity.launch(this, work, playable, if (playable.path == last?.videoPath) last.position else 0) }
        b.seasons.removeAllViews(); posterPreviews.clear()
        if (result.episodes.size <= 1) return
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
        b.seasons.addView(tabScroll)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val episodesScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
        b.seasons.addView(episodesScroll)
        fun select(index: Int) {
            selectedSeason = result.seasons[index].name; row.removeAllViews(); posterPreviews.clear()
            for (i in 0 until tabs.childCount) tabs.getChildAt(i).alpha = if (i == index) 1f else .5f
            result.seasons[index].episodes.forEachIndexed { number, episode ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4))
                    isFocusable = true; isClickable = true; setBackgroundResource(R.drawable.focus_card)
                    contentDescription = "播放 ${episode.name}"
                    setOnClickListener { PlayerActivity.launch(this@DetailActivity, work, episode, if (episode.path == last?.videoPath) last.position else 0) }
                }
                val preview = FrameLayout(this)
                val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageDrawable(b.cover.drawable); setBackgroundColor(Color.rgb(27, 33, 41)) }
                posterPreviews.add(image)
                preview.addView(image, FrameLayout.LayoutParams(-1, -1))
                preview.addView(ImageView(this).apply { setImageDrawable(PlayerIcon(PlayerIcon.Kind.PLAY)); setPadding(dp(10), dp(10), dp(10), dp(10)); setBackgroundResource(R.drawable.player_button) }, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
                card.addView(preview, LinearLayout.LayoutParams(dp(168), dp(94)))
                card.addView(text("${number + 1}. ${episode.name}", 13f).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(dp(168), dp(56)))
                row.addView(card, LinearLayout.LayoutParams(dp(180), -2).apply { rightMargin = dp(8) })
            }
            episodesScroll.scrollTo(0, 0)
        }
        result.seasons.forEachIndexed { index, season ->
            tabs.addView(button(season.name.ifEmpty { "选集" }) { select(index) }.apply { setBackgroundResource(R.drawable.player_button); textSize = 18f }, LinearLayout.LayoutParams(-2, dp(56)).apply { rightMargin = dp(16) })
        }
        var index = result.seasons.indexOfFirst { it.name == selectedSeason }
        if (index < 0) index = result.seasons.indexOfFirst { it.name == last?.season }.coerceAtLeast(0)
        select(index)
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("season", selectedSeason); super.onSaveInstanceState(outState) }
}
