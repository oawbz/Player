package cn.awbz.davplayer.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import cn.awbz.davplayer.data.*
import cn.awbz.davplayer.databinding.ActivityGridBinding
import cn.awbz.davplayer.databinding.ItemPosterBinding

class CategoryActivity : PosterActivity() {
    private lateinit var b: ActivityGridBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityGridBinding.inflate(layoutInflater); setContentView(b.root)
        b.back.setOnClickListener { finish() }
        b.refresh.setOnClickListener { library.clear(); app.posters.clear(); load() }
        load()
    }
    private fun load() {
        resetJobs()
        val category = store.categories().firstOrNull { it.id == intent.getStringExtra("category") } ?: return finish()
        val connection = store.connection(category.connectionId) ?: return error("连接配置不存在")
        b.title.text = category.name; b.status.text = "正在加载…"
        job({ library.works(connection, category, it) }, { works ->
            b.status.text = if (works.isEmpty()) "此分类暂无作品文件夹" else "${works.size} 部作品"
            b.grid.setOnItemClickListener { _, _, position, _ -> openWork(works[position]) }
            b.grid.adapter = object : BaseAdapter() {
                override fun getCount() = works.size
                override fun getItem(position: Int) = works[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val binding = if (convertView == null) ItemPosterBinding.inflate(layoutInflater, parent, false) else ItemPosterBinding.bind(convertView)
                    bindPoster(binding, works[position])
                    binding.root.isFocusable = false; binding.root.isClickable = false
                    return binding.root
                }
            }
        }, { b.status.text = it })
    }
}
