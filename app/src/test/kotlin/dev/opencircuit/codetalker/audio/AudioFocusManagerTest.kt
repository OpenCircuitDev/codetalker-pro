package dev.opencircuit.codetalker.audio

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCT-32 Task A.7 — AudioFocusManager state-machine tests.
 *
 * The class is testable without an AudioManager because the OS callback
 * surface is small (one int). We pass a fake "request focus" lambda and
 * drive the listener directly.
 */
class AudioFocusManagerTest {

    @Test
    fun `requestFocus invokes the request lambda exactly once`() {
        var requestCalls = 0
        val mgr = AudioFocusManager(
            requestFocus = { requestCalls++; AudioManager.AUDIOFOCUS_REQUEST_GRANTED },
            abandonFocus = { AudioManager.AUDIOFOCUS_REQUEST_GRANTED },
        )
        val granted = mgr.requestFocus(onPause = {}, onResume = {}, onStop = {})
        assertEquals(1, requestCalls)
        assertTrue(granted)
        assertTrue(mgr.hasFocus)
    }

    @Test
    fun `requestFocus returns false on denied`() {
        val mgr = AudioFocusManager(
            requestFocus = { AudioManager.AUDIOFOCUS_REQUEST_FAILED },
            abandonFocus = { AudioManager.AUDIOFOCUS_REQUEST_GRANTED },
        )
        val granted = mgr.requestFocus(onPause = {}, onResume = {}, onStop = {})
        assertFalse(granted)
        assertFalse(mgr.hasFocus)
    }

    @Test
    fun `LOSS_TRANSIENT triggers onPause`() {
        var pauseCalls = 0
        var resumeCalls = 0
        val mgr = AudioFocusManager(
            requestFocus = { AudioManager.AUDIOFOCUS_REQUEST_GRANTED },
            abandonFocus = { AudioManager.AUDIOFOCUS_REQUEST_GRANTED },
        )
        mgr.requestFocus(
            onPause = { pauseCalls++ },
            onResume = { resumeCalls++ },
            onStop = {},
        )
        mgr.dispatch(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(1, pauseCalls)
        assertEquals(0, resumeCalls)
        assertTrue("focus retained on transient loss", mgr.hasFocus)
    }

    @Test
    fun `GAIN after LOSS_TRANSIENT triggers onResume`() {
        var pauseCalls = 0
        var resumeCalls = 0
        val mgr = AudioFocusManager(
            requestFocus = { AudioManager.AUDIOFOCUS_REQUEST_GRANTED },
            abandonFocus = { AudioManager.AUDIOFOCUS_REQUEST_GRANTED },
        )
        mgr.requestFocus(
            onPause = { pauseCalls++ },
            onResume = { resumeCalls++ },
            onStop = {},
        )
        mgr.dispatch(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        mgr.dispatch(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(1, pauseCalls)
        assertEquals(1, resumeCalls)
    }

    @Test
    fun `permanent LOSS triggers onStop and clears focus`() {
        var stopCalls = 0
        var abandons = 0
        val mgr = AudioFocusManager(
            requestFocus = { AudioManager.AUDIOFOCUS_REQUEST_GRANTED },
            abandonFocus = { abandons++; AudioManager.AUDIOFOCUS_REQUEST_GRANTED },
        )
        mgr.requestFocus(
            onPause = {},
            onResume = {},
            onStop = { stopCalls++ },
        )
        mgr.dispatch(AudioManager.AUDIOFOCUS_LOSS)
        assertEquals(1, stopCalls)
        assertEquals(1, abandons)
        assertFalse(mgr.hasFocus)
    }

    @Test
    fun `LOSS_TRANSIENT_CAN_DUCK pauses without abandoning`() {
        var pauseCalls = 0
        val mgr = AudioFocusManager(
            requestFocus = { AudioManager.AUDIOFOCUS_REQUEST_GRANTED },
            abandonFocus = { AudioManager.AUDIOFOCUS_REQUEST_GRANTED },
        )
        mgr.requestFocus(onPause = { pauseCalls++ }, onResume = {}, onStop = {})
        mgr.dispatch(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertEquals(1, pauseCalls)
        assertTrue("focus retained on duck", mgr.hasFocus)
    }

    @Test
    fun `releaseFocus invokes abandon lambda`() {
        var abandons = 0
        val mgr = AudioFocusManager(
            requestFocus = { AudioManager.AUDIOFOCUS_REQUEST_GRANTED },
            abandonFocus = { abandons++; AudioManager.AUDIOFOCUS_REQUEST_GRANTED },
        )
        mgr.requestFocus(onPause = {}, onResume = {}, onStop = {})
        mgr.releaseFocus()
        assertEquals(1, abandons)
        assertFalse(mgr.hasFocus)
    }

    @Test
    fun `dispatch with no focus is a no-op`() {
        val mgr = AudioFocusManager(
            requestFocus = { AudioManager.AUDIOFOCUS_REQUEST_GRANTED },
            abandonFocus = { AudioManager.AUDIOFOCUS_REQUEST_GRANTED },
        )
        // Never requested focus.
        mgr.dispatch(AudioManager.AUDIOFOCUS_LOSS) // should not crash
        assertFalse(mgr.hasFocus)
    }
}
