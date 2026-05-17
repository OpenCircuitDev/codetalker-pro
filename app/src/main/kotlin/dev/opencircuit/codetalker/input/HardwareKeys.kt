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
 * 2026-05-12 binding move: STT hold-to-talk migrated from the side button
 * to volume-DOWN long-press. Rationale: the side button has system
 * defaults (e.g., power-menu on POWER-mapped firmware) that are useful to
 * preserve; binding STT there created collisions. Volume-down has no
 * critical system default beyond Assistant launch (which can be disabled
 * in Settings and which our `dispatchKeyEvent` suppresses for foreground
 * input anyway). Side button now only emits [ButtonInput.Click] (toggle
 * mute on SessionDetail); it no longer emits LongPress/HoldEnd.
 */
class HardwareKeys(
    private val onInput: (ButtonInput) -> Unit,
    // Lowered from 700ms → 300ms during CCT-32 v0.1.0 polish so press-and-hold
    // semantics feel snappy for the STT "hold to talk" gesture.
    var longPressMs: Long = 300,
) {
    private val volumeDown = LongPressDetector(
        onShortClick = { onInput(ButtonInput.RockerDown) },
        onLongPress = { onInput(ButtonInput.LongPress) },
        onHoldEnd = { onInput(ButtonInput.HoldEnd) },
        longPressMsProvider = { longPressMs },
    )
    // 2026-05-12 — vol-UP gains long-press detection for direct-STT
    // (vol-down handles Buddy STT; vol-up routes transcript via the
    // daemon's /api/companion/direct-stt → SendKeys into the user's CC
    // window). Short-tap still emits RockerUp (Mode → live).
    private val volumeUp = LongPressDetector(
        onShortClick = { onInput(ButtonInput.RockerUp) },
        onLongPress = { onInput(ButtonInput.LongPressUp) },
        onHoldEnd = { onInput(ButtonInput.HoldEndUp) },
        longPressMsProvider = { longPressMs },
    )
    private val sideButton = LongPressDetector(
        onShortClick = { onInput(ButtonInput.Click) },
        onLongPress = { /* intentionally unbound — system keeps long-press default */ },
        onHoldEnd = { /* paired with no-op long-press */ },
        longPressMsProvider = { longPressMs },
    )

    /** Returns true if the event was consumed; the Activity should pass that
     *  return value back from dispatchKeyEvent to suppress system handling. */
    fun handle(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                volumeUp.handle(event)
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                volumeDown.handle(event)
                true
            }
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
