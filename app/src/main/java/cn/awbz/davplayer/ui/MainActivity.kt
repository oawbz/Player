package cn.awbz.davplayer.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import cn.awbz.davplayer.data.*
import cn.awbz.davplayer.databinding.ActivityMainBinding

class MainActivity : PosterActivity() {
    private lateinit var b: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater); setContentView(b.root)
        icon(b.settings, PlayerIcon.Kind.SETTINGS)
        b.settings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
    }
    override fun onResume() {
        super.onResume()
        load()
    }
    private fun load() {
        clearPosterRequests(); resetJobs(); b.rows.removeAllViews()
        val histories = store.history().filterNot { MediaRules.isHidden(it.workName) || MediaRules.isHidden(it.videoName) || MediaRules.isHidden(it.season) }
        if (histories.isNotEmpty()) {
            b.rows.addView(text("继续播放", 22f))
            val row = row()
            histories.forEach { h -> row.addView(poster(Work(h.connectionId, h.workPath, h.workName, h.coverPath), h), cardParams()) }
        }
        val categories = store.categories()
        b.status.text = if (categories.isEmpty()) "在设置中连接 OpenList，并添加分类开始观看" else ""
        b.status.visibility = if (categories.isEmpty()) View.VISIBLE else View.GONE
        if (categories.isEmpty()) {
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
            if (connection == null) { status.text = "连接配置不存在，请到设置中检查"; continue }
            var displayed: List<Work>? = null
            fun show(works: List<Work>) {
                heading.text = "${category.name}   ·   ${works.size}   ›"
                status.text = if (works.isEmpty()) "此分类暂无作品文件夹" else ""
                status.visibility = if (works.isEmpty()) View.VISIBLE else View.GONE
                if (displayed == works) return
                displayed = works; row.removeAllViews()
                works.take(12).forEach { row.addView(poster(it), cardParams()) }
                if (works.size > 12) row.addView(button("查看全部 ${works.size} 部") {
                    startActivity(Intent(this, CategoryActivity::class.java).putExtra("category", category.id))
                }, cardParams())
            }
            fun refresh() {
                job({ library.works(connection, category, it) }, { show(it) }, { message ->
                    status.visibility = View.VISIBLE; status.text = message
                    if (displayed == null) row.addView(button("重试") { load() }, cardParams())
                })
            }
            refresh()
        }
    }
    private fun row(): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(16)) }
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
        b.rows.addView(scroll); return row
    }
    private fun cardParams() = LinearLayout.LayoutParams(dp(144), LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(12) }
}
