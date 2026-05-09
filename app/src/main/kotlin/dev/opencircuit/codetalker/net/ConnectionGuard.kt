package dev.opencircuit.codetalker.net

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

/**
 * CCT-31 Phase 5 — reconnection / retry policy for daemon calls.
 *
 * DESIGN DECISION (your call): when the daemon is unreachable, how do we
 * retry? This shapes the user's perceived "stickiness" of the AR experience.
 *
 * Three reasonable approaches, all of which I'm leaving to you because the
 * trade-off is heavily user-experience-flavored:
 *
 *   - AGGRESSIVE: 1s, 2s, 4s, 8s, 16s, 30s, 30s... — gets back online fast
 *     when the daemon hiccups, but burns battery if the PC is genuinely off.
 *
 *   - PATIENT: 5s, 10s, 30s, 60s, 60s... — saves battery, but if you walked
 *     to the kitchen for 30s, you get 30s of "Daemon unreachable" before
 *     reconnect.
 *
 *   - WIFI-AWARE: re-attempt immediately on network change events (handled
 *     by ConnectivityManager elsewhere) AND between events use one of the
 *     above fallback cadences.
 *
 * The function below is the placeholder. **In retryDelayMs(), implement
 * the policy you want.** The signature is: given an attempt count (0 = first
 * retry), return how many milliseconds to wait before the next attempt.
 *
 * Tradeoffs to weigh:
 *   - Beam Pro battery is small (~1500mAh). Aggressive retries warm the
 *     radio and drain quickly.
 *   - Tailscale takes a few seconds to re-establish after network changes;
 *     retrying at 1s when the radio just woke up is wasted.
 *   - The HUD already shows "Daemon unreachable" — so the UX cost of slower
 *     retry is just longer-visible bad-state, not silent failure.
 *   - You can add jitter (already imported `Random`) to avoid thundering
 *     herds when the daemon comes back online.
 *
 * v1 keeps this as a pure function so it's testable without a real
 * scheduler. ConnectionGuard wraps it with the actual delay loop.
 */
object RetryPolicy {

    /**
     * Returns the delay in milliseconds before retry #(attempt+1).
     * attempt = 0 means "first retry after the initial failure."
     *
     * v1 default: PATIENT cadence — 2s, 5s, 15s, 45s, then 60s steady.
     * Why: Beam Pro battery (~1500mAh) is the binding constraint, and the
     * HUD already shows "Daemon unreachable" so a slower cadence is just
     * "longer-visible bad state," never silent. Tailscale takes ~3s to
     * re-establish after a network change, so the first retry waits long
     * enough to give the VPN a chance to reconnect on its own.
     *
     * Easy to swap: replace this body with the AGGRESSIVE or WIFI-AWARE
     * shape from the docblock above. RetryPolicyTest pins the contract.
     */
    fun retryDelayMs(attempt: Int): Long {
        val ladder = longArrayOf(2_000L, 5_000L, 15_000L, 45_000L)
        val baseMs = if (attempt < ladder.size) ladder[attempt] else 60_000L
        // ±15% jitter so we don't thunder when the daemon comes back online
        // (multiple companion clients hitting the daemon simultaneously).
        val jitter = Random.nextDouble(0.85, 1.15)
        return (baseMs * jitter).toLong().coerceAtMost(60_000L)
    }
}

/**
 * Wraps a unit of work with the retry policy. Suspends between attempts.
 *
 * Usage:
 *   val sessions = ConnectionGuard.withRetry(maxAttempts = 5) {
 *       daemonClient.listSessions()
 *   }
 */
object ConnectionGuard {
    suspend fun <T> withRetry(maxAttempts: Int = 6, block: suspend () -> T): T {
        var lastError: Throwable? = null
        for (attempt in 0 until maxAttempts) {
            try {
                return block()
            } catch (t: Throwable) {
                lastError = t
                if (attempt == maxAttempts - 1) break
                delay(RetryPolicy.retryDelayMs(attempt))
            }
        }
        throw lastError ?: IllegalStateException("retry exhausted")
    }
}
