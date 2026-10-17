package cn.awbz.davplayer.data

import org.junit.Assert.*
import org.junit.Test

class RetryCooldownTest {
    @Test fun missesCoolDownWithoutBlockingDifferentPostersAndCanBeCleared() {
        var now = 0L
        val policy = RetryCooldown { now }
        assertTrue(policy.allowed("a"))
        policy.failed("a")
        assertFalse(policy.allowed("a")); assertTrue(policy.allowed("b"))
        now = 29_999; assertFalse(policy.allowed("a"))
        now = 30_000; assertTrue(policy.allowed("a"))
        policy.failed("a"); policy.succeeded("a"); assertTrue(policy.allowed("a"))
    }
    @Test fun serverRateLimitPausesAutomaticPostersForRetryAfter() {
        var now = 0L
        val policy = RetryCooldown { now }
        policy.failed("a", SourceStatusFailure(429, 120))
        assertFalse(policy.allowed("b"))
        now = 119_999; assertFalse(policy.allowed("b"))
        now = 120_000; assertTrue(policy.allowed("a")); assertTrue(policy.allowed("b"))
    }
}
