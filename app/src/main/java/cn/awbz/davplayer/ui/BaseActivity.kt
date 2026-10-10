package cn.awbz.davplayer.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import cn.awbz.davplayer.DavApplication
import cn.awbz.davplayer.data.*
import cn.awbz.davplayer.databinding.ActivityPageBinding
import java.net.SocketTimeoutException
import java.util.concurrent.Future
import javax.net.ssl.SSLException

open class BaseActivity : Activity() {
    protected val app get() = application as DavApplication
    protected val store get() = app.store
    protected val library get() = app.library
    protected val handler = Handler(Looper.getMainLooper())
    protected var requests = Requests()
    private var generation = 0
    private val tasks = mutableListOf<Future<*>>()
    protected var alive = true
    protected fun resetJobs() {
        generation++; requests.cancel(); requests = Requests()
        tasks.forEach { it.cancel(true) }; tasks.clear()
    }
    protected fun <T> job(block: (Requests) -> T, success: (T) -> Unit, failure: (String) -> Unit = { error(it) }) {
        val expected = generation; val scope = requests
        tasks.add(app.workers.submit {
            try {
                val result = block(scope)
                handler.post { if (alive && expected == generation) success(result) }
            } catch (e: Exception) {
                val message = when (e) {
                    is DavFailure -> e.userMessage
                    is SSLException -> "HTTPS 验证失败，请检查服务器证书、设备时间和旧系统 TLS 支持"
                    is SocketTimeoutException -> "连接超时，请检查网络后重试"
                    else -> "加载失败，请检查网络、服务器地址和权限后重试"
                }
                handler.post { if (alive && expected == generation) failure(message) }
            }
        })
    }
    protected fun page(title: String): ActivityPageBinding {
        val b = ActivityPageBinding.inflate(layoutInflater)
        setContentView(b.root); b.title.text = title; b.back.setOnClickListener { finish() }
        return b
    }
    protected fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    protected fun text(value: String, size: Float = 16f) = TextView(this).apply {
        text = value; textSize = size; setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    protected fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; setAllCaps(false); setOnClickListener { action() }
    }
    protected fun error(message: String) { if (alive) AlertDialog.Builder(this).setTitle("提示").setMessage(message).setPositiveButton("知道了", null).show() }
    protected fun prompt(title: String, initial: String = "", done: (String) -> Unit) {
        val input = EditText(this).apply { setText(initial); setSingleLine(true); setPadding(dp(16), dp(12), dp(16), dp(12)) }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isEmpty() || name.length > 80) input.error = "请输入 1～80 个字符" else { done(name); dialog.dismiss() }
        } }; dialog.show()
    }
    override fun onDestroy() { alive = false; resetJobs(); handler.removeCallbacksAndMessages(null); super.onDestroy() }
}

fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%02d:%02d".format(seconds / 60, seconds % 60)
}
