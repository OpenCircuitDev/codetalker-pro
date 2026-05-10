package dev.opencircuit.codetalker.ui.errors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * CCT-32 Task B.3 — locks down the throwable -> catalog mapping so the
 * UI doesn't accidentally leak raw exception messages.
 */
class AppErrorsTest {

    @Test
    fun `IOException maps to DaemonUnreachable`() {
        val e = IOException("listSessions HTTP 503")
        assertSame(AppError.DaemonUnreachable, AppErrors.fromThrowable(e))
    }

    @Test
    fun `HTTP 401 maps to TokenExpired`() {
        val e = IOException("listSessions HTTP 401")
        assertSame(AppError.TokenExpired, AppErrors.fromThrowable(e))
    }

    @Test
    fun `HTTP 403 also maps to TokenExpired`() {
        val e = IOException("getSession HTTP 403")
        assertSame(AppError.TokenExpired, AppErrors.fromThrowable(e))
    }

    @Test
    fun `HTTP 426 maps to DaemonVersionMismatch`() {
        val e = IOException("listSessions HTTP 426 api=v2")
        val mapped = AppErrors.fromThrowable(e)
        assertTrue(mapped is AppError.DaemonVersionMismatch)
        assertEquals("v2", (mapped as AppError.DaemonVersionMismatch).serverApi)
    }

    @Test
    fun `non-IO exception falls through to DaemonUnreachable`() {
        val e = RuntimeException("kaboom")
        assertSame(AppError.DaemonUnreachable, AppErrors.fromThrowable(e))
    }

    @Test
    fun `every catalog entry has body and recovery`() {
        val all = listOf(
            AppError.DaemonUnreachable,
            AppError.TokenExpired,
            AppError.MicDenied,
            AppError.CameraDenied,
            AppError.NetworkDown,
            AppError.AudioFocusLost,
            AppError.InvalidPairingPayload,
            AppError.SessionOffline,
            AppError.DaemonVersionMismatch("v2", "v1"),
        )
        for (entry in all) {
            assertTrue("title for $entry", entry.title.isNotBlank())
            assertTrue("body for $entry", entry.body.isNotBlank())
        }
    }

    // ---------- CCT-32 Phase C — SessionOffline mapping ----------

    @Test
    fun `Broken pipe IOException maps to SessionOffline`() {
        val e = IOException("inject failed: java.io.IOException: Broken pipe")
        assertSame(AppError.SessionOffline, AppErrors.fromThrowable(e))
    }

    @Test
    fun `stream was reset maps to SessionOffline`() {
        val e = IOException("stream was reset: CANCEL")
        assertSame(AppError.SessionOffline, AppErrors.fromThrowable(e))
    }

    @Test
    fun `HTTP 410 Gone maps to SessionOffline`() {
        val e = IOException("getSession HTTP 410: session no longer live")
        assertSame(AppError.SessionOffline, AppErrors.fromThrowable(e))
    }

    @Test
    fun `unexpected end of stream maps to SessionOffline`() {
        val e = IOException("unexpected end of stream while parsing SSE")
        assertSame(AppError.SessionOffline, AppErrors.fromThrowable(e))
    }

    @Test
    fun `SessionOffline has refresh-style action label`() {
        assertEquals("Refresh sessions", AppError.SessionOffline.actionLabel)
        assertSame(AppError.Recovery.Retry, AppError.SessionOffline.recovery)
    }
}
