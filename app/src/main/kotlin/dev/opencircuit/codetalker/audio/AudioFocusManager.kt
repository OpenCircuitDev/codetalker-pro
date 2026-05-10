package dev.opencircuit.codetalker.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * CCT-32 Task A.7 — wraps Android's AudioManager focus APIs.
 *
 * Two-tier injection:
 *   - In production, callers use [forContext] which builds the request
 *     against AudioManager.requestAudioFocus.
 *   - In tests, callers pass `requestFocus` / `abandonFocus` lambdas so
 *     focus state can be driven without a real AudioManager (and without
 *     the OS deciding what counts as a granted request mid-test).
 *
 * The class owns a single set of pause/resume/stop callbacks; calling
 * [requestFocus] replaces them. This matches our usage: TTSPlayer is the
 * only owner per session.
 */
class AudioFocusManager(
    private val requestFocus: () -> Int,
    private val abandonFocus: () -> Int,
) {
    var hasFocus: Boolean = false
        private set

    private var onPause: () -> Unit = {}
    private var onResume: () -> Unit = {}
    private var onStop: () -> Unit = {}

    /** Request focus. Returns true if granted. */
    fun requestFocus(
        onPause: () -> Unit,
        onResume: () -> Unit,
        onStop: () -> Unit,
    ): Boolean {
        this.onPause = onPause
        this.onResume = onResume
        this.onStop = onStop
        val result = requestFocus()
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    /** Abandon focus and clear callbacks. */
    fun releaseFocus() {
        if (!hasFocus) return
        abandonFocus()
        hasFocus = false
    }

    /**
     * Apply a focus event from AudioManager.OnAudioFocusChangeListener.
     * Idempotent when [hasFocus] is false.
     */
    fun dispatch(focusChange: Int) {
        if (!hasFocus) return
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> onResume()
            AudioManager.AUDIOFOCUS_LOSS -> {
                onStop()
                abandonFocus()
                hasFocus = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                onPause()
                // keep focus; we'll resume on GAIN
            }
        }
    }

    companion object {
        /** Production constructor: uses Android's AudioManager. */
        fun forContext(context: Context): AudioFocusManager {
            val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            // Built lazily so requestFocus closure has the same listener
            // every time. Listener forwards to the manager's dispatch().
            lateinit var managerRef: AudioFocusManager
            val listener = AudioManager.OnAudioFocusChangeListener { focusChange ->
                managerRef.dispatch(focusChange)
            }
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(listener)
                .setAcceptsDelayedFocusGain(false)
                .build()
            managerRef = AudioFocusManager(
                requestFocus = { am.requestAudioFocus(request) },
                abandonFocus = { am.abandonAudioFocusRequest(request) },
            )
            return managerRef
        }
    }
}
