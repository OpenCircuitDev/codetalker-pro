package dev.opencircuit.codetalker.net

import android.util.Log
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
 * The flow emits [DaemonEvent] objects keyed by `eventType`. Each
 * screen filters for the types it cares about — SessionListScreen
 * + CTCGalleryScreen watch `SessionChanged` / `LifecycleChanged`;
 * PreferencesScreen watches `MasterConfigChanged`; DiagnosticsScreen
 * watches `AudioJobStateChanged`.
 *
 * Auto-reconnect is OkHttp's default for SSE. On reconnect, the daemon
 * does NOT replay missed events (the bus is ephemeral by design); the
 * screens' polling-fallback timers (3-5s currently) pick up any state
 * changes that fell into the gap.
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
    }

    private val _events = MutableSharedFlow<DaemonEvent>(
        replay = 0,
        extraBufferCapacity = REPLAY_BUFFER,
    )

    /** Hot flow of daemon-pushed events. Subscribers collect, filter
     *  by [DaemonEvent.eventType], and refresh their local state. */
    val events: SharedFlow<DaemonEvent> = _events.asSharedFlow()

    @Volatile
    private var source: EventSource? = null

    /**
     * Open the daemon's SSE stream and start fanning events into
     * [events]. Idempotent: if already open, this is a no-op.
     *
     * Caller is responsible for calling [close] at app teardown.
     */
    fun connect(topics: List<String>? = null) {
        if (source != null) return
        source = client.openDaemonEventStream(
            topics = topics,
            listener = object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    Log.d(TAG, "/api/events stream opened")
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
                    // tryEmit is non-suspending; emit on the SSE
                    // thread without blocking the stream reader. If
                    // the buffer is full, the oldest event drops —
                    // subscribers see only the most recent
                    // REPLAY_BUFFER worth. Acceptable: state is
                    // refreshed via REST after any drop.
                    _events.tryEmit(
                        DaemonEvent(
                            eventType = eventType,
                            sessionId = sessionId,
                            data = payload,
                        )
                    )
                }

                override fun onClosed(eventSource: EventSource) {
                    Log.d(TAG, "/api/events stream closed")
                    source = null
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?,
                ) {
                    Log.w(TAG, "/api/events stream failure: ${t?.message}")
                    source = null
                    // OkHttp's EventSource doesn't auto-reconnect; the
                    // caller (MainActivity / ConnectionGuard) decides
                    // when to retry. Polling fallback in screens
                    // covers the gap.
                }
            }
        )
    }

    fun close() {
        source?.cancel()
        source = null
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
