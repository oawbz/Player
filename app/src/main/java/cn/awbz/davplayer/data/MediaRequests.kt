package cn.awbz.davplayer.data

import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response

/** Redirects apply only to video GETs; directory requests stay within the DAV root. */
object MediaRequests {
    private val redirects = setOf(301, 302, 303, 307, 308)
    fun open(dav: DavClient, connection: Connection, initial: HttpUrl, range: String?,
             onCall: (Call) -> Unit = {}): Response {
        if (!DavUrls.inside(DavUrls.base(connection.baseUrl), initial)) throw DavFailure("媒体不在当前 WebDAV 连接范围内")
        var url = initial
        var headers = dav.headers(connection)
        val visited = mutableSetOf<String>()
        for (hop in 0..5) {
            if (!visited.add(url.toString())) throw DavFailure("视频地址重定向循环")
            val builder = Request.Builder().url(url).header("Accept-Encoding", "identity")
            headers.forEach { (key, value) -> builder.header(key, value) }
            range?.let { builder.header("Range", it) }
            val call = dav.mediaTransport.newCall(builder.get().build()); onCall(call)
            val result = call.execute()
            PlaybackTrace.write("GET hop=$hop http=${result.code()} range=${range ?: "none"} length=${result.body()?.contentLength() ?: -1}")
            if (result.code() !in redirects) return result
            val location = result.header("Location")
            result.close()
            if (hop == 5) throw DavFailure("视频地址重定向次数过多")
            val next = location?.let { url.resolve(it) } ?: throw DavFailure("视频重定向地址无效")
            if (next.username().isNotEmpty() || next.password().isNotEmpty()) throw DavFailure("视频重定向地址不能包含账号密码")
            if (url.scheme() == "https" && next.scheme() != "https") throw DavFailure("拒绝视频 HTTPS 降级重定向")
            // Once a chain leaves the origin, never restore DAV credentials, even on return.
            if (!DavUrls.sameOrigin(url, next)) headers = emptyMap()
            url = next.newBuilder().fragment(null).build()
        }
        throw DavFailure("视频地址重定向次数过多")
    }
}
