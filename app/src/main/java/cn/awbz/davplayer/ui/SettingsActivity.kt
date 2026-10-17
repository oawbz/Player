package cn.awbz.davplayer.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import cn.awbz.davplayer.data.*
import cn.awbz.davplayer.R
import cn.awbz.davplayer.databinding.ActivityPageBinding
import java.util.UUID

class SettingsActivity : BaseActivity() {
    private var restoreConnectionForm: (() -> Unit)? = null
    private var cacheAction = false
    private lateinit var b: ActivityPageBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        b = page("设置")
    }
    override fun onResume() { super.onResume(); render() }
    override fun onPause() {
        restoreConnectionForm?.invoke()
        super.onPause()
    }
    private fun render() {
        b.content.removeAllViews(); b.status.text = "管理连接和媒体分类"
        b.content.addView(text("OpenList 连接", 22f))
        val connection = store.connection()
        if (connection == null) b.content.addView(button("＋ 添加 OpenList 连接") { editConnection(null) })
        else b.content.addView(button(connection.name + " · 编辑连接  ›") { editConnection(connection) })
        b.content.addView(text("分类", 22f))
        store.categories().forEach { category ->
            b.content.addView(button(category.name + "  ›") {
                UiDialog.Builder(this).setTitle(category.name).setItems(arrayOf("修改名称", "重新选择目录", "删除分类")) { _, which ->
                    when (which) {
                        0 -> prompt("分类名称", category.name) { store.saveCategory(category.copy(name = it)); render() }
                        1 -> chooseDirectory(category)
                        2 -> UiDialog.Builder(this).setTitle("删除分类？").setMessage("仅删除分类配置，服务器文件及播放记录会保留。")
                            .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ -> store.deleteCategory(category.id); render() }.show()
                    }
                }.show()
            })
        }
        b.content.addView(button("＋ 添加分类") { chooseDirectory(null) })
        val cacheStatus = text("正在统计资料缓存…", 14f)
        b.content.addView(text("资料缓存", 22f)); b.content.addView(cacheStatus)
        val clear = button(if (cacheAction) "正在清理…" else "清理海报和简介缓存") {
            UiDialog.Builder(this).setTitle("清理资料缓存？")
                .setMessage("删除本机缓存的海报和简介，包括旧连接留下的缓存。配置、分类、观看记录及服务器文件不受影响；之后按需重新获取。")
                .setNegativeButton("取消", null).setPositiveButton("清理") { _, _ ->
                    if (!cacheAction) {
                        cacheAction = true; render()
                        val scope = RequestScope()
                        val future = app.detailResources.clearCache(scope)
                        app.workers.execute {
                            var success = false
                            try { future.get(); success = true } catch (_: Exception) { }
                            handler.post {
                                cacheAction = false
                                if (alive && !isFinishing) {
                                    render()
                                    if (!success) error("部分缓存未能清理，请稍后重试")
                                }
                            }
                        }
                    }
                }.show()
        }
        clear.isEnabled = !cacheAction; b.content.addView(clear)
        job({ app.detailResources.cacheUsage() }, { usage ->
            cacheStatus.text = "%.1f MiB · %d 个缓存文件".format(java.util.Locale.ROOT, usage.bytes / 1048576.0, usage.files)
        }, { cacheStatus.text = "缓存统计暂不可用" })
        b.content.addView(text("使用 OpenList API，添加文件夹作为分类，读取 movie.nfo 和 poster.jpg；海报优先读取缓存，可见内容缺图自动下载，分类和详情可手动刷新。\n账号密码保存在应用私有区域，禁止系统备份；旧系统无法抵抗 root 或设备被控制。", 14f))
    }
    private fun chooseDirectory(category: Category?) {
        val connection = store.connection() ?: return error("请先添加 OpenList 连接")
        startActivityForResult(Intent(this, DirectoryActivity::class.java).putExtra("connection", connection.id)
            .putExtra("category", category?.id).putExtra("name", category?.name)
            .putExtra("path", if (category?.connectionId == connection.id) category.path else null), 1)
    }
    private fun styleAuthOption(option: RadioButton) {
        option.buttonDrawable = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        option.setBackgroundResource(R.drawable.ui_button)
        option.setPadding(dp(12), dp(12), dp(12), dp(12)); option.minHeight = dp(48)
        option.layoutParams = RadioGroup.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8); bottomMargin = dp(12) }
    }
    private fun editConnection(old: Connection?) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        fun input(hintText: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText = EditText(this).apply {
            container.addView(text(hintText, 14f))
            setBackgroundResource(R.drawable.ui_input); setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
            hint = hintText; setText(value); inputType = type; setSingleLine(true); isSaveEnabled = false; container.addView(this)
        }
        val name = input("连接名称", old?.name ?: "OpenList")
        val url = input("OpenList 服务地址", old?.baseUrl ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val mode = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL; container.addView(this) }
        mode.addView(RadioButton(this).apply { id = R.id.auth_anonymous; text = "匿名访问"; styleAuthOption(this) })
        mode.addView(RadioButton(this).apply { id = R.id.auth_basic; text = "用户名密码"; styleAuthOption(this) })
        val user = input("用户名", old?.username ?: "")
        val password = input("密码（可留空）", old?.password ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val note = text("先测试连接，成功后保存。使用 HTTP 时账号和视频会以明文传输。", 14f); container.addView(note)
        fun updateMode() {
            val authenticated = mode.checkedRadioButtonId == R.id.auth_basic
            user.visibility = if (authenticated) View.VISIBLE else View.GONE
            password.visibility = if (authenticated) View.VISIBLE else View.GONE
        }
        mode.setOnCheckedChangeListener { _, _ -> updateMode() }
        mode.check(if (old?.authMode == AuthMode.BASIC) R.id.auth_basic else R.id.auth_anonymous)
        updateMode()
        val scroll = ScrollView(this).apply { addView(container) }
        val dialog = UiDialog.Builder(this).setTitle(if (old == null) "添加连接" else "编辑连接").setView(scroll)
            .setNegativeButton("取消", null).setPositiveButton("测试并保存", null).create()
        fun enableForm(enabled: Boolean) {
            listOf(name, url, user, password).forEach { it.isEnabled = enabled }
            for (i in 0 until mode.childCount) mode.getChildAt(i).isEnabled = enabled
        }
        val restore = {
            if (dialog.isShowing) {
                enableForm(true); dialog.getButton(UiDialog.BUTTON_POSITIVE).isEnabled = true
                note.text = "先测试连接，成功后保存。"
            }
        }
        restoreConnectionForm = restore
        dialog.setOnDismissListener { if (restoreConnectionForm === restore) restoreConnectionForm = null }
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(UiDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val label = name.text.toString().trim()
                    if (label.isEmpty() || label.length > 80) { name.error = "请输入 1～80 个字符"; return@setOnClickListener }
                    val authMode = if (mode.checkedRadioButtonId == R.id.auth_basic) AuthMode.BASIC else AuthMode.ANONYMOUS
                    if (authMode == AuthMode.BASIC && user.text.toString().isBlank()) { user.error = "请输入用户名"; return@setOnClickListener }
                    val base = MediaPaths.base(url.text.toString())
                    val connection = Connection(old?.id ?: UUID.randomUUID().toString(),
                        label, base.toString(), if (authMode == AuthMode.BASIC) user.text.toString() else "", if (authMode == AuthMode.BASIC) password.text.toString() else "", authMode)
                    enableForm(false); dialog.getButton(UiDialog.BUTTON_POSITIVE).isEnabled = false; note.text = "正在测试连接…"
                    job({ library.list(connection, base.rawPath, it) }, {
                        if (dialog.isShowing) {
                            fun save(replaceServer: Boolean) {
                                try {
                                    store.saveConnection(connection, replaceServer); dialog.dismiss(); render()
                                } catch (_: Exception) { restore(); note.text = "配置未能保存，请检查目录或稍后重试" }
                            }
                            if (old != null && old.baseUrl != connection.baseUrl) {
                                enableForm(true); dialog.getButton(UiDialog.BUTTON_POSITIVE).isEnabled = true
                                UiDialog.Builder(this).setTitle("服务地址已改变")
                                    .setMessage("同一服务换地址将保留分类和观看记录。更换为另一服务将重置分类和观看记录，避免使用旧进度；资料缓存保留，可手动清理。")
                                    .setItems(arrayOf("同一服务换地址，保留分类和记录", "更换服务，重置分类和记录")) { _, which ->
                                        if (dialog.isShowing) save(which == 1)
                                    }.setNegativeButton("取消", null).show()
                            } else save(false)
                        }
                    }, { message -> if (dialog.isShowing) { enableForm(true); note.text = message; dialog.getButton(UiDialog.BUTTON_POSITIVE).isEnabled = true } })
                } catch (e: MediaFailure) { note.text = e.userMessage }
            }
        }
        dialog.show()
    }
}
