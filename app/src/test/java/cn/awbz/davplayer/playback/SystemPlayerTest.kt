package cn.awbz.davplayer.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import cn.awbz.davplayer.data.Connection
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class SystemPlayerTest {
    private class Events : PlaybackPlayer.Listener {
        var readyCount = 0; var endedCount = 0; var failedCount = 0
        override fun ready() { readyCount++ }
        override fun ended() { endedCount++ }
        override fun failed(message: String) { failedCount++ }
        override fun buffering(active: Boolean) {}
        override fun warning(message: String?) {}
        override fun videoSize(width: Int, height: Int, pixelRatio: Float) {}
    }
    /** Real OpenList HTTP fixture; Android media and main-loop boundaries are controlled. */
    private fun fixture(test: (SystemPlayer, Events, MediaPlayer, () -> Unit, () -> Unit, () -> Unit) -> Unit) {
        val pending = LinkedBlockingQueue<Runnable>()
        var prepared: MediaPlayer.OnPreparedListener? = null
        var sought: MediaPlayer.OnSeekCompleteListener? = null
        var completed: MediaPlayer.OnCompletionListener? = null
        val context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(context)
        mockStatic(Looper::class.java).use { loop ->
            loop.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mock(Looper::class.java))
            mockStatic(Uri::class.java).use { uris ->
                uris.`when`<Uri> { Uri.parse("https://example.com/video") }.thenReturn(mock(Uri::class.java))
                mockConstruction(Handler::class.java) { handler, _ ->
                    `when`(handler.post(any(Runnable::class.java))).thenAnswer { pending.add(it.getArgument(0)); true }
                }.use {
                    mockConstruction(AudioAttributes.Builder::class.java, withSettings().defaultAnswer(RETURNS_SELF)) { builder, _ ->
                        `when`(builder.build()).thenReturn(mock(AudioAttributes::class.java))
                    }.use {
                        mockConstruction(MediaPlayer::class.java) { mp, _ ->
                            `when`(mp.duration).thenReturn(120_000)
                            doAnswer { prepared = it.getArgument(0); null }.`when`(mp).setOnPreparedListener(any())
                            doAnswer { sought = it.getArgument(0); null }.`when`(mp).setOnSeekCompleteListener(any())
                            doAnswer { completed = it.getArgument(0); null }.`when`(mp).setOnCompletionListener(any())
                        }.use { media ->
                            MockWebServer().use { server ->
                                server.enqueue(MockResponse().setBody("{\"code\":200,\"data\":{\"raw_url\":\"https://example.com/video\"}}"))
                                val events = Events(); val player = SystemPlayer(context, events)
                                try {
                                    player.open(Connection("test", "test", server.url("/").toString(), "", ""), "/video", mock(SurfaceHolder::class.java), 42_000, false)
                                    val task = pending.poll(5, TimeUnit.SECONDS) ?: error("resolution callback missing")
                                    task.run()
                                    val mp = media.constructed().single()
                                    test(player, events, mp, { prepared!!.onPrepared(mp) }, { sought!!.onSeekComplete(mp) }, { completed!!.onCompletion(mp) })
                                } finally { player.release() }
                            }
                        }
                    }
                }
            }
        }
    }
    @Test fun pausedResumeWaitsForSeekAndNeverStartsPlayback() = fixture { player, events, mp, prepared, sought, _ ->
        prepared()
        verify(mp).seekTo(42_000)
        assertEquals(0, events.readyCount)
        assertEquals(42_000L, player.position)
        sought()
        assertEquals(1, events.readyCount)
        verify(mp, never()).start()
        player.play(); verify(mp).start()
        assertNull(player.downloadSpeed)
    }
    @Test fun releaseRejectsLatePreparedSeekAndCompletionCallbacks() = fixture { player, events, mp, prepared, sought, completed ->
        prepared(); player.release()
        prepared(); sought(); completed()
        assertEquals(0, events.readyCount)
        assertEquals(0, events.endedCount)
        verify(mp, times(1)).release()
        verify(mp, never()).start()
    }
    @Test fun failedInitialSeekReleasesMediaInsteadOfReportingReady() = fixture { _, events, mp, prepared, _, _ ->
        doThrow(IllegalStateException()).`when`(mp).seekTo(anyInt())
        prepared()
        assertEquals(1, events.failedCount)
        assertEquals(0, events.readyCount)
        verify(mp).release()
    }
    @Test fun defaultCoreStaysExoPlayer() {
        assertEquals(PlaybackEngine.EXOPLAYER, PlaybackEngine.stored(null))
        assertEquals(PlaybackEngine.EXOPLAYER, PlaybackEngine.stored("unknown"))
        assertEquals(PlaybackEngine.MEDIAPLAYER, PlaybackEngine.stored("MEDIAPLAYER"))
    }
}
