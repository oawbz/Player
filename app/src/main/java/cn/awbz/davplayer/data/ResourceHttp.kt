package cn.awbz.davplayer.data

import okhttp3.Request
import java.net.URI

/** Resolve via OpenList, then use the shared transport without API authentication headers. */
class ResourceHttp(private val resolve: (Connection, String, RequestScope) -> String =
    { c, p, scope -> cn.awbz.davplayer.data.source.OpenListSource.resolve(c, p, scope) }) {
    data class Response(val status: Int, val data: ByteArray)
    fun get(connection: Connection, path: String, limit: Int, scope: RequestScope): Response {
        val deadline = System.nanoTime() + 15_000_000_000L
        var target = try { URI(resolve(connection, path, scope)) }
        catch (_: cn.awbz.davplayer.data.source.OpenListSource.Missing) { return Response(404, ByteArray(0)) }
        repeat(6) {
            scope.check()
            validate(target)
            val request = Request.Builder().url(target.toASCIIString()).header("User-Agent", "CloudPlayer-Metadata/1").build()
            val response = SourceHttp.execute(request, limit, scope, deadline - System.nanoTime())
            when (response.status) {
                301, 302, 303, 307, 308 -> {
                    val next = target.resolve(response.location ?: throw MediaFailure("资料跳转地址无效"))
                    validate(next)
                    if (target.scheme.equals("https", true) && next.scheme.equals("http", true)) throw MediaFailure("资料跳转地址不受支持")
                    target = next
                }
                200, 404 -> return Response(response.status, response.data)
                else -> throw SourceStatusFailure(response.status, response.retryAfterSeconds)
            }
        }
        throw MediaFailure("资料跳转次数过多")
    }
    private fun validate(target: URI) {
        if (target.scheme?.lowercase() !in listOf("http", "https") || target.host.isNullOrBlank() || target.rawUserInfo != null)
            throw MediaFailure("资料跳转地址不受支持")
    }
}
