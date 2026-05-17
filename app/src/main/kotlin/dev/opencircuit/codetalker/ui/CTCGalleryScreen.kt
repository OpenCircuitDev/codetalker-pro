package dev.opencircuit.codetalker.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencircuit.codetalker.net.AttachedCharacter
import dev.opencircuit.codetalker.net.DaemonClient
import dev.opencircuit.codetalker.net.SessionLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CodeTalkerChat (CTC) gallery — Zoom-style multi-session view.
 *
 * Mirrors the webui's `CodeTalkerChat` tab. Cards for each live session:
 *   - Character avatar (initial-letter fallback)
 *   - Speaking pulse animation when `is_speaking == true`
 *   - Display name + headline
 *   - Mute toggle that stopPropagation-doesn't-also-select
 *
 * Tap a card → spotlight pane below shows that session's details + a
 * mode picker + mute toggle. Setting a session active routes audio to
 * the phone via `client.setActiveSession`.
 *
 * Polls /api/companion/sessions every 5s for liveness + speaking flag.
 * Future enhancement: SSE for sub-second is_speaking updates.
 */
@Composable
fun CTCGalleryScreen(
    daemonClient: DaemonClient,
    activeSessionId: String?,
    onBack: () -> Unit,
    onSelectSession: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var sessions by remember { mutableStateOf<List<SessionLite>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    // Polling refresh — same 5s cadence as SessionListScreen.
    LaunchedEffect(daemonClient) {
        while (true) {
            try {
                val list = withContext(Dispatchers.IO) { daemonClient.listSessions() }
                sessions = list.filter { it.isLive }
                error = null
            } catch (e: Throwable) {
                error = e.message
            } finally {
                loading = false
            }
            delay(5000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B10))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(onClick = onBack) { Text("← Sessions") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "CodeTalkerChat",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFFEDEEF3),
                )
                Text(
                    "${sessions.size} live · tap a card to focus",
                    fontSize = 10.sp,
                    color = Color(0xFF67E8F9),
                )
            }
            Spacer(Modifier.size(56.dp))  // balance the back button
        }
        Spacer(Modifier.height(8.dp))

        if (error != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x33EF4444), RoundedCornerShape(6.dp))
                    .padding(8.dp),
            ) {
                Text(error ?: "", color = Color(0xFFFCA5A5), fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF67E8F9))
            }
            return@Column
        }

        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No live sessions", color = Color(0xFF94A3B8), fontSize = 14.sp)
                    Text(
                        "Start a Claude Code conversation in any project to see it appear here.",
                        color = Color(0xFF6B7280),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
                    )
                }
            }
            return@Column
        }

        val selected = sessions.find { it.sessionId == selectedId }
            ?: sessions.firstOrNull()

        // Card grid — auto-fit 2 columns on phone, 3 on tablet.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sessions, key = { it.sessionId }) { s ->
                CTCCard(
                    session = s,
                    selected = s.sessionId == selected?.sessionId,
                    onSelect = { selectedId = s.sessionId },
                    onToggleMute = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                try { daemonClient.setMuted(s.sessionId, !s.enabled) } catch (_: Throwable) {}
                            }
                        }
                    },
                )
            }
        }

        // Spotlight pane — selected session controls.
        if (selected != null) {
            SpotlightPane(
                session = selected,
                isActive = activeSessionId == selected.sessionId,
                onSetActive = { onSelectSession(selected.sessionId) },
                onToggleMute = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try { daemonClient.setMuted(selected.sessionId, !selected.enabled) } catch (_: Throwable) {}
                        }
                    }
                },
                onModeChange = { mode ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try { daemonClient.setActiveMode(selected.sessionId, mode) } catch (_: Throwable) {}
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun CTCCard(
    session: SessionLite,
    selected: Boolean,
    onSelect: () -> Unit,
    onToggleMute: () -> Unit,
) {
    // Speaking-pulse animation: gentle scale 1.0 → 1.04 in 600ms loop.
    val transition = rememberInfiniteTransition(label = "speak-pulse")
    val pulse by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (session.isSpeaking) 1.04f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-scale",
    )
    val ringColor = when {
        selected -> Color(0xFF22D3EE)
        session.isSpeaking -> Color(0xFF67E8F9)
        else -> Color(0xFF1F2937)
    }

    Box(
        modifier = Modifier
            .scale(if (session.isSpeaking) pulse else 1.0f)
            .background(
                Color(0xFF111827),
                RoundedCornerShape(12.dp),
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = ringColor,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onSelect)
            .padding(12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Speaking ribbon (top-left, but only when NOT selected — the
            // ring already signals focus on the selected one).
            if (session.isSpeaking && !selected) {
                Text(
                    "SPEAKING",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF67E8F9),
                    modifier = Modifier
                        .align(Alignment.Start)
                        .background(Color(0xCC0E7490), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
                Spacer(Modifier.height(2.dp))
            }
            // Avatar — circle with character initial + persona-tinted bg.
            CharacterAvatar(character = session.attachedCharacter)
            Spacer(Modifier.height(8.dp))
            Text(
                session.attachedCharacter?.displayName ?: "(no character)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE2E8F0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                session.displayName,
                fontSize = 10.sp,
                color = Color(0xFF6B7280),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            // Mute pill — small, stopPropagation via separate clickable.
            Box(
                modifier = Modifier
                    .background(
                        if (!session.enabled) Color(0xCC7F1D1D) else Color(0xFF0F172A),
                        RoundedCornerShape(4.dp),
                    )
                    .border(
                        1.dp,
                        if (!session.enabled) Color(0xFFB91C1C) else Color(0xFF7F1D1D),
                        RoundedCornerShape(4.dp),
                    )
                    .clickable(onClick = onToggleMute)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    if (!session.enabled) "muted" else "mute",
                    fontSize = 9.sp,
                    color = if (!session.enabled) Color(0xFFFCA5A5) else Color(0xFFFCA5A5),
                )
            }
        }
    }
}

@Composable
private fun CharacterAvatar(character: AttachedCharacter?) {
    val bg = personaColor(character?.persona)
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            character?.displayName?.firstOrNull()?.toString() ?: "?",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

private fun personaColor(persona: String?): Color = when (persona) {
    "methodical" -> Color(0xFF334155)
    "warm" -> Color(0xFFB45309)
    "technical" -> Color(0xFF0E7490)
    "plain" -> Color(0xFF44403C)
    "sarcastic" -> Color(0xFF7E22CE)
    "energetic" -> Color(0xFFC2410C)
    else -> Color(0xFF374151)
}

@Composable
private fun SpotlightPane(
    session: SessionLite,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onToggleMute: () -> Unit,
    onModeChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(Color(0xFF111827), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.attachedCharacter?.displayName ?: "(no character)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE2E8F0),
                )
                Text(
                    "for ${session.displayName}",
                    fontSize = 10.sp,
                    color = Color(0xFF6B7280),
                )
            }
            OutlinedButton(
                onClick = onSetActive,
                enabled = !isActive,
            ) {
                Text(if (isActive) "ACTIVE" else "Make active", fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        // Speaking mode row — Brief / Live segmented row.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("brief", "live", "direct", "trigger").forEach { m ->
                val selected = session.activeMode == m
                OutlinedButton(
                    onClick = { onModeChange(m) },
                ) {
                    Text(
                        m,
                        fontSize = 10.sp,
                        color = if (selected) Color(0xFF22D3EE) else Color(0xFF94A3B8),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onToggleMute) {
            Text(if (!session.enabled) "Unmute" else "Mute", color = Color(0xFFFCA5A5))
        }
    }
}
