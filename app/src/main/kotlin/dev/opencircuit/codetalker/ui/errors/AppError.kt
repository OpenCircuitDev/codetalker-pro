package dev.opencircuit.codetalker.ui.errors

import java.io.IOException

/**
 * CCT-32 Task B.3 — exhaustive failure-mode catalog.
 *
 * Every error the user can see is one of these — built so [ErrorBanner]
 * can render a recoverable message + recovery action without a free-form
 * exception message leaking to the UI. Catalog members map 1:1 to the
 * task spec:
 *   - DaemonUnreachable (network or daemon down)
 *   - TokenExpired (HTTP 401 / 403 from daemon)
 *   - MicDenied (RECORD_AUDIO not granted)
 *   - CameraDenied (CAMERA not granted)
 *   - NetworkDown (Wi-Fi off, airplane mode)
 *   - AudioFocusLost (AUDIOFOCUS_LOSS — permanent steal)
 *   - InvalidPairingPayload (QR JSON malformed)
 *   - DaemonVersionMismatch (server reports an unsupported API version)
 *
 * Pure data — no Compose deps — so the catalog can be unit tested under
 * JVM and reused by the foreground service for notifications.
 */
sealed class AppError(
    val title: String,
    val body: String,
    val actionLabel: String?,
    val recovery: Recovery,
) {
    enum class Recovery {
        Retry,
        OpenSettings,
        RePair,
        None,
    }

    data object DaemonUnreachable : AppError(
        title = "Daemon unreachable",
        body = "Could not reach codetalker on your desktop. Check that the dashboard is running and that this device is on the same network.",
        actionLabel = "Retry",
        recovery = Recovery.Retry,
    )

    data object TokenExpired : AppError(
        title = "Pairing expired",
        body = "Your pairing token is no longer accepted by the daemon. Re-pair by scanning a fresh QR code from the dashboard.",
        actionLabel = "Re-pair",
        recovery = Recovery.RePair,
    )

    data object MicDenied : AppError(
        title = "Microphone permission needed",
        body = "Codetalker needs microphone access to capture your voice. Open Settings to allow it.",
        actionLabel = "Open Settings",
        recovery = Recovery.OpenSettings,
    )

    data object CameraDenied : AppError(
        title = "Camera permission needed",
        body = "Codetalker needs camera access to scan the pairing QR. Open Settings to allow it.",
        actionLabel = "Open Settings",
        recovery = Recovery.OpenSettings,
    )

    data object NetworkDown : AppError(
        title = "No network",
        body = "Wi-Fi looks disconnected. Reconnect, then try again.",
        actionLabel = "Retry",
        recovery = Recovery.Retry,
    )

    data object AudioFocusLost : AppError(
        title = "Audio paused by another app",
        body = "Another app took over audio focus permanently. Tap to reclaim audio for codetalker.",
        actionLabel = "Reclaim audio",
        recovery = Recovery.Retry,
    )

    data object InvalidPairingPayload : AppError(
        title = "Invalid pairing code",
        body = "That QR / payload didn't decode cleanly. Re-generate the QR from the dashboard and try again.",
        actionLabel = "Try again",
        recovery = Recovery.RePair,
    )

    data class DaemonVersionMismatch(val serverApi: String, val clientApi: String) : AppError(
        title = "Daemon version mismatch",
        body = "This companion expects daemon API $clientApi, but the daemon reports $serverApi. Update one side to match.",
        actionLabel = null,
        recovery = Recovery.None,
    )
}

/**
 * Map a thrown exception (from DaemonClient or one of its callers) onto a
 * concrete catalog entry. Keeps the entire mapping policy in one spot —
 * downstream UI never has to inspect IOException messages.
 */
object AppErrors {
    /**
     * Best-effort mapper from a Throwable raised by the daemon path onto
     * a catalog entry. Honours HTTP status codes baked into the message
     * (DaemonClient throws "fooBar HTTP 401" / "HTTP 426" etc.).
     */
    fun fromThrowable(t: Throwable): AppError {
        val msg = t.message.orEmpty()
        return when {
            "HTTP 401" in msg || "HTTP 403" in msg -> AppError.TokenExpired
            "HTTP 426" in msg -> AppError.DaemonVersionMismatch(
                serverApi = extractApiVersion(msg) ?: "?",
                clientApi = "v1",
            )
            t is IOException -> AppError.DaemonUnreachable
            else -> AppError.DaemonUnreachable
        }
    }

    private fun extractApiVersion(msg: String): String? =
        Regex("""api[=: ](\S+)""", RegexOption.IGNORE_CASE).find(msg)?.groupValues?.get(1)
}
