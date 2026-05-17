package dev.opencircuit.codetalker.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dev.opencircuit.codetalker.net.DaemonClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * CCT-31 Phase 7 — TTS audio playback for the AR companion.
 *
 * 2026-05-11 — promoted from single-session player to **multi-active**
 * fan-in. The phone may now have N session_ids active simultaneously;
 * each one runs its own long-poll coroutine against
 * `/api/companion/audio-stream/{sid}` and pushes the fetched WAV bytes
 * into a shared sequential channel. A single dispatcher loop drains the
 * channel through one [ExoPlayer], so two sessions narrating at the
 * same time queue cleanly instead of clobbering each other on the
 * speaker.
 *
 * Architecture:
 * ```
 *   session A poller ─┐
 *   session B poller ─┼──> WAV channel ──> dispatcher ──> ExoPlayer ──> speaker
 *   session C poller ─┘                       ▲
 *                                             │
 *                              selectNextWav(): TODO policy
 * ```
 *
 * The X-CCT-Pairing-Token header is added via the OkHttp request used by
 * each poller. ExoPlayer plays bytes from an in-memory [ByteArrayDataSource]
 * so it never touches the network — that way one connection-error in one
 * session's poller can't drop another session's audio mid-playback.
 */
@UnstableApi
class TTSPlayer(
    context: Context,
    private val daemonClient: DaemonClient,
    private val pairingToken: String,
    private val audioFocusManager: AudioFocusManager = AudioFocusManager.forContext(context),
) {
    /** One WAV fetched from one session, awaiting playback. */
    data class WavPayload(
        val sessionId: String,
        val bytes: ByteArray,
        /** When the bytes arrived on the phone — used by selectNextWav. */
        val arrivedAtEpochMs: Long,
    )

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ false,
        )
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Coroutine scope owning every poller + the dispatcher. SupervisorJob
     *  so a crash in one poller doesn't cascade-cancel siblings. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One poller job per subscribed session. */
    private val pollers = mutableMapOf<String, Job>()

    /** Mid-priority buffer between pollers and dispatcher. 32 slots is enough
     *  for ~30s of typical narration at burst rates; backpressure kicks in
     *  beyond that so pollers wait instead of dropping. */
    private val wavChannel = Channel<WavPayload>(capacity = 32)

    /** Set the dispatcher reads via observe() so it can re-prioritize when
     *  the user changes which sessions are active. Drives the "Active · N"
     *  count too. */
    private val _activeSessionIds = MutableStateFlow<Set<String>>(emptySet())
    val activeSessionIds get() = _activeSessionIds

    /** OkHttp client used by the pollers. Separate from the daemon client's
     *  shared OkHttp because each poller can be blocked in a 55s long-poll
     *  for minutes at a time, and we don't want one slow session to
     *  monopolize the connection pool used elsewhere. */
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Set on the playback coroutine when it's actively playing a WAV. The
     *  Player.Listener completes it on STATE_ENDED/error so the dispatcher
     *  can pull the next payload. Initialized to true so the first iteration
     *  doesn't block. */
    private val playbackDone = MutableStateFlow(true)

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                Log.w("TTSPlayer", "state=$state")
                if (state == Player.STATE_ENDED) {
                    playbackDone.value = true
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.w("TTSPlayer", "error code=${error.errorCode} msg=${error.message}")
                playbackDone.value = true
            }
        })
        // Dispatcher loop. Started here; lives until release().
        scope.launch { dispatcherLoop() }
    }

    /**
     * Set the full active set. Pollers for new sids are spawned; pollers
     * for removed sids are cancelled. Idempotent — safe to call from a
     * Compose effect on every change of the StateFlow.
     */
    fun setActiveSessions(sessionIds: Set<String>) {
        Log.w("TTSPlayer", "setActiveSessions(${sessionIds.joinToString()})")
        _activeSessionIds.value = sessionIds
        // Spawn missing pollers.
        for (sid in sessionIds) {
            if (sid !in pollers) {
                pollers[sid] = scope.launch { pollerLoop(sid) }
            }
        }
        // Cancel pollers no longer wanted.
        val toRemove = pollers.keys - sessionIds
        for (sid in toRemove) {
            pollers.remove(sid)?.cancel()
        }
        // Ensure focus when we have any active sessions; release when none.
        if (sessionIds.isNotEmpty()) {
            audioFocusManager.requestFocus(
                onPause = { mainHandler.post { exoPlayer.playWhenReady = false } },
                onResume = { mainHandler.post { exoPlayer.playWhenReady = true } },
                onStop = { mainHandler.post {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                } },
            )
        } else {
            audioFocusManager.releaseFocus()
            mainHandler.post {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            }
        }
    }

    /** Backward-compat entry point. Replaces the entire active set with one sid. */
    @Deprecated("Use setActiveSessions(setOf(sid)) for multi-active support.")
    fun playSession(sessionId: String) {
        setActiveSessions(setOf(sessionId))
    }

    /**
     * 2026-05-16 — force every active poller to be cancelled and respawned
     * with a fresh OkHttp connection pool. Use this when an external signal
     * suggests in-flight long-polls may be silently stale:
     *   - daemon process restarted (its TCP listener died; pooled sockets
     *     in OkHttp are half-closed and may write OK but never read);
     *   - app returned to foreground after a long background period
     *     (Doze/standby may have invalidated NAT mappings);
     *   - NetworkStateObserver.reconnectTick incremented (Wi-Fi restored).
     *
     * Idempotent and cheap when called with healthy connections — the only
     * cost is one extra round-trip per active poller. Without this method,
     * `setActiveSessions(currentSet)` is a no-op (it only spawns pollers
     * for sids NOT already present in the map), so a stuck poller stays
     * stuck until the user explicitly toggles its sid off then back on.
     */
    fun respawnAllPollers() {
        val currentSet = _activeSessionIds.value
        Log.w("TTSPlayer", "respawnAllPollers count=${pollers.size} -> ${currentSet.size}")
        pollers.values.forEach { it.cancel() }
        pollers.clear()
        // Evict pooled sockets so retries use fresh TCP. Without this, a
        // restarted daemon can leave OkHttp serving half-closed connections
        // that hang on read until readTimeout (60s) — multiplying the
        // post-restart silence window.
        try { httpClient.connectionPool.evictAll() } catch (_: Throwable) {}
        for (sid in currentSet) {
            pollers[sid] = scope.launch { pollerLoop(sid) }
        }
    }

    /**
     * Long-poll one session for WAVs. Each iteration fetches one bounded WAV
     * (or 204 No Content if nothing's queued) and pushes it into the shared
     * channel. Re-polls immediately on success, with light backoff on errors.
     */
    private suspend fun pollerLoop(sessionId: String) {
        val url = daemonClient.audioStreamUrl(sessionId)
        var backoffMs = 250L
        while (scope.isActive && sessionId in pollers) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header(DaemonClient.HEADER_TOKEN, pairingToken)
                    .get()
                    .build()
                val payload = withContext(Dispatchers.IO) {
                    httpClient.newCall(req).execute().use { resp ->
                        when {
                            resp.code == 204 -> null
                            resp.isSuccessful -> {
                                val bytes = resp.body?.bytes()
                                if (bytes != null && bytes.isNotEmpty()) {
                                    WavPayload(sessionId, bytes, System.currentTimeMillis())
                                } else null
                            }
                            else -> {
                                Log.w("TTSPlayer", "poller(${sessionId.take(8)}) HTTP ${resp.code}")
                                null
                            }
                        }
                    }
                }
                if (payload != null) {
                    backoffMs = 250L
                    wavChannel.send(payload)
                }
                // 204 just means "nothing yet — try again". Daemon's long-poll
                // window has already elapsed, so no extra delay is needed.
            } catch (e: Throwable) {
                Log.w("TTSPlayer", "poller(${sessionId.take(8)}) error: ${e.message}")
                // 2026-05-16 — evict pooled sockets after every error so the
                // next retry establishes a fresh TCP connection. Without
                // this, a half-closed connection from a restarted daemon
                // can persist in the pool and cause every subsequent
                // request to hang for the full readTimeout (60s) before
                // erroring, multiplying the post-restart silence window
                // by every retry attempt.
                try { httpClient.connectionPool.evictAll() } catch (_: Throwable) {}
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(5_000L)
            }
        }
        Log.w("TTSPlayer", "poller(${sessionId.take(8)}) exited")
    }

    /**
     * Drain the WAV channel sequentially through ExoPlayer. Suspends on
     * the previous playback's STATE_ENDED before starting the next so
     * concurrent narrations from different sessions queue cleanly on the
     * one speaker (per user spec: sequential, not overlapping).
     */
    private suspend fun dispatcherLoop() {
        // Buffer of payloads available at the same instant — drained by
        // selectNextWav() based on the user-defined policy.
        val ready = mutableListOf<WavPayload>()
        while (scope.isActive) {
            // Block until we have at least one payload to choose from.
            ready.add(wavChannel.receive())
            // Drain any other payloads that arrived in the same scheduling
            // tick so the policy can compare them. Non-blocking.
            while (true) {
                val more = wavChannel.tryReceive().getOrNull() ?: break
                ready.add(more)
            }
            // Wait for the previous WAV to finish before picking the next.
            playbackDone.filter { it }.first()
            val next = selectNextWav(ready)
            ready.remove(next)
            // Anything not selected stays in 'ready' for the next iteration —
            // they don't go back through the channel, so order is stable.
            // Re-buffer them at the head by leaving them in the list.
            playbackDone.value = false
            withContext(Dispatchers.Main) {
                val source = makeMediaSource(next)
                exoPlayer.setMediaSource(source, /* resetPosition = */ true)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
        }
    }

    /**
     * Build an ExoPlayer MediaSource from raw WAV bytes. Decouples the
     * fetch path (OkHttp) from the playback path (ByteArrayDataSource) so
     * one slow session can't stall another session's playback.
     */
    private fun makeMediaSource(payload: WavPayload): ProgressiveMediaSource {
        val factory = DataSource.Factory { ByteArrayDataSource(payload.bytes) }
        val mediaItem = MediaItem.fromUri("bytes://${payload.sessionId}/${payload.arrivedAtEpochMs}")
        return ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem)
    }

    /**
     * Pick which WAV to play next when multiple are sitting in `ready` at
     * the same instant. **TODO(user):** this is the playback-ordering
     * policy decision — see the brief in the conversation. Implement 5-10
     * lines that select the desired payload. Default below is FIFO
     * (oldest-arrival first).
     *
     * Policy options:
     *  - FIFO: `ready.minBy { it.arrivedAtEpochMs }` — simplest, "first
     *    synthesized, first played".
     *  - Mode-priority: prefer payloads from sessions in "live" mode over
     *    "brief" over "direct". Requires a SessionLite lookup; cache the
     *    most recent listSessions() result.
     *  - Recency-by-user-interaction: prefer the session the user most
     *    recently typed in. Read SessionLite.lastUserInteractionAt from a
     *    cached snapshot.
     *  - Mixed: live mode jumps the queue, otherwise FIFO.
     *
     * Return the payload to play next; the dispatcher will play exactly
     * that one and leave the others in `ready` for the next iteration.
     */
    private fun selectNextWav(ready: List<WavPayload>): WavPayload {
        // Default: FIFO. Replace with the chosen policy.
        return ready.minByOrNull { it.arrivedAtEpochMs } ?: ready.first()
    }

    fun pause() { mainHandler.post { exoPlayer.playWhenReady = false } }
    fun resume() { mainHandler.post { exoPlayer.playWhenReady = true } }

    fun stop() {
        setActiveSessions(emptySet())
    }

    fun release() {
        setActiveSessions(emptySet())
        mainHandler.post { exoPlayer.release() }
        scope.cancel()
        audioFocusManager.releaseFocus()
    }

    fun isPlaying(): Boolean =
        exoPlayer.playbackState == Player.STATE_READY && exoPlayer.playWhenReady
}
