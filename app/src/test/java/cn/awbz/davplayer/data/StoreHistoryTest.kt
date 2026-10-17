package cn.awbz.davplayer.data

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.*
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StoreHistoryTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun keepsTwelveWorksPersistsAndMovesResumedWorkToFront() {
        val context = mock(Context::class.java)
        val root = temp.newFolder()
        val disk = java.io.File(root, "openlist-history-v1.json")
        `when`(context.filesDir).thenReturn(root)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        val writer = Executors.newSingleThreadExecutor()
        mockConstruction(AtomicFile::class.java) { atom, _ ->
            `when`(atom.openRead()).thenAnswer { disk.inputStream() }
            `when`(atom.startWrite()).thenAnswer { FileOutputStream(disk) }
            doAnswer { (it.getArgument<Any>(0) as FileOutputStream).close(); null }.`when`(atom).finishWrite(any())
        }.use {
            try {
                val store = Store(context, writer)
                fun history(i: Int) = History("c", "/work$i/", "work$i", null,
                    "/work$i/video.mp4", "video.mp4", "", 10, 100, i.toLong())
                for (i in 0..13) store.record(history(i))
                store.record(history(7))
                writer.submit {}.get(3, TimeUnit.SECONDS)
                val expected = listOf(7, 13, 12, 11, 10, 9, 8, 6, 5, 4, 3, 2).map { "/work$it/" }
                assertEquals(expected, store.history().map { it.workPath })
                assertEquals(12, JSONArray(disk.readText()).length())
                assertEquals(expected, Store(context, writer).history().map { it.workPath })
                val existing = JSONArray(disk.readText())
                repeat(8) { existing.put(JSONObject(existing.getJSONObject(0).toString()).put("workPath", "/extra$it/")) }
                disk.writeText(existing.toString())
                assertEquals(expected, Store(context, writer).history().map { it.workPath })
                writer.submit {}.get(3, TimeUnit.SECONDS)
                assertEquals(12, JSONArray(disk.readText()).length())
            } finally { writer.shutdownNow() }
        }
    }
}
