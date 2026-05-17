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
 * Click vs long-press detection lives here (not in ButtonRouter) because
 * hardware events come in as ACTION_DOWN/UP pairs; we need to debounce +
 * window-detect at this layer.
 *
 * 2026-05-17 binding move: STT hold-to-talk migrated OFF the volume rocker
 * and ONTO per-session-card buttons (see SessionListScreen). Rationale:
 * binding hold-to-talk to vol-UP / vol-DOWN consumed the rocker entirely,
 * leaving the user no way to adjust system audio volume while the app is
 * foregrounded. With STT now invoked from on-card buttons, the volume
 * rocker is released back to system handling so notification + media +
 * call volume work normally.
 *
 * Side button still emits [ButtonInput.Click] (toggle mute on
 * SessionDetail; trigger voice-flow on the legacy in-glasses path).
 */
class HardwareKeys(
    private val onInput: (ButtonInput) -> Unit,
    // Lowered from 700ms → 300ms during CCT-32 v0.1.0 polish so press-and-hold
    // semantics feel snappy. Still relevant for the side-button if it ever
    // grows a long-press binding; volume rocker no longer uses this.
    var longPressMs: Long = 300,
) {
    private val sideButton = LongPressDetector(
        onShortClick = { onInput(ButtonInput.Click) },
        onLongPress = { /* intentionally unbound — system keeps long-press default */ },
        onHoldEnd = { /* paired with no-op long-press */ },
        longPressMsProvider = { longPressMs },
    )

    /** Returns true if the event was consumed; the Activity should pass that
     *  return value back from dispatchKeyEvent to suppress system handling.
     *
     *  Volume rocker (VOLUME_UP / VOLUME_DOWN) intentionally NOT handled here:
     *  we want system volume control to work normally now that STT lives on
     *  per-card buttons. Returning false for those keycodes (the `else` branch)
     *  lets Android handle them. */
    fun handle(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_CAMERA -> {
                sideButton.handle(event)
                true
            }
            else -> false
        }
    }
}

/**
 * Reusable click/long-press/hold-end detector for a single key.
 * Schedules a long-press emission at [longPressMsProvider] after ACTION_DOWN;
 * on ACTION_UP it emits either [onLongPress]+[onHoldEnd] (held past threshold)
 * or [onShortClick] (released before threshold).
 */
private class LongPressDetector(
    private val onShortClick: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onHoldEnd: () -> Unit,
    private val longPressMsProvider: () -> Long,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var downAtMs: Long = -1
    private var longPressFired: Boolean = false
    private val longPressRunnable = Runnable {
        if (downAtMs >= 0) {
            longPressFired = true
            onLongPress()
        }
    }

    fun handle(event: KeyEvent) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    downAtMs = event.eventTime
                    longPressFired = false
                    handler.postDelayed(longPressRunnable, longPressMsProvider())
                }
                // Repeats are intentionally ignored — we only care about the
                // initial press for click/long-press semantics; the long-press
                // timer fires regardless of whether repeats arrive.
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (longPressFired) {
                    onHoldEnd()
                } else if (downAtMs >= 0) {
                    onShortClick()
                }
                downAtMs = -1
                longPressFired = false
            }
        }
    }
}
