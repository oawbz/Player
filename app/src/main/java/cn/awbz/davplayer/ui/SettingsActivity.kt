package cn.awbz.davplayer.ui

import android.app.AlertDialog
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
    private lateinit var b: ActivityPageBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        b = page("设置")
    }
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        b.content.removeAllViews(); b.status.text = "分类配置仅影响应用，不修改服务器文件"
        b.content.addView(text("播放核心", 22f))
        b.content.addView(button(if (store.core == Core.SYSTEM) "当前：系统 MediaPlayer" else "当前：ExoPlayer") {
            AlertDialog.Builder(this).setTitle("默认播放核心").setSingleChoiceItems(arrayOf("系统 MediaPlayer", "ExoPlayer"), store.core.ordinal) { dialog, which ->
                store.core = Core.values()[which]; dialog.dismiss(); render()
            }.setNegativeButton("取消", null).show()
        })
        b.content.addView(text("WebDAV 连接", 22f))
        store.connections().forEach { connection -> b.content.addView(button(connection.name + if (connection.authMode == AuthMode.ANONYMOUS) " · 匿名访问" else " · 用户名密码") { editConnection(connection) }) }
        b.content.addView(button("添加 WebDAV 连接") { editConnection(null) })
        b.content.addView(text("分类", 22f))
        store.categories().forEach { category ->
            b.content.addView(button(category.name) {
                AlertDialog.Builder(this).setTitle(category.name).setItems(arrayOf("修改名称", "重新选择目录", "删除分类")) { _, which ->
                    when (which) {
                        0 -> prompt("分类名称", category.name) { store.saveCategory(category.copy(name = it)); render() }
                        1 -> chooseConnection(category)
                        2 -> AlertDialog.Builder(this).setTitle("删除分类？").setMessage("仅删除分类配置，服务器文件及播放记录会保留。")
                            .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ -> store.deleteCategory(category.id); render() }.show()
                    }
                }.show()
            })
        }
        b.content.addView(button("添加分类") { chooseConnection(null) })
        b.content.addView(text("作品简介：在作品文件夹中放入 description.txt（UTF-8，最多 64 KB）。\n账号密码保存在应用私有区域，禁止系统备份；旧系统无法抵抗 root 或设备被控制。", 14f))
    }
    private fun chooseConnection(category: Category?) {
        val connections = store.connections()
        if (connections.isEmpty()) { error("请先添加 WebDAV 连接"); return }
        fun select(connection: Connection) {
            startActivityForResult(Intent(this, DirectoryActivity::class.java).putExtra("connection", connection.id)
                .putExtra("category", category?.id).putExtra("name", category?.name)
                .putExtra("path", if (category?.connectionId == connection.id) category.path else null), 1)
        }
        if (connections.size == 1) select(connections[0]) else AlertDialog.Builder(this).setTitle("选择 WebDAV 连接")
            .setItems(connections.map { it.name }.toTypedArray()) { _, which -> select(connections[which]) }.show()
    }
    private fun editConnection(old: Connection?) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        fun input(hintText: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText = EditText(this).apply {
            hint = hintText; setText(value); inputType = type; setSingleLine(true); isSaveEnabled = false; container.addView(this)
        }
        val name = input("连接名称", old?.name ?: "")
        val url = input("WebDAV 地址（含根目录）", old?.baseUrl ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val mode = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL; container.addView(this) }
        mode.addView(RadioButton(this).apply { id = R.id.auth_anonymous; text = "匿名访问" })
        mode.addView(RadioButton(this).apply { id = R.id.auth_basic; text = "用户名密码" })
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
        val dialog = AlertDialog.Builder(this).setTitle(if (old == null) "添加连接" else "编辑连接").setView(scroll)
            .setNegativeButton("取消", null).setPositiveButton("测试并保存", null).create()
        fun enableForm(enabled: Boolean) {
            listOf(name, url, user, password).forEach { it.isEnabled = enabled }
            for (i in 0 until mode.childCount) mode.getChildAt(i).isEnabled = enabled
        }
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val label = name.text.toString().trim()
                    if (label.isEmpty() || label.length > 80) { name.error = "请输入 1～80 个字符"; return@setOnClickListener }
                    val authMode = if (mode.checkedRadioButtonId == R.id.auth_basic) AuthMode.BASIC else AuthMode.ANONYMOUS
                    if (authMode == AuthMode.BASIC && user.text.toString().isBlank()) { user.error = "请输入用户名"; return@setOnClickListener }
                    val base = DavUrls.base(url.text.toString())
                    val changedRoot = old != null && base.toString() != old.baseUrl
                    val connection = Connection(if (changedRoot || old == null) UUID.randomUUID().toString() else old.id,
                        label, base.toString(), if (authMode == AuthMode.BASIC) user.text.toString() else "", if (authMode == AuthMode.BASIC) password.text.toString() else "", authMode)
                    enableForm(false); dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false; note.text = "正在测试连接…"
                    job({ library.dav.list(connection, base.encodedPath(), it) }, {
                        if (dialog.isShowing) {
                            // A different endpoint is a new connection; old categories and credentials remain recoverable.
                            store.saveConnection(connection); library.clear(); app.posters.clear(); dialog.dismiss(); render()
                        }
                    }, { message -> if (dialog.isShowing) { enableForm(true); note.text = message; dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true } })
                } catch (e: DavFailure) { note.text = e.userMessage }
            }
        }
        dialog.show()
    }
}
