package cn.awbz.davplayer.data

import okhttp3.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Shared transport for API/metadata only. No default authentication headers or media reads. */
object SourceHttp {
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .build()
    data class Response(val status: Int, val data: ByteArray, val location: String?, val retryAfterSeconds: Long?)
    fun execute(request: Request, limit: Int, scope: RequestScope, timeoutNanos: Long = 15_000_000_000L): Response {
        scope.check()
        if (timeoutNanos <= 0) throw MediaFailure("资料读取超时")
        val call = client.newCall(request)
        call.timeout().timeout(minOf(timeoutNanos, 15_000_000_000L), TimeUnit.NANOSECONDS)
        return scope.run({ call.cancel() }) {
            scope.check()
            call.execute().use { response ->
                val body = response.body()
                val data = ByteArrayOutputStream()
                // Error bodies and redirect bodies are unnecessary; they may be very large.
                if (response.code() == 200 && body != null) {
                    if (body.contentLength() > limit) throw MediaFailure("响应超过大小限制")
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            scope.check()
                            val count = input.read(buffer); if (count < 0) break
                            if (data.size() + count > limit) throw MediaFailure("响应超过大小限制")
                            data.write(buffer, 0, count)
                        }
                    }
                }
                scope.check()
                Response(response.code(), data.toByteArray(), response.header("Location"),
                    response.header("Retry-After")?.toLongOrNull()?.coerceAtLeast(0))
            }
        }
    }
}

class SourceStatusFailure(val status: Int, val retryAfterSeconds: Long? = null) : java.io.IOException("请求失败（状态 $status）")
