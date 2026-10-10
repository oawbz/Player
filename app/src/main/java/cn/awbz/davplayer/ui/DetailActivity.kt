package cn.awbz.davplayer.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import cn.awbz.davplayer.data.*
import cn.awbz.davplayer.databinding.ActivityListBinding

class DetailActivity : BaseActivity() {
    private lateinit var b: ActivityListBinding
    private lateinit var work: Work
    private lateinit var header: LinearLayout
    private var selectedSeason = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        work = Work(intent.getStringExtra("connection") ?: return finish(), intent.getStringExtra("path") ?: return finish(),
            intent.getStringExtra("name") ?: "作品", intent.getStringExtra("cover"))
        b = ActivityListBinding.inflate(layoutInflater); setContentView(b.root)
        b.title.text = work.name; b.back.setOnClickListener { finish() }
        b.action.visibility = View.VISIBLE; b.action.text = "刷新"; b.action.setOnClickListener { library.clear(); load() }
        header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        b.list.addHeaderView(header, null, false)
    }
    override fun onResume() { super.onResume(); load() }
    private fun load() {
        resetJobs(); b.list.adapter = null; header.removeAllViews(); b.status.text = "正在读取作品…"
        val connection = store.connection(work.connectionId) ?: return error("连接配置不存在")
        job({ library.detail(connection, work, it) }, { detail ->
            work = detail.work
            b.status.text = if (detail.episodes.isEmpty()) "未找到支持识别的视频文件" else "${detail.episodes.size} 个视频 · 按名称排序"
            val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "${work.name}封面" }
            header.addView(image, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200)))
            job({ app.posters.load(connection, work, it) }, { image.setImageBitmap(it.second) }, { })
            header.addView(text(detail.description.ifEmpty { "暂无简介，可在作品目录添加 description.txt" }))
            if (detail.notices.isNotEmpty()) header.addView(text(detail.notices.joinToString("\n"), 14f))
            val last = store.history().firstOrNull { it.connectionId == work.connectionId && it.workPath == work.path }
            if (last != null) header.addView(button("继续播放 · ${last.videoName} · ${formatTime(last.position)}") {
                PlayerActivity.launch(this, work, Episode(last.videoPath, last.videoName, last.season), last.position)
            })
            if (detail.episodes.size == 1 && last == null) header.addView(button("播放") {
                PlayerActivity.launch(this, work, detail.episodes[0])
            })
            if (detail.seasons.isEmpty()) return@job
            var index = detail.seasons.indexOfFirst { it.name == selectedSeason }
            if (index < 0) index = detail.seasons.indexOfFirst { it.name == last?.season }.coerceAtLeast(0)
            fun showSeason(which: Int) {
                selectedSeason = detail.seasons[which].name
                val episodes = detail.seasons[which].episodes
                b.list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, episodes.map { it.name })
                b.list.setOnItemClickListener { _, _, position, _ ->
                    val episode = episodes.getOrNull(position - b.list.headerViewsCount) ?: return@setOnItemClickListener
                    PlayerActivity.launch(this, work, episode, if (last?.videoPath == episode.path) last.position else 0)
                }
            }
            if (detail.seasons.size > 1) {
                val spinner = Spinner(this)
                spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, detail.seasons.map { it.name.ifEmpty { "未分季" } })
                spinner.setSelection(index); header.addView(spinner)
                spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { showSeason(position) }
                }
            }
            showSeason(index)
        }, { b.status.text = it })
    }
}
