package dev.opencircuit.codetalker

import dev.opencircuit.codetalker.audio.STTEvent
import dev.opencircuit.codetalker.audio.STTRecorder
import dev.opencircuit.codetalker.input.ButtonState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 2026-05-21 — dictation review state machine.
 *
 * Replaces the old "tap to stop and SEND immediately" flow that surfaced
 * three different ways to lose user intent: (a) STT error captions sent
 * to the buddy LLM verbatim, (b) accidental long-press with no chance
 * to review what was heard, (c) no recovery path when the recognizer
 * misheard a key word. New flow:
 *
 *   IDLE ─tap─▶ RECORDING ─tap/30s/silence─▶ REVIEW ─Send─▶ DISPATCHING ─▶ IDLE
 *                                          └─Re-record──▶ RECORDING
 *                                          └─Cancel────▶ IDLE
 *
 * In REVIEW the user sees the final captured text and three buttons:
 * Send, Re-record, Cancel. Only Send dispatches to the daemon.
 */
enum class SttUiPhase { IDLE, RECORDING, REVIEW, DISPATCHING }

/**
 * CCT-32 Task A.8 — STT round-trip coordinator.
 *
 * Sits between [dev.opencircuit.codetalker.input.ButtonRouter]
 * (which knows about hardware buttons) and [STTRecorder] / the daemon
 * inject endpoint. ButtonRouter -> handleButtonState() -> STT.start /
 * dispatch -> daemon /api/companion/inject -> SSE-driven captionText.
 *
 * Not an `androidx.lifecycle.ViewModel`: keeping the surface dependency-
 * free for v1.0 so tests don't need a Robolectric context. The activity
 * still owns a single instance and calls [release] on destroy.
 */
/**
 * 2026-05-12 — distinguishes the two STT routes available on SessionDetail.
 *
 * `BUDDY`: vol-DOWN hold. Transcript goes to /api/companion/inject, which
 *           runs through the Buddy intermediate LLM. Reply auto-narrates.
 * `DIRECT_CC`: vol-UP hold. Transcript goes to /api/companion/direct-stt,
 *               which types the words into the OS-foreground window
 *               (presumed: the user's active CC session) via SendKeys
 *               + Enter. CC's reply auto-narrates via the existing hook
 *               pipeline. No Buddy LLM in the loop.
 */
enum class SttMode { BUDDY, DIRECT_CC }

