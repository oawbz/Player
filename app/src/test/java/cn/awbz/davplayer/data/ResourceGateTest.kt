package cn.awbz.davplayer.data

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class ResourceGateTest {
    @Test fun playbackWaitsForActualExitAndSkipsQueuedResources() {
        val serial = Executors.newSingleThreadExecutor(); val controls = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1); val cancelled = CountDownLatch(1); val exit = CountDownLatch(1); val ready = CountDownLatch(1)
        val extra = AtomicInteger()
        try {
            val gate = ResourceGate(serial, controls); val scope = RequestScope()
            gate.submit(scope) { scope.run({ cancelled.countDown() }) { started.countDown(); assertTrue(exit.await(3, TimeUnit.SECONDS)) } }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            gate.submit(RequestScope()) { extra.incrementAndGet() }
            gate.pauseAndDrain { ready.countDown() }
            assertTrue(cancelled.await(2, TimeUnit.SECONDS))
            assertFalse(ready.await(50, TimeUnit.MILLISECONDS))
            exit.countDown(); assertTrue(ready.await(2, TimeUnit.SECONDS)); assertEquals(0, extra.get())
            gate.resumeBrowsing(); gate.submit(RequestScope()) { extra.incrementAndGet() }.get(2, TimeUnit.SECONDS)
            assertEquals(1, extra.get())
        } finally { exit.countDown(); serial.shutdownNow(); controls.shutdownNow() }
    }
    @Test fun cancellingFutureStillWaitsForOperationToExit() {
        val serial = Executors.newSingleThreadExecutor(); val controls = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1); val exit = CountDownLatch(1); val ready = CountDownLatch(1)
        try {
            val gate = ResourceGate(serial, controls)
            val future = gate.submit(RequestScope()) {
                started.countDown()
                var running = true
                while (running) {
                    try { exit.await(); running = false } catch (_: InterruptedException) { }
                }
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            future.cancel(true)
            gate.pauseAndDrain { ready.countDown() }
            assertFalse(ready.await(50, TimeUnit.MILLISECONDS))
            exit.countDown(); assertTrue(ready.await(2, TimeUnit.SECONDS))
        } finally { exit.countDown(); serial.shutdownNow(); controls.shutdownNow() }
    }
}
