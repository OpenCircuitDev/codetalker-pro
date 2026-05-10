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
            AppError.DaemonVersionMismatch("v2", "v1"),
        )
        for (entry in all) {
            assertTrue("title for $entry", entry.title.isNotBlank())
            assertTrue("body for $entry", entry.body.isNotBlank())
        }
    }
}
