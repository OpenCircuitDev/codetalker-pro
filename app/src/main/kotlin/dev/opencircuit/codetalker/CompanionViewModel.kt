package dev.opencircuit.codetalker

import dev.opencircuit.codetalker.audio.STTEvent
import dev.opencircuit.codetalker.audio.STTRecorder
import dev.opencircuit.codetalker.input.ButtonState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
) {
    val activeSessionId = MutableStateFlow<String?>(null)
    val buddyId = MutableStateFlow<String?>(null)
    val captionText = MutableStateFlow("")
    val lastFinalText = MutableStateFlow<String?>(null)

    /** Which STT route is active for the *current* recording. Set by the
     *  gesture handler (onLongPress vs onRockerUpLongPress) before
     *  triggering [handleButtonState] with [ButtonState.Listening]. */
    val sttMode = MutableStateFlow(SttMode.BUDDY)

    private var sttCollectorJob: Job? = null

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
                    }
                    is STTEvent.Error -> {
                        captionText.value = "[stt error: ${event.message}]"
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

    private fun dispatch() {
        val sid = activeSessionId.value ?: return
        scope.launch {
            withContext(sttDispatcher) { sttRecorder.stop() }
        }
        val text = lastFinalText.value
            ?: captionText.value.takeIf { it.isNotBlank() }
            ?: return
        // 2026-05-12 — route by sttMode set at hold-start. BUDDY hits
        // /api/companion/inject through the Buddy intermediate LLM;
        // DIRECT_CC hits /api/companion/direct-stt which types straight
        // into the OS-foreground CC window via SendKeys.
        val mode = sttMode.value
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
        activeSessionId.value = sessionId
        sttMode.value = mode
        handleButtonState(ButtonState.Listening)
    }

    /** Release-half of [startHoldToTalk]. Stops the recorder and dispatches
     *  the final transcript via inject() (BUDDY) or directStt() (DIRECT_CC),
     *  depending on the mode the press started with. */
    fun endHoldToTalk() {
        handleButtonState(ButtonState.DispatchListening)
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
