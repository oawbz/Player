package cn.awbz.davplayer.data

import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/** One resource request at a time. Playback starts only after the running operation exits. */
class ResourceGate(private val serial: ExecutorService, private val control: Executor) {
    private val lock = Any()
    private var paused = false
    private var active: RequestScope? = null
    fun resumeBrowsing() { synchronized(lock) { paused = false } }
    fun submit(scope: RequestScope, operation: () -> Unit): Future<*> = serial.submit task@{
        synchronized(lock) {
            if (paused || scope.isCancelled) return@task
            active = scope
        }
        try { operation() } finally { synchronized(lock) { if (active === scope) active = null } }
    }
    fun pauseAndDrain(ready: () -> Unit) {
        val previous = synchronized(lock) { paused = true; active }
        control.execute {
            previous?.cancel()
            // Cancellation is not completion: this barrier runs after the operation's finally block.
            serial.execute { ready() }
        }
    }
}
