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
    private var directories: List<MediaEntry> = emptyList()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connection = store.connection(intent.getStringExtra("connection") ?: "") ?: return finish()
        path = savedInstanceState?.getString("path") ?: intent.getStringExtra("path") ?: MediaPaths.base(connection.baseUrl).rawPath
        b = ActivityListBinding.inflate(layoutInflater); setContentView(b.root)
        b.title.text = getString(cn.awbz.davplayer.R.string.choose_category_directory, connection.name)
        b.back.text = "上一级"; b.back.setOnClickListener { up() }
        b.action.visibility = View.VISIBLE; b.action.text = "选择此目录"
        b.action.setOnClickListener {
            prompt("分类名称", intent.getStringExtra("name") ?: "") { name ->
                store.saveCategory(Category(intent.getStringExtra("category") ?: UUID.randomUUID().toString(), name, connection.id, path))
                setResult(RESULT_OK); finish()
            }
        }
        b.list.setOnItemClickListener { _, _, position, _ -> path = directories[position].path; load() }
        load()
    }
    override fun reloadAfterPause() { if (::b.isInitialized) load() }
    private fun load() {
        resetJobs(); b.action.isEnabled = false; b.status.text = "正在加载目录…"; b.list.adapter = null
        job({ library.list(connection, path, it).filter { entry -> entry.directory } }, {
            directories = it; b.action.isEnabled = true
            val decoded = MediaPaths.segments(MediaPaths.fromPath(connection, path)).filter { segment -> segment.isNotEmpty() }.joinToString("/", prefix = "/")
            b.status.text = getString(cn.awbz.davplayer.R.string.directory_status, decoded, getString(if (it.isEmpty()) cn.awbz.davplayer.R.string.no_child_directories else cn.awbz.davplayer.R.string.enter_then_choose_directory))
            b.list.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, it.map { entry -> "▸  ${entry.name}" }) {
                override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = super.getView(position, convertView, parent).apply {
                    setBackgroundResource(cn.awbz.davplayer.R.drawable.ui_button)
                    setPadding(dp(16), dp(16), dp(16), dp(16)); minimumHeight = dp(56)
                    (this as android.widget.TextView).setTextColor(resources.getColor(cn.awbz.davplayer.R.color.text_primary))
                }
            }
        }, { message -> b.status.text = message; b.action.isEnabled = false })
    }
    override fun onSaveInstanceState(outState: Bundle) { if (::path.isInitialized) outState.putString("path", path); super.onSaveInstanceState(outState) }
    private fun up() {
        val base = MediaPaths.base(connection.baseUrl)
        val current = MediaPaths.directory(MediaPaths.fromPath(connection, path))
        if (current.rawPath == base.rawPath) { finish(); return }
        path = MediaPaths.parent(connection, path); load()
    }
    @Deprecated("Framework API") override fun onBackPressed() { up() }
}
