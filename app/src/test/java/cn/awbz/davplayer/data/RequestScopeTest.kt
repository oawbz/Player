package cn.awbz.davplayer.data

import org.junit.Assert.*
import org.junit.Test

class RequestScopeTest {
    @Test fun cancelClosesTransportOnceAndRejectsNewWork() {
        val scope = RequestScope(); var closes = 0
        scope.run({ closes++ }) { scope.cancel(); scope.cancel() }
        assertEquals(1, closes)
        try { scope.run({}) { fail("cancelled work started") }; fail("request admitted") } catch (_: MediaFailure) {}
    }
    @Test fun completedRequestIsNotClosedAgain() {
        val scope = RequestScope(); var closes = 0
        assertEquals(42, scope.run({ closes++ }) { 42 })
        scope.cancel(); assertEquals(0, closes)
    }
    @Test fun failedRequestRetiresItsCancellationHook() {
        val scope = RequestScope(); var closes = 0
        try { scope.run({ closes++ }) { throw java.io.IOException("fixture") } } catch (_: java.io.IOException) {}
        scope.cancel(); assertEquals(0, closes)
    }
    @Test fun concurrentReadsShareOneLoadButNewScopesReadAgain() {
        val scope = RequestScope()
        val count = java.util.concurrent.atomic.AtomicInteger()
        val entered = java.util.concurrent.CountDownLatch(1)
        val exit = java.util.concurrent.CountDownLatch(1)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(3)
        try {
            val tasks = (1..3).map { pool.submit<Int> {
                scope.once("same-directory") {
                    count.incrementAndGet(); entered.countDown()
                    check(exit.await(2, java.util.concurrent.TimeUnit.SECONDS)); 42
                }
            } }
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            exit.countDown()
            tasks.forEach { assertEquals(42, it.get(2, java.util.concurrent.TimeUnit.SECONDS)) }
            assertEquals(1, count.get())
            assertEquals(43, RequestScope().once("same-directory") { count.incrementAndGet(); 43 })
            assertEquals(2, count.get())
        } finally { exit.countDown(); pool.shutdownNow() }
    }
}
