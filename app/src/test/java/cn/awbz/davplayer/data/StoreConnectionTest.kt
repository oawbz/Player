package cn.awbz.davplayer.data

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.*
import java.util.concurrent.Executors

class StoreConnectionTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun savingAnotherConnectionReplacesTheOnlyEntryAndKeepsItsCategoryIdentity() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        val values = mutableMapOf<String, String>()
        `when`(context.filesDir).thenReturn(temp.newFolder())
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        `when`(prefs.getString(anyString(), any())).thenAnswer { values[it.getArgument(0)] ?: it.getArgument(1) }
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenAnswer { values[it.getArgument(0)] = it.getArgument(1); editor }
        val writer = Executors.newSingleThreadExecutor()
        mockConstruction(AtomicFile::class.java).use {
            try {
                val store = Store(context, writer)
                store.saveConnection(Connection("first", "first", "https://first.invalid/", "", ""))
                store.saveCategory(Category("cat", "cat", "first", "/movies/"))
                store.saveConnection(Connection("second", "updated", "https://second.invalid/", "user", "secret"))
                assertEquals(1, JSONArray(values["connections"]).length())
                assertEquals("first", store.connection()!!.id)
                assertEquals("https://second.invalid/", store.connection("first")!!.baseUrl)
                assertEquals("first", store.categories().single().connectionId)
                assertNull(store.connection("second"))
                store.record(History("first", "/movies/work/", "work", null, "/movies/work/video.mp4", "video.mp4", "", 20, 100, 1))
                store.saveConnection(Connection("first", "moved", "https://second.invalid/prefix/", "user", "secret"))
                assertEquals("/prefix/movies/", store.categories().single().path)
                assertEquals("/prefix/movies/work/video.mp4", store.history().single().videoPath)
                assertEquals(20L, store.history().single().position)
                store.saveConnection(Connection("first", "new service", "https://third.invalid/", "", ""), replaceServer = true)
                assertNotEquals("first", store.connection()!!.id)
                assertEquals(1, JSONArray(values["connections"]).length())
                assertTrue(store.categories().isEmpty())
                assertTrue(store.history().isEmpty())
            } finally { writer.shutdownNow() }
        }
    }
}
