package cn.awbz.davplayer.data

import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/** All browsing directories and metadata share one lane; playback waits for actual exit. */
class ResourceGate(private val serial: ExecutorService, private val control: Executor) {
    private val lock = Any()
    private val inside = ThreadLocal<Boolean>()
    private var paused = false
    private var active: RequestScope? = null
    fun resumeBrowsing() { synchronized(lock) { paused = false } }
    fun <T> submit(scope: RequestScope, operation: () -> T): Future<T> = serial.submit(Callable {
        synchronized(lock) {
            scope.check()
            if (paused) throw MediaFailure("资料请求已暂停")
            active = scope
        }
        inside.set(true)
        try { operation() } finally {
            inside.remove()
            synchronized(lock) { if (active === scope) active = null }
        }
    })
    /** Called only by background directory jobs, never the UI thread. */
    fun <T> call(scope: RequestScope, operation: () -> T): T {
        scope.check()
        if (inside.get() == true) return operation()
        val future = submit(scope, operation)
        try { return future.get() }
        catch (_: InterruptedException) {
            scope.cancel(); future.cancel(true); Thread.currentThread().interrupt()
            throw MediaFailure("请求已取消")
        } catch (e: java.util.concurrent.ExecutionException) { throw e.cause ?: e }
    }
    fun pauseAndDrain(ready: () -> Unit) {
        val previous = synchronized(lock) { paused = true; active }
        control.execute {
            previous?.cancel()
            // Cancelled Future does not mean the operation has exited.
            serial.execute { ready() }
        }
    }
}
