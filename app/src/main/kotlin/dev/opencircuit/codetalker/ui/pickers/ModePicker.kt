package dev.opencircuit.codetalker.ui.pickers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * CCT-32 Task A.3 — speaking-mode segmented picker.
 *
 * Modes mirror the daemon's `cfg.active_mode` keys:
 *   - brief: short hooks only (lowest verbosity)
 *   - direct: tool-output as-spoken (default)
 *   - live: continuous narration with cadence control
 *   - trigger: only fires on tagged narration triggers
 *
 * Selecting a mode PUTs `{"active_mode":"<mode>"}` to the daemon.
 */
@Composable
fun ModePicker(
    current: String?,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    SegmentedRow(
        options = MODE_OPTIONS,
        selected = current,
        onSelect = onChange,
        modifier = modifier,
    )
}

internal val MODE_OPTIONS = listOf(
    "brief" to "Brief",
    "direct" to "Direct",
    "live" to "Live",
    "trigger" to "Trigger",
)
