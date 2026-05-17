package dev.opencircuit.codetalker.ui.pickers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CCT-32 Task A.3 — segmented chip row reused by ModePicker / CadencePicker.
 *
 * Pure Compose; tests live as part of the consuming pickers' unit
 * tests. Style mirrors the dashboard's segmented chip palette so the AR
 * companion looks coherent on Beam Pro.
 */
@Composable
fun SegmentedRow(
    options: List<Pair<String, String>>,    // value -> label
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1D26))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val isSel = selected == value
            // 2026-05-11 — Modifier.selectable replaces .clickable so the
            // chip publishes Role.RadioButton + selected state to the
            // accessibility tree. uiautomator dumps now expose the active
            // chip via the `selected="true"` attribute (Compose's plain
            // .clickable left every chip selected=false regardless of
            // visual highlight, which masked audit verification).
            Text(
                label,
                color = if (isSel) Color.White else Color(0xFFBDC2D0),
                fontSize = 13.sp,
                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSel) Color(0xFF2D3142) else Color.Transparent)
                    .selectable(
                        selected = isSel,
                        onClick = { onSelect(value) },
                        role = Role.RadioButton,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
