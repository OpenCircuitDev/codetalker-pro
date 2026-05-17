package dev.opencircuit.codetalker.ui.pickers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CCT-32 Task A.3 — Muted toggle.
 *
 * Daemon stores `cfg.enabled: bool` (true = speaking). UI surfaces it
 * inverted as "Muted" so the switch matches user expectations: ON =
 * silent, OFF = speaking.
 */
@Composable
fun MutedToggle(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
) {
    // v0.1.0 unification — optimistic local state. The parent's `enabled`
    // prop comes from a stale SessionState snapshot that's only refreshed
    // by the daemon getSession round-trip (which 404s for dormant
    // sessions). Without local optimistic update, the switch UI would
    // freeze even though the daemon write succeeded. `remember(enabled)`
    // re-seeds when the parent's enabled actually changes (a future poll
    // refresh or another surface modifies it).
    var localEnabled by remember(enabled) { mutableStateOf(enabled) }
    val isMuted = !localEnabled
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    if (isMuted) "Muted" else "Speaking",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (isMuted) "TTS suppressed; transcripts still stream."
                    else "TTS plays as the daemon emits.",
                    fontSize = 11.sp,
                    color = Color(0xFF8B91A0),
                )
            }
            Switch(
                checked = isMuted,
                onCheckedChange = { mute ->
                    val newEnabled = !mute
                    localEnabled = newEnabled    // optimistic UI flip
                    onChange(newEnabled)         // fire-and-forget daemon write
                },
            )
        }
    }
}
