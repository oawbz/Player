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
    private var loadedAt = 0L
    private var loadedCategory: Category? = null
    private var loadedConnection: Connection? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityGridBinding.inflate(layoutInflater); setContentView(b.root)
        b.grid.columnWidth = dp(if (resources.configuration.screenWidthDp < 600) 112 else 160)
        icon(b.back, PlayerIcon.Kind.BACK); icon(b.refresh, PlayerIcon.Kind.REPLAY)
        b.refresh.drawable.setColorFilter(resources.getColor(cn.awbz.davplayer.R.color.text_secondary), android.graphics.PorterDuff.Mode.SRC_IN)
        b.back.setOnClickListener { finish() }
        b.refresh.contentDescription = "刷新分类列表和海报"
        b.refresh.setOnClickListener {
            resetJobs()
            val category = store.categories().firstOrNull { it.id == intent.getStringExtra("category") }
            val connection = category?.let { store.connection(it.connectionId) }
            if (category != null && connection != null) {
                loadedAt = 0; loadedCategory = category; loadedConnection = connection
                refreshMetadata(connection, category, b.status, b.refresh) { showWorks(it) }
            }
        }
        load()
    }
    override fun reloadAfterPause() {
        val category = store.categories().firstOrNull { it.id == intent.getStringExtra("category") }
        val fresh = loadedAt != 0L && System.nanoTime() / 1_000_000 - loadedAt < 30_000
        if (::b.isInitialized && (!fresh || category != loadedCategory || category?.let { store.connection(it.connectionId) } != loadedConnection)) load()
    }
    private fun load() {
        resetJobs()
        val hadContent = displayed != null
        val category = store.categories().firstOrNull { it.id == intent.getStringExtra("category") } ?: return finish()
        val connection = store.connection(category.connectionId) ?: return error("连接配置不存在")
        loadedAt = 0; loadedCategory = category; loadedConnection = connection
        b.title.text = category.name; b.status.visibility = View.VISIBLE; b.status.text = "正在加载…"
        job({ library.works(connection, category, it) }, { showWorks(it, hadContent) }, { b.status.text = it })
    }
    private fun showWorks(works: List<Work>, force: Boolean = false) {
        loadedAt = System.nanoTime() / 1_000_000
        if (!refreshingMetadata) {
            b.status.text = if (works.isEmpty()) "此分类暂无作品文件夹" else ""
            b.status.visibility = if (works.isEmpty()) View.VISIBLE else View.GONE
        }
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

}
