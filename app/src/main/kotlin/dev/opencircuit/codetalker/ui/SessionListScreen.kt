package dev.opencircuit.codetalker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import dev.opencircuit.codetalker.ui.errors.AppError
import dev.opencircuit.codetalker.ui.errors.AppErrors
import dev.opencircuit.codetalker.ui.errors.ErrorBanner
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionListScreen(
    daemonClient: DaemonClient,
    activeSessionId: String?,
    onSelect: (SessionLite) -> Unit,
    onUnpair: () -> Unit,
    onLongPressMenu: () -> Unit = {},
) {
    var sessions by remember { mutableStateOf<List<SessionLite>>(emptyList()) }
    // CCT-32 Task B.3: classify the load failure into an AppError so the
    // user gets a recoverable banner with a Retry / Re-pair action instead
    // of a raw exception message.
    var loadError by remember { mutableStateOf<AppError?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        try {
            val list = withContext(Dispatchers.IO) { daemonClient.listSessions() }
            sessions = list
            loadError = null
        } catch (e: Throwable) {
            loadError = AppErrors.fromThrowable(e)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // CCT-32 Task B.6: long-press the title to surface the menu
            // (Diagnostics entry).
            Text(
                "Sessions",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = onLongPressMenu,
                ),
            )
            OutlinedButton(onClick = onUnpair) { Text("Unpair") }
        }
        Spacer(Modifier.height(8.dp))

        loadError?.let { err ->
            ErrorBanner(
                error = err,
                onAction = { e ->
                    when (e.recovery) {
                        AppError.Recovery.Retry -> { loadError = null; reloadKey++ }
                        AppError.Recovery.RePair -> onUnpair()
                        AppError.Recovery.OpenSettings -> { /* not reachable from list */ }
                        AppError.Recovery.None -> { /* nothing actionable */ }
                    }
                },
                onDismiss = { loadError = null },
            )
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
                    isActive = activeSessionId == session.sessionId,
                    onClick = { onSelect(session) },
                )
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionLite,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top line: live indicator + display name + active flag
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
                if (isActive) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "ACTIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF34D399),
                    )
                }
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