class CompanionViewModel(
    private val sttRecorder: STTRecorder,
    private val inject: suspend (sessionId: String, text: String) -> Unit,
    private val startBuddy: suspend (sessionId: String) -> String,
    /** 2026-05-12 — direct-STT delivery hook. Implementation POSTs to
     *  daemon `/api/companion/direct-stt` with the active session_id and
     *  the transcribed text. Daemon handles the SendKeys injection. */
    private val directStt: suspend (sessionId: String, text: String) -> Unit =
        { _, _ -> /* no-op default — tests can omit */ },
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    /** Dispatcher used for STT operations — Android's SpeechRecognizer
     *  must be called from the main thread. Tests inject Default. */
    private val sttDispatcher: kotlin.coroutines.CoroutineContext = Dispatchers.Main,
    /** 2026-05-21 — hard cap on dictation length. When the recording
     *  reaches this duration the recorder is stopped and we transition
     *  to REVIEW regardless of whether the recognizer auto-stops on
     *  silence. Default 30s per user request. Test override for short
     *  deterministic runs. */
    private val maxRecordingMs: Long = 30_000L,
) {
    val activeSessionId = MutableStateFlow<String?>(null)
    val buddyId = MutableStateFlow<String?>(null)
    val captionText = MutableStateFlow("")
    val lastFinalText = MutableStateFlow<String?>(null)

    /** Which STT route is active for the *current* recording. Set by the
     *  gesture handler (onLongPress vs onRockerUpLongPress) before
     *  triggering [handleButtonState] with [ButtonState.Listening]. */
    val sttMode = MutableStateFlow(SttMode.BUDDY)

    /** 2026-05-21 — exposes the dictation phase so the UI can render the
     *  review row (Send / Re-record / Cancel) when in REVIEW. */
    val sttPhase = MutableStateFlow(SttUiPhase.IDLE)

    /** 2026-05-21 — wall-clock elapsed time within the current recording.
     *  Updated every ~200ms while phase==RECORDING so the UI can render
     *  a countdown ("12s / 30s"). Reset to 0 on each new recording. */
    val recordingElapsedMs = MutableStateFlow(0L)

    private var sttCollectorJob: Job? = null
    private var maxRecordingJob: Job? = null
    private var elapsedTickerJob: Job? = null
    private var recordingStartedAt: Long = 0L

    fun handleButtonState(state: ButtonState) {
        when (state) {
            is ButtonState.Listening -> startListening()
            is ButtonState.DispatchListening -> dispatch()
            else -> {}
        }
    }

    private fun startListening() {
        // Cancel any prior collector — fresh utterance.
        sttCollectorJob?.cancel()
        captionText.value = ""
        lastFinalText.value = null
        sttCollectorJob = scope.launch {
            sttRecorder.events.collect { event ->
                when (event) {
                    is STTEvent.Partial -> captionText.value = event.text
                    is STTEvent.Final -> {
                        captionText.value = event.text
                        lastFinalText.value = event.text
                        // 2026-05-21 — when the recognizer signals a final
                        // result during a RECORDING phase (silence auto-stop
                        // or natural end-of-utterance), transition into
                        // REVIEW so the user can confirm or re-record. The
                        // old behavior was to stay in RECORDING until the
                        // user pressed stop — but on Android the recognizer
                        // typically auto-stops in 1-3s of silence regardless,
                        // and continuing to "show" RECORDING after that was
                        // misleading.
                        if (sttPhase.value == SttUiPhase.RECORDING) {
                            transitionToReview(reason = "stt_final")
                        }
                    }
                    is STTEvent.Error -> {
                        captionText.value = "[stt error: ${event.message}]"
                        // Errors during RECORDING return us to IDLE — there's
                        // nothing to review. The UI surfaces the error caption
                        // on the row briefly so the user sees what happened.
                        if (sttPhase.value == SttUiPhase.RECORDING) {
                            sttPhase.value = SttUiPhase.IDLE
                            maxRecordingJob?.cancel()
                            elapsedTickerJob?.cancel()
                        }
                    }
                    else -> {}
                }
            }
        }
        // Android's SpeechRecognizer is main-thread-only.
        scope.launch {
            withContext(sttDispatcher) { sttRecorder.start() }
        }
    }

    /**
     * 2026-05-21 — start the 30s cap + elapsed ticker for a new recording.
     * Called from both startHoldToTalk() and discardAndReRecord() so the
     * timers reset cleanly between attempts.
     */
    private fun startRecordingTimers() {
        recordingStartedAt = System.currentTimeMillis()
        recordingElapsedMs.value = 0L
        elapsedTickerJob?.cancel()
        elapsedTickerJob = scope.launch {
            while (sttPhase.value == SttUiPhase.RECORDING) {
                recordingElapsedMs.value = System.currentTimeMillis() - recordingStartedAt
                delay(200)
            }
        }
        maxRecordingJob?.cancel()
        maxRecordingJob = scope.launch {
            delay(maxRecordingMs)
            if (sttPhase.value == SttUiPhase.RECORDING) {
                // Force-stop and review. The user has exactly 30s before
                // they get a transcript to review, regardless of whether
                // the recognizer chose to auto-stop earlier.
                scope.launch {
                    withContext(sttDispatcher) { sttRecorder.stop() }
                }
                // The collector will emit STTEvent.Final shortly and
                // transitionToReview() will pick it up. If not, the
                // 800ms grace below catches the empty-final case.
                delay(800)
                if (sttPhase.value == SttUiPhase.RECORDING) {
                    transitionToReview(reason = "max_duration")
                }
            }
        }
    }

    /**
     * 2026-05-21 — common transition into REVIEW. Sanitizes the caption
     * (rejects STT-error strings as "real" content), then either lands
     * in REVIEW with the cleaned text or bounces back to IDLE if there
     * was nothing intelligible captured.
     */
    private fun transitionToReview(reason: String) {
        maxRecordingJob?.cancel()
        elapsedTickerJob?.cancel()
        val raw = lastFinalText.value ?: captionText.value.takeIf { it.isNotBlank() }
        val isErrorCaption = raw?.startsWith("[") == true
        if (raw.isNullOrBlank() || isErrorCaption) {
            // Nothing usable — surface the failure (or "no speech")
            // on the caption flow and return to IDLE. User can re-tap
            // to try again.
            if (raw.isNullOrBlank()) captionText.value = "[no speech captured]"
            sttPhase.value = SttUiPhase.IDLE
            return
        }
        captionText.value = raw
        sttPhase.value = SttUiPhase.REVIEW
    }

    /**
     * 2026-05-21 — legacy dispatch path kept for the volume-rocker
     * hardware route in MainActivity. Calls confirmSend() directly so
     * the hold-and-release gesture still works without going through
     * the new review step. The button-based dictation flow on session
     * cards now uses the explicit Send / Re-record / Cancel review row
     * instead — see endHoldToTalk() below.
     */
    private fun dispatch() {
        val sid = activeSessionId.value ?: return
        val mode = sttMode.value
        // 2026-05-17 fix — the previous version read `lastFinalText`
        // and `captionText` synchronously immediately after launching
        // an async stop(). SpeechRecognizer's onResults() fires
        // 100-500ms AFTER stopListening(), so `lastFinalText` was
        // almost always null and we ended up sending whatever partial
        // happened to be in captionText (often empty → silent
        // early-return). "Dictate first time worked, then intermittent"
        // was exactly this race: when the user spoke slowly enough that
        // a partial landed before release, it worked; otherwise nothing
        // happened.
        //
        // Now we coroutine-await the final transcript with an 800ms
        // deadline. SpeechRecognizer.stopListening() typically delivers
        // onResults inside ~300ms; 800ms gives slack for slow devices.
        scope.launch {
            withContext(sttDispatcher) { sttRecorder.stop() }
            val deadline = System.currentTimeMillis() + 800L
            while (lastFinalText.value == null
                && System.currentTimeMillis() < deadline) {
                kotlinx.coroutines.delay(40)
            }
            // 2026-05-18 — also reject captionText fallback if it's an
            // STT error string. Without this check, when the recognizer
            // emits ERROR_NO_MATCH (or similar), captionText gets set to
            // "[stt error: no match]" and dispatch then SENDS THAT to the
            // buddy LLM as if it were the user's question. The buddy
            // replies about the error text, and TTS reads it back as
            // "STT error no match" — which the user perceives as the
            // system "breaking" mid-press. With the prefix guard, an
            // STT error caption short-circuits cleanly instead of
            // poisoning the inject/direct-stt round-trip.
            val raw = lastFinalText.value
                ?: captionText.value.takeIf { it.isNotBlank() }
            val isErrorCaption = raw?.startsWith("[") == true
            val text = if (isErrorCaption) null else raw
            if (text.isNullOrBlank()) {
                // Surface the failure on the caption flow so the UI can
                // show "no speech captured" instead of silent dead-air.
                // Preserve the original STT error text if present so the
                // user sees WHY the recognizer didn't capture anything.
                captionText.value = raw?.takeIf { isErrorCaption }
                    ?: "[no speech captured]"
                return@launch
            }
            try {
                when (mode) {
                    SttMode.BUDDY -> {
                        val bid = buddyId.value
                            ?: startBuddy(sid).also { buddyId.value = it }
                        inject(bid, text)
                    }
                    SttMode.DIRECT_CC -> {
                        directStt(sid, text)
                    }
                }
            } catch (e: Throwable) {
                captionText.value = "[${mode.name.lowercase()} error: ${e.message}]"
            }
        }
    }

    /**
     * 2026-05-17 — per-session-card hold-to-talk entry point. Replaces the
     * volume-rocker long-press hardware path (which was unbound so system
     * volume control works normally).
     *
     * Call this from the card button's press-down (ACTION_DOWN); pair with
     * [endHoldToTalk] on release (ACTION_UP). Each press pins the STT
     * round-trip to the given [sessionId] and [mode], independent of which
     * session was last active globally — so the user can dictate to BF
     * Skills from a card while CodeTalker is the audio-focused session.
     *
     * Internally re-uses [handleButtonState] so the STT recorder + caption
     * stream + dispatch pipeline stay identical to the legacy flow.
     */
    fun startHoldToTalk(sessionId: String, mode: SttMode) {
        // 2026-05-21 — if we were mid-review or mid-recording for any other
        // (session, mode) combo, cancel that first so the new recording
        // starts clean. Common case: user taps Buddy on session A, then
        // Dictate on session B without first tapping Send.
        if (sttPhase.value != SttUiPhase.IDLE) {
            cancelDictation()
        }
        activeSessionId.value = sessionId
        sttMode.value = mode
        sttPhase.value = SttUiPhase.RECORDING
        startRecordingTimers()
        handleButtonState(ButtonState.Listening)
    }

    /**
     * 2026-05-21 — release / second-tap. Stops the recorder and transitions
     * to REVIEW. The actual dispatch happens later when the user presses
     * Send via [confirmSend].
     */
    fun endHoldToTalk() {
        if (sttPhase.value != SttUiPhase.RECORDING) return
        scope.launch {
            withContext(sttDispatcher) { sttRecorder.stop() }
            // SpeechRecognizer.onResults() can arrive 100-500ms after
            // stop(). Wait briefly for it before deciding what to do.
            val deadline = System.currentTimeMillis() + 800L
            while (lastFinalText.value == null
                && System.currentTimeMillis() < deadline) {
                delay(40)
            }
            if (sttPhase.value == SttUiPhase.RECORDING) {
                transitionToReview(reason = "user_stop")
            }
        }
    }

    /**
     * 2026-05-21 — user pressed Send in the review row. This is the
     * actual dispatch to the daemon. Pulled out of the old dispatch()
     * so that:
     *   - REVIEW gives the user a chance to read the transcript before
     *     it goes anywhere — fixing the "STT misheard 'tests' as 'taxes'
     *     and Claude went down a tax-prep rabbit hole" failure mode.
     *   - Re-record stays in the same UX (no need to tap a different
     *     button) instead of starting a new conversation from idle.
     */
    fun confirmSend() {
        if (sttPhase.value != SttUiPhase.REVIEW) return
        val sid = activeSessionId.value ?: return
        val mode = sttMode.value
        val text = captionText.value.trim()
        if (text.isBlank() || text.startsWith("[")) {
            // Defensive: REVIEW should never have entered with an error
            // caption, but if it did, refuse to send and bounce to IDLE.
            sttPhase.value = SttUiPhase.IDLE
            return
        }
        sttPhase.value = SttUiPhase.DISPATCHING
        scope.launch {
            try {
                when (mode) {
                    SttMode.BUDDY -> {
                        val bid = buddyId.value
                            ?: startBuddy(sid).also { buddyId.value = it }
                        inject(bid, text)
                    }
                    SttMode.DIRECT_CC -> {
                        directStt(sid, text)
                    }
                }
                // Success: clear the caption and return to IDLE so the
                // button is ready for the next dictation.
                captionText.value = ""
                lastFinalText.value = null
                sttPhase.value = SttUiPhase.IDLE
            } catch (e: Throwable) {
                // Network/buddy failure — surface the error and put the
                // user back in REVIEW so they can retry Send without
                // losing their transcript.
                captionText.value = "[${mode.name.lowercase()} error: ${e.message}]"
                sttPhase.value = SttUiPhase.REVIEW
            }
        }
    }

    /**
     * 2026-05-21 — user pressed Re-record in the review row. Discards
     * the current transcript and starts a fresh recording with the same
     * (session, mode). Keeps activeSessionId + sttMode pinned so the
     * user doesn't have to re-tap to choose which session to dictate to.
     */
    fun discardAndReRecord() {
        if (sttPhase.value != SttUiPhase.REVIEW) return
        captionText.value = ""
        lastFinalText.value = null
        sttPhase.value = SttUiPhase.RECORDING
        startRecordingTimers()
        handleButtonState(ButtonState.Listening)
    }

    /**
     * 2026-05-21 — user pressed Cancel in the review row OR tapped a
     * different session's button mid-recording. Aborts everything,
     * returns to IDLE with no dispatch.
     */
    fun cancelDictation() {
        maxRecordingJob?.cancel()
        elapsedTickerJob?.cancel()
        sttCollectorJob?.cancel()
        scope.launch {
            withContext(sttDispatcher) { sttRecorder.stop() }
        }
        captionText.value = ""
        lastFinalText.value = null
        sttPhase.value = SttUiPhase.IDLE
    }

    /**
     * v0.1.0 polish — public hook for the SessionDetail Chat tab so typed
     * messages can be sent to the buddy without going through the STT path.
     * Same plumbing as dispatch() but takes explicit text.
     */
    fun injectText(text: String) {
        val sid = activeSessionId.value ?: return
        if (text.isBlank()) return
        scope.launch {
            try {
                val bid = buddyId.value ?: startBuddy(sid).also { buddyId.value = it }
                inject(bid, text)
            } catch (e: Throwable) {
                captionText.value = "[inject error: ${e.message}]"
            }
        }
    }

    fun release() {
        sttCollectorJob?.cancel()
        sttRecorder.release()
        scope.cancel()
    }
}
