package cn.awbz.davplayer.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import cn.awbz.davplayer.data.*
import cn.awbz.davplayer.databinding.ActivityMainBinding
import cn.awbz.davplayer.databinding.ItemBrowseAllBinding

class MainActivity : PosterActivity() {
    private lateinit var b: ActivityMainBinding
    private var lastReadAt = 0L
    private var loadedConnection: Connection? = null
    private var loadedCategories: List<Category> = emptyList()
    private var loadedHistory: List<History> = emptyList()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater); setContentView(b.root)
        icon(b.settings, PlayerIcon.Kind.SETTINGS)
        b.settings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
    }
    override fun onResume() {
        super.onResume()
        val fresh = lastReadAt != 0L && System.nanoTime() / 1_000_000 - lastReadAt < 30_000
        if (!fresh || store.connection() != loadedConnection || store.categories() != loadedCategories || store.history() != loadedHistory) load()
    }
    private fun load() {
        clearPosterRequests(); resetJobs(); b.rows.removeAllViews()
        lastReadAt = 0
        loadedConnection = store.connection(); loadedCategories = store.categories(); loadedHistory = store.history()
        val histories = store.history().filterNot { MediaRules.isHidden(it.workName) || MediaRules.isHidden(it.videoName) || MediaRules.isHidden(it.season) }
        if (histories.isNotEmpty()) {
            b.rows.addView(text("继续播放", 22f))
            val row = row()
            histories.forEach { h -> row.addView(poster(Work(h.connectionId, h.workPath, h.workName, h.coverPath), h), cardParams()) }
        }
        val categories = loadedCategories
        var remaining = categories.size
        var allSucceeded = true
        fun completed(success: Boolean) {
            allSucceeded = allSucceeded && success
            remaining--
            if (remaining == 0 && allSucceeded) lastReadAt = System.nanoTime() / 1_000_000
        }
        b.status.text = if (categories.isEmpty()) "在设置中连接 OpenList，并添加分类开始观看" else ""
        b.status.visibility = if (categories.isEmpty()) View.VISIBLE else View.GONE
        if (categories.isEmpty()) {
            lastReadAt = System.nanoTime() / 1_000_000
            b.rows.addView(button("连接 OpenList / 添加分类") { b.settings.performClick() }); return
        }
        for (category in categories) {
            val heading = button("${category.name}  ›") {
                startActivity(Intent(this, CategoryActivity::class.java).putExtra("category", category.id))
            }.apply { textSize = 22f; setTypeface(null, android.graphics.Typeface.BOLD) }
            b.rows.addView(heading)
            val status = text("正在加载…"); b.rows.addView(status)
            val row = row()
            val connection = store.connection(category.connectionId)
            if (connection == null) { status.text = "连接配置不存在，请到设置中检查"; completed(false); continue }
            var displayed: List<Work>? = null
            fun show(works: List<Work>) {
                heading.text = getString(cn.awbz.davplayer.R.string.category_heading, category.name)
                status.text = if (works.isEmpty()) "此分类暂无作品文件夹" else ""
                status.visibility = if (works.isEmpty()) View.VISIBLE else View.GONE
                if (displayed == works) return
                displayed = works; row.removeAllViews()
                works.take(12).forEach { row.addView(poster(it), cardParams()) }
                if (works.size > 12) row.addView(browseAll(category), cardParams())
            }
            fun refresh() {
                job({ library.works(connection, category, it) }, { show(it); completed(true) }, { message ->
                    completed(false)
                    status.visibility = View.VISIBLE; status.text = message
                    if (displayed == null) row.addView(button("重试") { load() }, cardParams())
                })
            }
            refresh()
        }
    }
    private fun browseAll(category: Category): View {
        val card = ItemBrowseAllBinding.inflate(layoutInflater)
        card.icon.setImageDrawable(PlayerIcon(PlayerIcon.Kind.EPISODES))
        card.icon.setColorFilter(resources.getColor(cn.awbz.davplayer.R.color.accent))
        card.root.contentDescription = getString(cn.awbz.davplayer.R.string.browse_all_category, category.name)
        card.root.setOnClickListener {
            startActivity(Intent(this, CategoryActivity::class.java).putExtra("category", category.id))
        }
        return card.root
    }
    private fun row(): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(16)) }
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
        b.rows.addView(scroll); return row
    }
    private fun cardParams() = LinearLayout.LayoutParams(dp(144), LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(12) }
}
