package cn.awbz.davplayer.ui

import cn.awbz.davplayer.R
import android.view.Gravity
import android.graphics.Color
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.Surface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import cn.awbz.davplayer.MediaApplication
import cn.awbz.davplayer.data.*
import cn.awbz.davplayer.databinding.ActivityPageBinding
import java.net.SocketTimeoutException
import java.util.concurrent.Future
import javax.net.ssl.SSLException

open class BaseActivity : Activity() {
    protected open fun displayOrientation(session: Int): Int = session
    protected val app get() = application as MediaApplication
    protected val store get() = app.store
    protected val library get() = app.library
    protected val handler = Handler(Looper.getMainLooper())
    protected var requests = RequestScope()
    private var generation = 0
    private val tasks = mutableListOf<Future<*>>()
    protected var alive = true
    private var jobsPaused = false
    private var playbackPending = false
    internal fun preparePlayback(action: () -> Unit) {
        if (playbackPending || jobsPaused || !alive) return
        playbackPending = true
        val expected = generation
        app.detailResources.gate.pauseAndDrain { handler.post {
            playbackPending = false
            if (alive && !jobsPaused && expected == generation && !isFinishing) action()
            else if (alive && !jobsPaused) app.detailResources.gate.resumeBrowsing()
        } }
    }
    protected open fun reloadAfterPause() = Unit
    protected fun resetJobs() {
        generation++; requests.cancel(); requests = RequestScope()
        tasks.forEach { it.cancel(true) }; tasks.clear()
    }
    protected fun <T> job(block: (RequestScope) -> T, success: (T) -> Unit, failure: (String) -> Unit = { error(it) }, executor: java.util.concurrent.ExecutorService = app.workers) {
        if (jobsPaused || !alive) return
        val expected = generation; val scope = requests
        tasks.add(executor.submit {
            try {
                val result = block(scope)
                handler.post { if (alive && expected == generation) success(result) }
            } catch (e: Exception) {
                val message = when (e) {
                    is MediaFailure -> e.userMessage
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
        setContentView(b.root); icon(b.back, PlayerIcon.Kind.BACK); b.title.text = title; b.back.setOnClickListener { finish() }
        return b
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.activeScreens++
        val restored = savedInstanceState?.getInt("sessionOrientation", -1)?.takeIf { it >= 0 }
        val reverse = windowManager.defaultDisplay.rotation in arrayOf(Surface.ROTATION_180, Surface.ROTATION_270)
        val initial = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (reverse) ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            if (reverse) ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val orientation = app.sessionOrientation ?: restored ?: initial
        app.sessionOrientation = orientation; requestedOrientation = displayOrientation(orientation)
        window.statusBarColor = resources.getColor(R.color.background)
        window.navigationBarColor = resources.getColor(R.color.background)
    }
    protected fun icon(view: ImageButton, kind: PlayerIcon.Kind) { view.setImageDrawable(PlayerIcon(kind)) }
    protected fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    protected fun text(value: String, size: Float = 16f) = TextView(this).apply {
        text = value; textSize = size; setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    protected fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; setAllCaps(false); gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
        setPadding(dp(16), dp(12), dp(16), dp(12)); minimumHeight = dp(52)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(8) }
        setOnClickListener { action() }
    }
    protected fun error(message: String) { if (alive) UiDialog.Builder(this).setTitle("提示").setMessage(message).setPositiveButton("知道了", null).show() }
    protected fun prompt(title: String, initial: String = "", done: (String) -> Unit) {
        val input = EditText(this).apply { setBackgroundResource(R.drawable.ui_input); setText(initial); setSingleLine(true); setPadding(dp(16), dp(12), dp(16), dp(12)) }
        val dialog = UiDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener { dialog.getButton(UiDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isEmpty() || name.length > 80) input.error = "请输入 1～80 个字符" else { done(name); dialog.dismiss() }
        } }; dialog.show()
    }
    override fun onResume() {
        super.onResume()
        if (this !is PlayerActivity) app.detailResources.gate.resumeBrowsing()
        if (jobsPaused) { jobsPaused = false; reloadAfterPause() }
    }
    override fun onPause() {
        jobsPaused = true; playbackPending = false
        resetJobs()
        (app.workers as? java.util.concurrent.ThreadPoolExecutor)?.purge()
        super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        app.sessionOrientation?.let { outState.putInt("sessionOrientation", it) }
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        alive = false; resetJobs(); handler.removeCallbacksAndMessages(null)
        app.activeScreens--
        if (app.activeScreens == 0 && isFinishing && !isChangingConfigurations) app.sessionOrientation = null
        super.onDestroy()
    }
}

fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%02d:%02d".format(seconds / 60, seconds % 60)
}
