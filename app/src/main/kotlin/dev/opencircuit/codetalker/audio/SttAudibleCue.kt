package dev.opencircuit.codetalker.audio

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * CCT-32 v0.1.0 polish — short audible cue for STT start/stop.
 *
 * The user needs to hear "I'm recording now" / "I stopped" without staring
 * at the phone screen (the AR HUD case has no visible UI yet — Phase 8
 * deferred). We use Android's built-in [ToneGenerator] so we don't need to
 * ship a WAV asset; the tones route to STREAM_MUSIC, same path as buddy
 * audio, so they come out the glasses speakers under the polished routing.
 */
object SttAudibleCue {
    private val generator: ToneGenerator by lazy {
        ToneGenerator(AudioManager.STREAM_MUSIC, /* volume = */ 75)
    }

    /** Two-tone rising chirp — "listening." */
    fun playStart() {
        try {
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (_: Throwable) {
            // ToneGenerator can fail on some OEM stacks; silent fallback is fine.
        }
    }

    /** Falling tone — "stopped, sending." */
    fun playStop() {
        try {
            generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 120)
        } catch (_: Throwable) {
        }
    }
}
