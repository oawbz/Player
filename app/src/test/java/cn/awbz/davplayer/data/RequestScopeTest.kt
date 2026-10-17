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
}
