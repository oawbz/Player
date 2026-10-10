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
        b.settings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.refresh.setOnClickListener { library.clear(); app.posters.clear(); load() }
    }
    override fun onResume() { super.onResume(); load() }
    private fun load() {
        resetJobs(); b.rows.removeAllViews()
        val histories = store.history()
        if (histories.isNotEmpty()) {
            b.rows.addView(text("继续播放", 22f))
            val row = row()
            histories.forEach { h -> row.addView(poster(Work(h.connectionId, h.workPath, h.workName, h.coverPath), h), cardParams()) }
        }
        val categories = store.categories()
        b.status.text = if (categories.isEmpty()) "在设置中连接 WebDAV，并添加分类开始观看" else ""
        b.status.visibility = if (categories.isEmpty()) View.VISIBLE else View.GONE
        if (categories.isEmpty()) {
            b.rows.addView(button("连接 WebDAV / 添加分类") { b.settings.performClick() }); return
        }
        for (category in categories) {
            b.rows.addView(button("${category.name}  ›") {
                startActivity(Intent(this, CategoryActivity::class.java).putExtra("category", category.id))
            })
            val status = text("正在加载…"); b.rows.addView(status)
            val row = row()
            val connection = store.connection(category.connectionId)
            if (connection == null) { status.text = "连接配置不存在，请到设置中检查"; continue }
            job({ library.works(connection, category, it) }, { works ->
                status.text = if (works.isEmpty()) "此分类暂无作品文件夹" else "${works.size} 部作品"
                works.take(12).forEach { row.addView(poster(it), cardParams()) }
                if (works.size > 12) row.addView(button("查看全部 ${works.size} 部") {
                    startActivity(Intent(this, CategoryActivity::class.java).putExtra("category", category.id))
                })
            }, { message -> status.text = message; row.addView(button("重试") { load() }) })
        }
    }
    private fun row(): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(16)) }
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
        b.rows.addView(scroll); return row
    }
    private fun cardParams() = LinearLayout.LayoutParams(dp(144), LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(12) }
}
