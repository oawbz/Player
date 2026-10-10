package cn.awbz.davplayer.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import cn.awbz.davplayer.data.*
import cn.awbz.davplayer.databinding.ActivityListBinding
import java.util.UUID

class DirectoryActivity : BaseActivity() {
    private lateinit var b: ActivityListBinding
    private lateinit var connection: Connection
    private lateinit var path: String
    private var directories: List<DavEntry> = emptyList()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connection = store.connection(intent.getStringExtra("connection") ?: "") ?: return finish()
        path = savedInstanceState?.getString("path") ?: intent.getStringExtra("path") ?: DavUrls.base(connection.baseUrl).encodedPath()
        b = ActivityListBinding.inflate(layoutInflater); setContentView(b.root)
        b.title.text = "选择分类目录 · ${connection.name}"
        b.back.text = "上一级"; b.back.setOnClickListener { up() }
        b.action.visibility = View.VISIBLE; b.action.text = "选择此目录"
        b.action.setOnClickListener {
            prompt("分类名称", intent.getStringExtra("name") ?: "") { name ->
                store.saveCategory(Category(intent.getStringExtra("category") ?: UUID.randomUUID().toString(), name, connection.id, path))
                setResult(RESULT_OK); finish()
            }
        }
        b.list.setOnItemClickListener { _, _, position, _ -> path = directories[position].url.encodedPath(); load() }
        load()
    }
    private fun load() {
        resetJobs(); b.action.isEnabled = false; b.status.text = "正在加载目录…"; b.list.adapter = null
        job({ library.list(connection, path, it, true).filter { entry -> entry.directory } }, {
            directories = it; b.action.isEnabled = true
            val decoded = DavUrls.fromPath(connection, path).pathSegments().filter { segment -> segment.isNotEmpty() }.joinToString("/", prefix = "/")
            b.status.text = "$decoded\n${if (it.isEmpty()) "没有子目录，可以选择当前目录" else "进入目录后点击“选择此目录”"}"
            b.list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, it.map { entry -> "▸  ${entry.name}" })
        }, { message -> b.status.text = message; b.action.isEnabled = false })
    }
    override fun onSaveInstanceState(outState: Bundle) { if (::path.isInitialized) outState.putString("path", path); super.onSaveInstanceState(outState) }
    private fun up() {
        val base = DavUrls.base(connection.baseUrl)
        val current = DavUrls.directory(DavUrls.fromPath(connection, path))
        if (current.encodedPath() == base.encodedPath()) { finish(); return }
        val parent = current.resolve("../") ?: base
        path = if (DavUrls.inside(base, parent)) parent.encodedPath() else base.encodedPath(); load()
    }
    @Deprecated("Framework API") override fun onBackPressed() { up() }
}
