package cn.awbz.davplayer.data

/** A screen owns cancellation; active API calls and downloads register cancellation hooks. */
class RequestScope {
    private val lock = Any()
    private val pending = mutableSetOf<() -> Unit>()
    private val results = mutableMapOf<Any, java.util.concurrent.FutureTask<*>>()
    @Volatile var isCancelled = false
        private set
    fun <T> run(cancel: () -> Unit, operation: () -> T): T {
        synchronized(lock) {
            if (isCancelled) throw MediaFailure("请求已取消")
            pending.add(cancel)
        }
        return try { operation() } finally { synchronized(lock) { pending.remove(cancel) } }
    }
    fun check() {
        if (isCancelled || Thread.currentThread().isInterrupted) throw MediaFailure("请求已取消")
    }
    /** Deduplicate within one screen load only; a new scope always reads fresh directories. */
    fun <T> once(key: Any, operation: () -> T): T {
        check()
        val created = java.util.concurrent.FutureTask(java.util.concurrent.Callable { check(); operation() })
        val task = synchronized(lock) { results.getOrPut(key) { created } }
        if (task === created) created.run()
        try {
            @Suppress("UNCHECKED_CAST")
            val result = task.get() as T
            check()
            return result
        } catch (e: java.util.concurrent.ExecutionException) { throw e.cause ?: e }
        catch (_: InterruptedException) { Thread.currentThread().interrupt(); throw MediaFailure("请求已取消") }
    }
    fun cancel() {
        val hooks = synchronized(lock) { if (isCancelled) return; isCancelled = true; pending.toList().also { pending.clear() } }
        hooks.forEach { runCatching { it() } }
        synchronized(lock) { results.clear() }
    }
}
