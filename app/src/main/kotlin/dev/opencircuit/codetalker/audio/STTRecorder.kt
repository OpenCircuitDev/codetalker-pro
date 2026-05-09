package dev.opencircuit.codetalker.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * CCT-31 Phase 6 — speech-to-text wrapper.
 *
 * Two-tier abstraction:
 *   - [STTRecorder] interface: testable contract (start/stop/events).
 *   - [AndroidSTTRecorder]: production impl that wraps android.speech.SpeechRecognizer.
 *
 * v1 default: Android's built-in recognizer. Free, fast, on-device on
 * Pixel 7+ and other modern phones; falls back to network STT on older
 * devices. Whisper.cpp is a v2 toggle for offline-everywhere use; the
 * STTRecorder interface is unchanged when we swap engines.
 *
 * Emitted events follow a simple shape so the consumer doesn't have to
 * know about Android's RecognitionListener internals.
 */
interface STTRecorder {
    val events: SharedFlow<STTEvent>
    fun start()
    fun stop()
    fun release()
}

sealed interface STTEvent {
    data object ReadyForSpeech : STTEvent
    data class Partial(val text: String) : STTEvent
    data class Final(val text: String) : STTEvent
    data class Error(val code: Int, val message: String) : STTEvent
    data object EndOfSpeech : STTEvent
}

class AndroidSTTRecorder(
    context: Context,
    languageTag: String = "en-US",
) : STTRecorder, RecognitionListener {

    private val _events = MutableSharedFlow<STTEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<STTEvent> = _events

    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context))
            SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(this) }
        else null

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }

    override fun start() {
        recognizer?.startListening(intent)
            ?: run { _events.tryEmit(STTEvent.Error(-1, "SpeechRecognizer unavailable")) }
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun release() {
        recognizer?.destroy()
    }

    // RecognitionListener glue
    override fun onReadyForSpeech(params: Bundle?) { _events.tryEmit(STTEvent.ReadyForSpeech) }
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { _events.tryEmit(STTEvent.EndOfSpeech) }
    override fun onError(error: Int) {
        val msg = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "audio error"
            SpeechRecognizer.ERROR_CLIENT -> "client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "missing RECORD_AUDIO permission"
            SpeechRecognizer.ERROR_NETWORK -> "network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "no match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech timeout"
            else -> "error $error"
        }
        _events.tryEmit(STTEvent.Error(error, msg))
    }
    override fun onResults(results: Bundle?) {
        val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        if (list.isNotEmpty()) _events.tryEmit(STTEvent.Final(list[0]))
    }
    override fun onPartialResults(partialResults: Bundle?) {
        val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        if (list.isNotEmpty()) _events.tryEmit(STTEvent.Partial(list[0]))
    }
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
