package dev.opencircuit.codetalker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencircuit.codetalker.net.DaemonClient
import dev.opencircuit.codetalker.net.SessionLite
import dev.opencircuit.codetalker.ui.character.CharacterChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CCT-31 Phase 5c + character integration — paired-state landing screen.
 *
 * Lists daemon sessions with each session's attached character (if any).
 * Voice cloning is implicit in this UI: if the desktop has bound a cloned
 * voice to a character, that character's name + persona surface here, and
 * when the user picks the session as Active, the audio stream the AR
 * companion plays IS that cloned voice.
 *
 * Phase 8 swaps this Compose screen for the AR HUD; this surface stays
 * as the before-glasses config layer.
 */
@Composable
fun SessionListScreen(
    daemonClient: DaemonClient,
    onUnpair: () -> Unit,
) {
    var sessions by remember { mutableStateOf<List<SessionLite>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var activeId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val list = withContext(Dispatchers.IO) { daemonClient.listSessions() }
            sessions = list
        } catch (e: Throwable) {
            loadError = e.message ?: "load failed"
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sessions", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onUnpair) { Text("Unpair") }
        }
        Spacer(Modifier.height(8.dp))

        loadError?.let {
            Text("Could not load: $it", fontSize = 13.sp)
            return@Column
        }
        if (sessions.isEmpty()) {
            Text(
                "No sessions yet — start a Claude Code instance and pair it with codetalker.",
                fontSize = 13.sp,
            )
            return@Column
        }

        LazyColumn {
            items(sessions, key = { it.sessionId }) { session ->
                SessionRow(
                    session = session,
                    isActive = activeId == session.sessionId,
                    onSetActive = {
                        activeId = session.sessionId
                        // Phase 7+ wires the active selection to:
                        //   - daemonClient.setActiveSession(sessionId) so the
                        //     daemon routes the right audio stream to us
                        //   - daemonClient.startBuddy(sessionId) so the buddy
                        //     Claude has access to this session's transcript
                        // Both happen in CompanionViewModel (Phase 8).
                    },
                )
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionLite,
    isActive: Boolean,
    onSetActive: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top line: live indicator + display name
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (session.isLive) {
                    LiveDot()
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    session.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
            Text(
                session.sessionId.take(12),
                fontSize = 11.sp,
            )

            // Character chip — voice cloning surfaces here.
            session.attachedCharacter?.let { char ->
                Spacer(Modifier.height(8.dp))
                CharacterChip(character = char)
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = onSetActive) {
                Text(if (isActive) "Active" else "Set active")
            }
        }
    }
}

@Composable
private fun LiveDot() {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(Color(0xFF34D399)),  // Phase 27 accent_live
    )
}
