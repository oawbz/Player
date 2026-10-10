package cn.awbz.davplayer.data

import okhttp3.*
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.concurrent.TimeUnit

class Requests {
    private val calls = Collections.synchronizedSet(mutableSetOf<Call>())
    @Volatile private var cancelled = false
    fun <T> execute(client: OkHttpClient, request: Request, read: (Response) -> T): T {
        val call = client.newCall(request)
        synchronized(calls) { if (cancelled) throw DavFailure("请求已取消"); calls.add(call) }
        try { return call.execute().use(read) } finally { calls.remove(call) }
    }
    fun cancel() { synchronized(calls) { cancelled = true; calls.forEach { it.cancel() }; calls.clear() } }
}

class DavClient {
    // No automatic redirect: an untrusted Location must never receive WebDAV credentials.
    val transport = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    val mediaTransport = transport.newBuilder().callTimeout(0, TimeUnit.SECONDS).build()
    fun headers(connection: Connection): Map<String, String> = if (connection.authMode == AuthMode.ANONYMOUS) emptyMap()
        else mapOf("Authorization" to Credentials.basic(connection.username, connection.password))
    private fun request(connection: Connection, url: HttpUrl): Request.Builder {
        if (!DavUrls.inside(DavUrls.base(connection.baseUrl), url)) throw DavFailure("资源不在当前 WebDAV 连接范围内")
        val builder = Request.Builder().url(url)
        headers(connection).forEach { (name, value) -> builder.header(name, value) }
        return builder
    }
    fun list(connection: Connection, path: String, requests: Requests): List<DavEntry> {
        val url = DavUrls.directory(DavUrls.fromPath(connection, path))
        val body = RequestBody.create(MediaType.parse("application/xml; charset=utf-8"),
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/></d:prop></d:propfind>")
        return requests.execute(transport, request(connection, url).header("Depth", "1").method("PROPFIND", body).build()) { response ->
            if (response.code() != 207) fail(response.code())
            DavXml.parse(String(bytes(response, 2 * 1024 * 1024), Charsets.UTF_8), url)
        }
    }
    fun read(connection: Connection, path: String, requests: Requests, limit: Int): ByteArray =
        requests.execute(transport, request(connection, DavUrls.fromPath(connection, path)).get().build()) { response ->
            if (!response.isSuccessful) fail(response.code())
            bytes(response, limit)
        }
    private fun bytes(response: Response, limit: Int): ByteArray {
        val body = response.body() ?: throw DavFailure("服务器返回了空响应")
        if (body.contentLength() > limit) throw DavFailure("资源超过大小限制")
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        body.byteStream().use { stream ->
            while (true) {
                if (Thread.currentThread().isInterrupted) throw DavFailure("请求已取消")
                val count = stream.read(buffer); if (count < 0) break
                if (output.size() + count > limit) throw DavFailure("资源超过大小限制")
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }
    private fun fail(code: Int): Nothing = throw DavFailure(when (code) {
        401, 403 -> "访问被拒绝，请检查账号密码和目录权限"
        404 -> "目录或文件不存在，可能已被移动或删除"
        in 300..399 -> "服务器要求重定向，请将连接地址改为最终 WebDAV 地址"
        else -> "WebDAV 请求失败（HTTP $code）"
    })
}
