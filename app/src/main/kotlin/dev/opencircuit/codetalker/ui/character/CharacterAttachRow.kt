package dev.opencircuit.codetalker.ui.character

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.opencircuit.codetalker.net.AttachedCharacter
import dev.opencircuit.codetalker.net.DaemonClient

/**
 * CCT-32 Task A.5 — inline row in SessionDetailScreen for attaching /
 * detaching the session's character.
 *
 * When attached, shows the chip + Detach button. When unattached, shows
 * a "Pick character" button that opens [CharacterPickerSheet].
 */
@Composable
fun CharacterAttachRow(
    attachedCharacterId: String?,
    attachedDisplay: AttachedCharacter?,
    daemonClient: DaemonClient,
    modifier: Modifier = Modifier,
    onAttach: (characterId: String) -> Unit,
    onDetach: () -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (attachedDisplay != null) {
                CharacterChip(character = attachedDisplay)
            } else if (attachedCharacterId != null) {
                Column {
                    Text("Attached: $attachedCharacterId", fontWeight = FontWeight.SemiBold)
                    Text("Voice via daemon roster.", fontSize = 11.sp, color = Color(0xFF8B91A0))
                }
            } else {
                Column {
                    Text("No character", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Default voice is used.",
                        fontSize = 11.sp,
                        color = Color(0xFF8B91A0),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (attachedCharacterId != null) {
                    TextButton(onClick = onDetach) { Text("Detach") }
                    Spacer(Modifier.width(4.dp))
                }
                OutlinedButton(onClick = { sheetOpen = true }) {
                    Text(if (attachedCharacterId != null) "Change" else "Pick")
                }
            }
        }
    }

    if (sheetOpen) {
        CharacterPickerSheet(
            daemonClient = daemonClient,
            onSelect = { c ->
                sheetOpen = false
                onAttach(c.id)
            },
            onDismiss = { sheetOpen = false },
        )
    }
}
