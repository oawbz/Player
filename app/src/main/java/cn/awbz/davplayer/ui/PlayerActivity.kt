package cn.awbz.davplayer.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Build
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.HorizontalScrollView
import android.widget.Button
import android.widget.TextView
import android.view.Gravity
import android.graphics.Color
import cn.awbz.davplayer.R
import cn.awbz.davplayer.data.*
import cn.awbz.davplayer.databinding.ActivityPlayerBinding
import cn.awbz.davplayer.playback.*

class PlayerActivity : BaseActivity(), SurfaceHolder.Callback {
    override val fullscreenAlways = true
    override fun displayOrientation(session: Int): Int = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    private lateinit var b: ActivityPlayerBinding
    private lateinit var work: Work
    private lateinit var episode: Episode
    private lateinit var connection: Connection
    private lateinit var audio: AudioManager
    private var playback: Media3Player? = null
    private var resumed = false
    private var surfaceReady = false
    private var ready = false
    private var buffering = false
    private var awaitingFrame = false
    private val playbackWait = PlaybackWait()
    private var desiredPlay = true
    private var attempt = 0
    private var position = 0L
    private var duration = 0L
    private var videoRatio = 0f
    private var tickCount = 0
    private var failed = false
    private var lostFocus = false
    private var focusHeld = false
    private var controlsVisible = true
    private var scrubbing = false
    private var completed = false
    private var overlayOpen = false
    private var detail: WorkDetail? = null
    private var playlistReady = false
    private var audioPreferenceApplied = false
    private var playbackWarning: String? = null
    private var episodeDrawer: FrameLayout? = null
    private var toggleKind: PlayerIcon.Kind? = null
    private val hideControls = Runnable { if (resumed && ready && desiredPlay && !buffering && !awaitingFrame && !failed && !scrubbing && !overlayOpen) setControlsVisible(false) }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                lostFocus = desiredPlay; playback?.pause()
                if (change == AudioManager.AUDIOFOCUS_LOSS) { desiredPlay = false; focusHeld = false; lostFocus = false }
                updateControls()
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (lostFocus && resumed) { lostFocus = false; playback?.play(); updateControls() }
        }
    }
    private val ticker = object : Runnable {
        override fun run() {
            if (!resumed) return
            if (playbackWait.expired(desiredPlay && !lostFocus && !failed && (buffering || awaitingFrame), android.os.SystemClock.elapsedRealtime())) {
                fail("播放等待超时，请点击重试")
            }
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
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        b = ActivityPlayerBinding.inflate(layoutInflater); setContentView(b.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        b.back.setImageDrawable(PlayerIcon(PlayerIcon.Kind.BACK))
        b.rewind.setImageDrawable(PlayerIcon(PlayerIcon.Kind.REWIND))
        b.forward.setImageDrawable(PlayerIcon(PlayerIcon.Kind.FORWARD))
        b.previousEpisode.setImageDrawable(PlayerIcon(PlayerIcon.Kind.PREVIOUS))
        b.nextEpisode.setImageDrawable(PlayerIcon(PlayerIcon.Kind.NEXT))
        b.previousEpisode.setOnClickListener { stepEpisode(-1) }
        b.nextEpisode.setOnClickListener { stepEpisode(1) }
        b.audioTracks.setImageDrawable(PlayerIcon(PlayerIcon.Kind.AUDIO))
        b.audioTracks.setOnClickListener { showAudioTracks() }
        b.tapArea.setOnClickListener { setControlsVisible(!controlsVisible) }
        applyFullscreen()
        b.title.text = work.name
        b.episodes.setImageDrawable(PlayerIcon(PlayerIcon.Kind.EPISODES))
        b.episodes.setOnClickListener { showEpisodes() }
        showBuffering(true); b.seek.isEnabled = false
        loadEpisodes()
        b.surface.holder.addCallback(this)
        b.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitVideo() }
        b.back.setOnClickListener { finish() }
        b.toggle.setOnClickListener {
            if (failed) { failed = false; desiredPlay = true; start() }
            else if (desiredPlay) { desiredPlay = false; playback?.pause(); save(); updateControls() }
            else {
                if (duration > 0 && position >= duration) seek(0)
                desiredPlay = true; if (requestFocus()) playback?.play(); updateControls()
            }
            if (!desiredPlay || failed) setControlsVisible(true) else scheduleHide()
        }
        b.rewind.setOnClickListener { seek(position - 10_000) }
        b.forward.setOnClickListener { seek(position + 10_000) }
        b.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !scrubbing) { seek(duration * progress / 1000); scheduleHide() }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { scrubbing = true; handler.removeCallbacks(hideControls) }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { scrubbing = false; seek(duration * (seekBar?.progress ?: 0) / 1000); scheduleHide() }
        })
        b.toggle.requestFocus(); updateControls()
    }
    private fun loadEpisodes() {
        fun apply(result: WorkDetail) {
            detail = result
            val series = result.episodes.size > 1 || result.seasons.any { it.name.isNotBlank() }
            b.episodes.visibility = if (series) View.VISIBLE else View.GONE
            val season = result.seasons.firstOrNull { group -> group.episodes.any { it.path == episode.path } }
            val number = season?.episodes?.indexOfFirst { it.path == episode.path }?.plus(1)
            b.title.text = if (series && number != null && number > 0) getString(R.string.episode_title, work.name, number) else work.name
            updateControls()
        }
        val reused = library.takePlaybackDetail(connection, work, episode)
        if (reused != null) {
            apply(reused); playlistReady = true
            return
        }
        // Process restoration can lose the in-memory playlist. Play only the known episode;
        // browsing its directory belongs to the detail page, never the player.
        apply(WorkDetail(work, listOf(Season(episode.season, listOf(episode))), ""))
        playlistReady = true
    }

    private fun stepEpisode(offset: Int) {
        val result = detail ?: return
        val episodes = result.episodes
        val index = episodes.indexOfFirst { it.path == episode.path }
        if (index < 0) return
        episodes.getOrNull(index + offset)?.let { switchEpisode(it, result); scheduleHide() }
    }
    private fun restoreAudioPreference() {
        if (audioPreferenceApplied) return
        val current = playback ?: return
        val tracks = current.audioTracks()
        if (tracks.isEmpty()) return
        audioPreferenceApplied = true
        val saved = store.audioPreference(connection.id, episode.path) ?: return
        val matching = tracks.firstOrNull { it.preferenceKey == saved.first && it.playable }
            ?: tracks.firstOrNull { it.id == saved.first && it.playable }
            ?: tracks.filter { it.label == saved.second }.singleOrNull()
        matching?.let { current.selectAudioTrack(it.id) { } }
    }
    private fun showAudioTracks() {
        val current = playback ?: return
        val tracks = current.audioTracks()
        if (tracks.size <= 1) return
        overlayOpen = true; handler.removeCallbacks(hideControls)
        UiDialog.Builder(this).setTitle("声道选择").setSingleChoiceItems(tracks.map { it.label }.toTypedArray(), tracks.indexOfFirst { it.selected }) { dialog, which ->
            if (playback === current) {
                val selected = tracks[which]
                current.selectAudioTrack(selected.id) { success ->
                    if (playback === current) {
                        if (success) {
                            store.saveAudioPreference(connection.id, episode.path, selected.preferenceKey, selected.label)
                            audioPreferenceApplied = true
                        } else error("此音轨暂不可用，请选择其他音轨")
                    }
                }
            }
            dialog.dismiss()
        }.setNegativeButton("关闭", null).create().apply {
            setOnDismissListener { overlayOpen = false; scheduleHide(); applyFullscreen() }; show()
        }
    }
    private fun closeEpisodes() {
        episodeDrawer?.let { b.root.removeView(it) }; episodeDrawer = null
        overlayOpen = false; scheduleHide(); applyFullscreen()
    }
    private fun switchEpisode(selected: Episode, result: WorkDetail) {
        if (selected.path == episode.path) return
        save(); episode = selected
        intent.putExtra("videoPath", episode.path).putExtra("videoName", episode.name).putExtra("season", episode.season)
        position = 0; duration = 0; failed = false; desiredPlay = true
        val season = result.seasons.first { group -> group.episodes.any { it.path == episode.path } }
        b.title.text = getString(R.string.episode_title, work.name, season.episodes.indexOfFirst { it.path == episode.path } + 1)
        playbackWait.reset(); awaitingFrame = true
        completed = false; ready = false; audioPreferenceApplied = false; playbackWarning = null; buffering = true
        b.seek.isEnabled = false; showBuffering(true)
        updateControls()
        playback?.open(connection, episode.path, b.surface.holder, 0, true) ?: start()
    }
    private fun showEpisodes() {
        val result = detail ?: return
        if (episodeDrawer != null) { closeEpisodes(); return }
        overlayOpen = true; handler.removeCallbacks(hideControls)
        val overlay = FrameLayout(this).apply { setBackgroundColor(0x22000000); setOnClickListener { closeEpisodes() } }
        episodeDrawer = overlay
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(16))
            setBackgroundColor(0xCC171E28.toInt()); isClickable = true
        }
        val width = minOf(dp(360), (resources.displayMetrics.widthPixels * .48f).toInt())
        overlay.addView(panel, FrameLayout.LayoutParams(width, -1, Gravity.END))
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(TextView(this).apply { text = "选集"; textSize = 20f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(android.widget.ImageButton(this).apply {
            setImageDrawable(PlayerIcon(PlayerIcon.Kind.BACK)); contentDescription = "关闭选集"
            setBackgroundResource(R.drawable.player_button); setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { closeEpisodes() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        panel.addView(heading)
        val tabs = LinearLayout(this)
        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
        if (result.seasons.any { it.name.isNotBlank() }) panel.addView(tabScroll, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(rows) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        fun render(index: Int, page: Int? = null) {
            val episodes = result.seasons[index].episodes
            val selected = episodes.indexOfFirst { it.path == episode.path }.coerceAtLeast(0)
            val window = EpisodePage.of(episodes.size, page ?: selected / EpisodePage.SIZE)
            for (i in 0 until tabs.childCount) tabs.getChildAt(i).isSelected = i == index
            rows.removeAllViews()
            var selectedRow: View? = null
            if (window.page > 0) rows.addView(button("上一页") { render(index, window.page - 1) })
            episodes.subList(window.start, window.end).forEachIndexed { offset, item ->
                val number = window.start + offset
                val row = Button(this).apply {
                    text = getString(R.string.episode_number, number + 1); isSelected = item.path == episode.path
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL; setAllCaps(false)
                    setPadding(dp(16), dp(12), dp(16), dp(12)); minimumHeight = dp(52)
                    setBackgroundResource(R.drawable.ui_button)
                    setOnClickListener { switchEpisode(item, result); closeEpisodes() }
                }
                rows.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
                if (row.isSelected) selectedRow = row
            }
            if (window.pages > 1) rows.addView(text("${window.page + 1} / ${window.pages} 页"))
            if (window.page + 1 < window.pages) rows.addView(button("下一页") { render(index, window.page + 1) })
            scroll.post {
                val focus = selectedRow ?: rows.getChildAt(0)
                scroll.scrollTo(0, selectedRow?.top ?: 0); focus?.requestFocus()
            }
        }
        result.seasons.forEachIndexed { index, season ->
            tabs.addView(Button(this).apply {
                text = season.name.ifBlank { "剧集" }; setAllCaps(false); setBackgroundResource(R.drawable.ui_button)
                setOnClickListener { render(index) }
            }, LinearLayout.LayoutParams(-2, dp(48)).apply { rightMargin = dp(8) })
        }
        val current = result.seasons.indexOfFirst { group -> group.episodes.any { it.path == episode.path } }.coerceAtLeast(0)
        b.root.addView(overlay, FrameLayout.LayoutParams(-1, -1)); render(current)
        panel.translationX = width.toFloat(); panel.animate().translationX(0f).setDuration(180).start()
    }
    private fun requestFocus(): Boolean {
        if (focusHeld) return true
        focusHeld = audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!focusHeld) { desiredPlay = false; b.status.text = "其他应用正在使用音频，点击播放重试" }
        return focusHeld
    }
    private fun start() {
        if (!resumed || !surfaceReady || !playlistReady || playback != null || failed) return
        playbackWait.reset(); awaitingFrame = true
        val expected = ++attempt; completed = false; ready = false; playbackWarning = null; videoRatio = 0f; audioPreferenceApplied = false
        showBuffering(true); b.seek.isEnabled = false
        if (desiredPlay) requestFocus()
        val listener = object : Media3Player.Listener {
            private fun current() = resumed && expected == attempt && playback != null
            override fun firstFrame() { if (current()) { awaitingFrame = false; showBuffering(buffering); scheduleHide() } }
            override fun ready() { if (current()) {
                ready = true; b.seek.isEnabled = true
                awaitingFrame = playback?.let { it.hasVideo && !it.hasRenderedVideo } ?: false
                showBuffering(false); scheduleHide()
            } }
            override fun buffering(active: Boolean) { if (current()) {
                buffering = active
                showBuffering(active)
            } }
            override fun ended() { if (current()) { capture(); position = duration; completed = true; buffering = false; b.loading.visibility = View.GONE; desiredPlay = false; save(); b.status.text = "播放结束"; updateControls(); setControlsVisible(true) } }
            override fun warning(message: String?) { if (current()) {
                playbackWarning = message
                if (ready && !buffering) b.status.text = message.orEmpty()
                updateControls()
            } }
            override fun failed(message: String) { if (current()) fail(message) }
            override fun videoSize(width: Int, height: Int, pixelRatio: Float) {
                if (current() && width > 0 && height > 0) { videoRatio = width * pixelRatio / height; fitVideo() }
            }
        }
        val created = Media3Player(this, listener)
        playback = created
        try { created.open(connection, episode.path, b.surface.holder, position, desiredPlay) }
        catch (_: Exception) { fail("无法打开当前媒体，请检查地址、认证或文件格式") }
    }
    private fun showBuffering(active: Boolean) {
        buffering = active
        b.status.text = playbackWarning.orEmpty()
        b.loading.visibility = if (active || awaitingFrame) View.VISIBLE else View.GONE
        b.status.visibility = if (b.status.text.isEmpty()) View.GONE else View.VISIBLE
        updateControls()
    }
    private fun fail(message: String) {
        capture(); save(); release(); failed = true
        b.status.text = message; b.loading.visibility = View.GONE; b.seek.isEnabled = false; updateControls(); setControlsVisible(true)
    }
    private fun seek(target: Long) {
        if (!ready || duration <= 0) return
        completed = false; position = target.coerceIn(0, duration)
        playbackWait.reset(); awaitingFrame = playback?.hasVideo == true
        playback?.seek(position)
        awaitingFrame = playback?.let { it.hasVideo && !it.hasRenderedVideo } ?: false
        showBuffering(playback?.isBuffering == true)
    }
    private fun capture() {
        val e = playback ?: return
        if (ready && !completed && e.duration > 0 && (!e.hasVideo || e.hasRenderedVideo)) { duration = e.duration; position = e.position.coerceIn(0, duration) }
    }
    private fun save() {
        capture()
        store.record(History(connection.id, work.path, work.name, work.coverPath, episode.path, episode.name,
            episode.season, position, duration, System.currentTimeMillis()))
    }
    private fun updateControls() {
        if (!::b.isInitialized) return
        if (ready) restoreAudioPreference()
        val episodes = detail?.episodes.orEmpty()
        val current = episodes.indexOfFirst { it.path == episode.path }
        b.previousEpisode.visibility = if (current > 0) View.VISIBLE else View.GONE
        b.nextEpisode.visibility = if (current >= 0 && current < episodes.lastIndex) View.VISIBLE else View.GONE
        b.audioTracks.visibility = if (ready && (playback?.audioTracks()?.size ?: 0) > 1) View.VISIBLE else View.GONE
        b.status.visibility = if (b.status.text.isEmpty()) View.GONE else View.VISIBLE
        val icon = if (failed || (duration > 0 && position >= duration)) PlayerIcon.Kind.REPLAY else if (desiredPlay) PlayerIcon.Kind.PAUSE else PlayerIcon.Kind.PLAY
        if (toggleKind != icon) { toggleKind = icon; b.toggle.setImageDrawable(PlayerIcon(icon)) }
        b.toggle.contentDescription = if (failed) "重试" else if (icon == PlayerIcon.Kind.REPLAY) "重新播放" else if (desiredPlay) "暂停" else "播放"
        val currentPlayer = playback
        b.network.visibility = if (currentPlayer == null && !buffering) View.GONE else View.VISIBLE
        if (currentPlayer != null || buffering) {
            val speed = currentPlayer?.downloadSpeed ?: 0L
            val rate = if (speed >= 1024 * 1024) "%.2f MB/s".format(java.util.Locale.ROOT, speed / (1024.0 * 1024))
                else "%.0f KB/s".format(java.util.Locale.ROOT, speed / 1024.0)
            val seconds = (currentPlayer?.bufferedDuration ?: 0L) / 1000
            b.network.text = "$rate · ${if (buffering || awaitingFrame) "缓冲中" else "缓冲"} $seconds 秒"
        }
        b.time.text = formatTime(position)
        b.totalTime.text = formatTime(duration)
        if (!scrubbing) b.seek.progress = if (duration > 0) (position * 1000 / duration).toInt() else 0
    }
    private fun release() {
        attempt++; buffering = false; awaitingFrame = false; playbackWait.reset()
        val old = playback; playback = null; ready = false; old?.release()
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
    override fun reloadAfterPause() { if (::b.isInitialized && !playlistReady) loadEpisodes() }
    override fun onResume() {
        super.onResume(); if (!::b.isInitialized) return
        resumed = true; applyFullscreen(); start(); handler.post(ticker); scheduleHide()
    }
    override fun onPause() {
        if (::b.isInitialized) {
            save(); resumed = false; handler.removeCallbacks(ticker); handler.removeCallbacks(hideControls); release()
            audio.abandonAudioFocus(focusListener); focusHeld = false; lostFocus = false
        }
        super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) { capture(); outState.putLong("position", position); outState.putBoolean("playing", desiredPlay); super.onSaveInstanceState(outState) }
    override fun surfaceCreated(holder: SurfaceHolder) { surfaceReady = true; start() }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) { capture(); save(); surfaceReady = false; release() }
    private fun scheduleHide() {
        handler.removeCallbacks(hideControls)
        if (controlsVisible && resumed && ready && desiredPlay && !buffering && !awaitingFrame && !failed && !scrubbing && !overlayOpen) handler.postDelayed(hideControls, 4000)
    }
    private fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        b.controls.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) { b.toggle.requestFocus(); scheduleHide() }
        else { handler.removeCallbacks(hideControls); b.root.requestFocus() }
        applyFullscreen()
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (::b.isInitialized && controlsVisible && event.actionMasked == MotionEvent.ACTION_DOWN) scheduleHide()
        return super.dispatchTouchEvent(event)
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::b.isInitialized) return super.dispatchKeyEvent(event)
        val navigation = event.keyCode in arrayOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && episodeDrawer != null) { closeEpisodes(); return true }
            if (navigation && !controlsVisible) { setControlsVisible(true); return true }
            if (controlsVisible) scheduleHide()
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { b.toggle.performClick(); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { if (!desiredPlay || failed) b.toggle.performClick(); return true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (desiredPlay && !failed) b.toggle.performClick(); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { b.rewind.performClick(); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { b.forward.performClick(); return true }
                KeyEvent.KEYCODE_BACK -> { if (!controlsVisible) setControlsVisible(true) else finish(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }
    companion object {
        fun launch(context: Activity, work: Work, episode: Episode, position: Long = 0) {
            val start = { context.startActivity(Intent(context, PlayerActivity::class.java).putExtra("connection", work.connectionId)
                .putExtra("workPath", work.path).putExtra("workName", work.name).putExtra("cover", work.coverPath)
                .putExtra("videoPath", episode.path).putExtra("videoName", episode.name).putExtra("season", episode.season)
                .putExtra("position", position)) }
            (context as? BaseActivity)?.preparePlayback(start) ?: start()
        }
    }
}
