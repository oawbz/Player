package cn.awbz.davplayer.data

/** A screen owns cancellation; active API calls and downloads register cancellation hooks. */
class RequestScope {
    private val lock = Any()
    private val pending = mutableSetOf<() -> Unit>()
    @Volatile var isCancelled = false
        private set
    fun <T> run(cancel: () -> Unit, operation: () -> T): T {
        synchronized(lock) {
            if (isCancelled) throw MediaFailure("请求已取消")
            pending.add(cancel)
        }
        return try { operation() } finally { synchronized(lock) { pending.remove(cancel) } }
    }
    fun cancel() {
        val hooks = synchronized(lock) { if (isCancelled) return; isCancelled = true; pending.toList().also { pending.clear() } }
        hooks.forEach { runCatching { it() } }
    }
}
