package cn.awbz.davplayer.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import cn.awbz.davplayer.data.*
import cn.awbz.davplayer.databinding.ActivityGridBinding
import cn.awbz.davplayer.databinding.ItemPosterBinding

class CategoryActivity : PosterActivity() {
    private var displayed: List<Work>? = null
    private lateinit var b: ActivityGridBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityGridBinding.inflate(layoutInflater); setContentView(b.root)
        b.grid.columnWidth = dp(if (resources.configuration.screenWidthDp < 600) 112 else 160)
        icon(b.back, PlayerIcon.Kind.BACK); icon(b.refresh, PlayerIcon.Kind.REPLAY)
        b.refresh.drawable.setColorFilter(resources.getColor(cn.awbz.davplayer.R.color.text_secondary), android.graphics.PorterDuff.Mode.SRC_IN)
        b.back.setOnClickListener { finish() }
        b.refresh.contentDescription = "刷新分类海报和简介"
        b.refresh.setOnClickListener {
            load()
            val category = store.categories().firstOrNull { it.id == intent.getStringExtra("category") }
            val connection = category?.let { store.connection(it.connectionId) }
            if (category != null && connection != null) refreshMetadata(connection, category, b.status, b.refresh)
        }
        load()
    }
    override fun reloadAfterPause() { if (::b.isInitialized) load() }
    private fun load() {
        resetJobs()
        val hadContent = displayed != null
        val category = store.categories().firstOrNull { it.id == intent.getStringExtra("category") } ?: return finish()
        val connection = store.connection(category.connectionId) ?: return error("连接配置不存在")
        b.title.text = category.name; b.status.text = "正在加载…"
        fun show(works: List<Work>, force: Boolean = false) {
            if (!refreshingMetadata) b.status.text = if (works.isEmpty()) "此分类暂无作品文件夹" else "${works.size} 部作品"
            if (displayed == works) {
                if (force) (b.grid.adapter as? BaseAdapter)?.notifyDataSetChanged()
                return
            }
            clearPosterRequests(); displayed = works
            b.grid.setOnItemClickListener { _, _, position, _ -> openWork(works[position]) }
            val first = b.grid.firstVisiblePosition
            b.grid.adapter = object : BaseAdapter() {
                override fun getCount() = works.size
                override fun getItem(position: Int) = works[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val binding = if (convertView == null) ItemPosterBinding.inflate(layoutInflater, parent, false) else ItemPosterBinding.bind(convertView)
                    val width = b.grid.columnWidth
                    (binding.image.parent as View).layoutParams = (binding.image.parent as View).layoutParams.apply {
                        height = (width - dp(12)).coerceAtLeast(dp(64)) * 3 / 2
                    }
                    bindPoster(binding, works[position])
                    binding.root.isFocusable = false; binding.root.isClickable = false
                    return binding.root
                }
            }
            b.grid.setSelection(first)
        }
        fun refresh() { job({ library.works(connection, category, it) }, { show(it, hadContent) }, { b.status.text = it }) }
        refresh()
    }
}
