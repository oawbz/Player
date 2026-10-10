package cn.awbz.davplayer.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.SeekBar
import cn.awbz.davplayer.data.*
import cn.awbz.davplayer.databinding.ActivityPlayerBinding
import cn.awbz.davplayer.playback.*

class PlayerActivity : BaseActivity(), SurfaceHolder.Callback {
    private lateinit var b: ActivityPlayerBinding
    private lateinit var work: Work
    private lateinit var episode: Episode
    private lateinit var connection: Connection
    private lateinit var audio: AudioManager
    private var engine: Engine? = null
    private var core = Core.SYSTEM
    private var resumed = false
    private var surfaceReady = false
    private var ready = false
    private var desiredPlay = true
    private var attempt = 0
    private var position = 0L
    private var duration = 0L
    private var videoRatio = 0f
    private var tickCount = 0
    private var failed = false
    private var lostFocus = false
    private var focusHeld = false
    private val timeout = Runnable { if (engine != null && !ready) fail("媒体准备超时，请检查网络或手动切换核心") }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                lostFocus = desiredPlay; engine?.pause()
                if (change == AudioManager.AUDIOFOCUS_LOSS) { desiredPlay = false; focusHeld = false; lostFocus = false }
                updateControls()
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (lostFocus && resumed) { lostFocus = false; engine?.play(); updateControls() }
        }
    }
    private val ticker = object : Runnable {
        override fun run() {
            if (!resumed) return
            capture(); updateControls()
            tickCount++; if (tickCount % 5 == 0) save()
            handler.postDelayed(this, 1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        work = Work(intent.getStringExtra("connection") ?: return finish(), intent.getStringExtra("workPath") ?: return finish(),
            intent.getStringExtra("workName") ?: "作品", intent.getStringExtra("cover"))
        episode = Episode(intent.getStringExtra("videoPath") ?: return finish(), intent.getStringExtra("videoName") ?: "视频", intent.getStringExtra("season") ?: "")
        connection = store.connection(work.connectionId) ?: return finish()
        position = savedInstanceState?.getLong("position") ?: intent.getLongExtra("position", 0)
        desiredPlay = savedInstanceState?.getBoolean("playing") ?: true
        volumeControlStream = AudioManager.STREAM_MUSIC
        core = store.core; audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        b = ActivityPlayerBinding.inflate(layoutInflater); setContentView(b.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        b.title.text = listOf(work.name, episode.season, episode.name).filter { it.isNotEmpty() }.joinToString(" · ")
        b.surface.holder.addCallback(this)
        b.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitVideo() }
        b.back.setOnClickListener { finish() }
        b.toggle.setOnClickListener {
            if (failed) { failed = false; desiredPlay = true; start() }
            else if (desiredPlay) { desiredPlay = false; engine?.pause(); save(); updateControls() }
            else {
                if (duration > 0 && position >= duration) seek(0)
                desiredPlay = true; if (requestFocus()) engine?.play(); updateControls()
            }
        }
        b.rewind.setOnClickListener { seek(position - 10_000) }
        b.forward.setOnClickListener { seek(position + 10_000) }
        b.core.setOnClickListener {
            AlertDialog.Builder(this).setTitle("切换播放核心").setSingleChoiceItems(arrayOf("系统 MediaPlayer", "ExoPlayer"), core.ordinal) { dialog, which ->
                val chosen = Core.values()[which]
                if (chosen != core) { capture(); save(); release(); core = chosen; store.core = chosen; failed = false; start() }
                dialog.dismiss()
            }.setNegativeButton("取消", null).show()
        }
        b.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) { seek(duration * (seekBar?.progress ?: 0) / 1000) }
        })
        b.toggle.requestFocus()
    }
    private fun requestFocus(): Boolean {
        if (focusHeld) return true
        focusHeld = audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!focusHeld) { desiredPlay = false; b.status.text = "其他应用正在使用音频，点击播放重试" }
        return focusHeld
    }
    private fun start() {
        if (!resumed || !surfaceReady || engine != null || failed) return
        val expected = ++attempt; ready = false; videoRatio = 0f
        b.status.text = "正在准备 · ${coreName()}"; b.seek.isEnabled = false
        if (desiredPlay) requestFocus()
        val listener = object : Engine.Listener {
            private fun current() = resumed && expected == attempt && engine != null
            override fun ready() { if (current()) { ready = true; handler.removeCallbacks(timeout); b.seek.isEnabled = true; b.status.text = coreName(); updateControls() } }
            override fun buffering(active: Boolean) { if (current()) b.status.text = if (active) "缓冲中 · ${coreName()}" else coreName() }
            override fun ended() { if (current()) { capture(); position = duration; desiredPlay = false; save(); b.status.text = "播放结束"; updateControls() } }
            override fun failed(message: String) { if (current()) fail(message) }
            override fun videoSize(width: Int, height: Int, pixelRatio: Float) {
                if (current() && width > 0 && height > 0) { videoRatio = width * pixelRatio / height; fitVideo() }
            }
        }
        val created: Engine = if (core == Core.SYSTEM) SystemEngine(this, listener) else ExoEngine(this, library.dav, listener)
        engine = created
        try { created.start(connection, episode.path, b.surface.holder, position, desiredPlay); handler.postDelayed(timeout, 25_000) }
        catch (_: Exception) { fail("无法打开当前媒体，请检查地址、认证或文件格式") }
    }
    private fun fail(message: String) {
        capture(); save(); release(); failed = true
        b.status.text = message; b.toggle.text = "重试"; b.seek.isEnabled = false
    }
    private fun coreName() = if (core == Core.SYSTEM) "系统 MediaPlayer" else "ExoPlayer"
    private fun seek(target: Long) {
        if (!ready || duration <= 0) return
        position = target.coerceIn(0, duration); engine?.seek(position); updateControls()
    }
    private fun capture() {
        val e = engine ?: return
        if (ready && e.duration > 0) { duration = e.duration; position = e.position.coerceIn(0, duration) }
    }
    private fun save() {
        capture()
        store.record(History(connection.id, work.path, work.name, work.coverPath, episode.path, episode.name,
            episode.season, position, duration, System.currentTimeMillis()))
    }
    private fun updateControls() {
        if (!::b.isInitialized) return
        b.toggle.text = if (failed) "重试" else if (desiredPlay) "暂停" else "播放"
        b.time.text = "${formatTime(position)} / ${formatTime(duration)}"
        if (!b.seek.isPressed) b.seek.progress = if (duration > 0) (position * 1000 / duration).toInt() else 0
    }
    private fun release() {
        attempt++; handler.removeCallbacks(timeout)
        val old = engine; engine = null; ready = false; old?.release()
    }
    private fun fitVideo() {
        if (videoRatio <= 0 || b.root.width == 0 || b.root.height == 0) return
        val availableWidth = b.root.width; val availableHeight = b.root.height
        val width: Int; val height: Int
        if (availableWidth.toFloat() / availableHeight > videoRatio) { height = availableHeight; width = (height * videoRatio).toInt() }
        else { width = availableWidth; height = (width / videoRatio).toInt() }
        val old = b.surface.layoutParams
        if (old.width != width || old.height != height) b.surface.layoutParams = FrameLayout.LayoutParams(width, height, android.view.Gravity.CENTER)
    }
    override fun onResume() {
        super.onResume(); if (!::b.isInitialized) return
        resumed = true; start(); handler.post(ticker)
    }
    override fun onPause() {
        if (::b.isInitialized) {
            save(); resumed = false; handler.removeCallbacks(ticker); release()
            audio.abandonAudioFocus(focusListener); focusHeld = false; lostFocus = false
        }
        super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) { capture(); outState.putLong("position", position); outState.putBoolean("playing", desiredPlay); super.onSaveInstanceState(outState) }
    override fun surfaceCreated(holder: SurfaceHolder) { surfaceReady = true; start() }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) { capture(); save(); surfaceReady = false; release() }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { b.toggle.performClick(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { if (!desiredPlay || failed) b.toggle.performClick(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (desiredPlay && !failed) b.toggle.performClick(); return true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { b.rewind.performClick(); return true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { b.forward.performClick(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }
    companion object {
        fun launch(context: Activity, work: Work, episode: Episode, position: Long = 0) {
            context.startActivity(Intent(context, PlayerActivity::class.java).putExtra("connection", work.connectionId)
                .putExtra("workPath", work.path).putExtra("workName", work.name).putExtra("cover", work.coverPath)
                .putExtra("videoPath", episode.path).putExtra("videoName", episode.name).putExtra("season", episode.season)
                .putExtra("position", position))
        }
    }
}
