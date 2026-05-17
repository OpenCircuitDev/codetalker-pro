package dev.opencircuit.codetalker.net

import android.util.Log
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import org.json.JSONObject


/**
 * CompositionLocal holder for the app's single [DaemonEvents] instance.
 *
 * MainActivity creates the events bus after pairing and provides it
 * via CompositionLocalProvider. Deeply-nested screens collect events
 * without prop-drilling by reading `LocalDaemonEvents.current`. Null
 * when no pairing is active (the read path returns immediately).
 */
val LocalDaemonEvents = compositionLocalOf<DaemonEvents?> { null }

/**
 * Phase 4 (2026-05-16) — Pro Android consumer of the daemon's
 * `/api/events` SSE stream.
 *
 * One instance per app process. MainActivity opens the stream after
 * pairing succeeds; UI screens collect from the [events] flow and
 * refresh their local state when a relevant event arrives.
 *
 * 2026-05-16 (Option C bidirectional sync) — added auto-reconnect with
 * backoff and a [connections] SharedFlow that emits each time the SSE
 * channel (re)opens. Consumers use [connections] to bootstrap any
 * state that must be re-synced after a daemon restart (e.g. POSTing
 * the local active session set so the daemon's
 * `companion_active_sessions` repopulates). Replaces the brittle
 * one-shot reconciliation that kept getting stale across daemon
 * restarts + transient network blips.
 */
class DaemonEvents(
    private val client: DaemonClient,
) {
    companion object {
        private const val TAG = "DaemonEvents"
        // Buffer size for replay-on-late-subscribe. Tight: SSE events
        // are deltas, not snapshots — subscribers that join late
        // re-fetch state from REST rather than reading historical
        // events. Buffer guards against the very-rapid-fire case where
        // a SessionChanged storm could otherwise drop events between
        // emit and collect.
        private const val REPLAY_BUFFER = 8
        // 2026-05-16 — exponential backoff ladder for SSE reconnects.
        // Starts at 2s (fast recovery from daemon kill/restart),
        // settles at 60s (avoid hammering a genuinely-down daemon).
        private val RECONNECT_BACKOFF_MS = longArrayOf(2_000, 5_000, 15_000, 30_000, 60_000)
    }

    private val _events = MutableSharedFlow<DaemonEvent>(
        replay = 0,
        extraBufferCapacity = REPLAY_BUFFER,
    )

    /** Hot flow of daemon-pushed events. Subscribers collect, filter
     *  by [DaemonEvent.eventType], and refresh their local state. */
    val events: SharedFlow<DaemonEvent> = _events.asSharedFlow()

    /** 2026-05-16 — emits Unit every time the SSE channel (re)opens
     *  successfully. Subscribers use this to push canonical state back
     *  to the daemon after a restart (e.g. MainActivity POSTs the local
     *  active session set so the daemon repopulates and audio routing
     *  starts working again). */
    private val _connections = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 4,
    )
    val connections: SharedFlow<Unit> = _connections.asSharedFlow()

    @Volatile
    private var source: EventSource? = null
    @Volatile
    private var closing: Boolean = false
    private var reconnectScope: CoroutineScope? = null
    private var reconnectJob: Job? = null
    private var attempt: Int = 0
    private var topicsForReconnect: List<String>? = null

    /**
     * Open the daemon's SSE stream and start fanning events into
     * [events]. Idempotent: if already open, this is a no-op.
     *
     * On failure or remote close, auto-reconnects with exponential
     * backoff up to 60s. Each successful reopen emits on [connections]
     * so subscribers can re-bootstrap.
     *
     * Caller is responsible for calling [close] at app teardown.
     */
    fun connect(topics: List<String>? = null) {
        if (source != null) return
        closing = false
        topicsForReconnect = topics
        if (reconnectScope == null) {
            reconnectScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
        openSource()
    }

    private fun openSource() {
        source = client.openDaemonEventStream(
            topics = topicsForReconnect,
            listener = object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    Log.w(TAG, "/api/events stream opened (attempt=$attempt)")
                    attempt = 0
                    _connections.tryEmit(Unit)
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String,
                ) {
                    val eventType = type ?: "unknown"
                    val payload = try {
                        JSONObject(data)
                    } catch (e: Exception) {
                        Log.w(TAG, "malformed event payload: $e")
                        return
                    }
                    val sessionId = payload.optString("session_id", "").ifBlank { null }
                    _events.tryEmit(
                        DaemonEvent(
                            eventType = eventType,
                            sessionId = sessionId,
                            data = payload,
                        )
                    )
                }

                override fun onClosed(eventSource: EventSource) {
                    Log.w(TAG, "/api/events stream closed (closing=$closing)")
                    source = null
                    if (!closing) scheduleReconnect()
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?,
                ) {
                    Log.w(TAG, "/api/events stream failure: ${t?.message}")
                    source = null
                    if (!closing) scheduleReconnect()
                }
            }
        )
    }

    private fun scheduleReconnect() {
        val scope = reconnectScope ?: return
        val idx = attempt.coerceIn(0, RECONNECT_BACKOFF_MS.size - 1)
        val backoffMs = RECONNECT_BACKOFF_MS[idx]
        attempt += 1
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(backoffMs)
            if (!closing && source == null) {
                Log.w(TAG, "/api/events reconnecting (attempt=$attempt, prev_backoff=${backoffMs}ms)")
                openSource()
            }
        }
    }

    fun close() {
        closing = true
        reconnectJob?.cancel()
        reconnectJob = null
        source?.cancel()
        source = null
        reconnectScope?.cancel()
        reconnectScope = null
    }
}

/**
 * A single event delivered over the /api/events SSE channel.
 *
 * @property eventType discriminator from the SSE `event:` header
 *   (e.g. "SessionChanged"). Use this to dispatch.
 * @property sessionId extracted from the payload when the event
 *   carries one (SessionChanged, LifecycleChanged, etc.); null for
 *   master-level events.
 * @property data the full JSON payload as a JSONObject for handlers
 *   that need fields beyond sessionId.
 */
data class DaemonEvent(
    val eventType: String,
    val sessionId: String?,
    val data: JSONObject,
)
