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
class CompanionViewModel(
    private val sttRecorder: STTRecorder,
    private val inject: suspend (sessionId: String, text: String) -> Unit,
    private val startBuddy: suspend (sessionId: String) -> String,
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
        scope.launch {
            try {
                val bid = buddyId.value
                    ?: startBuddy(sid).also { buddyId.value = it }
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
