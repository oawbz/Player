package cn.awbz.davplayer.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ExecutorService

class Store(context: Context, private val writer: ExecutorService) {
    private val prefs = context.getSharedPreferences("openlist-settings-v1", Context.MODE_PRIVATE)
    private val historyFile = AtomicFile(File(context.filesDir, "openlist-history-v1.json"))
    var playbackEngine: cn.awbz.davplayer.playback.PlaybackEngine
        get() = cn.awbz.davplayer.playback.PlaybackEngine.stored(prefs.getString("playback-engine", null))
        set(value) { prefs.edit().putString("playback-engine", value.name).apply() }
    private var records = readHistory()
    private fun audioKey(connectionId: String, path: String): String {
        val value = "$connectionId\n$path"
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return "audio_" + bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun audioPreference(connectionId: String, path: String): Pair<String, String>? = runCatching {
        val key = audioKey(connectionId, path)
        val raw = prefs.getString(key, null) ?: return null
        val json = JSONObject(raw); json.getString("id") to json.getString("label")
    }.getOrNull()
    fun saveAudioPreference(connectionId: String, path: String, id: String, label: String) {
        prefs.edit().putString(audioKey(connectionId, path), JSONObject().put("id", id).put("label", label).toString()).apply()
    }
    fun connections(): List<Connection> = array("connections").objects().map {
        Connection(it.getString("id"), it.getString("name"), it.getString("url"), it.optString("user"), it.optString("password"), AuthMode.stored(it.optString("authMode"), it.optString("user"), it.optString("password")))
    }
    fun connection(id: String): Connection? = connections().firstOrNull { it.id == id }
    fun saveConnection(connection: Connection) {
        val existing = connections()
        val list = if (existing.any { it.id == connection.id }) existing.map { if (it.id == connection.id) connection else it } else existing + connection
        prefs.edit().putString("connections", JSONArray().apply { list.forEach { c -> put(JSONObject()
            .put("id", c.id).put("name", c.name).put("url", c.baseUrl).put("authMode", c.authMode.name).put("user", if (c.authMode == AuthMode.BASIC) c.username else "").put("password", if (c.authMode == AuthMode.BASIC) c.password else "")) } }.toString()).apply()
    }
    fun categories(): List<Category> = array("categories").objects().map {
        Category(it.getString("id"), it.getString("name"), it.getString("connection"), it.getString("path"))
    }
    fun saveCategory(category: Category) {
        val existing = categories()
        writeCategories(if (existing.any { it.id == category.id }) existing.map { if (it.id == category.id) category else it } else existing + category)
    }
    fun deleteCategory(id: String) = writeCategories(categories().filter { it.id != id })
    private fun writeCategories(list: List<Category>) {
        prefs.edit().putString("categories", JSONArray().apply { list.forEach { c -> put(JSONObject()
            .put("id", c.id).put("name", c.name).put("connection", c.connectionId).put("path", c.path)) } }.toString()).apply()
    }
    @Synchronized fun history(): List<History> = records.toList()
    @Synchronized fun record(history: History) {
        if (history.duration <= 0) return
        records = (listOf(history) + records.filterNot { it.connectionId == history.connectionId && it.workPath == history.workPath }).take(100)
        val snapshot = JSONArray().apply { records.forEach { h -> put(JSONObject().put("connection", h.connectionId)
            .put("workPath", h.workPath).put("workName", h.workName).put("cover", h.coverPath ?: JSONObject.NULL)
            .put("videoPath", h.videoPath).put("videoName", h.videoName).put("season", h.season)
            .put("position", h.position.coerceAtLeast(0)).put("duration", h.duration).put("updatedAt", h.updatedAt)) } }.toString()
        writer.execute {
            var stream: java.io.FileOutputStream? = null
            try { stream = historyFile.startWrite(); stream.write(snapshot.toByteArray(Charsets.UTF_8)); historyFile.finishWrite(stream) }
            catch (_: Exception) { if (stream != null) historyFile.failWrite(stream) }
        }
    }
    private fun array(key: String) = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrDefault(JSONArray())
    private fun readHistory(): List<History> = runCatching {
        val text = historyFile.openRead().use { String(it.readBytes(), Charsets.UTF_8) }
        JSONArray(text).objects().map { h -> History(h.getString("connection"), h.getString("workPath"), h.getString("workName"),
            if (h.isNull("cover")) null else h.getString("cover"), h.getString("videoPath"), h.getString("videoName"),
            h.optString("season"), h.getLong("position"), h.getLong("duration"), h.getLong("updatedAt")) }.take(100)
    }.getOrDefault(emptyList())
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
