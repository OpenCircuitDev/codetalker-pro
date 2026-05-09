package dev.opencircuit.codetalker.input

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * CCT-31 Phase 6 — controller state machine.
 *
 * Translates raw button events from the Beam Pro side button into
 * higher-level intents the rest of the app cares about:
 *
 *   ┌─────────────┐      click      ┌────────────────┐
 *   │   IDLE      │────────────────▶│   LISTENING    │
 *   │             │                 │  (mic on)      │
 *   │             │◀────────────────│                │
 *   │             │  click / silent  │                │
 *   │             │                 └────────────────┘
 *   │             │
 *   │             │   double-click  ┌────────────────┐
 *   │             │────────────────▶│     MENU       │
 *   │             │                 │ (rocker scroll)│
 *   │             │                 │                │
 *   │             │◀────────────────│                │
 *   │             │  click on item   │                │
 *   │             │                 └────────────────┘
 *   └─────────────┘
 *
 *   Long-press (any state) → IDLE (universal escape)
 *
 * State and event types are sealed so the consumer must handle every case.
 * Pure Kotlin, no Android dependencies — can be unit-tested without an
 * emulator. The Activity will own a [HardwareKeys] adapter (Phase 6c) that
 * captures KeyEvents from Beam Pro hardware buttons and feeds them as
 * [ButtonInput] into this router.
 */
class ButtonRouter {

    private val _state = MutableStateFlow<ButtonState>(ButtonState.Idle)
    val state: StateFlow<ButtonState> = _state

    /** Configurable thresholds (ms). Defaults match the spec. */
    var doubleClickWindowMs: Long = 350
    var longPressMs: Long = 700
    var sttSilenceTimeoutMs: Long = 1500

    private var lastClickAtMs: Long = -1L

    /**
     * Feed a raw button input. Returns the new state for convenience.
     *
     * @param now caller-supplied wall clock; lets tests inject fake time
     *            instead of relying on System.currentTimeMillis().
     */
    fun handle(input: ButtonInput, now: Long = System.currentTimeMillis()): ButtonState {
        val current = _state.value
        val next = when (input) {
            is ButtonInput.LongPress -> ButtonState.Idle
            is ButtonInput.Click -> handleClick(current, now)
            is ButtonInput.RockerUp -> handleRocker(current, delta = -1)
            is ButtonInput.RockerDown -> handleRocker(current, delta = +1)
            is ButtonInput.Silence -> handleSilence(current)
        }
        _state.value = next
        return next
    }

    private fun handleClick(current: ButtonState, now: Long): ButtonState {
        // A click within the double-click window upgrades the previous click.
        val isDouble = lastClickAtMs >= 0 && (now - lastClickAtMs) <= doubleClickWindowMs
        return when {
            isDouble -> {
                lastClickAtMs = -1
                ButtonState.Menu(selectedIndex = (current as? ButtonState.Menu)?.selectedIndex ?: 0)
            }
            current is ButtonState.Listening -> {
                lastClickAtMs = -1
                // Click while listening = stop + dispatch the recording.
                ButtonState.DispatchListening
            }
            current is ButtonState.Menu -> {
                lastClickAtMs = -1
                // Click while menu = confirm selection.
                ButtonState.MenuSelect(current.selectedIndex)
            }
            current is ButtonState.Idle -> {
                // First click in idle → wait briefly for a possible double.
                lastClickAtMs = now
                ButtonState.Listening
            }
            else -> current
        }
    }

    private fun handleRocker(current: ButtonState, delta: Int): ButtonState {
        return when (current) {
            is ButtonState.Menu -> current.copy(
                selectedIndex = (current.selectedIndex + delta).coerceAtLeast(0)
            )
            else -> current  // ignored outside menu mode
        }
    }

    private fun handleSilence(current: ButtonState): ButtonState {
        return when (current) {
            is ButtonState.Listening -> ButtonState.DispatchListening
            else -> current
        }
    }
}

sealed interface ButtonState {
    data object Idle : ButtonState
    data object Listening : ButtonState
    data object DispatchListening : ButtonState
    data class Menu(val selectedIndex: Int) : ButtonState
    data class MenuSelect(val index: Int) : ButtonState
}

sealed interface ButtonInput {
    data object Click : ButtonInput
    data object LongPress : ButtonInput
    data object RockerUp : ButtonInput
    data object RockerDown : ButtonInput
    data object Silence : ButtonInput
}
