package dev.opencircuit.codetalker.net

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RetryPolicy.retryDelayMs].
 *
 * These pin the contract regardless of which retry strategy the user
 * implements: monotonically non-decreasing within reason, never zero,
 * and capped to keep the radio cool.
 */
class RetryPolicyTest {

    @Test
    fun `attempt 0 is at least 100ms`() {
        // Even the most aggressive policy should not retry under 100ms —
        // that's just hammering the wire.
        val ms = RetryPolicy.retryDelayMs(0)
        assertTrue("attempt 0 was ${ms}ms, expected >= 100", ms >= 100L)
    }

    @Test
    fun `delays generally grow with attempt count`() {
        // Allow some jitter — mean across 3 sample windows of attempt 0
        // should be smaller than mean of attempt 5.
        val low = (1..10).map { RetryPolicy.retryDelayMs(0) }.average()
        val high = (1..10).map { RetryPolicy.retryDelayMs(5) }.average()
        assertTrue("attempt 5 mean (${high}ms) should exceed attempt 0 mean (${low}ms)", high > low)
    }

    @Test
    fun `capped at 60 seconds even at very high attempt counts`() {
        val ms = RetryPolicy.retryDelayMs(20)
        assertTrue("attempt 20 was ${ms}ms — battery would suffer if uncapped", ms <= 60_000L)
    }
}
