package dev.opencircuit.codetalker.ui.pickers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * CCT-32 Task A.3 — live-mode cadence segmented picker.
 *
 * Cadence values map to daemon's `cfg.live.cadence`:
 *   - slow: longer pauses, beats between sentences
 *   - normal: default rhythm
 *   - fast: condensed pacing for quick listens
 */
@Composable
fun CadencePicker(
    current: String?,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    SegmentedRow(
        options = CADENCE_OPTIONS,
        selected = current,
        onSelect = onChange,
        modifier = modifier,
    )
}

internal val CADENCE_OPTIONS = listOf(
    "slow" to "Slow",
    "normal" to "Normal",
    "fast" to "Fast",
)
