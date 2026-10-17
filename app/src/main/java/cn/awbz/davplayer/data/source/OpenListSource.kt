package cn.awbz.davplayer.data.source

import cn.awbz.davplayer.data.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** API tokens and signed URLs remain in memory, never in models or logs. */
object OpenListSource {
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).build()
    private val tokens = ConcurrentHashMap<String, String>()
    private fun key(c: Connection): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest((c.baseUrl + "\u0000" + c.username + "\u0000" + c.password + c.authMode).toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun post(c: Connection, method: String, data: JSONObject, token: String, scope: RequestScope): JSONObject {
        val request = Request.Builder().url(MediaPaths.base(c.baseUrl).toString() + "api/" + method)
            .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), data.toString()))
            .apply { if (token.isNotEmpty()) header("Authorization", token) }.build()
        val call = client.newCall(request)
        return scope.run({ call.cancel() }) {
            call.execute().use { response ->
                if (response.code() == 401) return@use JSONObject().put("code", 401)
                if (!response.isSuccessful) throw MediaFailure("OpenList 请求失败（HTTP ${response.code()}）")
                val input = response.body()?.byteStream() ?: throw MediaFailure("OpenList 响应为空")
                val output = java.io.ByteArrayOutputStream()
                input.use { stream ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        if (scope.isCancelled || Thread.currentThread().isInterrupted) throw MediaFailure("请求已取消")
                        val count = stream.read(buffer); if (count < 0) break
                        if (output.size() + count > 8 * 1024 * 1024) throw MediaFailure("OpenList 响应过大")
                        output.write(buffer, 0, count)
                    }
                }
                JSONObject(output.toString("UTF-8"))
            }
        }
    }
    private fun token(c: Connection, scope: RequestScope): String {
        if (c.authMode == AuthMode.ANONYMOUS) return ""
        val key = key(c)
        tokens[key]?.let { return it }
        val result = post(c, "auth/login", JSONObject().put("username", c.username).put("password", c.password), "", scope)
        if (result.optInt("code") != 200) throw MediaFailure("OpenList 登录失败，请检查账号密码")
        val token = result.getJSONObject("data").getString("token")
        if (token.isBlank()) throw MediaFailure("OpenList 登录令牌为空")
        if (tokens.size > 16) tokens.clear()
        tokens[key] = token
        return token
    }
    private fun api(c: Connection, method: String, data: JSONObject, scope: RequestScope): JSONObject {
        repeat(2) { attempt ->
            val result = post(c, method, data, token(c, scope), scope)
            when (val code = result.optInt("code")) {
                200 -> return result.getJSONObject("data")
                401 -> { tokens.remove(key(c)); if (attempt == 1) throw MediaFailure("OpenList 登录已失效") }
                404 -> throw Missing()
                else -> throw MediaFailure("OpenList 请求失败（状态 $code）")
            }
        }
        throw MediaFailure("OpenList 登录失败")
    }
    class Missing : java.io.IOException()
    private fun path(c: Connection, path: String): String {
        val target = MediaPaths.fromPath(c, path)
        val root = MediaPaths.base(c.baseUrl)
        return "/" + MediaPaths.segments(target).drop(MediaPaths.segments(root).size).joinToString("/")
    }
    fun list(c: Connection, path: String, scope: RequestScope): List<MediaEntry> {
        val parent = MediaPaths.fromPath(c, path)
        val rows = mutableListOf<MediaEntry>()
        for (page in 1..51) {
            val data = api(c, "fs/list", JSONObject().put("path", path(c, path)).put("password", "")
                .put("page", page).put("per_page", 100).put("refresh", false), scope)
            val content = data.optJSONArray("content") ?: org.json.JSONArray()
            for (i in 0 until content.length()) {
                val item = content.getJSONObject(i)
                val name = item.getString("name"); val directory = item.getBoolean("is_dir")
                rows.add(MediaEntry(MediaPaths.child(parent, name, directory).rawPath, name, directory,
                    SourceTime.modified(item.optString("modified"), item.optString("created"))))
            }
            if (rows.size > 5000) throw MediaFailure("单层目录超过 5000 项")
            if (content.length() < 100 || rows.size >= data.optInt("total", Int.MAX_VALUE)) return rows
        }
        throw MediaFailure("单层目录超过 5000 项")
    }
    fun resolve(c: Connection, path: String, scope: RequestScope): String {
        val data = api(c, "fs/get", JSONObject().put("path", path(c, path)).put("password", ""), scope)
        val raw = data.optString("raw_url")
        val uri = try { URI(raw) } catch (_: Exception) { throw MediaFailure("OpenList 下载地址无效") }
        if (uri.scheme?.lowercase() !in listOf("http", "https") || uri.host.isNullOrBlank() || uri.rawUserInfo != null)
            throw MediaFailure("OpenList 下载地址无效")
        return uri.toASCIIString()
    }
}
