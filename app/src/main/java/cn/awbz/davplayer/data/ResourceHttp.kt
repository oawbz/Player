package cn.awbz.davplayer.data

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI

/** Resolve each resource through OpenList; download without account credentials. */
class ResourceHttp(private val resolve: (Connection, String, RequestScope) -> String =
    { c, p, scope -> cn.awbz.davplayer.data.source.OpenListSource.resolve(c, p, scope) }) {
    data class Response(val status: Int, val data: ByteArray)
    fun get(connection: Connection, path: String, limit: Int, scope: RequestScope): Response {
        var target = try { URI(resolve(connection, path, scope)) }
        catch (_: cn.awbz.davplayer.data.source.OpenListSource.Missing) { return Response(404, ByteArray(0)) }
        val deadline = System.nanoTime() + 15_000_000_000L
        repeat(6) {
            check(scope, deadline)
            val url = target.toURL().openConnection() as HttpURLConnection
            url.instanceFollowRedirects = false; url.useCaches = false; url.connectTimeout = 5000; url.readTimeout = 5000
            url.setRequestProperty("Connection", "close"); url.setRequestProperty("Accept-Encoding", "identity")
            url.setRequestProperty("User-Agent", "CloudPlayer-Metadata/1")
            val result = scope.run({ url.disconnect() }) {
                try {
                    val code = url.responseCode
                    if (code in listOf(301, 302, 303, 307, 308)) {
                        val location = url.getHeaderField("Location") ?: throw MediaFailure("资料跳转地址无效")
                        val next = target.resolve(location)
                        if (next.scheme?.lowercase() !in listOf("http", "https") || next.host.isNullOrEmpty() || next.rawUserInfo != null ||
                            target.scheme.equals("https", true) && next.scheme.equals("http", true)) throw MediaFailure("资料跳转地址不受支持")
                        target = next
                        null
                    } else {
                        if (code == 404) Response(code, ByteArray(0))
                        else {
                            if (code != 200) throw MediaFailure("资料暂时无法更新")
                            if ((url.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L) > limit) throw MediaFailure("资料文件超过大小限制")
                            val bytes = ByteArrayOutputStream()
                            url.inputStream.use { stream ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    check(scope, deadline)
                                    val count = stream.read(buffer); if (count < 0) break
                                    if (bytes.size() + count > limit) throw MediaFailure("资料文件超过大小限制")
                                    bytes.write(buffer, 0, count)
                                }
                            }
                            check(scope, deadline)
                            Response(code, bytes.toByteArray())
                        }
                    }
                } finally { url.disconnect() }
            }
            if (result != null) return result
        }
        throw MediaFailure("资料跳转次数过多")
    }
    private fun check(scope: RequestScope, deadline: Long) {
        if (scope.isCancelled || Thread.currentThread().isInterrupted) throw MediaFailure("请求已取消")
        if (System.nanoTime() > deadline) throw MediaFailure("资料读取超时")
    }
}
