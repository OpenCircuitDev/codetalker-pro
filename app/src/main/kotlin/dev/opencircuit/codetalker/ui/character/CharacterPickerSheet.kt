package dev.opencircuit.codetalker.ui.character

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.opencircuit.codetalker.net.CharacterLite
import dev.opencircuit.codetalker.net.DaemonClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CCT-32 Task A.5 — character library bottom sheet.
 *
 * Loads the daemon's character roster and lets the user pick one to
 * attach to the current session. Attaching binds the character's voice
 * (including cloned voices) to the session and surfaces the persona
 * label in the AR HUD.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterPickerSheet(
    daemonClient: DaemonClient,
    onSelect: (CharacterLite) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var characters by remember { mutableStateOf<List<CharacterLite>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            characters = withContext(Dispatchers.IO) { daemonClient.listCharacters() }
        } catch (e: Throwable) {
            loadError = e.message ?: "load failed"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Pick a character", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            loadError?.let {
                Text("Could not load: $it", color = Color(0xFFFB7185), fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
            }
            if (characters.isEmpty() && loadError == null) {
                Text(
                    "No characters yet — create one in the desktop dashboard.",
                    fontSize = 13.sp,
                    color = Color(0xFF8B91A0),
                )
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(characters, key = { it.id }) { c ->
                    CharacterListRow(c) { onSelect(c) }
                }
            }
        }
    }
}

@Composable
private fun CharacterListRow(
    character: CharacterLite,
    onClick: () -> Unit,
) {
    val attached = AttachedCharacter(
        id = character.id,
        displayName = character.displayName,
        persona = character.persona,
        voiceRef = character.voiceRef,
        meshPath = character.meshPath,
    )
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CharacterAvatar(character = attached, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                character.displayName,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE6E8EE),
                fontSize = 14.sp,
            )
            character.persona?.let {
                Text(
                    it,
                    fontSize = 11.sp,
                    color = Color(0xFF8B91A0),
                )
            }
            Text(
                "voice: ${character.voiceRef}",
                fontSize = 10.sp,
                color = Color(0xFF6B7280),
            )
        }
    }
}
