package dev.opencircuit.codetalker.input

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/**
 * CCT-31 Phase 6-polish — translate Android KeyEvents into [ButtonInput].
 *
 * The Beam Pro side button reports as `KeyEvent.KEYCODE_POWER` or one of
 * the headset-button codes depending on firmware. The volume rocker
 * reports as `KEYCODE_VOLUME_UP` / `KEYCODE_VOLUME_DOWN`. We register
 * `dispatchKeyEvent` in MainActivity, route raw events here, and emit
 * higher-level [ButtonInput] values consumed by [ButtonRouter].
 *
 * Click vs double-click vs long-press is detected here (not in
 * ButtonRouter) because hardware events come in as ACTION_DOWN/UP pairs;
 * we need to debounce + window-detect at this layer.
 *
 * The KEY_MAP can be tuned per device once we know which codes Beam Pro
 * actually emits — until we have hardware in hand, all three "side
 * button" candidates feed the click/long-press pipeline.
 */
class HardwareKeys(
    private val onInput: (ButtonInput) -> Unit,
    var longPressMs: Long = 700,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var downAtMs: Long = -1
    private var longPressFired: Boolean = false
    private val longPressRunnable = Runnable {
        if (downAtMs >= 0) {
            longPressFired = true
            onInput(ButtonInput.LongPress)
        }
    }

    /** Returns true if the event was consumed; the Activity should pass that
     *  return value back from dispatchKeyEvent to suppress system handling. */
    fun handle(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN) onInput(ButtonInput.RockerUp)
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) onInput(ButtonInput.RockerDown)
                true
            }
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_CAMERA -> {
                handleSideButton(event)
                true
            }
            else -> false
        }
    }

    private fun handleSideButton(event: KeyEvent) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    downAtMs = event.eventTime
                    longPressFired = false
                    handler.postDelayed(longPressRunnable, longPressMs)
                }
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (!longPressFired && downAtMs >= 0) {
                    onInput(ButtonInput.Click)
                }
                downAtMs = -1
                longPressFired = false
            }
        }
    }
}
