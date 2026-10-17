package cn.awbz.davplayer.data

import okhttp3.*
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Shared transport for API/metadata only. No default authentication headers or media reads. */
object SourceHttp {
    private val calls = AtomicLong()
    private val connections = AtomicLong()
    private val bytes = AtomicLong()
    private val active = AtomicLong()
    private val peak = AtomicLong()
    private val elapsed = AtomicLong()
    enum class Kind { LOGIN, LIST, LINK, RESOURCE }
    private val kinds = java.util.concurrent.ConcurrentHashMap<Kind, AtomicLong>()
    private val statuses = java.util.concurrent.ConcurrentHashMap<Int, AtomicLong>()
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .eventListener(object : EventListener() {
            override fun connectStart(call: Call, address: InetSocketAddress, proxy: Proxy) { connections.incrementAndGet() }
        }).build()
    data class Response(val status: Int, val data: ByteArray, val location: String?, val retryAfterSeconds: Long?)
    data class Summary(val calls: Long, val connections: Long, val bytes: Long, val active: Long, val peak: Long, val elapsedMs: Long, val kinds: Map<Kind, Long>, val statuses: Map<Int, Long>)
    fun summary() = Summary(calls.get(), connections.get(), bytes.get(), active.get(), peak.get(), elapsed.get(), kinds.mapValues { it.value.get() }, statuses.mapValues { it.value.get() })
    fun execute(request: Request, limit: Int, scope: RequestScope, timeoutNanos: Long = 15_000_000_000L, kind: Kind = Kind.RESOURCE): Response {
        scope.check()
        if (timeoutNanos <= 0) throw MediaFailure("资料读取超时")
        val call = client.newCall(request)
        call.timeout().timeout(minOf(timeoutNanos, 15_000_000_000L), TimeUnit.NANOSECONDS)
        return scope.run({ call.cancel() }) {
            scope.check()
            val start = System.nanoTime()
            calls.incrementAndGet()
            kinds.getOrPut(kind) { AtomicLong() }.incrementAndGet()
            val running = active.incrementAndGet()
            while (true) {
                val previous = peak.get()
                if (previous >= running || peak.compareAndSet(previous, running)) break
            }
            try {
                call.execute().use { response ->
                    statuses.getOrPut(response.code()) { AtomicLong() }.incrementAndGet()
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
                                data.write(buffer, 0, count); bytes.addAndGet(count.toLong())
                            }
                        }
                    }
                    scope.check()
                    Response(response.code(), data.toByteArray(), response.header("Location"),
                        response.header("Retry-After")?.toLongOrNull()?.coerceAtLeast(0))
                }
            } finally {
                active.decrementAndGet()
                elapsed.addAndGet((System.nanoTime() - start) / 1_000_000)
            }
        }
    }
}

class SourceStatusFailure(val status: Int, val retryAfterSeconds: Long? = null) : java.io.IOException("请求失败（状态 $status）")
