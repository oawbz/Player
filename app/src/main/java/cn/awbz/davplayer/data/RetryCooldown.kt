package cn.awbz.davplayer.data

/** No retry polling. A later visible request can retry after this bounded cooldown. */
class RetryCooldown(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val misses = object : LinkedHashMap<String, Long>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 512
    }
    private var pausedUntil = Long.MIN_VALUE
    @Synchronized fun allowed(key: String) = clock().let { it >= pausedUntil && (misses[key]?.let { until -> it >= until } ?: true) }
    @Synchronized fun failed(key: String, failure: Throwable? = null) {
        val now = clock()
        misses[key] = now + 30_000
        if (failure is SourceStatusFailure && failure.status in listOf(429, 503)) {
            pausedUntil = now + ((failure.retryAfterSeconds ?: 30).coerceIn(30, 86_400) * 1000)
        }
    }
    @Synchronized fun succeeded(key: String) { misses.remove(key) }
}
