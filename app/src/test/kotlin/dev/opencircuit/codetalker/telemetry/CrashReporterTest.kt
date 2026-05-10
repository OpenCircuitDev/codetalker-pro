package dev.opencircuit.codetalker.telemetry

import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CCT-32 Task G.1 — locks down the CrashReporter init guard.
 *
 * Three invariants:
 *   1. enabled = false → SDK is NEVER initialized.
 *   2. enabled = true but DSN blank → still NEVER initialized.
 *   3. enabled = true + DSN non-blank → SDK initialized exactly once,
 *      regardless of how many times init() is called.
 */
class CrashReporterTest {

    private val ctx = mockk<android.content.Context>(relaxed = true)
    private var initCount = 0
    private var seenDsn = ""

    @Before
    fun setUp() {
        CrashReporter.resetForTest()
        initCount = 0
        seenDsn = ""
        CrashReporter.sdkInitOverride = { _, dsn ->
            initCount++
            seenDsn = dsn
        }
    }

    @After
    fun tearDown() {
        CrashReporter.resetForTest()
    }

    @Test
    fun `disabled never initializes the SDK`() {
        CrashReporter.init(ctx, enabled = false, dsn = "https://example.sentry.io/123")
        assertFalse(CrashReporter.isInitialized())
        assertEquals(0, initCount)
    }

    @Test
    fun `enabled with blank DSN never initializes`() {
        CrashReporter.init(ctx, enabled = true, dsn = "")
        assertFalse(CrashReporter.isInitialized())
        assertEquals(0, initCount)
    }

    @Test
    fun `enabled with whitespace-only DSN never initializes`() {
        CrashReporter.init(ctx, enabled = true, dsn = "   ")
        assertFalse(CrashReporter.isInitialized())
        assertEquals(0, initCount)
    }

    @Test
    fun `enabled with valid DSN initializes the SDK once`() {
        CrashReporter.init(ctx, enabled = true, dsn = "https://example.sentry.io/123")
        assertTrue(CrashReporter.isInitialized())
        assertEquals(1, initCount)
        assertEquals("https://example.sentry.io/123", seenDsn)
    }

    @Test
    fun `init is idempotent`() {
        CrashReporter.init(ctx, enabled = true, dsn = "https://example.sentry.io/123")
        CrashReporter.init(ctx, enabled = true, dsn = "https://example.sentry.io/123")
        CrashReporter.init(ctx, enabled = true, dsn = "https://other.sentry.io/999")
        assertEquals(1, initCount)
        assertEquals("https://example.sentry.io/123", seenDsn)
    }
}
