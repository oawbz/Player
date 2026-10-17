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
        var isBuffering = false
        override fun ready() { readyCount++ }
        override fun ended() { endedCount++ }
        override fun failed(message: String) { failedCount++ }
        override fun buffering(active: Boolean) { isBuffering = active }
        override fun warning(message: String?) {}
        override fun videoSize(width: Int, height: Int, pixelRatio: Float) {}
    }
    /** Real OpenList HTTP fixture; Android media and main-loop boundaries are controlled. */
    private fun fixture(delayed: MutableList<Runnable> = mutableListOf(), infos: MutableList<MediaPlayer.OnInfoListener> = mutableListOf(), test: (SystemPlayer, Events, MediaPlayer, () -> Unit, () -> Unit, () -> Unit) -> Unit) {
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
                    `when`(handler.postDelayed(any(Runnable::class.java), anyLong())).thenAnswer { delayed.add(it.getArgument(0)); true }
                }.use {
                    mockConstruction(AudioAttributes.Builder::class.java, withSettings().defaultAnswer(RETURNS_SELF)) { builder, _ ->
                        `when`(builder.build()).thenReturn(mock(AudioAttributes::class.java))
                    }.use {
                        mockConstruction(MediaPlayer::class.java) { mp, _ ->
                            `when`(mp.duration).thenReturn(120_000)
                            `when`(mp.currentPosition).thenReturn(42_000)
                            doAnswer { prepared = it.getArgument(0); null }.`when`(mp).setOnPreparedListener(any())
                            doAnswer { sought = it.getArgument(0); null }.`when`(mp).setOnSeekCompleteListener(any())
                            doAnswer { completed = it.getArgument(0); null }.`when`(mp).setOnCompletionListener(any())
                            doAnswer { infos.add(it.getArgument(0)); null }.`when`(mp).setOnInfoListener(any())
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
    @Test fun legacySystemCoreUsesMusicStreamAndCanSelectAudioWithoutApi21() = fixture { player, _, mp, prepared, sought, _ ->
        verify(mp).setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)
        val first = mock(MediaPlayer.TrackInfo::class.java)
        val second = mock(MediaPlayer.TrackInfo::class.java)
        `when`(first.trackType).thenReturn(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO)
        `when`(second.trackType).thenReturn(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO)
        `when`(first.language).thenReturn("eng"); `when`(second.language).thenReturn("zho")
        `when`(mp.trackInfo).thenReturn(arrayOf(first, second))
        prepared(); sought()
        assertFalse(player.audioTracks().any { it.selected })
        var selected = false
        player.selectAudioTrack("1") { selected = it }
        assertTrue(selected); verify(mp).selectTrack(1)
        assertEquals("1", player.audioTracks().single { it.selected }.id)
        verify(mp, never()).getSelectedTrack(anyInt())
    }
    @Test fun successiveSeeksWaitForOldCallbackAndKeepLatestTarget() = fixture { player, events, mp, prepared, sought, _ ->
        prepared(); sought()
        player.seek(60_000); player.seek(80_000); player.seek(100_000)
        verify(mp).seekTo(60_000)
        verify(mp, never()).seekTo(80_000)
        verify(mp, never()).seekTo(100_000)
        `when`(mp.currentPosition).thenReturn(60_000)
        sought()
        verify(mp).seekTo(100_000)
        assertEquals(100_000L, player.position)
        `when`(mp.currentPosition).thenReturn(100_000)
        sought()
        assertEquals(100_000L, player.position)
        assertEquals(0, events.failedCount)
    }
    @Test fun seekCallbackAtOldPositionFailsWithoutLosingRequestedProgress() = fixture { player, events, mp, prepared, sought, _ ->
        prepared(); sought()
        player.seek(100_000)
        sought() // Native position remains 42 seconds, outside conservative keyframe tolerance.
        assertEquals(1, events.failedCount)
        assertEquals(100_000L, player.position)
        assertEquals(120_000L, player.duration)
        assertEquals(0, events.endedCount)
        verify(mp).release()
    }
    @Test fun prematureEofIsFailureAndPreservesLastObservedPosition() = fixture { player, events, mp, prepared, sought, completed ->
        prepared(); sought()
        `when`(mp.currentPosition).thenReturn(60_000)
        assertEquals(60_000L, player.position)
        `when`(mp.currentPosition).thenReturn(0)
        completed()
        assertEquals(1, events.failedCount)
        assertEquals(0, events.endedCount)
        assertEquals(60_000L, player.position)
        verify(mp).release()
    }
    @Test fun eofDuringSeekPreservesTargetAndRejectsLateCallback() = fixture { player, events, mp, prepared, sought, completed ->
        prepared(); sought(); player.seek(80_000)
        `when`(mp.currentPosition).thenReturn(0)
        completed(); sought()
        assertEquals(80_000L, player.position)
        assertEquals(1, events.failedCount)
        assertEquals(0, events.endedCount)
    }
    @Test fun normalCompletionNearEndStillEnds() = fixture { player, events, mp, prepared, sought, completed ->
        prepared(); sought()
        `when`(mp.currentPosition).thenReturn(119_500)
        assertEquals(119_500L, player.position)
        `when`(mp.currentPosition).thenReturn(0)
        completed()
        assertEquals(1, events.endedCount)
        assertEquals(0, events.failedCount)
        assertNull(player.bufferedDuration)
    }
    @Test fun seekTimeoutPreservesTargetAndReleasesPlayer() {
        val delayed = mutableListOf<Runnable>()
        fixture(delayed) { player, events, mp, prepared, sought, _ ->
            prepared(); sought(); player.seek(90_000)
            delayed.last().run()
            assertEquals(1, events.failedCount)
            assertEquals(90_000L, player.position)
            verify(mp).release()
            sought()
            assertEquals(1, events.failedCount)
        }
    }
    @Test fun releaseMakesPendingSeekDeadlineHarmless() {
        val delayed = mutableListOf<Runnable>()
        fixture(delayed) { player, events, mp, prepared, sought, _ ->
            prepared(); sought(); player.seek(90_000)
            val deadline = delayed.last()
            player.release(); deadline.run()
            assertEquals(0, events.failedCount)
            verify(mp, times(1)).release()
        }
    }
    @Test fun bufferingEndCannotHidePendingSeekSpinner() {
        val infos = mutableListOf<MediaPlayer.OnInfoListener>()
        fixture(infos = infos) { player, events, mp, prepared, sought, _ ->
            prepared(); sought(); player.seek(60_000)
            assertTrue(events.isBuffering)
            infos.single().onInfo(mp, MediaPlayer.MEDIA_INFO_BUFFERING_END, 0)
            assertTrue(events.isBuffering)
            `when`(mp.currentPosition).thenReturn(60_000)
            sought()
            assertFalse(events.isBuffering)
        }
    }
    @Test fun defaultCoreStaysExoPlayer() {
        assertEquals(PlaybackEngine.EXOPLAYER, PlaybackEngine.stored(null))
        assertEquals(PlaybackEngine.EXOPLAYER, PlaybackEngine.stored("unknown"))
        assertEquals(PlaybackEngine.MEDIAPLAYER, PlaybackEngine.stored("MEDIAPLAYER"))
    }
}
